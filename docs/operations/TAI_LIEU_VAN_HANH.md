# TÀI LIỆU VẬN HÀNH (RUNBOOK) — Microservice Platform (CRBT)

> Tài liệu vận hành cho **đội OPS** sau khi triển khai: giám sát, kiểm tra sức khỏe, log, backup, scale, xử lý sự cố.
> Phiên bản: 1.0 · Cập nhật: 2026-06-14 · Đi kèm: `TAI_LIEU_TRIEN_KHAI_MODULE.md`.

---

## 1. Vận hành hằng ngày

### 1.1. Lệnh thường dùng

```bash
# Trạng thái toàn bộ container
docker compose ps

# Xem log realtime 1 service
docker compose logs -f <service-name>           # vd: crbt-campaign-service

# Khởi động lại 1 service (không ảnh hưởng service khác)
docker compose restart <service-name>

# Dừng / khởi động lại toàn bộ
docker compose stop
docker compose up -d

# Tài nguyên realtime (CPU/RAM theo container)
docker stats --no-stream
```

### 1.2. Thứ tự khởi động lại (sau reboot máy chủ)

Tuân thủ thứ tự tầng — **giống lúc triển khai**:
`postgres redis rabbitmq minio` → `eureka-server` → `config-server` → infra-services → business-services → `ai-media-worker` → `api-gateway`.

> ⚠️ Khởi động sai thứ tự → service không lấy được config, không đăng ký Eureka. Nếu lỡ, chỉ cần `docker compose restart` service lỗi sau khi Eureka + Config đã UP.

---

## 2. Giám sát (Monitoring)

| Công cụ | URL | Dùng để |
|---|---|---|
| **Grafana** | `http://<host>:3001` | Dashboard metrics + log tập trung |
| **Prometheus** | `http://<host>:9090` | Truy vấn metrics, kiểm tra alert rule |
| **Loki** | (qua Grafana) | Log aggregation toàn hệ thống |
| **Eureka** | `http://<host>:8761` | Trạng thái đăng ký service (UP/DOWN) |
| **RabbitMQ UI** | `http://<host>:15672` | Queue depth, consumer, DLQ |

- **Alert rules:** `infrastructure/monitoring-server/crbt-monitoring-rules.yml`.
- **Prometheus retention:** 7 ngày (`--storage.tsdb.retention.time=7d`). Cần lưu lâu hơn → tăng giá trị này hoặc gắn remote-write.
- **Log:** Promtail thu log container → Loki. Truy vấn trong Grafana bằng label `{container="<service>"}`.

### 2.1. Chỉ số cần theo dõi

| Chỉ số | Ngưỡng cảnh báo | Hành động |
|---|---|---|
| API p95 latency | > 500ms | Kiểm tra service chậm + DB query |
| Container restart liên tục | > 3 lần/5 phút | Xem log, kiểm tra health |
| RabbitMQ queue depth | Tăng liên tục không giảm | Consumer chết → restart service consumer |
| RabbitMQ DLQ có message | > 0 | Có message lỗi → điều tra nguyên nhân |
| Disk volume | > 80% | Dọn log/Loki hoặc tăng disk |
| Redis memory | Gần `maxmemory` | Kiểm tra key tồn đọng |

---

## 3. Kiểm tra sức khỏe (Health Check)

Mọi service Java expose Spring Actuator:

```bash
curl -s http://localhost:8761/actuator/health    # Eureka
curl -s http://localhost:8889/actuator/health    # Config Server
curl -s http://localhost:18080/actuator/health   # API Gateway
curl -s http://localhost:8081/actuator/health    # auth-service (ví dụ)
curl -s http://localhost:8765/health             # AI Worker (Python)
```

Kết quả mong đợi: `{"status":"UP"}`. Nếu `DOWN` → trường `components` chỉ ra thành phần lỗi (db, redis, rabbit, diskSpace).

