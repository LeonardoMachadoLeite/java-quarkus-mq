# Queue Stats

Show current queue depths, rate limit token state, and recent job statistics. Arguments: $ARGUMENTS

If a provider name is passed as $ARGUMENTS, filter output to that provider only. Otherwise show all providers.

## Steps to Execute

### 1. RabbitMQ queue depths (via management API)

```
curl -s -u guest:guest http://localhost:15672/api/queues/%2F
```

Parse and display for each queue matching `api.*`:
| Queue | Messages ready | Messages unacked | Consumers | Throughput (msg/s) |
|---|---|---|---|---|
| api.github.requests | N | N | N | N |
| api.github.priority | N | N | N | N |
| api.stripe.requests | N | N | N | N |
| ... | | | | |
| api.dead-letter | N | N | N | N |

Flag any queue where `messages_ready > 1000` (backlog warning) or `consumers == 0` (no active consumer).

### 2. Redis rate limit token state

For each configured provider, check the bucket state. The key pattern is `rate-limit:{provider}:bucket`.

```
docker exec redis redis-cli keys "rate-limit:*"
```

Then for context, check raw TTL on each key:
```
docker exec redis redis-cli ttl "rate-limit:{provider}:bucket"
```

Report: which providers have active bucket keys in Redis and their TTL.

### 3. Live token count from the app API

```
curl -s http://localhost:8080/api/providers | jq .
```

If the endpoint is available, report the current available tokens per provider from the app's perspective.

### 4. PostgreSQL job status counts

```
docker exec postgres psql -U quarkus -d ratelimitdb -c "
  SELECT provider, status, COUNT(*) as count
  FROM jobs
  GROUP BY provider, status
  ORDER BY provider, status;
"
```

Also check dead letters:
```
docker exec postgres psql -U quarkus -d ratelimitdb -c "
  SELECT provider, COUNT(*) as dead_count, MAX(dead_lettered_at) as last_at
  FROM dead_letters
  GROUP BY provider;
"
```

### 5. Recent job completion rate (last 10 minutes)

```
docker exec postgres psql -U quarkus -d ratelimitdb -c "
  SELECT provider, status, COUNT(*) as count
  FROM jobs
  WHERE updated_at > NOW() - INTERVAL '10 minutes'
  GROUP BY provider, status
  ORDER BY provider, status;
"
```

## Summary Output Format

End with a concise summary table:

```
Provider   | Queue Depth | Tokens Available | Jobs (last 10m) | Dead Letters
-----------|-------------|------------------|-----------------|-------------
github     | 42          | 4958             | 120 completed   | 0
stripe     | 5           | 80               | 45 completed    | 2
```

If any service is unreachable (RabbitMQ management API, Redis, PostgreSQL), note which and why.
