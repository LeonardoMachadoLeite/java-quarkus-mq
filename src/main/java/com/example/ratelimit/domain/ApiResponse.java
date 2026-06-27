package com.example.ratelimit.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ApiResponse(
        UUID jobId,
        int statusCode,
        String body,
        Map<String, String> headers,
        Instant completedAt
) {}
