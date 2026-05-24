# Service Operations Manual (Spring Boot Services)

This document describes how to operate, configure, monitor, and troubleshoot the Java Spring Boot microservices. It applies to all services under `infra-services/` and `business-services/`.

---

## 1. Prerequisites
- Java 21 (verify via `java -version`)
- Maven 3.9+ (or use included Maven Wrapper `./mvnw`)
- Docker & Docker Compose (for local infrastructure)
- Running Eureka Server (port 8761) and Config Server (port 8888)

---

## 2. Building Services

### Build All
```bash
./mvnw clean install -DskipTests
```

### Build a Single Service
```bash
./mvnw clean install -DskipTests -pl infra-services/auth-service -am
```
- `-pl`: project list (specific module)
- `-am`: also-make (build dependencies)

### Build & Test
```bash
./mvnw clean install
```

---

## 3. Running a Service

### Via Maven (Development)
```bash
./mvnw spring-boot:run -pl infra-services/auth-service
```

### Via Java JAR (Production)
```bash
java -jar infra-services/auth-service/target/auth-service-*.jar
```

### With Profile
```bash
./mvnw spring-boot:run -pl infra-services/auth-service -Dspring-boot.run.profiles=prod
```

---

## 4. Configuration

### Sources of Configuration (priority order, top wins)
1. Command-line args (`--server.port=8080`)
2. Environment variables (`SERVER_PORT=8080`)
3. Service's `application.yml` (local fallback)
4. Config Server's centralized config (`<service-name>.yml`)

### Common Environment Variables
| Variable | Default | Description |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `default` | Active Spring profile |
| `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE` | `http://localhost:8761/eureka/` | Eureka URL |
| `SPRING_CLOUD_CONFIG_URI` | `http://localhost:8888` | Config Server URL |
| `SPRING_DATASOURCE_URL` | per-service | Database connection URL |
| `SPRING_REDIS_HOST` | `localhost` | Redis host |
| `SPRING_RABBITMQ_HOST` | `localhost` | RabbitMQ host |

### Per-Service Database
Each service has its own database (e.g., `auth_db`, `wallet_db`, `audio_db`). Databases are created via:
```sql
CREATE DATABASE <service>_db;
```
Schema migration is handled by Flyway on service startup (`src/main/resources/db/migration/`).

---

## 5. Health Checks

### Liveness Probe
```bash
curl http://localhost:<port>/actuator/health
# Expected: {"status":"UP"}
```

### Detailed Health
```bash
curl http://localhost:<port>/actuator/health/db
curl http://localhost:<port>/actuator/health/rabbit
curl http://localhost:<port>/actuator/health/redis
```

### Service Info
```bash
curl http://localhost:<port>/actuator/info
```

### Eureka Registration
Open http://localhost:8761 → look for the service under "Instances currently registered".

---

## 6. Monitoring & Metrics

### Prometheus Endpoint
Each service exposes metrics at `/actuator/prometheus`:
```bash
curl http://localhost:<port>/actuator/prometheus
```

### Key Metrics to Watch
| Metric | Description |
|---|---|
| `jvm_memory_used_bytes` | JVM memory usage |
| `jvm_threads_live_threads` | Live thread count |
| `http_server_requests_seconds` | HTTP request latency histogram |
| `hikaricp_connections_active` | Active DB connections |
| `rabbitmq_consumed_total` | Total messages consumed |
| `resilience4j_circuitbreaker_state` | Circuit breaker state |

### Log Aggregation
Logs flow to Grafana Loki via Promtail. Query via Grafana → Explore.

---

## 7. Common Operational Tasks

### Restart a Service
- **Local dev**: Kill the process (`Ctrl+C`) and rerun the Maven command.
- **Production (containerized)**: `docker restart <container>` or `kubectl rollout restart deployment/<name>`.

### Refresh Configuration
After changing config in Config Server:
```bash
curl -X POST http://localhost:<port>/actuator/refresh
```

### View Active Threads
```bash
curl http://localhost:<port>/actuator/threaddump
```

### Heap Dump (for memory leaks)
```bash
curl -X POST http://localhost:<port>/actuator/heapdump -o heap.hprof
```

### Reset Circuit Breaker
```bash
curl -X POST http://localhost:<port>/actuator/circuitbreakers/<name>/state -d "CLOSED"
```

---

## 8. Troubleshooting Guide

### Issue: Service fails to start with "Cannot connect to Eureka"
- **Cause**: Eureka not running, or wrong URL.
- **Fix**: Verify Eureka is up (`curl http://localhost:8761`). Check `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE`.

### Issue: Service fails with database connection error
- **Cause**: PostgreSQL not running, wrong credentials, or DB does not exist.
- **Fix**:
  ```bash
  docker-compose ps          # check Postgres status
  docker exec -it postgres psql -U postgres -c "\l"  # list databases
  ```

### Issue: Flyway migration error on startup
- **Cause**: Checksum mismatch, or migration script syntax error.
- **Fix**: Check `flyway_schema_history` table. Repair if needed:
  ```bash
  ./mvnw flyway:repair -pl <service>
  ```

### Issue: 503 Service Unavailable on requests
- **Cause**: Circuit breaker is OPEN, or downstream service is down.
- **Fix**: Check logs for `CallNotPermittedException`. Verify downstream service health.

### Issue: RabbitMQ messages stuck in queue
- **Cause**: Consumer is down, or messages are being repeatedly NACKed.
- **Fix**:
  - Check consumer logs for exceptions.
  - Check the DLQ for failed messages.
  - Inspect via RabbitMQ Management UI (http://localhost:15672).

### Issue: High memory usage / OutOfMemoryError
- **Fix**: Increase JVM heap with `-Xmx2g`. Investigate via heap dump.

### Issue: Slow API responses
- **Fix**:
  - Check `http_server_requests_seconds` metric.
  - Inspect database query times.
  - Check for thread pool saturation.

---

## 9. Logging Conventions

### Log Levels
- `ERROR`: Unrecoverable failures (e.g., DB connection lost).
- `WARN`: Recoverable issues (e.g., retried RPC).
- `INFO`: Major lifecycle events (e.g., service started, request processed).
- `DEBUG`: Detailed diagnostics (disabled in production).

### Structured Logging
Logs are emitted in JSON format with consistent fields:
- `timestamp`, `level`, `service`, `traceId`, `spanId`, `userId`, `message`

### Correlation
Each request gets a `traceId` (via Spring Cloud Sleuth / Micrometer Tracing). Use it to trace requests across services.

---

## 10. Deployment Checklist

Before deploying a new version:
- [ ] All tests pass: `./mvnw test`
- [ ] Flyway migrations reviewed and tested on a staging DB
- [ ] No breaking changes to public APIs (or coordinated with consumers)
- [ ] Configuration changes deployed to Config Server first
- [ ] Health endpoint verified
- [ ] Monitoring alerts reviewed
- [ ] Rollback plan documented
