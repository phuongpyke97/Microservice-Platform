# SỰ CỐ THƯỜNG GẶP & CÁCH XỬ LÝ — Microservice Platform (CRBT)

> Phụ lục cho `TAI_LIEU_VAN_HANH.md`. Liệt kê sự cố hay gặp theo nhóm, kèm: **Triệu chứng → Nguyên nhân → Cách xử lý → Lệnh kiểm tra**.
> Phiên bản: 1.0 · Cập nhật: 2026-06-14.

Mục lục:
1. [Build & Deploy](#1-build--deploy)
2. [Khởi động & Thứ tự service](#2-khởi-động--thứ-tự-service)
3. [Eureka & Config Server](#3-eureka--config-server)
4. [API Gateway](#4-api-gateway)
5. [Database & Flyway](#5-database--flyway)
6. [RabbitMQ & DLQ](#6-rabbitmq--dlq)
7. [Redis & Redisson Lock](#7-redis--redisson-lock)
8. [MinIO & File](#8-minio--file)
9. [AI Worker & Gemini Lyria](#9-ai-worker--gemini-lyria)
10. [Auth: JWT & CRBT Token](#10-auth-jwt--crbt-token)
11. [Audio Generation](#11-audio-generation)
12. [Tài nguyên & Hiệu năng](#12-tài-nguyên--hiệu-năng)

---

## 1. Build & Deploy

### 1.1. Maven build fail — thiếu JAR
- **Triệu chứng:** `build-and-deploy.sh` báo `X module thieu JAR`.
- **Nguyên nhân:** Chạy `--skip-build` khi chưa từng build; hoặc build lỗi giữa chừng.
- **Xử lý:** Chạy lại không có `--skip-build`:
  ```bash
  ./mvnw clean install -DskipTests
  ```
- **Kiểm tra:** `find . -path '*/target/*.jar' ! -name '*-sources.jar'` đủ 14 module.

### 1.2. (Windows) Build/deploy ghi đè JAVA_HOME sai
- **Triệu chứng:** Build lỗi `JAVA_HOME` trỏ sai JDK, dù máy đã cài Java 21.
- **Nguyên nhân:** Script trước đây hardcode override `JAVA_HOME` trên Windows (đã fix ở commit `81b7d0f`). Nếu dùng script cũ vẫn gặp.
- **Xử lý:** Dùng bản script mới nhất. Đảm bảo `JAVA_HOME` hệ thống trỏ JDK 21:
  ```powershell
  $env:JAVA_HOME    # phải là JDK 21
  java -version     # build 21.x
  ```
- **Lưu ý:** Script chỉ override `JAVA_HOME` khi biến chưa set và đúng đường dẫn Linux mặc định — không đụng giá trị Windows đã set.

### 1.3. MinIO image không load được
- **Triệu chứng:** `Khong tim thay file minio.tar` hoặc `docker load` lỗi.
- **Nguyên nhân:** File `minio.tar` (158MB) thiếu hoặc hỏng khi clone.
- **Xử lý:** Pull trực tiếp từ registry thay vì load tar:
  ```bash
  docker pull quay.io/minio/minio
  ```
  Rồi chạy deploy bỏ qua bước load tar (chạy `docker compose up -d minio`).

### 1.4. Port đã bị chiếm (port already allocated)
- **Triệu chứng:** `Bind for 0.0.0.0:18080 failed: port is already allocated`.
- **Nguyên nhân:** Cổng host đã có tiến trình khác (hoặc container cũ chưa dừng).
- **Xử lý:**
  ```bash
  docker compose down                 # dừng container cũ
  # Windows: tìm tiến trình chiếm cổng
  netstat -ano | findstr :18080
  # Linux:
  sudo lsof -i :18080
  ```
  Hoặc đổi cổng host trong `.env` (`API_GATEWAY_PORT_EXTERNAL`, `POSTGRES_PORT_PUBLIC`...).

---

## 2. Khởi động & Thứ tự service

### 2.1. Service start nhưng tự tắt / restart loop ngay sau khi up
- **Triệu chứng:** `docker compose ps` thấy container `Restarting`.
- **Nguyên nhân:** Khởi động trước khi Eureka/Config `healthy`; không lấy được config → crash.
- **Xử lý:** Đợi Eureka + Config UP rồi restart:
  ```bash
  docker compose up -d eureka-server config-server
  # đợi healthy
  docker compose restart <service-lỗi>
  ```
- **Kiểm tra:** `docker compose logs --tail=100 <service>` — thường thấy lỗi connect config-server / eureka.

### 2.2. Toàn bộ chậm lên sau reboot
- **Nguyên nhân:** Đồng loạt khởi động → tranh tài nguyên.
- **Xử lý:** Khởi động theo tầng (xem `TAI_LIEU_VAN_HANH.md` §1.2), chờ giữa các tầng.

---

## 3. Eureka & Config Server

### 3.1. Service không xuất hiện trên Eureka dashboard
- **Triệu chứng:** Eureka (`:8761`) thiếu service; Gateway gọi trả 503.
- **Nguyên nhân:** Sai `EUREKA_USER/PASSWORD`; service chưa start xong; network Docker lỗi.
- **Xử lý:**
  ```bash
  docker compose logs <service> | grep -i eureka
  # kiểm tra biến
  docker compose exec <service> env | grep EUREKA
  ```
  Sửa `.env` nếu sai credential → `docker compose up -d <service>`.

### 3.2. Config Server trả 401 / service không lấy được config
- **Triệu chứng:** Log service: `401 Unauthorized` khi gọi config-server.
- **Nguyên nhân:** Lệch `CONFIG_SERVER_USER/PASSWORD` giữa config-server và service client.
- **Xử lý:** Đồng bộ 2 biến này trong `.env` cho **tất cả** service, restart.

### 3.3. Config Server không healthy
- **Nguyên nhân:** Profile phải là `native,docker`; thiếu file config native mount.
- **Kiểm tra:** `curl http://localhost:8889/actuator/health`. Xem log `docker compose logs config-server`.

---

## 4. API Gateway

### 4.1. Gateway trả 503 Service Unavailable
- **Nguyên nhân:** Service đích DOWN / chưa đăng ký Eureka.
- **Xử lý:** Mở Eureka, xác nhận service đích UP; restart service đích. (Resilience4j fallback có thể trả 503 khi circuit OPEN.)

### 4.2. Gateway trả 401/403 cho request hợp lệ
- **Nguyên nhân:** `JWT_SECRET` (Admin/CMS) hoặc `CRBT_SHARED_SECRET` (nhà mạng) sai/lệch.
- **Xử lý:** Đảm bảo `JWT_SECRET` của Gateway = giá trị dùng ký token; `CRBT_SHARED_SECRET` = secret nhà mạng cấp. Restart Gateway.

### 4.3. Gateway timeout với request tạo nhạc
- **Nguyên nhân:** Luồng AI chậm, nhưng đúng thiết kế là **async** (trả 202 + polling). Nếu client gọi sync sẽ timeout.
- **Xử lý:** Client phải poll `GET` job theo `jobId`, không chờ đồng bộ. Kiểm tra `audio-generation-service` trả 202.

---

## 5. Database & Flyway

### 5.1. Service báo `database "xxx_db" does not exist`
- **Nguyên nhân:** `01-create-databases.sql` chỉ chạy **lần đầu** Postgres khởi tạo volume rỗng. Nếu thêm DB mới sau khi volume đã tồn tại → không tự tạo.
- **Xử lý:** Tạo thủ công:
  ```bash
  docker compose exec postgres psql -U postgres -c "CREATE DATABASE library_db;"
  ```
  Hoặc xóa volume `postgres_data` (⚠️ mất data) để chạy lại init.

### 5.2. Lệch tên DB giữa `.env` và thực tế
- **Triệu chứng:** Service connect nhầm DB / DB rỗng.
- **Nguyên nhân:** Alias `.env` khác tên thật (`community_db` vs `library_db`, `credit_tx_db` vs `credit_transaction_db`).
- **Xử lý:** Lấy `01-create-databases.sql` làm chuẩn; sửa cấu hình service cho khớp.

### 5.3. Flyway migration fail khi start
- **Triệu chứng:** Service crash, log `FlywayException: Validate failed` / `Migration checksum mismatch`.
- **Nguyên nhân:** File migration bị sửa sau khi đã apply; hoặc chạy nửa chừng.
- **Xử lý:** KHÔNG sửa file `V{n}__*.sql` đã apply. Nếu môi trường dev:
  ```bash
  # kiểm tra trạng thái
  docker compose exec postgres psql -U postgres -d <db> -c "SELECT * FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;"
  ```
  Sửa file lệch → `flyway repair` (qua cấu hình service) hoặc rollback thủ công. Prod: tạo migration mới `V{n+1}` thay vì sửa file cũ.

---

## 6. RabbitMQ & DLQ

### 6.1. Message ứ đọng trong queue, không giảm
- **Nguyên nhân:** Consumer (service) chết hoặc xử lý lỗi.
- **Xử lý:**
  ```bash
  docker compose restart <service-consumer>
  ```
  Mở RabbitMQ UI (`:15672`) → tab Queues xem `Ready`/`Unacked`.

### 6.2. Có message trong DLQ (Dead Letter Queue)
- **Triệu chứng:** Queue `*.dlq` có message.
- **Nguyên nhân:** Consumer xử lý fail 3 lần (RetryTemplate backoff 1s/2s/4s) → đẩy vào DLQ.
- **Xử lý:**
  1. Mở message trong RabbitMQ UI, đọc payload + header lỗi.
  2. Tìm nguyên nhân gốc (data lỗi / service phụ thuộc down).
  3. Sau khi fix, re-publish message từ DLQ về queue chính (qua UI hoặc shovel).
- **Lưu ý:** Mọi consumer bắt buộc có DLQ (common-rmq) — message không bao giờ mất, nhưng phải xử lý DLQ định kỳ.

### 6.3. `audit-log-service` / `notification-service` không nhận message
- **Nguyên nhân:** Hai service này chỉ qua RabbitMQ (notification không HTTP). Nếu down, audit/notify ngừng — luồng chính KHÔNG bị chặn (async).
- **Xử lý:** Restart consumer; message tồn trong queue sẽ được xử lý lại.

---

## 7. Redis & Redisson Lock

### 7.1. Trừ/cộng credit sai số hoặc double
- **Triệu chứng:** Số dư ví lệch khi nhiều request đồng thời.
- **Nguyên nhân:** Redisson Lock `wallet:{userId}` không hoạt động (Redis down / sai `REDIS_*`).
- **Xử lý:**
  ```bash
  docker compose exec redis redis-cli -a "$REDIS_PASSWORD" ping   # → PONG
  docker compose logs credit-wallet-service | grep -i redisson
  ```
  Đảm bảo Redis UP trước `credit-wallet-service`. Lock là BẮT BUỘC — không được bỏ.

### 7.2. Service báo Redis `NOAUTH Authentication required`
- **Nguyên nhân:** Redis đặt `REDIS_PASSWORD` nhưng service không truyền.
- **Xử lý:** Đồng bộ `REDIS_PASSWORD` ở `.env` cho redis + mọi service dùng Redis (gateway, wallet, campaign, library, audio-gen). Restart.

---

## 8. MinIO & File

### 8.1. Upload/tải file lỗi — bucket không tồn tại
- **Nguyên nhân:** Chưa chạy `setup-minio-init`.
- **Xử lý:**
  ```bash
  ./setup-minio-init.sh        # hoặc .\setup-minio-init.ps1
  ```
  Tạo: `media-images`, `media-audio`, `media-audio-lib`, `media-temp`, `media-private`.

### 8.2. Presigned URL không truy cập được từ client ngoài
- **Triệu chứng:** URL trả về dùng host nội bộ `minio:9000`, client ngoài không mở được.
- **Nguyên nhân:** `MINIO_EXTERNAL_ENDPOINT` chưa set đúng domain/IP public.
- **Xử lý:** Đặt `MINIO_EXTERNAL_ENDPOINT=https://<domain-public>` trong `.env`, restart `file-service` + `crbt-community-library`.

### 8.3. MinIO đầy disk
- **Nguyên nhân:** Bucket `media-temp` tích file tạm không dọn.
- **Xử lý:** Lên lifecycle policy xóa tự động hoặc dọn thủ công `mc rm --recursive --force local/media-temp/`.

### 8.4. Upload file lớn bị chặn (hoặc tạm thời cho phép)
- **Lưu ý:** Validation giới hạn dung lượng file DIY/CMS đang **tạm tắt** (commit `fc86e95`). Nếu cần bật lại để tránh abuse → mở lại validation trong file-service. Theo dõi disk khi đang tắt.

---

## 9. AI Worker & Gemini Lyria

### 9.1. Tạo nhạc AI lỗi / job FAILED
- **Nguyên nhân:** `GEMINI_API_KEY` sai, hết quota, hoặc AI Worker DOWN.
- **Xử lý:**
  ```bash
  curl -s http://localhost:8765/health
  docker compose logs ai-media-worker | tail -50
  docker compose logs crbt-campaign-service | grep -i gemini
  ```
  Kiểm tra key + quota Google. AI Worker down → `docker compose up -d ai-media-worker`.

### 9.2. Endpoint thực tế khác thiết kế
- **Lưu ý vận hành/test:** API tạo nhạc AI thực tế là `POST /api/campaigns/generate` (qua Gateway), tham số **Query Param** (`genre`, `mood` bắt buộc, `instrument` optional) — KHÔNG phải JSON body. (Xem `flow_analysis_results.md`.)

### 9.3. Scheduler đối soát Lyria gây tải lúc 00:00
- **Nguyên nhân:** `audit-log-service` chạy reconcile 00:00 hằng ngày.
- **Xử lý:** Tắt tạm nếu cần: đặt `LYRIA_RECONCILE_SCHEDULER_ENABLED=false`, restart `audit-log-service`.

### 9.4. TTS chèn delay 5s
- **Lưu ý:** DIY mixing có delay TTS 5s + hỗ trợ crop start/end (commit `97995c8`) — đúng thiết kế, không phải lỗi.

---

## 10. Auth: JWT & CRBT Token

### 10.1. Admin/CMS login OK nhưng gọi API 401
- **Nguyên nhân:** `JWT_SECRET` lúc ký (auth-service) khác lúc verify (Gateway).
- **Xử lý:** Đồng bộ `JWT_SECRET` toàn hệ thống. Access token mặc định hết hạn 15 phút (`JWT_EXPIRY_ACCESS_MINUTES`) → dùng refresh token.

### 10.2. Request CRBT (nhà mạng) bị từ chối
- **Nguyên nhân:** `CRBT_SHARED_SECRET` không khớp secret nhà mạng → verify local fail, không bóc được `X-MSISDN`/`X-Subscription-Type`.
- **Xử lý:** Lấy đúng secret nhà mạng, đặt vào `.env`, restart Gateway.

### 10.3. Service nội bộ trả lỗi auth
- **Lưu ý:** Service nội bộ KHÔNG validate JWT (Gateway đã làm). Service đọc header `X-User-Id`/`X-User-Email`/`X-User-Roles` do Gateway inject. Nếu gọi thẳng service (bỏ qua Gateway) → thiếu header → lỗi. **Luôn đi qua Gateway.**

---

## 11. Audio Generation

### 11.1. User không tạo được job mới
- **Triệu chứng:** Trả lỗi vượt giới hạn.
- **Nguyên nhân:** Giới hạn **max 5 job đồng thời/user**.
- **Xử lý:** Đợi job cũ xong hoặc kiểm tra job treo (PENDING quá lâu) trong `audio_gen_db`.

### 11.2. Job treo ở PENDING
- **Nguyên nhân:** AI Worker down giữa chừng / message mất kết nối.
- **Xử lý:** Kiểm tra AI Worker + RabbitMQ; restart `audio-generation-service`. Job dùng `@Async` chạy nền — worker phải UP.

---

## 12. Tài nguyên & Hiệu năng

### 12.1. Container bị OOM / kill
- **Triệu chứng:** Service tắt đột ngột, `docker inspect` thấy `OOMKilled: true`.
- **Nguyên nhân:** Thiếu RAM (14 JVM + hạ tầng).
- **Xử lý:** Tăng RAM máy chủ; giới hạn heap mỗi service qua `JAVA_OPTS=-Xmx512m`; cân nhắc tách hạ tầng sang máy riêng.

### 12.2. API p95 > 500ms
- **Nguyên nhân:** DB query chậm / thiếu index; N+1; service đích quá tải.
- **Xử lý:** Xem Grafana xác định service chậm; review query (EXPLAIN); thêm index; scale ngang service stateless.

### 12.3. Disk tăng nhanh
- **Nguyên nhân:** Loki log + Prometheus metrics + MinIO temp + Postgres WAL.
- **Xử lý:** Prometheus retention 7d (chỉnh nếu cần); dọn `media-temp`; rotate log; theo dõi volume `> 80%`.

---

## Phụ lục — Lệnh chẩn đoán nhanh

```bash
# Tổng quan
docker compose ps
docker stats --no-stream

# Log 1 service (200 dòng cuối)
docker compose logs --tail=200 <service>

# Lọc theo traceId trong Loki (Grafana Explore):
#   {container="<service>"} |= "<traceId>"

# Health toàn bộ infra
docker compose exec postgres pg_isready -U postgres
docker compose exec redis redis-cli ping
docker compose exec rabbitmq rabbitmq-diagnostics ping
curl -f http://localhost:9000/minio/health/live

# Health service (đổi cổng theo port map)
for p in 8761 8889 18080 8081 8083 8086 8090 8091 8092; do
  echo -n "$p: "; curl -s http://localhost:$p/actuator/health; echo
done
```

---

## Tài liệu liên quan
- `docs/operations/TAI_LIEU_VAN_HANH.md` — Vận hành tổng thể
- `docs/operations/TAI_LIEU_TRIEN_KHAI_MODULE.md` — Triển khai module
- `flow_analysis_results.md` — Đối chiếu luồng thiết kế vs source thực tế
- `CLAUDE.md` — Quy tắc bắt buộc
