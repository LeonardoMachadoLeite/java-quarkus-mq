# java-quarkus-mq — Project Constitution

## Purpose

A Quarkus 3.17 / Java 21 application that buffers outgoing HTTP requests in RabbitMQ and dispatches them at a controlled pace, respecting per-provider rate limits stored in Redis. Clients submit requests and either poll for results or receive webhook callbacks.

---

## Non-Negotiable Architectural Decisions

These three decisions are locked. Do not change them without explicit user approval:

1. **Response strategy**: Both polling (`GET /api/jobs/{id}`) AND webhooks (callback URL). Never remove either path.
2. **Rate limit state**: Redis via Bucket4j `LettuceBasedProxyManager` — distributed, safe under horizontal scaling. Never replace with in-memory or per-instance solutions.
3. **Queue topology**: One queue per API provider (`api.{provider}.requests` + `api.{provider}.priority`). Never collapse providers into a shared queue.

---

## Tech Stack

| Component | Technology | Version |
| --- | --- | --- |
| Runtime | Java 21 + Quarkus | 3.17.4 |
| Messaging | RabbitMQ + SmallRye Reactive Messaging | 3.13 |
| Rate limiting | Bucket4j + Lettuce Redis backend | 8.10.1 / 6.4.0 |
| Persistence | PostgreSQL + Hibernate ORM Panache + Flyway | 16 |
| Cache / rate state | Redis | 7 |
| HTTP dispatch | Java native `HttpClient` (Java 11+) | — |
| Observability | Micrometer + Prometheus + OpenTelemetry | — |
| Testing | JUnit 5, Testcontainers, WireMock, Mockito, Awaitility | — |

**Why native `HttpClient` for dispatch, not Quarkus REST Client Reactive**: dynamic base URIs and no code-gen overhead. Do not migrate to generated REST clients unless the provider list is static.

---

## Package Structure

```text
src/main/java/com/example/ratelimit/
├── config/          RabbitMQTopologyConfig (startup), RateLimitConfig (mapping)
├── domain/          Records: ApiRequest, ApiResponse, MessageEnvelope, SubmitJobRequest
│                    Sealed interface: JobStatus (Pending|Queued|Processing|Completed|Failed|DeadLettered)
│                    Enum: Priority
├── entity/          Job (Panache entity, JSONB columns), DeadLetter (audit)
├── producer/        JobResource (REST), JobService (orchestration), MessagePublisher (AMQP)
├── consumer/        MessageConsumer (one @Incoming method per provider)
├── ratelimit/       RateLimiterService (interface), RateLimiterServiceImpl (Bucket4j+Lettuce)
├── dispatcher/      HttpDispatcher, DispatchResult (sealed: Success|RateLimited|Error)
├── webhook/         WebhookSender (HMAC-SHA256 signed, 3-attempt retry)
└── observability/   QueueDepthHealthCheck, RabbitMQHealthCheck, RedisHealthCheck, MetricsCollector
```

**JSONB columns**: `Job.request` / `Job.response` are pre-serialized JSON `String`s mapped to `jsonb` columns. The datasource sets `jdbc.additional-jdbc-properties.stringtype=unspecified` so PostgreSQL casts the bound `varchar` into `jsonb`. Without it, inserts fail with *"column ... is of type jsonb but expression is of type character varying"*. Keep this property when changing datasource config.

---

## Running Locally

### Prerequisites

- Docker Desktop running
- JDK 21 (`java -version` → `21.x`)
- Maven 3.9+ (`mvn -version`)

### Start infrastructure (infra only, no app container)

```bash
docker-compose up -d rabbitmq postgres redis
```

Wait for all three to report `healthy`:

```bash
docker-compose ps
```

### Dev mode (auto-reload, dev services not used — infra must be up)

```bash
mvn quarkus:dev
```

App starts on `http://localhost:8080`.

### Full stack via Docker Compose (builds app image)

```bash
docker-compose up --build
```

---

## Adding a New Provider (4-Step Checklist)

When the user asks to add a provider (e.g., `twilio`), complete all four steps in order:

### Step 1 — application.yaml: rate limit config

```yaml
rate-limit:
  providers:
    twilio:
      requests-per-window: 100
      window-duration: PT1S
      burst-capacity: 20
      retry-delay: PT500MS
      max-retries: 5
      max-retry-delay: PT30S
```

### Step 2 — application.yaml: SmallRye incoming channel

```yaml
mp:
  messaging:
    incoming:
      twilio-requests:
        connector: smallrye-rabbitmq
        exchange:
          name: api.requests
          type: topic
          durable: true
        queue:
          name: api.twilio.requests
          durable: true
          x-dead-letter-exchange: api.dlx
          x-message-ttl: 3600000
          x-max-length: 100000
        binding:
          routing-key: provider.twilio
        failure-strategy: reject
```

### Step 3 — MessageConsumer.java: add @Incoming method

```java
@Incoming("twilio-requests")
@Blocking(ordered = false)
public CompletionStage<Void> consumeTwilio(Message<JsonObject> message) {
    return processMessage(message);
}
```

The payload type is `io.vertx.core.json.JsonObject`, **not** `String`: the publisher sends messages with content-type `application/json`, so the SmallRye RabbitMQ connector hands the consumer a `JsonObject`. `handleMessage` re-encodes it (`getPayload().encode()`) before passing to Jackson. Declaring `Message<String>` causes a `ClassCastException` at delivery.

