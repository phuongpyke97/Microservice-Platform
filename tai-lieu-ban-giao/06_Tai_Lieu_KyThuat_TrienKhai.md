# TÀI LIỆU BÀN GIAO KỸ THUẬT & TRIỂN KHAI HỆ THỐNG

Tài liệu này cung cấp thiết kế tổng thể, sơ đồ mô hình kiến trúc C4, mô hình triển khai thực tế bằng Docker Compose, hướng dẫn cài đặt từng bước chi tiết và quy trình vận hành kiểm tra hệ thống **Mytel CRBT Microservice Platform**.

---

## 1. Thiết Kế Tổng Thể & Mô Hình Kiến Trúc (C4 Container Diagram)

Kiến trúc hệ thống được xây dựng theo mô hình phân tầng chặt chẽ. Dưới đây là sơ đồ **C4 Container Diagram** biểu diễn mối liên hệ giữa các tầng và cơ chế giao tiếp nội bộ:

```mermaid
graph TD
    Client[Thiết bị Client / App Mytel] -- "HTTP/HTTPS (Port 8080)" --> Gateway[API Gateway]
    
    subgraph Infrastructure Layer
        Gateway
        Registry[Netflix Eureka Server: 8761]
        Config[Spring Cloud Config Server: 8888]
    end

    subgraph Internal Microservices
        Auth[auth-service: 8081]
        File[file-service: 8083]
        Notify[notification-service: 8082]
        Payment[payment-gateway-service: 8085]
        Wallet[credit-wallet-service: 8086]
        Campaign[crbt-campaign-service: 8090]
        Library[crbt-community-library: 8091]
        AudioGen[audio-generation-service: 8092]
        CoreAdapter[crbt-core-adapter: 8094]
    end

    subgraph Python AI Workers
        PythonWorker[ai-media-worker: 8765]
    end

    subgraph Shared Data Stores
        DB[(PostgreSQL 16)]
        Cache[(Redis Cache & Lock)]
        Broker{RabbitMQ Message Broker}
        S3[(MinIO Object Storage)]
    end

    %% Giao tiếp nội bộ
    Gateway -.-> Registry : "Tra cứu Service Instance"
    Internal Microservices -.-> Registry : "Đăng ký dịch vụ"
    Internal Microservices -.-> Config : "Tải cấu hình khởi động"
    
    %% API Gateway điều phối
    Gateway --> Auth & File & AudioGen & Campaign & Library & Payment & Wallet
    
    %% Tương tác cơ sở dữ liệu và hạ tầng phụ trợ
    Internal Microservices --> DB
    Internal Microservices --> Cache
    Internal Microservices --> S3
    
    %% Truyền tin bất đồng bộ
    Auth & Payment & AudioGen -- "Publish Events" --> Broker
    Broker -- "Consume Events" --> Notify & CoreAdapter & AudioGen
    
    %% Gọi AI Worker
    AudioGen -- "gRPC (Port 50051)" --> PythonWorker
```

### 1.1 Vai trò của Cổng API Gateway:
API Gateway là "lá chắn" bảo mật duy nhất lộ ra ngoài Internet. Mọi dịch vụ nội bộ còn lại đều được cấu hình trong mạng con (Private Network / Docker Bridge Network), không thể truy cập trực tiếp từ IP ngoài, hạn chế tối đa nguy cơ bị quét cổng bảo mật.

---

## 2. Mô Hình Triển Khai Thực Tế (Docker Compose Deploy)

Hệ thống cung cấp file cấu hình `docker-compose.yml` tối ưu hóa cho môi trường triển khai nhanh (Staging/Production).

### 2.1 Bản Đồ Phân Bổ Cổng Dịch Vụ (Port Map)
Dưới đây là sơ đồ quy hoạch cổng kết nối của toàn bộ ứng dụng:

- **8080** -> API Gateway (Cổng truy cập ứng dụng duy nhất)
- **8761** -> Eureka Dashboard (Quản lý trạng thái các Service)
- **8888** -> Config Server (Cung cấp file cấu hình tập trung)
- **8081 - 8086** -> Layer dịch vụ nền tảng (Auth, Notification, File, Audit, Payment, Credit Wallet)
- **8090 - 8094** -> Layer nghiệp vụ chính (Campaign, Library, Audio Gen, Credit Tx, Core Adapter)
- **8765** -> Python AI Media Worker (FastAPI HTTP / gRPC)
- **15433 (Host)** -> PostgreSQL (Chỉ mở cho Admin quản trị DB)
- **6379** -> Redis (Lưu trữ cache)
- **15672** -> RabbitMQ Management Console (Quản lý hàng đợi tin nhắn)
- **9001** -> MinIO Console (Giao diện quản lý file S3)
- **3001** -> Grafana Dashboard (Giám sát Logs & System Metrics)

