# Check Health

Check all health, metrics, and management endpoints for the running application.

## Steps to Execute

### 1. Overall health

```bash
curl -s http://localhost:8080/q/health | jq .
```

Report: overall `status` (UP/DOWN) and each named check's status and data.

### 2. Liveness check (RabbitMQ connectivity)

```bash
curl -s http://localhost:8080/q/health/live | jq .
```

### 3. Readiness checks (queue depth, Redis, PostgreSQL)

```bash
curl -s http://localhost:8080/q/health/ready | jq .
```

### 4. Key Prometheus metrics (per provider)

```bash
curl -s http://localhost:8080/q/metrics | grep -E "jobs_|rate_limit_|queue_depth|webhook_"
```

Report values for these metrics across all providers:

- `jobs_submitted_total`
- `jobs_completed_total` (by status label)
- `rate_limit_hits_total`
- `queue_depth`
- `rate_limit_tokens_available`
- `webhook_sent_total`

### 5. RabbitMQ management API (if accessible)

```bash
curl -s -u guest:guest http://localhost:15672/api/queues/%2F | jq '[.[] | {name, messages, consumers}]'
```

Report queue names, message counts, and consumer counts. Flag any queue with 0 consumers (no active consumer) or message count above 1000.

## Interpreting Results

| Signal | Meaning | Action |
| --- | --- | --- |
| Health check DOWN | Dependency unreachable | Start infra: `/start-infra` |
| `queue_depth` rising fast | Consumer not keeping up or rate-limited | Check `rate_limit_hits_total` |
| `rate_limit_tokens_available` = 0 | Bucket exhausted | Normal if under load; check if stuck |
| `webhook_sent_total{result=failure}` > 0 | Callback URL unreachable | Check `callbackUrl` in job submissions |
| 0 consumers on a queue | Provider consumer not running | Check if app is up and that `@Incoming` method exists |

If the app is not reachable on port 8080, say so and suggest `mvn quarkus:dev` or `docker-compose up`.
