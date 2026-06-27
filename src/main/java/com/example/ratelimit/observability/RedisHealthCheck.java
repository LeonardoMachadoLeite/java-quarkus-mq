package com.example.ratelimit.observability;

import io.quarkus.redis.client.RedisClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

import java.util.List;

@Readiness
@ApplicationScoped
public class RedisHealthCheck implements HealthCheck {

    @Inject
    RedisClient redisClient;

    @Override
    public HealthCheckResponse call() {
        try {
            io.vertx.redis.client.Response response = redisClient.ping(List.of());
            String pong = response != null ? response.toString() : "";
            boolean up = "PONG".equalsIgnoreCase(pong);
            return HealthCheckResponse.named("redis")
                    .status(up)
                    .withData("response", pong)
                    .build();
        } catch (Exception e) {
            return HealthCheckResponse.named("redis")
                    .down()
                    .withData("error", e.getMessage())
                    .build();
        }
    }
}