**Step 4 — Verify topology** `RabbitMQTopologyConfig` reads `rateLimitConfig.providers()` at startup and declares queues automatically. No code change needed there.

---

## Testing Strategy

| Layer | Tool | What to test |
| --- | --- | --- |
| Unit | JUnit 5 + Mockito + `@PanacheMock` | Rate limiter logic, state machine transitions, serialization |
| Integration | `@QuarkusTest` + Testcontainers (auto via `%test` profile) | Full flow end-to-end |
| API simulation | WireMock | 200 OK, 429 with `Retry-After`, 5xx errors |

### Key scenarios every integration test file should cover

1. Happy path: submit → queue → dispatch → poll → Completed
2. Happy path: submit with `callbackUrl` → webhook received
3. Rate limit hit → consumer backs off → eventual success
4. Max retries exceeded → status `DeadLettered`
5. External 429 with `Retry-After` header respected
6. RabbitMQ disconnect/reconnect: consumer resumes

### Test profile (`%test` in application.yaml)

All three backends run as throwaway containers via Quarkus DevServices — `mvn test` / `mvn verify` need no manually-started infra (only Docker):

- PostgreSQL: Quarkus DevServices (Testcontainers) — no real DB needed
- Redis: Quarkus DevServices (Testcontainers)
- RabbitMQ: Quarkus DevServices (Testcontainers) — auto-started by `quarkus-messaging-rabbitmq`

**Config layout that makes this work**: connection endpoints (datasource `jdbc.url`/credentials, `redis.hosts`, `rabbitmq-host`/port/credentials) live **only under the `%dev` and `%prod` profiles**, never in the shared base config. A static endpoint in the base is inherited by `%test` and shadows the DevServices container, causing connection/auth failures. The `%test` profile sets only `devservices.enabled: true` and test-specific `rate-limit` providers.

### Running tests

`*IT` integration tests run under the **maven-failsafe-plugin** (bound to `integration-test`/`verify`), so use `mvn verify` to exercise them — `mvn test` (surefire) only runs the unit `*Test` classes and skips `*IT`. Note `JobResourceIT` is a `@QuarkusTest` (in-JVM with DevServices), not a `@QuarkusIntegrationTest`.

---

## Java 21 Conventions in This Codebase

- **Records** for all domain data (`ApiRequest`, `ApiResponse`, `MessageEnvelope`, `SubmitJobRequest`)
- **Sealed interfaces** for state machines (`JobStatus`, `DispatchResult`)
- **Pattern matching `switch`** when switching on sealed types (see `dispatchAndComplete` in `MessageConsumer`)
- **Virtual threads** via `@Blocking(ordered = false)` on `@Incoming` methods — do not use `@Blocking` without `ordered = false`
- **`ConcurrentHashMap`** for any shared mutable state (e.g., `configCache` in `RateLimiterServiceImpl`)

---

## Prometheus Metrics Defined in This Codebase

| Metric | Type | Labels |
| --- | --- | --- |
| `jobs_submitted_total` | Counter | `provider`, `priority` |
| `jobs_completed_total` | Counter | `provider`, `status` |
| `jobs_processing_duration_seconds` | Timer | `provider` |
| `rate_limit_hits_total` | Counter | `provider` |
| `webhook_sent_total` | Counter | `provider`, `result` |
| `queue_depth` | Gauge | `provider` |
| `rate_limit_tokens_available` | Gauge | `provider` |

When adding new metrics, always include a `provider` label. Register in `MetricsCollector` for gauges; inline via `MeterRegistry` injection for event counters/timers.

---

## Known Simplifications

These are intentional deviations from a more complete production design, made during initial implementation:

- **Delayed requeue**: Rate-limited jobs are republished to the queue immediately rather than using a TTL/DLX backoff queue. Production upgrade path: declare per-delay TTL queues that expire via DLX back to the main provider queue (requires the RabbitMQ delayed message plugin or one queue per backoff tier).
- **Structured Concurrency deferred**: Java 21 Structured Concurrency requires preview flags; replaced with `Executors.newVirtualThreadPerTaskExecutor()` for virtual thread fan-out.
- **Load tests excluded**: No Gatling module is included. Load testing requires a separate Maven module and a running environment; add when needed.

---

## Banned Patterns

- **In-memory rate limit state** — must use Redis (architectural decision #2)
- **Shared queues across providers** — each provider gets its own queues (architectural decision #3)
- **Synchronous dispatch on the reactive thread** — always `@Blocking(ordered = false)` on consumers
- **Calling the RabbitMQ emitter inside an active `@Transactional`** — the send runs on a Vert.x context and corrupts the JTA connection/transaction association (`Enlisted connection used without active transaction`). Persist inside a transaction (e.g. `QuarkusTransaction.requiringNew().call(...)`), then `publisher.publish(...)` **after** it commits. See `JobService.submit`.
- **Connection endpoints in the base config profile** — datasource/Redis/RabbitMQ hosts belong under `%dev`/`%prod` only, so `%test` falls through to DevServices
- **Polling-only or webhook-only response** — both paths must exist (architectural decision #1)
- **Amending existing commits** — always create new commits
- **`--no-verify`** on git hooks
