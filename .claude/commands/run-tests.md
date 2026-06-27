# Run Tests

Run the Maven test suite with an optional filter. Arguments: $ARGUMENTS

Parse $ARGUMENTS for any of the following flags (all optional):
- A test class name or partial name (e.g., `RateLimiter`) → use `-Dtest=*{name}*`
- `it` or `integration` → run only integration tests (`@QuarkusTest`, suffix `IT`) via `-Dit.test`
- `unit` → run only unit tests (skip `@QuarkusTest`) via `-Dquarkus.test.profile=test -Dtest=!*IT`
- `verbose` or `-v` → add `-Dsurefire.useFile=false` to print output to console

Default (no arguments): run all tests.

## Commands to Execute

### All tests (default)
```
mvn test
```

### Filter by class name (example: RateLimiter)
```
mvn test -Dtest="*RateLimiter*"
```

### Integration tests only
```
mvn test -Dtest="*IT"
```

### Single class by name
```
mvn test -Dtest="<ClassName>"
```

### Verbose output
```
mvn test -Dsurefire.useFile=false
```

## After Running

1. Report the test summary: total run, failures, errors, skipped.
2. For any failure, show:
   - Full test method name
   - The assertion or exception message
   - The relevant stack frame (first frame in `com.example`)
3. If Testcontainers fails to start (Docker not running), say so explicitly and suggest `docker-compose up -d rabbitmq postgres redis` first.
4. If there are compilation errors, show them and do not report a test summary.

Note: The `%test` Quarkus profile activates Quarkus DevServices for PostgreSQL and Redis automatically. RabbitMQ DevServices is not configured — integration tests that need RabbitMQ start their own container via Testcontainers in `@BeforeAll`.
