# TÀI LIỆU BÀN GIAO SYSTEM CONFIGURATION SPECIFICATION

Tài liệu này hướng dẫn chi tiết về cấu trúc tệp cấu hình, biến môi trường, và các tham số kết nối hạ tầng theo từng môi trường triển khai (Dev, Test, Prod) của hệ thống **Mytel CRBT Microservice Platform**.

---

## 1. Cấu Trúc File Cấu Hình Theo Môi Trường (Spring Profiles)

Hệ thống sử dụng cơ chế **Centralized Configuration** quản lý bởi `config-server`. Các tệp cấu hình được tổ chức theo cấu trúc Spring Profiles để đảm bảo tính mềm dẻo khi chuyển đổi môi trường:

```
config-server (Thư mục cấu hình native)
├── application.yml (Cấu hình dùng chung cho tất cả các service)
├── application-dev.yml (Cấu hình ghi đè cho môi trường Developer cục bộ)
├── application-docker.yml (Cấu hình ghi đè khi chạy cụm Docker Compose)
├── application-prod.yml (Cấu hình ghi đè cho môi trường Production thực tế)
├── auth-service.yml (Cấu hình riêng của auth-service)
├── crbt-campaign-service.yml (Cấu hình riêng của campaign-service)
└── ... (Các file cấu hình riêng cho từng service tương tự)
```

### Cách thức hoạt động của các Profile:
- **`dev` Profile**: Dùng cho phát triển dưới máy local. Đọc các kết nối trực tiếp tới `localhost` (ví dụ: `localhost:5432` cho DB, `localhost:6379` cho Redis).
- **`docker` Profile**: Dùng cho môi trường Test/Staging chạy Docker Compose. Các đường dẫn kết nối sử dụng DNS nội bộ của Docker (ví dụ: `jdbc:postgresql://postgres:5432/...` hoặc `redis:6379`).
- **`prod` Profile**: Dùng cho môi trường Production thực tế. Đọc cấu hình từ kho Git Private mã hóa, kết nối tới các cụm DB Cluster vật lý, Redis Sentinel/Cluster và RabbitMQ Cluster ngoài.

---

## 2. Danh Sách Biến Môi Trường (Tệp `.env` & `.env.example`)

File `.env` nằm tại thư mục gốc là nơi tập trung khai báo toàn bộ các biến môi trường nhạy cảm và thông tin cổng dịch vụ.

> [!WARNING]
> File `.env` chứa thông tin bảo mật tuyệt mật (API Keys, Mật khẩu cơ sở dữ liệu, Khóa bí mật JWT). Tuyệt đối **KHÔNG** được commit file này lên Git (đã được cấu hình chặn trong `.gitignore`).

### Đặc tả chi tiết các biến trong `.env`:

| Nhóm biến môi trường | Tên biến | Giá trị mặc định (Dev/Docker) | Ý nghĩa & Hướng dẫn cấu hình Production |
|---|---|---|---|
| **Security & JWT** | `JWT_SECRET` | `platform-jwt-secret-for-dev-only...` | Khóa bí mật dùng để ký và xác thực mã JWT Token. Trên Production **PHẢI** sinh chuỗi ngẫu nhiên 256-bit có độ dài tối thiểu 32 ký tự để đảm bảo an toàn. |
| **Security & JWT** | `CRBT_SHARED_SECRET`| `crbt-shared-secret-dev` | Khóa bí mật dùng chung giữa hệ thống và nhà mạng để xác minh chữ ký Webhook và Token thuê bao. |
| **AI Credentials** | `GEMINI_API_KEY` | *(Để trống)* | **BẮT BUỘC** điền khóa API Google Gemini hợp lệ để luồng tạo nhạc AI hoạt động. |
| **Database (Postgres)**| `POSTGRES_USER` | `postgres` | Tài khoản quản trị tối cao của PostgreSQL. |
| **Database (Postgres)**| `POSTGRES_PASSWORD`| `postgres` | Mật khẩu quản trị PostgreSQL. Trên Production cần đổi mật khẩu mạnh. |
| **Database (Postgres)**| `POSTGRES_HOST` | `postgres` | Địa chỉ IP/Domain của máy chủ chứa PostgreSQL. |
| **Cache & Lock** | `REDIS_HOST` | `redis` | Địa chỉ kết nối cụm cache Redis. |
| **Cache & Lock** | `REDIS_PASSWORD` | *(Không mật khẩu)* | Mật khẩu bảo mật Redis. Trên Prod **bắt buộc** phải đặt mật khẩu kết nối. |
| **Message Broker** | `RABBITMQ_USER` | `guest` | Tài khoản đăng nhập RabbitMQ. |
| **Message Broker** | `RABBITMQ_PASSWORD`| `guest` | Mật khẩu RabbitMQ. |
| **Object Storage** | `MINIO_ROOT_USER` | `minioadmin` | Tài khoản quản trị MinIO Object Storage Console. |
| **Object Storage** | `MINIO_ROOT_PASSWORD`| `minioadmin` | Mật khẩu quản trị MinIO Object Storage Console. |
| **External Integration**| `MYTONE_API_URL` | *(Để trống)* | Endpoint API kết nối tới hệ thống CMS Mytone của đối tác nhà mạng. |
| **External Integration**| `MYTONE_API_KEY` | *(Để trống)* | API Key xác thực gửi kèm header `X-API-Key` khi gọi CMS Mytone. |

