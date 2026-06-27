package com.example.ratelimit.producer;

import java.util.UUID;

public record JobSubmitResponse(UUID jobId, long estimatedWaitSeconds) {}
