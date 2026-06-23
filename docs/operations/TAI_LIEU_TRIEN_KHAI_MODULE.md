# TÀI LIỆU TRIỂN KHAI MODULE — Microservice Platform (CRBT)

> Tài liệu bàn giao cho **đội triển khai (DevOps/OPS)** để dựng mô hình, các module, API Gateway và hạ tầng phụ trợ.
> Phiên bản: 1.0 · Cập nhật: 2026-06-14 · Đối tượng: `[OPS]` triển khai môi trường Staging/Production.

---

## 1. Tổng quan hệ thống

Nền tảng microservice cho dịch vụ **CRBT (Nhạc chờ)**, gồm 14 service Java (Spring Boot 3.2 / Java 21), 1 service Python AI (FastAPI + gRPC), và khối hạ tầng (PostgreSQL, Redis, RabbitMQ, MinIO, Eureka, Config Server, API Gateway, Monitoring).

**Mô hình phân tầng:**

```
                         ┌────────────────────────┐
        Client ───────►  │  API Gateway (8080)    │  ◄── Cổng DUY NHẤT từ ngoài vào
                         └───────────┬────────────┘
                                     │  (service discovery qua Eureka)
        ┌────────────────────────────┼────────────────────────────┐
        ▼                            ▼                            ▼
  Infra-Services              Business-Services             Python AI Worker
  (auth, file, wallet,        (campaign, library,           (ai-media-worker
   payment, audit, notif)      audio-gen, credit-tx,         8765 HTTP / 50051 gRPC)
                               core-adapter)
        └────────────────────────────┴────────────────────────────┘
                                     │
        ┌──────────────┬─────────────┼─────────────┬──────────────┐
        ▼              ▼             ▼             ▼              ▼
   PostgreSQL       Redis        RabbitMQ        MinIO        Loki/Prom/Grafana
```

**Quy tắc khởi động bắt buộc (đúng thứ tự):**
`Hạ tầng (DB/Cache/MQ/Storage)` → `Eureka` → `Config Server` → `Infra-Services` + `Business-Services` + `AI Worker` → `API Gateway (sau cùng)`.

---

## 2. Yêu cầu môi trường (Prerequisites)

| Thành phần | Phiên bản tối thiểu | Ghi chú |
|---|---|---|
| Docker Engine | 24.x | Bắt buộc |
| Docker Compose | v2 (plugin `docker compose`) | Bắt buộc |
| Java JDK | 21 | Chỉ cần khi build từ source (không build trong container) |
| Maven | 3.9.x (hoặc dùng `./mvnw`) | Build JAR |
| RAM máy chủ | ≥ 16 GB | 16 service + hạ tầng; khuyến nghị 32 GB cho Prod |
| CPU | ≥ 8 vCPU | |
| Disk | ≥ 50 GB SSD | Volume DB/MinIO/Loki tăng theo thời gian |

