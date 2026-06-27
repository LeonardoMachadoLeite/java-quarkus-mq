package com.example.ratelimit.domain;

import java.time.Instant;
import java.util.UUID;

public record MessageEnvelope(
        UUID jobId,
        String provider,
        Instant submittedAt,
        int retryCount,
        ApiRequest payload
) {
    public MessageEnvelope withIncrementedRetry() {
        return new MessageEnvelope(jobId, provider, submittedAt, retryCount + 1, payload);
    }
}
