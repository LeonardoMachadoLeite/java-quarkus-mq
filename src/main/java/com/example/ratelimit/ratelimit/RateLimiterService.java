package com.example.ratelimit.ratelimit;

import java.time.Instant;

public interface RateLimiterService {
    boolean tryConsume(String provider);
    long availableTokens(String provider);
    Instant resetAt(String provider);
    void forceRefill(String provider);
}
