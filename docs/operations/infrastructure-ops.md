# Infrastructure Operations Manual

This document describes how to operate the core infrastructure components: Eureka, Config Server, API Gateway, and the data stores managed via Docker Compose.

---

## 1. Docker Compose Stack

### Components
| Component | Port | Image |
|---|---|---|
| PostgreSQL | 5432 | `postgres:16` |
| Redis Cluster | 6379 | `redis:7` |
| RabbitMQ | 5672 (AMQP), 15672 (UI) | `rabbitmq:3.12-management` |
| MinIO | 9000 (API), 9001 (UI) | `minio/minio` |
| Loki | 3100 | `grafana/loki` |
| Promtail | — | `grafana/promtail` |
| Grafana | 3000 | `grafana/grafana` |
| Prometheus | 9090 | `prom/prometheus` |

### Startup
```bash
docker-compose up -d
```

### Health Check
```bash
docker-compose ps
docker-compose logs -f <service-name>
```

### Shutdown
```bash
docker-compose down              # stop containers, preserve volumes
docker-compose down -v           # also remove volumes (DATA LOSS)
```

### Default Credentials
| Service | URL | Username | Password |
|---|---|---|---|
| RabbitMQ UI | http://localhost:15672 | `guest` | `guest` |
| MinIO Console | http://localhost:9001 | `minioadmin` | `minioadmin` |
| Grafana | http://localhost:3000 | `admin` | `admin` |

---

## 2. Eureka Server (Service Registry)

### Port: 8761
### URL: http://localhost:8761

### Start
```bash
./mvnw spring-boot:run -pl infrastructure/eureka-server
```

### Verify
- Open http://localhost:8761 in browser.
- All registered microservices should appear under "Instances currently registered with Eureka".

### Common Issues
- **Service not appearing**: Wait 30-60s for registration heartbeat. Check service logs for `Eureka client registration failed`.
- **Self-preservation mode triggered**: Indicates network instability between Eureka and clients. Verify network connectivity.

---

## 3. Config Server

### Port: 8888
### URL: http://localhost:8888

### Start
```bash
./mvnw spring-boot:run -pl infrastructure/config-server
```
*Note: Config Server must start AFTER Eureka.*

### Configuration Source
- Backend: Native (local filesystem) or Git repo (configurable via `spring.profiles.active`).
- Default config files location: `infrastructure/config-server/src/main/resources/config/`.

### Verify
Test fetching config for a service:
```bash
curl http://localhost:8888/auth-service/default
curl http://localhost:8888/audio-generation-service/dev
```

### Common Issues
- **Service cannot load config on startup**: Ensure Config Server is running BEFORE the dependent service. Add `spring.cloud.config.fail-fast=true` to fail fast on misconfig.
- **Config not refreshing**: Call `POST /actuator/refresh` on the consumer service after changing config.

---

## 4. API Gateway

### Port: 8080
### URL: http://localhost:8080

### Start
```bash
./mvnw spring-boot:run -pl infrastructure/api-gateway
```
*Note: Start LAST, after all backend services are up.*

### Routing
Gateway routes are configured via `application.yml` (or Config Server). Each route maps a path predicate (e.g., `/api/auth/**`) to a service registered in Eureka (e.g., `lb://auth-service`).

### Common Routes
| Path Prefix | Target Service |
|---|---|
| `/api/auth/**` | `auth-service` |
| `/files/**` | `file-service` |
| `/wallet/**` | `credit-wallet-service` |
| `/campaigns/**` | `crbt-campaign-service` |
| `/library/**` | `crbt-community-library` |
| `/audio-jobs/**` | `audio-generation-service` |
| `/credit-transactions/**` | `crbt-credit-transaction-service` |
| `/ringtone-assignments/**` | `crbt-core-adapter` |
| `/audit/**` | `audit-log-service` |
| `/api/payments/**` | `payment-gateway-service` |

### Authentication Flow
1. Client sends request to Gateway with `Authorization: Bearer <JWT>` header.
2. Gateway validates JWT using shared secret.
3. Gateway extracts `userId`, `email`, `roles`, and `msisdn` from JWT claims.
4. Gateway injects them as headers: `X-User-Id`, `X-User-Email`, `X-User-Roles`, `X-Msisdn`.
5. Gateway forwards the request to the downstream service.
6. Downstream services trust these headers and do NOT re-validate the JWT.

### Common Issues
- **404 on a valid path**: Check `application.yml` route definitions. Verify the target service is registered with Eureka.
- **503 Service Unavailable**: Target service is down or circuit breaker is open.

---

## 5. Monitoring Stack

### Grafana
- URL: http://localhost:3000
- Default dashboards: pre-loaded JVM, RabbitMQ, PostgreSQL, Redis dashboards.

### Prometheus
- URL: http://localhost:9090
- Scrapes `/actuator/prometheus` endpoints of all Spring Boot services.
- Default scrape interval: 15s.

### Grafana Loki
- URL via Grafana: http://localhost:3000/explore (select Loki datasource).
- All Spring Boot service logs are aggregated via Promtail → Loki.

### Query Examples
```logql
# All ERROR logs across all services
{service=~".+"} |= "ERROR"

# Logs for a specific service
{service="auth-service"}

# Logs for a specific user ID
{service=~".+"} | json | userId="42"
```

---

## 6. Startup Order Cheat Sheet

```bash
# 1. Infrastructure (Docker)
docker-compose up -d

# 2. Service Registry
./mvnw spring-boot:run -pl infrastructure/eureka-server

# 3. Config Server (wait for Eureka)
./mvnw spring-boot:run -pl infrastructure/config-server

# 4. Backend services (can run in parallel)
./mvnw spring-boot:run -pl infra-services/auth-service
./mvnw spring-boot:run -pl infra-services/file-service
./mvnw spring-boot:run -pl infra-services/credit-wallet-service
./mvnw spring-boot:run -pl infra-services/payment-gateway-service
./mvnw spring-boot:run -pl infra-services/audit-log-service
./mvnw spring-boot:run -pl infra-services/notification-service
./mvnw spring-boot:run -pl business-services/crbt-campaign-service
./mvnw spring-boot:run -pl business-services/crbt-community-library
./mvnw spring-boot:run -pl business-services/audio-generation-service
./mvnw spring-boot:run -pl business-services/crbt-credit-transaction-service
./mvnw spring-boot:run -pl business-services/crbt-core-adapter

# 5. Python AI Worker
cd python-services/ai-media-worker
uvicorn main:app --host 0.0.0.0 --port 8765 --reload

# 6. API Gateway (start LAST)
./mvnw spring-boot:run -pl infrastructure/api-gateway
```

---

## 7. Backup & Restore

### PostgreSQL
```bash
# Backup
docker exec -t postgres pg_dumpall -c -U postgres > backup_$(date +%Y%m%d).sql

# Restore
cat backup_20240101.sql | docker exec -i postgres psql -U postgres
```

### MinIO
Use `mc` (MinIO Client) for bucket backups:
```bash
mc mirror localminio/audio-bucket /backups/audio-bucket
```

### RabbitMQ
- Definitions (queues, exchanges, bindings): export via Management UI → Overview → "Download broker definitions".
- Restore: import the JSON file via Management UI.
