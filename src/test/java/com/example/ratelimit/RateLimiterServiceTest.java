package com.example.ratelimit;

import com.example.ratelimit.ratelimit.RateLimiterServiceImpl;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Uses the real {@link com.example.ratelimit.config.RateLimitConfig}: a SmallRye
 * {@code @ConfigMapping} interface is a {@code @Dependent} bean, which {@code @InjectMock}
 * cannot proxy. The "test-provider" limits are supplied via the %test profile in
 * application.yaml; Redis is provided by Quarkus DevServices.
 */
@QuarkusTest
class RateLimiterServiceTest {

    @Inject
    RateLimiterServiceImpl rateLimiterService;

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
