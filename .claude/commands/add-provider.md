# Add Provider

Add a new API provider to the rate-limit queue system. Arguments: $ARGUMENTS

The argument is the provider name in lowercase (e.g., `twilio`, `sendgrid`, `openai`).

If no argument is given, ask the user for the provider name before proceeding.

## Information to Collect

Before writing any code, ask the user for these values (or use sensible defaults if they say "use defaults"):

| Field | Description | Default |
|---|---|---|
| `requests-per-window` | Max requests in the window | 100 |
| `window-duration` | ISO-8601 duration (e.g., `PT1S`, `PT1H`) | `PT1S` |
| `burst-capacity` | Initial token count (burst allowance) | 20 |
| `retry-delay` | Base delay before first retry (ISO-8601) | `PT500MS` |
| `max-retries` | Max retry attempts before DeadLettered | 5 |
| `max-retry-delay` | Cap on exponential backoff (ISO-8601) | `PT30S` |

## 4-Step Implementation

Complete ALL four steps. Do not skip any.

### Step 1 — `application.yaml`: rate-limit config

Add under `rate-limit.providers`:
```yaml
rate-limit:
  providers:
    <provider>:
      requests-per-window: <value>
      window-duration: <value>
      burst-capacity: <value>
      retry-delay: <value>
      max-retries: <value>
      max-retry-delay: <value>
```

### Step 2 — `application.yaml`: SmallRye incoming channel

Add under `mp.messaging.incoming`:
```yaml
mp:
  messaging:
    incoming:
      <provider>-requests:
        connector: smallrye-rabbitmq
        exchange:
          name: api.requests
          type: topic
          durable: true
        queue:
          name: api.<provider>.requests
          durable: true
          x-dead-letter-exchange: api.dlx
          x-message-ttl: 3600000
          x-max-length: 100000
        binding:
          routing-key: provider.<provider>
        failure-strategy: reject
```

### Step 3 — `MessageConsumer.java`: add @Incoming method

Add a new method following the exact pattern of existing provider methods:
```java
@Incoming("<provider>-requests")
@Blocking(ordered = false)
public CompletionStage<Void> consume<ProvidePascalCase>(Message<String> message) {
    return processMessage(message);
}
```

File: `src/main/java/com/example/ratelimit/consumer/MessageConsumer.java`

### Step 4 — Verify topology (no code change needed)

`RabbitMQTopologyConfig.onStart()` reads `rateLimitConfig.providers()` and calls `declareProviderQueues()` for every key. Since Step 1 adds the provider to the config, the topology is automatically declared on next startup. Confirm this to the user.

## Post-Implementation Checklist

After completing all four steps, tell the user:

1. Restart the app (`mvn quarkus:dev` picks up config changes automatically in dev mode).
2. Verify queue creation in RabbitMQ management UI: `http://localhost:15672` → Queues → look for `api.<provider>.requests` and `api.<provider>.priority`.
3. Submit a test job: `POST /api/jobs` with `"provider": "<provider>"`.
4. Confirm metrics appear: `curl http://localhost:8080/q/metrics | grep <provider>`.
