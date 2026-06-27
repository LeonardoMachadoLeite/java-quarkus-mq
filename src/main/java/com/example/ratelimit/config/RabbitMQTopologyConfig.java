package com.example.ratelimit.config;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

@ApplicationScoped
public class RabbitMQTopologyConfig {

    private static final Logger LOG = Logger.getLogger(RabbitMQTopologyConfig.class);

    @Inject
    RateLimitConfig rateLimitConfig;

    @ConfigProperty(name = "quarkus.rabbitmq-host", defaultValue = "localhost")
    String host;

    @ConfigProperty(name = "quarkus.rabbitmq-port", defaultValue = "5672")
    int port;

    @ConfigProperty(name = "quarkus.rabbitmq-username", defaultValue = "guest")
    String username;

    @ConfigProperty(name = "quarkus.rabbitmq-password", defaultValue = "guest")
    String password;

    void onStart(@Observes StartupEvent event) {
        try {
            declareTopology();
        } catch (Exception e) {
            LOG.warnf("Could not declare RabbitMQ topology at startup (will retry on connection): %s", e.getMessage());
        }
    }

    private void declareTopology() throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(host);
        factory.setPort(port);
        factory.setUsername(username);
        factory.setPassword(password);

        try (Connection connection = factory.newConnection();
             Channel channel = connection.createChannel()) {

            // Dead-letter exchange and queue
            channel.exchangeDeclare("api.dlx", "fanout", true);
            channel.queueDeclare("api.dead-letter", true, false, false, null);
            channel.queueBind("api.dead-letter", "api.dlx", "#");

            // Main topic exchange
            channel.exchangeDeclare("api.requests", "topic", true);

            // Per-provider queues
            for (String provider : rateLimitConfig.providers().keySet()) {
                declareProviderQueues(channel, provider);
            }

            LOG.info("RabbitMQ topology declared successfully");
        }
    }

    private void declareProviderQueues(Channel channel, String provider) throws IOException {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", "api.dlx");
        args.put("x-message-ttl", 3_600_000L);
        args.put("x-max-length", 100_000L);

        String requestQueue = "api." + provider + ".requests";
        channel.queueDeclare(requestQueue, true, false, false, args);
        channel.queueBind(requestQueue, "api.requests", "provider." + provider);

        String priorityQueue = "api." + provider + ".priority";
        channel.queueDeclare(priorityQueue, true, false, false, args);
        channel.queueBind(priorityQueue, "api.requests", "provider." + provider + ".priority");

        LOG.infof("Declared queues for provider: %s", provider);
    }
}