### 2.2 Quy Hoạch Lưu Trữ Dữ Liệu Vật Lý (Docker Volumes)
Để tránh mất mát dữ liệu khi container bị khởi động lại hoặc phá hủy, dữ liệu được ánh xạ trực tiếp từ container ra đĩa cứng của máy chủ (Host System) thông qua các Docker Volume:
- `postgres_data` -> Ánh xạ thư mục `/var/lib/postgresql/data` (Lưu trữ toàn bộ dữ liệu DB).
- `redis_data` -> Ánh xạ `/data` (Lưu trữ bản sao lưu Redis RDB/AOF).
- `rabbitmq_data` -> Ánh xạ `/var/lib/rabbitmq` (Lưu trữ trạng thái queue và tin nhắn chờ).
- `minio_data` -> Ánh xạ `/data` (Lưu trữ file vật lý trên MinIO S3).
- `loki_data` & `prometheus_data` -> Lưu trữ dữ liệu log và chỉ số giám sát hệ thống.

---

## 3. Hướng Dẫn Cài Đặt Chi Tiết Từ Môi Trường Trắng

### 3.1 Yêu Cầu Cấu Hình Máy Chủ Tối Thiểu (Prerequisites)
- **Hệ điều hành**: Ubuntu 22.04 LTS hoặc Windows Server 2022.
- **CPU**: Tối thiểu 4 Cores (Khuyên dùng 8 Cores vì chạy các tác vụ AI tách âm).
- **RAM**: Tối thiểu 16GB (Khuyên dùng 32GB để chạy mượt mà 13+ container).
- **Công cụ cài đặt**: Docker Engine v24+, Docker Compose v2.20+, Java 21 SDK, Maven 3.9+, Python 3.11.

---

### 3.2 Quy Trình Cài Đặt Từng Bước (Step-by-Step Installation)

#### Bước 1: Tải mã nguồn về máy chủ
```bash
git clone <URL_REPOSITOY> microservice-platform
cd microservice-platform
```

#### Bước 2: Thiết lập môi trường bảo mật
Copy tệp cấu hình môi trường mẫu thành tệp chạy thực tế:
```bash
cp .env.example .env
```
Sử dụng công cụ chỉnh sửa văn bản (ví dụ: `nano .env`) để điền thông số bảo mật bắt buộc:
1. Điền chuỗi khóa API Google Gemini: `GEMINI_API_KEY=your_gemini_key_here`
2. Sinh mật khẩu mạnh cho DB PostgreSQL và Redis.

#### Bước 3: Khởi động các hạ tầng phụ trợ (Data Stores & Monitoring)
Chạy lệnh sau để tải ảnh và kích hoạt toàn bộ cơ sở dữ liệu và hệ thống giám sát log:
```bash
docker compose up -d postgres redis rabbitmq minio prometheus grafana loki promtail
```
*Đợi khoảng 30 - 45 giây để các dịch vụ hoàn tất khởi tạo. Kiểm tra trạng thái sức khỏe:*
```bash
docker compose ps
```
Đảm bảo tất cả các container hiển thị trạng thái `healthy` hoặc `Up`.

#### Bước 4: Khởi chạy và Build dịch vụ tự động (Khuyên dùng cho Server)
Hệ thống hỗ trợ chạy tự động hoàn toàn thông qua Docker Compose và script điều phối `build-service-and-deploy.sh`. 

1. **Cấp quyền thực thi cho script**:
   ```bash
   chmod +x ./build-service-and-deploy.sh
   ```

2. **Chạy build và triển khai toàn bộ hệ thống**:
   Lệnh này sẽ tự động gọi Maven build code Java, tạo Docker Images cho từng service và khởi động chúng lên:
   ```bash
   ./build-service-and-deploy.sh
   ```

3. **Chạy build và cập nhật chỉ một hoặc một vài service cụ thể (Ví dụ: `auth-service`)**:
   Khi bạn chỉ sửa code của 1 service, hãy chạy lệnh này để tiết kiệm thời gian (chỉ rebuild lại service đó):
   ```bash
   ./build-service-and-deploy.sh auth-service
   ```

*(Ngoài ra, bạn cũng có thể build bằng lệnh Docker chuẩn nếu không muốn dùng script: `docker compose build && docker compose up -d`)*

---

