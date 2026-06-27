# API Rate-Limit Queue Manager

A Quarkus service that accepts arbitrary HTTP requests, queues them in RabbitMQ, and dispatches them to external APIs while strictly honouring per-provider rate limits enforced through Bucket4j token buckets stored in Redis.

## Architecture

```text
[Client]
   │
   ▼
[REST API  — POST /api/jobs]
   │  returns jobId (202 Accepted)
   ▼
[RabbitMQ — exchange: api.requests (topic)]
   ├── Queue: api.github.requests   (routing key: provider.github)
   ├── Queue: api.stripe.requests   (routing key: provider.stripe)
   └── Exchange: api.dlx → Queue: api.dead-letter
   │
   ▼
[Consumer — virtual thread per message]
   │  checks Bucket4j token bucket in Redis
   ├── [tokens available]  → HttpDispatcher → external API → persist response → ack
   └── [rate limit hit]    → exponential back-off requeue → DeadLettered after max retries
   │
   ▼
[PostgreSQL — job status + JSONB request/response]
[Client: polls GET /api/jobs/{id}  OR  receives webhook callback]
```

## Tech Stack

| Concern | Technology |
| --- | --- |
| Runtime | Java 21 + Quarkus 3.17.4 |
| Messaging | RabbitMQ 3.13 via SmallRye Reactive Messaging |
| Rate limiting | Bucket4j 8.10 + Redis (Lettuce) |
| Persistence | PostgreSQL 16 + Hibernate ORM Panache + Flyway |
| Cache / rate state | Redis 7 |
| HTTP dispatch | Java native `HttpClient` |
| Observability | Micrometer + Prometheus + OpenTelemetry |
| Testing | JUnit 5, Mockito, WireMock, Awaitility |

## Prerequisites

- Java 21+
- Maven 3.9+
- Docker + Docker Compose

## Quick Start

Start the full stack (RabbitMQ, PostgreSQL, Redis, Prometheus, and the app):

```bash
docker compose up --build
```

Run only the infrastructure and the app in dev mode:

```bash
docker compose up rabbitmq postgres redis -d
./mvnw quarkus:dev
```

## API Endpoints

### Submit a job

```http
POST /api/jobs
Content-Type: application/json

{
  "provider": "github",
  "method": "GET",
  "targetUrl": "https://api.github.com/repos/quarkusio/quarkus",
  "headers": { "Authorization": "Bearer <token>" },
  "priority": "NORMAL",
  "callbackUrl": "https://your-service/webhook"   // optional
}
```

Response `202 Accepted`:

```json
{ "jobId": "uuid", "estimatedWait": "PT2S" }
```

### Poll job status

```http
GET /api/jobs/{id}
```

```json
{
  "jobId": "uuid",
  "status": "Completed",
  "provider": "github",
  "retryCount": 0,
  "createdAt": "...",
  "updatedAt": "...",
  "response": { "statusCode": 200, "body": "...", "completedAt": "..." }
}
```

### Other endpoints

| Method | Path | Description |
| --- | --- | --- |
| `DELETE` | `/api/jobs/{id}` | Cancel a pending job |
| `GET` | `/api/providers` | List providers and current rate-limit state |
| `GET` | `/api/providers/{provider}/stats` | Queue depth, throughput, error rate |

## Rate Limit Configuration

Provider limits are declared in [src/main/resources/application.yaml](src/main/resources/application.yaml):

```yaml
rate-limit:
  providers:
    github:
      requests-per-window: 5000
      window-duration: PT1H
      burst-capacity: 100
      retry-delay: PT5S
      max-retries: 5
      max-retry-delay: PT5M
    stripe:
      requests-per-window: 100
      window-duration: PT1S
      burst-capacity: 10
      retry-delay: PT500MS
      max-retries: 10
      max-retry-delay: PT30S
```

Add a new provider by adding a new key under `rate-limit.providers` — the topology (queue, binding, dead-letter) is declared automatically on startup.

## Webhook Callbacks

When `callbackUrl` is set, the service sends a signed `POST` after job completion:

```http
X-Job-Id: <uuid>
X-Signature: <HMAC-SHA256 of body>
```

The webhook sender retries up to 3 times with a fixed 2-second delay independently of the main rate limiter.

## Observability

| Endpoint | Description |
| --- | --- |
| `GET /q/health` | Aggregated health (RabbitMQ, Redis, PostgreSQL, queue depth) |
| `GET /q/health/live` | Liveness (RabbitMQ connectivity) |
| `GET /q/health/ready` | Readiness (Redis, PostgreSQL, queue depth) |
| `GET /q/metrics` | Prometheus metrics |

Key metrics exported:

| Metric | Type |
| --- | --- |
| `jobs_submitted_total` | Counter — labels: `provider`, `priority` |
| `jobs_completed_total` | Counter — labels: `provider`, `status` |
| `jobs_processing_duration_seconds` | Histogram — label: `provider` |
| `queue_depth` | Gauge — label: `provider` |
| `rate_limit_tokens_available` | Gauge — label: `provider` |
| `rate_limit_hits_total` | Counter — label: `provider` |
| `webhook_sent_total` | Counter — labels: `provider`, `result` |

Prometheus is available at `http://localhost:9090` when using `docker compose up`.

## Running Tests

```bash
./mvnw test
```

Tests use Quarkus Dev Services — PostgreSQL and Redis containers are started automatically. RabbitMQ must be available on `localhost:5672` (start it via `docker compose up rabbitmq -d`).

## Infrastructure Ports (local)

| Service | Port |
| --- | --- |
| Quarkus app | 8080 |
| RabbitMQ AMQP | 5672 |
| RabbitMQ management UI | 15672 |
| PostgreSQL | 5432 |
| Redis | 6379 |
| Prometheus | 9090 |

RabbitMQ management credentials: `guest` / `guest`.