**Hạ tầng:**
```bash
docker exec -it <postgres-container> pg_isready -U postgres
docker exec -it <redis-container> redis-cli ping            # → PONG
docker exec -it <rabbitmq-container> rabbitmq-diagnostics ping
curl -f http://localhost:9000/minio/health/live             # MinIO
```

---

## 4. Sao lưu & Khôi phục (Backup & Restore)

### 4.1. PostgreSQL (quan trọng nhất)

```bash
# Backup 1 DB
docker exec -t <postgres-container> pg_dump -U postgres campaign_db > campaign_db_$(date +%F).sql

# Backup TẤT CẢ DB
docker exec -t <postgres-container> pg_dumpall -U postgres > all_db_$(date +%F).sql

# Restore
cat campaign_db_2026-06-14.sql | docker exec -i <postgres-container> psql -U postgres -d campaign_db
```

> 🔴 **Ưu tiên backup:** `credit_transaction_db` (bản ghi tài chính bất biến), `wallet_db`, `payment_db`, `auth_db`. Lên lịch cron hằng ngày, lưu off-site.

### 4.2. MinIO (file media)

```bash
# Dùng mc mirror sang nơi backup
mc mirror local/media-audio /backup/minio/media-audio
```

### 4.3. Volume Docker

Các volume cần backup định kỳ: `postgres_data`, `minio_data`, `rabbitmq_data`. `redis_data`/`loki_data`/`prometheus_data` ít quan trọng hơn (cache/log/metrics).

---

## 5. Mở rộng (Scaling)

- **Scale ngang service stateless** (business/infra service):
  ```bash
  docker compose up -d --scale crbt-campaign-service=3
  ```
  Eureka tự cân bằng tải; Gateway định tuyến qua Eureka.

- **KHÔNG scale:**
  - `eureka-server`, `config-server` (singleton trong setup này)
  - `credit-wallet-service` chạy nhiều instance **vẫn an toàn** nhờ Redisson Lock `wallet:{userId}` (distributed lock) — không bị race khi trừ/cộng credit.
  - `notification-service` consumer: scale được, RabbitMQ chia message.

- **audio-generation-service:** giới hạn max 5 job/user, chạy async. Scale instance để tăng throughput tổng.

---

## 6. Xử lý sự cố (Troubleshooting)

| Triệu chứng | Nguyên nhân thường gặp | Cách xử lý |
|---|---|---|
| Service không lên Eureka | Khởi động trước Config/Eureka; sai credential | Restart sau khi Eureka+Config UP; kiểm tra `EUREKA_*`, `CONFIG_SERVER_*` |
| Gateway trả 503 | Service đích DOWN hoặc chưa đăng ký Eureka | Kiểm tra Eureka dashboard; restart service đích |
| Service báo lỗi DB | Sai `POSTGRES_*`; DB chưa tạo | Kiểm tra `01-create-databases.sql` đã chạy; xem log Postgres |
| Message ứ trong queue | Consumer chết | Restart service consumer; kiểm tra DLQ |
| Có message trong DLQ | Xử lý message thất bại 3 lần (retry 1s/2s/4s) | Đọc message DLQ trong RabbitMQ UI, điều tra payload lỗi |
| Tạo nhạc AI lỗi | `GEMINI_API_KEY` sai/hết quota; AI Worker DOWN | Kiểm tra key + `curl http://localhost:8765/health` |
| Trừ credit sai/double | (Hiếm) Lock không hoạt động | Kiểm tra Redis UP; log Redisson trong `credit-wallet-service` |
| Container OOM/restart loop | Thiếu RAM | `docker stats`; tăng RAM máy chủ hoặc giới hạn JVM heap |

### 6.1. Quy trình khi 1 service lỗi

```bash
# 1. Xác định service lỗi
docker compose ps                          # tìm container không Up/healthy

# 2. Đọc log gần nhất
docker compose logs --tail=200 <service>

# 3. Truy vết theo traceId (X-Correlation-ID) trong Grafana/Loki
#    Mọi log đã tự in kèm traceId — lọc theo traceId để xem trọn luồng

# 4. Restart service
docker compose restart <service>

# 5. Nếu vẫn lỗi → kiểm tra dependency (DB/Redis/RabbitMQ/Eureka/Config)
```

