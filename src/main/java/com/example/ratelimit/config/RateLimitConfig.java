package com.example.ratelimit.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

import java.time.Duration;
import java.util.Map;

@ConfigMapping(prefix = "rate-limit")
public interface RateLimitConfig {

    Map<String, ProviderConfig> providers();

    interface ProviderConfig {
        @WithName("requests-per-window")
        long requestsPerWindow();

        @WithName("window-duration")
        Duration windowDuration();

        @WithName("burst-capacity")
        long burstCapacity();

        @WithName("retry-delay")
        Duration retryDelay();

        @WithName("max-retries")
        int maxRetries();

        @WithName("max-retry-delay")
        Duration maxRetryDelay();
    }
}
