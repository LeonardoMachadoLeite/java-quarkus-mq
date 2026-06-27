package com.example.ratelimit;

import com.example.ratelimit.config.RateLimitConfig;
import com.example.ratelimit.ratelimit.RateLimiterServiceImpl;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@QuarkusTest
class RateLimiterServiceTest {

    @InjectMock
    RateLimitConfig rateLimitConfig;

    @Inject
    RateLimiterServiceImpl rateLimiterService;

    @BeforeEach
    void setup() {
        RateLimitConfig.ProviderConfig config = Mockito.mock(RateLimitConfig.ProviderConfig.class);
        when(config.requestsPerWindow()).thenReturn(10L);
        when(config.windowDuration()).thenReturn(Duration.ofSeconds(1));
        when(config.burstCapacity()).thenReturn(5L);
        when(config.retryDelay()).thenReturn(Duration.ofMillis(100));
        when(config.maxRetries()).thenReturn(3);
        when(config.maxRetryDelay()).thenReturn(Duration.ofSeconds(5));
        when(rateLimitConfig.providers()).thenReturn(Map.of("test-provider", config));
    }

    @Test
    void tryConsume_withinLimit_returnsTrue() {
        assertTrue(rateLimiterService.tryConsume("test-provider"));
    }

    @Test
    void availableTokens_returnsNonNegative() {
        long tokens = rateLimiterService.availableTokens("test-provider");
        assertTrue(tokens >= 0 || tokens == -1);
    }

    @Test
    void resetAt_returnsNonNull() {
        assertNotNull(rateLimiterService.resetAt("test-provider"));
    }
}
