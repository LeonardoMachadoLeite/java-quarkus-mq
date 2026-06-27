# Start Infrastructure

Start the backing services (RabbitMQ, PostgreSQL, Redis) without the application container, then verify all three are healthy.

Run the following steps:

1. Start the three infrastructure services:
```
docker-compose up -d rabbitmq postgres redis
```

2. Wait for all three to report `healthy`. Poll with:
```
docker-compose ps
```
Repeat until the `STATUS` column shows `healthy` for `rabbitmq`, `postgres`, and `redis`. Use Awaitility-style logic: check every 3 seconds, give up after 60 seconds.

3. Print a summary of what is running:
   - RabbitMQ AMQP: `localhost:5672` (management UI: `http://localhost:15672`, user: guest/guest)
   - PostgreSQL: `localhost:5432`, db: `ratelimitdb`, user: `quarkus`
   - Redis: `localhost:6379`

4. If any service fails to reach `healthy`, show its logs:
```
docker-compose logs <service-name>
```

Do not start the `app` or `prometheus` containers — those are started separately.
After infrastructure is up, remind the user they can start the app in dev mode with `mvn quarkus:dev`.