---

## 3. Tham Số Kết Nối Cơ Sở Dữ Liệu & Connection Pool

Môi trường **Production** yêu cầu tối ưu hóa cấu hình kết nối để chịu tải cao.

```yaml
spring:
  datasource:
    # Định tuyến kết nối độc lập từng service
    url: jdbc:postgresql://${POSTGRES_HOST}:${POSTGRES_PORT}/${DATABASE_NAME}?useSSL=false
    username: ${POSTGRES_USER}
    password: ${POSTGRES_PASSWORD}
    driver-class-name: org.postgresql.Driver
    # Cấu hình pool kết nối HikariCP tối ưu
    hikari:
      pool-name: HikariCP-${spring.application.name}-Pool
      minimum-idle: 10          # Số lượng kết nối nhàn rỗi tối thiểu luôn duy trì
      maximum-pool-size: 50     # Số kết nối tối đa cho phép mở đồng thời với DB
      idle-timeout: 30000       # Thời gian giải phóng kết nối nhàn rỗi (ms) - 30 giây
      max-lifetime: 1800000     # Tuổi thọ tối đa của 1 kết nối trong pool (ms) - 30 phút
      connection-timeout: 20000 # Thời gian chờ tối đa khi lấy kết nối ra từ pool - 20 giây
```

---

## 4. Tham Số Cấu Hình Bộ Nhớ Đệm & Khóa Phân Tán (Redis & Redisson)

Hệ thống sử dụng Redis vừa làm bộ nhớ đệm cho dữ liệu danh mục nhạc vừa làm cơ chế lock giao dịch tài chính.

### Cấu hình Redis Cache (Spring Data Redis)
- **TTL Cache nổi bật/danh mục**: `86400s (24 giờ)`.
- **TTL Cache nhạc AI**: `604800s (7 ngày)` -> Tránh gọi API Gemini Lyria trùng lặp đối với cùng một cụm từ khóa (genre + mood) giúp tiết kiệm chi phí gọi API.

### Cấu hình Redisson Lock (Khóa phân tán giao dịch)
```yaml
redisson:
  address: "redis://${REDIS_HOST}:${REDIS_PORT}"
  password: ${REDIS_PASSWORD}
  # Cấu hình Connection Pool cho Redis
  connection-minimum-idle-size: 8
  connection-pool-size: 32
  # Tham số kiểm soát Transaction Lock
  lock:
    wait-time: 3000   # Thời gian chờ lấy khóa tối đa (ms) - quá 3s trả lỗi nghẽn ví
    lease-time: 10000 # Thời gian tự động giải phóng khóa (ms) tránh treo luồng nếu service chết đột tử
```

---

## 5. Tham Số Cấu Hình RabbitMQ (Retry & DLQ)

Đảm bảo tính toàn vẹn của dữ liệu thông điệp nghiệp vụ viễn thông, không để mất sự kiện.

```yaml
spring:
  rabbitmq:
    host: ${RABBITMQ_HOST}
    port: ${RABBITMQ_PORT}
    username: ${RABBITMQ_USER}
    password: ${RABBITMQ_PASSWORD}
    listener:
      simple:
        retry:
          enabled: true
          initial-interval: 1000ms    # Lần thử lại đầu tiên sau khi lỗi: 1 giây
          max-interval: 10000ms       # Khoảng cách tối đa giữa các lần thử lại: 10 giây
          multiplier: 2.0             # Hệ số tăng khoảng cách (1s -> 2s -> 4s -> 8s)
          max-attempts: 3             # Thử lại tối đa 3 lần
          stateless: true             # Quản lý transaction không trạng thái
```

---

## 6. Sơ Đồ Quy Hoạch Phân Vùng MinIO Storage Buckets

Các tệp tin đa phương tiện trong hệ thống được quản lý trong 5 bucket logic riêng biệt trên MinIO S3:

| Tên Bucket | Chế độ Quyền Hạn | Vai trò | Chính sách vòng đời (Retention / Lifecycle) |
|---|---|---|---|
| **`media-images`** | **Public Read** | Lưu trữ ảnh bìa của album, ca sĩ, nhạc chờ. | Lưu trữ vĩnh viễn, không tự động xóa. |
| **`media-audio`** | **Public Read** | Lưu trữ các sản phẩm nhạc chờ hoàn chỉnh đã mix (vocal + beat + TTS) để phát trực tuyến (Stream). | Lưu trữ vĩnh viễn, hỗ trợ Range Requests. |
| **`media-temp`** | **Private** | Chứa tệp nhạc thô do client tải lên hoặc file cắt tạm thời. | **Tự động xóa sau 24 giờ** cấu hình thông qua chính sách Lifecycle Rule của MinIO. |
| **`media-private`** | **Private** | Chứa các file ghi âm nội bộ, file sao lưu cấu hình bảo mật. | Chỉ truy cập được qua Presigned URL hết hạn nhanh (TTL < 1 giờ). |
