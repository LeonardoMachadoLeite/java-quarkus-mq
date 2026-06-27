package com.example.ratelimit.producer;

import com.example.ratelimit.domain.MessageEnvelope;
import com.example.ratelimit.domain.Priority;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.reactive.messaging.rabbitmq.OutgoingRabbitMQMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;

import java.util.concurrent.CompletableFuture;

@ApplicationScoped
public class MessagePublisher {

    private static final Logger LOG = Logger.getLogger(MessagePublisher.class);

    @Inject
    @Channel("api-requests")
    Emitter<String> emitter;

    @Inject
    ObjectMapper objectMapper;

    public void publish(MessageEnvelope envelope) {
        try {
            String json = objectMapper.writeValueAsString(envelope);
            String routingKey = buildRoutingKey(envelope);

            OutgoingRabbitMQMetadata metadata = OutgoingRabbitMQMetadata.builder()
                    .withRoutingKey(routingKey)
                    .withContentType("application/json")
                    .withHeader("X-Job-Id", envelope.jobId().toString())
                    .withHeader("X-Provider", envelope.provider())
                    .withHeader("X-Retry-Count", String.valueOf(envelope.retryCount()))
                    .build();

            Message<String> message = Message.of(json)
                    .addMetadata(metadata)
                    .withAck(() -> {
                        LOG.debugf("Message acked for job %s", envelope.jobId());
                        return CompletableFuture.completedFuture(null);
                    })
                    .withNack(t -> {
                        LOG.errorf(t, "Message nacked for job %s", envelope.jobId());
                        return CompletableFuture.completedFuture(null);
                    });

            emitter.send(message);
            LOG.infof("Published message for job %s to provider %s (retry=%d)",
                    envelope.jobId(), envelope.provider(), envelope.retryCount());
        } catch (Exception e) {
            throw new RuntimeException("Failed to publish message for job " + envelope.jobId(), e);
        }
    }

    private String buildRoutingKey(MessageEnvelope envelope) {
        if (envelope.payload() != null && envelope.payload().priority() == Priority.HIGH) {
            return "provider." + envelope.provider() + ".priority";
        }
        return "provider." + envelope.provider();
    }
}