### 3.3 Hướng dẫn khởi chạy thủ công (Chỉ dành cho môi trường Local Development)
Nếu bạn đang phát triển code trực tiếp dưới máy cá nhân (Local Dev) và muốn chạy debug từng service bằng Java process (không qua Docker container), hãy chạy theo thứ tự sau:

#### Bước 1: Build đóng gói mã nguồn Java ở local
```bash
./mvnw clean install -DskipTests
```
*(Nếu trên Windows, chạy lệnh: `mvnw.cmd clean install -DskipTests`)*

#### Bước 2: Cài đặt và kích hoạt Python AI Worker ở local
```bash
cd python-services/ai-media-worker
pip install -r requirements.txt
bash scripts/generate_protos.sh
uvicorn main:app --host 0.0.0.0 --port 8765 --reload
```

#### Bước 3: Khởi chạy các Service Java theo thứ tự bắt buộc
1. **Khởi động Eureka Server** (Registry):
   ```bash
   ./mvnw spring-boot:run -pl infrastructure/eureka-server
   ```
   *Đợi đến khi log báo: `Started EurekaServerApplication`*

2. **Khởi động Config Server**:
   ```bash
   ./mvnw spring-boot:run -pl infrastructure/config-server
   ```
   *Đợi đến khi log báo: `Started ConfigServerApplication`*

3. **Khởi chạy đồng thời các Layer Service nền tảng** (mỗi service mở 1 terminal riêng):
   ```bash
   ./mvnw spring-boot:run -pl infra-services/auth-service
   ./mvnw spring-boot:run -pl infra-services/file-service
   ./mvnw spring-boot:run -pl infra-services/credit-wallet-service
   ./mvnw spring-boot:run -pl infra-services/payment-gateway-service
   ./mvnw spring-boot:run -pl infra-services/notification-service
   ./mvnw spring-boot:run -pl infra-services/audit-log-service
   ```

4. **Khởi chạy đồng thời các Service Nghiệp vụ CRBT**:
   ```bash
   ./mvnw spring-boot:run -pl business-services/crbt-campaign-service
   ./mvnw spring-boot:run -pl business-services/crbt-community-library
   ./mvnw spring-boot:run -pl business-services/audio-generation-service
   ./mvnw spring-boot:run -pl business-services/crbt-credit-transaction-service
   ./mvnw spring-boot:run -pl business-services/crbt-core-adapter
   ```

5. **Khởi chạy API Gateway sau cùng**:
   ```bash
   ./mvnw spring-boot:run -pl infrastructure/api-gateway
   ```

---

## 4. Quy Trình Kiểm Tra & Xác Minh Hệ Thống Sau Khi Chạy (Verification)

Sau khi khởi chạy toàn bộ các dịch vụ, quản trị viên kiểm tra tính đúng đắn của hệ thống thông qua các giao diện Dashboard quản trị sau:

1. **Kiểm tra danh sách Service Instance**:
   - Truy cập Eureka Dashboard tại: `http://localhost:8761` (Sử dụng tài khoản/mật khẩu cấu hình ở `.env`, ví dụ: `eureka` / `eureka-secret`).
   - Yêu cầu: Tất cả 11 service Spring Boot đều xuất hiện trong danh sách `Instances currently registered with Eureka` với trạng thái `UP`.

2. **Kiểm tra luồng xử lý tin nhắn**:
   - Truy cập RabbitMQ Management tại: `http://localhost:15672` (tài khoản: `guest` / `guest`).
   - Yêu cầu: Kiểm tra tab `Exchanges` và `Queues` đảm bảo các exchange `crbt.event.exchange` đã được tạo và binding thành công tới các hàng đợi tin nhắn.

3. **Kiểm tra lưu trữ tệp tin**:
   - Truy cập MinIO Object Storage Console tại: `http://localhost:9001` (tài khoản: `minioadmin` / `minioadmin`).
   - Yêu cầu: Đảm bảo 5 buckets (`media-images`, `media-audio`, `media-temp`, `media-private`, `media-audio-lib`) đã được khởi tạo tự động.

4. **Kiểm tra giám sát và thu thập Log tập trung**:
   - Truy cập Grafana tại: `http://localhost:3001` (tài khoản: `admin` / `admin`).
   - Đi tới mục **Explore**, lựa chọn Datasource là **Loki**.
   - Thực hiện query log của một service bất kỳ (ví dụ: `{service_name="api-gateway"}`) để kiểm tra dòng chảy log realtime.
```
🚀 Chạy lại trên production:
./build-service-and-deploy.sh
```
