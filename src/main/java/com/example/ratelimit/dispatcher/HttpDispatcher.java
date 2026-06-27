package com.example.ratelimit.dispatcher;

import com.example.ratelimit.domain.ApiRequest;
import com.example.ratelimit.domain.ApiResponse;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;

@ApplicationScoped
public class HttpDispatcher {

    private static final Logger LOG = Logger.getLogger(HttpDispatcher.class);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .executor(Executors.newVirtualThreadPerTaskExecutor())
            .build();

    public DispatchResult dispatch(ApiRequest request) {
        LOG.infof("Dispatching job %s to %s %s", request.jobId(), request.method(), request.targetUrl());

        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(request.targetUrl()))
                    .timeout(Duration.ofSeconds(60));

            // Set request headers
            if (request.headers() != null) {
                request.headers().forEach(builder::header);
            }
            builder.header("X-Job-Id", request.jobId().toString());
            builder.header("X-Request-Id", request.jobId().toString());

            String body = request.body() != null ? request.body() : "";
            HttpRequest.BodyPublisher bodyPublisher = body.isEmpty()
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body);

            builder.method(request.method().toUpperCase(), bodyPublisher);

            HttpResponse<String> response = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString());

            int status = response.statusCode();
            String responseBody = response.body();
            Map<String, String> headers = extractResponseHeaders(response);
            Instant completedAt = Instant.now();

            if (status == 429) {
                Optional<Duration> retryAfter = parseRetryAfter(response);
                LOG.infof("Rate limited by external API for job %s, retryAfter=%s", request.jobId(), retryAfter);
                return DispatchResult.rateLimited(request.jobId(), retryAfter);
            }

            ApiResponse apiResponse = new ApiResponse(request.jobId(), status, responseBody, headers, completedAt);

            if (status >= 200 && status < 300) {
                return DispatchResult.success(apiResponse);
            } else {
                return DispatchResult.error(apiResponse, "HTTP " + status);
            }

        } catch (Exception e) {
            LOG.errorf(e, "Dispatch failed for job %s", request.jobId());
            return DispatchResult.error(
                    new ApiResponse(request.jobId(), 0, e.getMessage(), Map.of(), Instant.now()),
                    e.getMessage()
            );
        }
    }

    private Map<String, String> extractResponseHeaders(HttpResponse<String> response) {
        Map<String, String> headers = new HashMap<>();
        response.headers().map().forEach((k, v) -> {
            if (!v.isEmpty()) headers.put(k, v.get(0));
        });
        return headers;
    }

    private Optional<Duration> parseRetryAfter(HttpResponse<String> response) {
        return response.headers().firstValue("Retry-After").map(val -> {
            try {
                return Duration.ofSeconds(Long.parseLong(val));
            } catch (NumberFormatException e) {
                return null;
            }
        });
    }
}