### 6.2. Truy vết phân tán (Distributed Tracing)

- Request qua Gateway tự sinh `X-Correlation-ID`, truyền xuyên suốt qua Feign, lưu vào MDC (`traceId`).
- Lọc log theo `traceId` trong Loki để xem trọn luồng 1 request qua nhiều service.
- **Cấm ghi đè MDC traceId thủ công** (theo quy ước hệ thống).

---

## 7. Bảo trì định kỳ

| Chu kỳ | Việc cần làm |
|---|---|
| Hằng ngày | Kiểm tra Grafana dashboard, queue depth, DLQ, disk usage; backup DB tài chính |
| Hằng tuần | Backup toàn bộ DB + MinIO; review log lỗi; kiểm tra dung lượng volume |
| Hằng tháng | Cập nhật security patch (image base); review alert; kiểm tra chứng chỉ/secret rotation |
| Theo nhu cầu | Scale service theo tải; dọn dữ liệu cũ (log, temp bucket) |

- **Scheduler tự động:** `audit-log-service` chạy đối soát Lyria lúc 00:00 hằng ngày (bật/tắt bằng `LYRIA_RECONCILE_SCHEDULER_ENABLED`). `crbt-campaign-service` có scheduler dọn lịch sử Lyria.
- **Bucket `media-temp`:** chứa file tạm — lên lịch dọn định kỳ tránh đầy MinIO.

---

## 8. An toàn vận hành (Security Ops)

- 🔒 Toàn bộ secret nằm trong `.env` / Vault — **không hardcode**, không commit `.env` thật.
- 🔒 Chỉ mở cổng **API Gateway** ra ngoài. Mọi cổng service/DB/Redis/RabbitMQ/MinIO chỉ trong mạng nội bộ.
- 🔒 `notification-service` không có HTTP endpoint public — chỉ nhận RabbitMQ.
- 🔒 Đổi toàn bộ tài khoản mặc định (Grafana/RabbitMQ/MinIO/Eureka/Config) trước khi mở Prod.
- 🔒 JWT do Gateway validate; service nội bộ không validate lại — đảm bảo không expose service nội bộ ra ngoài Gateway.
- 🔒 Rotate `JWT_SECRET`, `CRBT_SHARED_SECRET`, API key định kỳ; nếu nghi lộ → rotate ngay + restart service liên quan.

---

## 9. Liên hệ leo thang (Escalation)

| Mức | Phạm vi | Người chịu trách nhiệm |
|---|---|---|
| P0 | Mất dịch vụ (Gateway/DB down, mất tiền/credit) | OPS lead + Backend lead — xử lý ngay |
| P1 | 1 business service lỗi, ảnh hưởng tính năng | OPS trực |
| P2 | Lỗi không chặn người dùng (log, scheduler) | Backlog, xử lý trong ngày làm việc |

> Điền thông tin liên hệ thực tế (tên/SĐT/kênh) trước khi bàn giao.

---

> 📖 Danh mục sự cố đầy đủ (12 nhóm, theo Triệu chứng → Nguyên nhân → Cách xử lý): xem `docs/operations/SU_CO_THUONG_GAP.md`.

## 10. Tài liệu liên quan

- `docs/operations/SU_CO_THUONG_GAP.md` — Sự cố thường gặp & cách xử lý (chi tiết)
- `docs/operations/TAI_LIEU_TRIEN_KHAI_MODULE.md` — Hướng dẫn triển khai module
- `docs/operations/infrastructure-ops.md` — Chi tiết hạ tầng
- `docs/operations/service-ops.md` — Chi tiết từng service
- `docs/operations/ai-worker-ops.md` — Python AI Worker
- `CLAUDE.md` — Quy ước kiến trúc & quy tắc bắt buộc
