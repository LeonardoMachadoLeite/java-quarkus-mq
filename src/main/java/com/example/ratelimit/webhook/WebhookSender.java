package com.example.ratelimit.webhook;

import com.example.ratelimit.domain.ApiResponse;
import com.example.ratelimit.entity.Job;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

@ApplicationScoped
public class WebhookSender {

    private static final Logger LOG = Logger.getLogger(WebhookSender.class);
    private static final int MAX_ATTEMPTS = 3;
    private static final Duration RETRY_DELAY = Duration.ofSeconds(2);
    private static final String HMAC_SECRET = "webhook-secret-change-in-production";

    @Inject
    ObjectMapper objectMapper;

    @Inject
    MeterRegistry meterRegistry;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .executor(Executors.newVirtualThreadPerTaskExecutor())
            .build();

    public void sendAsync(Job job, ApiResponse apiResponse) {
        CompletableFuture.runAsync(() -> send(job, apiResponse),
                Executors.newVirtualThreadPerTaskExecutor());
    }

    private void send(Job job, ApiResponse apiResponse) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "jobId", job.id.toString(),
                    "status", job.status,
                    "provider", job.provider,
                    "response", apiResponse
            ));

            String signature = computeHmac(payload);
            boolean sent = false;

            for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                try {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(job.callbackUrl))
                            .POST(HttpRequest.BodyPublishers.ofString(payload))
                            .header("Content-Type", "application/json")
                            .header("X-Job-Id", job.id.toString())
                            .header("X-Signature", "sha256=" + signature)
                            .timeout(Duration.ofSeconds(10))
                            .build();

                    HttpResponse<String> response = httpClient.send(request,
                            HttpResponse.BodyHandlers.ofString());

                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        meterRegistry.counter("webhook_sent_total",
                                "provider", job.provider, "result", "success").increment();
                        LOG.infof("Webhook sent for job %s to %s (attempt %d)", job.id, job.callbackUrl, attempt);
                        sent = true;
                        break;
                    } else {
                        LOG.warnf("Webhook attempt %d failed for job %s: HTTP %d", attempt, job.id, response.statusCode());
                    }
                } catch (Exception e) {
                    LOG.warnf(e, "Webhook attempt %d failed for job %s", attempt, job.id);
                }

                if (attempt < MAX_ATTEMPTS) {
                    Thread.sleep(RETRY_DELAY.toMillis());
                }
            }

            if (!sent) {
                meterRegistry.counter("webhook_sent_total",
                        "provider", job.provider, "result", "failure").increment();
                LOG.errorf("Webhook delivery failed for job %s after %d attempts", job.id, MAX_ATTEMPTS);
            }
        } catch (Exception e) {
            LOG.errorf(e, "Webhook sender error for job %s", job.id);
        }
    }

    private String computeHmac(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(HMAC_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            LOG.warnf(e, "Failed to compute HMAC signature");
            return "";
        }
    }
}
