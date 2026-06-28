package com.example.ratelimit.consumer;

import com.example.ratelimit.config.RateLimitConfig;
import com.example.ratelimit.dispatcher.DispatchResult;
import com.example.ratelimit.dispatcher.HttpDispatcher;
import com.example.ratelimit.domain.JobStatus;
import com.example.ratelimit.domain.MessageEnvelope;
import com.example.ratelimit.entity.DeadLetter;
import com.example.ratelimit.entity.Job;
import com.example.ratelimit.producer.MessagePublisher;
import com.example.ratelimit.ratelimit.RateLimiterService;
import com.example.ratelimit.webhook.WebhookSender;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.smallrye.reactive.messaging.annotations.Blocking;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

@ApplicationScoped
public class MessageConsumer {

    private static final Logger LOG = Logger.getLogger(MessageConsumer.class);

    @Inject
    RateLimiterService rateLimiterService;

    @Inject
    HttpDispatcher httpDispatcher;

    @Inject
    WebhookSender webhookSender;

    @Inject
    MessagePublisher publisher;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    RateLimitConfig rateLimitConfig;

    @Inject
    MeterRegistry meterRegistry;

    @Incoming("github-requests")
    @Blocking(ordered = false)
    public CompletionStage<Void> consumeGithub(Message<JsonObject> message) {
        return processMessage(message);
    }

    @Incoming("stripe-requests")
    @Blocking(ordered = false)
    public CompletionStage<Void> consumeStripe(Message<JsonObject> message) {
        return processMessage(message);
    }

    private CompletionStage<Void> processMessage(Message<JsonObject> message) {
        return message.getPayload() != null
                ? handleMessage(message)
                : message.ack();
    }

    private CompletionStage<Void> handleMessage(Message<JsonObject> message) {
        // The RabbitMQ connector delivers an application/json body as a Vert.x JsonObject,
        // not a String, so re-encode it before handing it to Jackson (which owns the
        // record/Instant mapping for MessageEnvelope).
        MessageEnvelope envelope;
        try {
            envelope = objectMapper.readValue(message.getPayload().encode(), MessageEnvelope.class);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to deserialize message, rejecting");
            return message.nack(e);
        }

        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            process(envelope);
            sample.stop(meterRegistry.timer("jobs_processing_duration_seconds",
                    "provider", envelope.provider()));
            return message.ack();
        } catch (Exception e) {
            LOG.errorf(e, "Unhandled error processing job %s", envelope.jobId());
            return message.nack(e);
        }
    }

    @Transactional
    void process(MessageEnvelope envelope) {
        UUID jobId = envelope.jobId();
        String provider = envelope.provider();

        Job job = Job.findByIdOrNull(jobId);
        if (job == null) {
            LOG.warnf("Job %s not found in DB, skipping", jobId);
            return;
        }

        job.updateStatus(new JobStatus.Processing());

        if (rateLimiterService.tryConsume(provider)) {
            dispatchAndComplete(job, envelope);
        } else {
            handleRateLimitExceeded(job, envelope);
        }
    }

    private void dispatchAndComplete(Job job, MessageEnvelope envelope) {
        DispatchResult result = httpDispatcher.dispatch(envelope.payload());

        switch (result) {
            case DispatchResult.Success(var apiResponse) -> {
                try {
                    job.response = objectMapper.writeValueAsString(apiResponse);
                } catch (Exception e) {
                    LOG.errorf(e, "Failed to serialize response for job %s", job.id);
                }
                job.updateStatus(new JobStatus.Completed());
                meterRegistry.counter("jobs_completed_total",
                        "provider", envelope.provider(), "status", "Completed").increment();

                if (job.callbackUrl != null && !job.callbackUrl.isBlank()) {
                    webhookSender.sendAsync(job, apiResponse);
                }
                LOG.infof("Job %s completed successfully", job.id);
            }

            case DispatchResult.RateLimited(var jobId, var retryAfter) -> {
                // External API returned 429 — treat same as internal rate limit
                retryAfter.ifPresent(d -> LOG.infof("External 429 for job %s, Retry-After=%s", jobId, d));
                handleRateLimitExceeded(job, envelope);
            }

            case DispatchResult.Error(var apiResponse, var reason) -> {
                try {
                    job.response = objectMapper.writeValueAsString(apiResponse);
                } catch (Exception e) {
                    LOG.errorf(e, "Failed to serialize error response for job %s", job.id);
                }
                job.updateStatus(new JobStatus.Failed(reason));
                job.errorMessage = reason;
                meterRegistry.counter("jobs_completed_total",
                        "provider", envelope.provider(), "status", "Failed").increment();
                LOG.warnf("Job %s failed: %s", job.id, reason);
            }
        }
    }

    private void handleRateLimitExceeded(Job job, MessageEnvelope envelope) {
        var providerConfig = rateLimitConfig.providers().get(envelope.provider());
        int nextRetry = envelope.retryCount() + 1;

        if (nextRetry > providerConfig.maxRetries()) {
            job.updateStatus(new JobStatus.DeadLettered("Max retries exceeded"));
            job.errorMessage = "Max retries exceeded after " + envelope.retryCount() + " attempts";
            persistDeadLetter(job, envelope);
            meterRegistry.counter("jobs_completed_total",
                    "provider", envelope.provider(), "status", "DeadLettered").increment();
            LOG.warnf("Job %s dead-lettered after %d retries", job.id, envelope.retryCount());
        } else {
            job.retryCount = nextRetry;
            job.updateStatus(new JobStatus.Queued());
            scheduleRequeue(envelope.withIncrementedRetry(), providerConfig);
            LOG.infof("Job %s rate limited, scheduling retry %d/%d",
                    job.id, nextRetry, providerConfig.maxRetries());
        }
    }

    private void scheduleRequeue(MessageEnvelope envelope, RateLimitConfig.ProviderConfig config) {
        Duration delay = computeBackoff(envelope.retryCount(), config);
        // Publish with delay — actual delayed delivery handled by publisher + TTL queue
        // For simplicity in this implementation, we republish immediately;
        // production deployments should use a delay queue with TTL expiry via DLX
        publisher.publish(envelope);
        LOG.debugf("Requeued job %s (retry=%d, backoff=%s)", envelope.jobId(), envelope.retryCount(), delay);
    }

    private Duration computeBackoff(int retryCount, RateLimitConfig.ProviderConfig config) {
        long baseMs = config.retryDelay().toMillis();
        long backoffMs = (long) (baseMs * Math.pow(2, retryCount - 1));
        return Duration.ofMillis(Math.min(backoffMs, config.maxRetryDelay().toMillis()));
    }

    @Transactional
    void persistDeadLetter(Job job, MessageEnvelope envelope) {
        DeadLetter dl = new DeadLetter();
        dl.jobId = job.id;
        dl.provider = envelope.provider();
        dl.retryCount = envelope.retryCount();
        dl.requestPayload = job.request;
        dl.errorMessage = job.errorMessage;
        dl.deadLetteredAt = Instant.now();
        dl.persist();
    }
}
