package com.example.ratelimit.observability;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;

import java.net.InetSocketAddress;
import java.net.Socket;

@Liveness
@ApplicationScoped
public class RabbitMQHealthCheck implements HealthCheck {

    @ConfigProperty(name = "rabbitmq-host", defaultValue = "localhost")
    String host;

    @ConfigProperty(name = "rabbitmq-port", defaultValue = "5672")
    int port;

    @Override
    public HealthCheckResponse call() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 2000);
            return HealthCheckResponse.named("rabbitmq")
                    .up()
                    .withData("host", host)
                    .withData("port", port)
                    .build();
        } catch (Exception e) {
            return HealthCheckResponse.named("rabbitmq")
                    .down()
                    .withData("error", e.getMessage())
                    .build();
        }
    }
}
