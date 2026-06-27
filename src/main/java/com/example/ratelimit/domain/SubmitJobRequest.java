package com.example.ratelimit.domain;

import java.util.Map;

public record SubmitJobRequest(
        String provider,
        String method,
        String targetUrl,
        Map<String, String> headers,
        String body,
        Priority priority,
        String callbackUrl
) {}
