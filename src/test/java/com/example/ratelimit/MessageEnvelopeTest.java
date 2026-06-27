package com.example.ratelimit;

import com.example.ratelimit.domain.ApiRequest;
import com.example.ratelimit.domain.MessageEnvelope;
import com.example.ratelimit.domain.Priority;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MessageEnvelopeTest {

    @Test
    void withIncrementedRetry_incrementsCount() {
        UUID jobId = UUID.randomUUID();
        ApiRequest req = new ApiRequest(jobId, "github", "GET", "http://example.com",
                Map.of(), null, Priority.NORMAL, null, Instant.now());
        MessageEnvelope envelope = new MessageEnvelope(jobId, "github", Instant.now(), 0, req);

        MessageEnvelope retried = envelope.withIncrementedRetry();

        assertEquals(1, retried.retryCount());
        assertEquals(jobId, retried.jobId());
        assertEquals("github", retried.provider());
    }

    @Test
    void apiRequest_nullPriority_defaultsToNormal() {
        ApiRequest req = new ApiRequest(null, "stripe", "POST", "http://api.stripe.com",
                Map.of(), "{}", null, null, null);
        assertEquals(Priority.NORMAL, req.priority());
        assertNotNull(req.jobId());
        assertNotNull(req.submittedAt());
    }

    @Test
    void apiRequest_blankProvider_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                new ApiRequest(null, "", "GET", "http://example.com",
                        Map.of(), null, Priority.NORMAL, null, null));
    }
}