**Cổng cần mở (firewall):** xem [Mục 6 – Bản đồ cổng](#6-bản-đồ-cổng-port-map). Tối thiểu mở cổng API Gateway ra ngoài; các cổng còn lại chỉ nội bộ.

---

## 3. Chuẩn bị cấu hình (.env)

Toàn bộ biến môi trường tập trung tại file `.env` ở thư mục gốc. Bắt đầu bằng cách copy file mẫu:

```bash
cp .env.example .env
```

### 3.1. Biến BẮT BUỘC đổi trước khi lên Production

| Biến | Mục đích | Cảnh báo |
|---|---|---|
| `POSTGRES_PASSWORD` | Mật khẩu PostgreSQL | ⚠️ Đổi, không để `postgres` |
| `REDIS_PASSWORD` | Mật khẩu Redis | ⚠️ Đặt giá trị mạnh (mặc định để trống) |
| `RABBITMQ_USER` / `RABBITMQ_PASSWORD` | Tài khoản RabbitMQ | ⚠️ Đổi, không để `guest/guest` |
| `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD` | Tài khoản MinIO | ⚠️ Đổi, không để `minioadmin` |
| `EUREKA_PASSWORD` | Basic-auth Eureka | ⚠️ Đổi |
| `CONFIG_SERVER_PASSWORD` | Basic-auth Config Server | ⚠️ Đổi |
| `JWT_SECRET` | Ký JWT Admin/CMS | 🔴 BẮT BUỘC đổi — chuỗi ngẫu nhiên ≥ 32 ký tự |
| `CRBT_SHARED_SECRET` | Verify JWT nhà mạng (CRBT) | 🔴 BẮT BUỘC — lấy từ nhà mạng |
| `GEMINI_API_KEY` | Google Gemini Lyria 3 (tạo nhạc AI) | 🔴 BẮT BUỘC cho luồng tạo nhạc |
| `MYTONE_API_URL` / `MYTONE_API_KEY` | Adapter CMS Mytone | Lấy từ hệ thống CMS Mytone |
| `GRAFANA_ADMIN_PASSWORD` | Admin Grafana | ⚠️ Đổi |

> 🔒 **An toàn:** Không commit file `.env` thật. Trên Prod nên đưa secret vào Vault/SSM hoặc Docker secrets thay vì file phẳng.

### 3.2. Biến hạ tầng quan trọng

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `docker` | Profile chạy trong container |
| `LOG_LEVEL` | `INFO` | Mức log toàn cục |
| `API_GATEWAY_PORT_EXTERNAL` | `18080` | Cổng host map vào Gateway (container 8080) |
| `CONFIG_SERVER_PORT` | `8889` | Cổng host map vào Config Server (container 8888) |
| `POSTGRES_PORT_PUBLIC` | `15433` | Cổng host expose PostgreSQL (container 5432) |
| `LYRIA_RECONCILE_SCHEDULER_ENABLED` | `true` | Bật scheduler đối soát Lyria 00:00 hằng ngày |

---

## 4. Quy trình triển khai (Deployment)

### Cách A — Dùng script tự động (khuyến nghị)

**Linux/macOS:**
```bash
chmod +x build-and-deploy.sh
./build-and-deploy.sh                 # full: maven build + docker build + up
./build-and-deploy.sh --skip-build    # bỏ qua maven, chỉ deploy lại
./build-and-deploy.sh --no-cache --logs
```
Script tự: kiểm tra prerequisites → `mvn clean package -DskipTests` → kiểm tra đủ 14 JAR → `docker load` ảnh MinIO từ `minio.tar` → `docker compose build --parallel` → `docker compose up -d` → in trạng thái.

**Windows (PowerShell):**
```powershell
.\deploy-docker.ps1
```
Script PowerShell khởi động **theo đúng thứ tự tầng** (hạ tầng → Eureka → Config → Infra → Business → AI Worker → Gateway) với thời gian chờ giữa các bước.

### Cách B — Thủ công từng bước

```bash
# 0. Build JAR (nếu chưa có)
./mvnw clean install -DskipTests

# 1. Hạ tầng + monitoring
docker compose up -d postgres redis rabbitmq minio prometheus grafana loki promtail

# 2. Eureka (chờ healthy)
docker compose up -d --build eureka-server

# 3. Config Server (chờ healthy)
docker compose up -d --build config-server

# 4. Infra-Services
docker compose up -d --build auth-service credit-wallet-service file-service \
    notification-service audit-log-service payment-gateway-service

# 5. Business-Services
docker compose up -d --build crbt-campaign-service crbt-community-library \
    audio-generation-service crbt-credit-transaction-service crbt-core-adapter

# 6. Python AI Worker
docker compose up -d --build ai-media-worker

# 7. API Gateway (sau cùng)
docker compose up -d --build api-gateway

# Kiểm tra
docker compose ps
```

> ⚠️ **Quan trọng:** KHÔNG khởi động Infra/Business service trước khi Eureka + Config Server đã `healthy`. Service sẽ không lấy được cấu hình và không đăng ký discovery.

### 4.1. Khởi tạo MinIO bucket (sau khi MinIO chạy)

```bash
# Linux/macOS
./setup-minio-init.sh
# Windows
.\setup-minio-init.ps1
```
Tạo các bucket: `media-images`, `media-audio`, `media-audio-lib`, `media-temp`, `media-private`.

---

## 5. Danh mục Module & cấu hình triển khai

### 5.1. Hạ tầng (Infrastructure)

| Module | Cổng (container) | Phụ thuộc | Biến cấu hình chính |
|---|---|---|---|
| `eureka-server` | 8761 | Postgres | `EUREKA_USER`, `EUREKA_PASSWORD` |
| `config-server` | 8888 (host 8889) | Eureka | profile `native,docker`; `CONFIG_SERVER_USER/PASSWORD` |
| `api-gateway` | 8080 (host 18080) | Config, Redis | `JWT_SECRET`, `CRBT_SHARED_SECRET`, `REDIS_*` |
| `monitoring` (prometheus/grafana/loki/promtail) | 9090 / 3001(host)→3000 / 3100 | — | volume mount config trong `infrastructure/monitoring-server/` |

### 5.2. Infra-Services

| Module | Cổng | DB riêng | Backing services | Vai trò |
|---|---|---|---|---|
| `auth-service` | 8081 | `auth_db` | Postgres, RabbitMQ | JWT, BCrypt strength 12 |
| `notification-service` | 8082 | — | RabbitMQ | **Consumer-only, KHÔNG mở HTTP ra ngoài** |
| `file-service` | 8083 | `file_db` | Postgres, MinIO, RabbitMQ | Presigned URL, AI worker URL |
| `audit-log-service` | 8084 | `audit_db` | Postgres, RabbitMQ | Audit trail async |
| `payment-gateway-service` | 8085 | `payment_db` | Postgres, RabbitMQ | Telco MPS/Charging |
| `credit-wallet-service` | 8086 | `wallet_db` | Postgres, RabbitMQ, **Redis** | Redisson Lock `wallet:{userId}` |

### 5.3. Business-Services

| Module | Cổng | DB riêng | Backing services | Vai trò |
|---|---|---|---|---|
| `crbt-campaign-service` | 8090 | `campaign_db` | Postgres, RabbitMQ, Redis, **Gemini API** | Gói cước, Lyria API |
| `crbt-community-library` | 8091 | `library_db` | Postgres, Redis, MinIO, RabbitMQ | Kho nhạc, Fallback |
| `audio-generation-service` | 8092 | `audio_gen_db` | Postgres, RabbitMQ, Redis, AI Worker | Async job, max 5/user, trả 202 |
| `crbt-credit-transaction-service` | 8093 | `credit_transaction_db` | Postgres, RabbitMQ | Bản ghi bất biến (chỉ INSERT) |
| `crbt-core-adapter` | 8094 | `adapter_db` | Postgres, RabbitMQ, Mytone API | Adapter CMS Mytone |

### 5.4. Python AI Worker

| Module | Cổng | Vai trò |
|---|---|---|
| `ai-media-worker` | 8765 (HTTP) / 50051 (gRPC) | Gemini Lyria, Edge TTS, tách nhạc (Spleeter/Demucs) |

---

## 6. Bản đồ cổng (Port Map)

| Service | Cổng container | Cổng host (mặc định) | Hướng |
|---|---|---|---|
| api-gateway | 8080 | **18080** | 🌐 Public (mở ra ngoài) |
| eureka-server | 8761 | 8761 | Nội bộ |
| config-server | 8888 | 8889 | Nội bộ |
| auth-service | 8081 | 8081 | Nội bộ (qua Gateway) |
| notification-service | 8082 | 8082 | Nội bộ (không HTTP public) |
| file-service | 8083 | 8083 | Nội bộ |
| audit-log-service | 8084 | 8084 | Nội bộ |
| payment-gateway-service | 8085 | 8085 | Nội bộ |
| credit-wallet-service | 8086 | 8086 | Nội bộ |
| crbt-campaign-service | 8090 | 8090 | Nội bộ |
| crbt-community-library | 8091 | 8091 | Nội bộ |
| audio-generation-service | 8092 | 8092 | Nội bộ |
| crbt-credit-transaction-service | 8093 | 8093 | Nội bộ |
| crbt-core-adapter | 8094 | 8094 | Nội bộ |
| ai-media-worker | 8765 / 50051 | 8765 / 50051 | Nội bộ |
| PostgreSQL | 5432 | 15433 | Nội bộ |
| Redis | 6379 | 6379 | Nội bộ |
| RabbitMQ | 5672 / 15672 | 5672 / 15672 | Nội bộ + UI |
| MinIO | 9000 / 9001 | 9000 / 9001 | Nội bộ + Console |
| Prometheus | 9090 | 9090 | Nội bộ |
| Grafana | 3000 | 3001 | Nội bộ UI |
| Loki | 3100 | 3100 | Nội bộ |

> ⚠️ **Lưu ý mismatch giữa script và compose:** `docker-compose.yml` map Gateway ra **18080** và Config ra **8889**. Script `deploy-docker.ps1` in ra `8080`/`8761` (giá trị cổng nội bộ container). Trên Prod, thống nhất một giá trị `API_GATEWAY_PORT_EXTERNAL` và publish đúng cổng đó ra reverse-proxy/load-balancer.

---

## 7. Cơ sở dữ liệu (Database)

- **Mỗi service một DB riêng** (DB isolation — không service nào query DB service khác).
- Script khởi tạo: `infrastructure/postgres-init/01-create-databases.sql` chạy tự động lần đầu Postgres khởi động.

**Danh sách DB (nguồn chuẩn = file SQL init):**
```
auth_db · file_db · payment_db · wallet_db · audit_db · notification_db
campaign_db · library_db · audio_gen_db · credit_transaction_db · adapter_db
```
- **Migration:** mỗi service tự chạy Flyway khi khởi động, file đặt tại `src/main/resources/db/migration/` theo chuẩn `V{n}__{description}.sql`. Không cần thao tác thủ công.

> ⚠️ **Lưu ý đặt tên DB:** Một số alias trong `.env.example` khác tên DB thực tế (ví dụ `COMMUNITY_DB=community_db` nhưng DB thực là `library_db`; `CREDIT_TX_DB=credit_tx_db` nhưng DB thực là `credit_transaction_db`). **Lấy file `01-create-databases.sql` làm chuẩn.** Nếu chỉnh tên DB, phải đồng bộ cả file SQL init lẫn cấu hình từng service.

---

## 8. Kiểm tra sau triển khai (Smoke Test)

```bash
# 1. Tất cả container Up + healthy
docker compose ps

# 2. Eureka đã đăng ký đủ service
#    Mở http://<host>:8761  (user/pass: EUREKA_USER/EUREKA_PASSWORD)
#    → Phải thấy 14 service Java + gateway ở trạng thái UP

# 3. Health check từng service (qua actuator)
curl -s http://localhost:8761/actuator/health   # Eureka
curl -s http://localhost:8889/actuator/health   # Config Server
curl -s http://localhost:18080/actuator/health  # API Gateway

# 4. AI Worker
curl -s http://localhost:8765/health
```

**Bảng truy cập nhanh (UI quản trị):**

| Giao diện | URL | Tài khoản mặc định |
|---|---|---|
| Eureka Dashboard | `http://<host>:8761` | `eureka / eureka-secret` |
| RabbitMQ Management | `http://<host>:15672` | `guest / guest` |
| MinIO Console | `http://<host>:9001` | `minioadmin / minioadmin` |
| Grafana | `http://<host>:3001` | `admin / admin` |
| Prometheus | `http://<host>:9090` | — |

> ⚠️ Đổi toàn bộ tài khoản mặc định trên trước khi mở Staging/Prod.

---

## 9. Checklist bàn giao triển khai

- [ ] Đã cài Docker + Docker Compose v2, RAM/CPU/Disk đạt yêu cầu Mục 2
- [ ] Đã copy `.env.example` → `.env` và **đổi toàn bộ secret** ở Mục 3.1
- [ ] `JWT_SECRET`, `CRBT_SHARED_SECRET`, `GEMINI_API_KEY`, `MYTONE_*` đã có giá trị thật
- [ ] Đã mở firewall đúng cổng Public (chỉ API Gateway), chặn cổng nội bộ
- [ ] Khởi động đúng thứ tự tầng; Eureka + Config `healthy` trước khi lên service khác
- [ ] Đã chạy `setup-minio-init` tạo đủ 5 bucket
- [ ] Eureka hiển thị đủ 14 service + gateway = UP
- [ ] Health check Gateway/Config/Eureka/AI Worker = `{"status":"UP"}`
- [ ] Đã đổi tài khoản mặc định Grafana/RabbitMQ/MinIO
- [ ] Đã bàn giao tài liệu vận hành (`TAI_LIEU_VAN_HANH.md`) cho đội OPS

---

## 10. Tài liệu liên quan

- `docs/operations/TAI_LIEU_VAN_HANH.md` — Vận hành, giám sát, sự cố, backup
- `docs/operations/infrastructure-ops.md` — Chi tiết hạ tầng
- `docs/operations/service-ops.md` — Chi tiết từng service
- `docs/operations/ai-worker-ops.md` — Python AI Worker
- `CLAUDE.md` — Quy ước kiến trúc & quy tắc bắt buộc
- `docs/Microservice_Platform_Architecture.md` — Kiến trúc tổng thể
