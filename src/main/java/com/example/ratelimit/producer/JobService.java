package com.example.ratelimit.producer;

import com.example.ratelimit.config.RateLimitConfig;
import com.example.ratelimit.domain.*;
import com.example.ratelimit.entity.Job;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.UUID;

@ApplicationScoped
public class JobService {

    private static final Logger LOG = Logger.getLogger(JobService.class);

    @Inject
    MessagePublisher publisher;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    RateLimitConfig rateLimitConfig;

    @Inject
    MeterRegistry meterRegistry;

    @Transactional
    public JobSubmitResponse submit(SubmitJobRequest request) {
        if (!rateLimitConfig.providers().containsKey(request.provider())) {
            throw new IllegalArgumentException("Unknown provider: " + request.provider());
        }

        UUID jobId = UUID.randomUUID();
        Priority priority = request.priority() != null ? request.priority() : Priority.NORMAL;
        Instant now = Instant.now();

        ApiRequest apiRequest = new ApiRequest(
                jobId,
                request.provider(),
                request.method(),
                request.targetUrl(),
                request.headers(),
                request.body(),
                priority,
                request.callbackUrl(),
                now
        );

        Job job = new Job();
        job.id = jobId;
        job.provider = request.provider();
        job.status = new JobStatus.Queued().name();
        job.retryCount = 0;
        job.callbackUrl = request.callbackUrl();
        job.createdAt = now;
        job.updatedAt = now;

        try {
            job.request = objectMapper.writeValueAsString(apiRequest);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize request", e);
        }

        job.persist();

        MessageEnvelope envelope = new MessageEnvelope(jobId, request.provider(), now, 0, apiRequest);
        publisher.publish(envelope);

        meterRegistry.counter("jobs_submitted_total",
                "provider", request.provider(),
                "priority", priority.name()).increment();

        long estimatedWait = estimateWaitSeconds(request.provider());
        LOG.infof("Job submitted: id=%s provider=%s", jobId, request.provider());

        return new JobSubmitResponse(jobId, estimatedWait);
    }

    private long estimateWaitSeconds(String provider) {
        var config = rateLimitConfig.providers().get(provider);
        if (config == null) return 0;
        long queueDepth = Job.count("provider = ?1 and status in ('Queued', 'Processing')", provider);
        long requestsPerSecond = config.requestsPerWindow() / Math.max(1, config.windowDuration().toSeconds());
        return requestsPerSecond > 0 ? queueDepth / requestsPerSecond : 0;
    }
}
