package com.example.ratelimit.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ApiRequest(
        UUID jobId,
        String provider,
        String method,
        String targetUrl,
        Map<String, String> headers,
        String body,
        Priority priority,
        String callbackUrl,
        Instant submittedAt
) {
    public ApiRequest {
        if (provider == null || provider.isBlank()) throw new IllegalArgumentException("provider required");
        if (method == null || method.isBlank()) throw new IllegalArgumentException("method required");
        if (targetUrl == null || targetUrl.isBlank()) throw new IllegalArgumentException("targetUrl required");
        if (priority == null) priority = Priority.NORMAL;
        if (submittedAt == null) submittedAt = Instant.now();
        if (jobId == null) jobId = UUID.randomUUID();
    }
}
