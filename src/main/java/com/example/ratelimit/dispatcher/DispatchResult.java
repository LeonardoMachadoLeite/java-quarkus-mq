package com.example.ratelimit.dispatcher;

import com.example.ratelimit.domain.ApiResponse;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

public sealed interface DispatchResult permits
        DispatchResult.Success,
        DispatchResult.RateLimited,
        DispatchResult.Error {

    record Success(ApiResponse response) implements DispatchResult {}
    record RateLimited(UUID jobId, Optional<Duration> retryAfter) implements DispatchResult {}
    record Error(ApiResponse response, String reason) implements DispatchResult {}

    static DispatchResult success(ApiResponse response) {
        return new Success(response);
    }

    static DispatchResult rateLimited(UUID jobId, Optional<Duration> retryAfter) {
        return new RateLimited(jobId, retryAfter);
    }

    static DispatchResult error(ApiResponse response, String reason) {
        return new Error(response, reason);
    }
}
