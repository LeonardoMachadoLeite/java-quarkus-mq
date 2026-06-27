package com.example.ratelimit.observability;

import com.example.ratelimit.entity.Job;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

@Readiness
@ApplicationScoped
public class QueueDepthHealthCheck implements HealthCheck {

    private static final long MAX_QUEUE_DEPTH = 50_000;

    @Override
    public HealthCheckResponse call() {
        try {
            long queueDepth = Job.count("status in ('Queued', 'Processing')");
            boolean healthy = queueDepth < MAX_QUEUE_DEPTH;
            return HealthCheckResponse.named("queue-depth")
                    .status(healthy)
                    .withData("depth", queueDepth)
                    .withData("threshold", MAX_QUEUE_DEPTH)
                    .build();
        } catch (Exception e) {
            return HealthCheckResponse.named("queue-depth")
                    .down()
                    .withData("error", e.getMessage())
                    .build();
        }
    }
}
