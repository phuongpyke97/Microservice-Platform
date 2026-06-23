# TÀI LIỆU BÀN GIAO API & INTEGRATION SPECIFICATION

Tài liệu này mô tả chi tiết đặc tả các giao diện lập trình ứng dụng (API), cơ chế bảo mật xác thực tích hợp, danh sách mã lỗi nghiệp vụ và đặc tả giao tiếp kết nối các hệ thống đối tác bên ngoài của **Mytel CRBT Microservice Platform**.

---

## 1. Định Tuyến API & Tài Liệu Postman/Swagger

### 1.1 Cổng Truy Cập Duy Nhất (Single Entry Point)
Toàn bộ API được client gọi tập trung qua **API Gateway** tại địa chỉ: `http://<API_GATEWAY_HOST>:8080/`. API Gateway thực hiện định tuyến động (dynamic routing) dựa trên tiền tố đường dẫn (prefix path) xuống các microservice tương ứng phía sau:

- `/api/auth/**` -> Định tuyến tới `auth-service` (8081)
- `/api/files/**` -> Định tuyến tới `file-service` (8083)
- `/api/audio/**` -> Định tuyến tới `audio-generation-service` (8092)
- `/api/campaigns/**` -> Định tuyến tới `crbt-campaign-service` (8090)
- `/api/library/**` -> Định tuyến tới `crbt-community-library` (8091)
- `/api/payments/**` -> Định tuyến tới `payment-gateway-service` (8085)
- `/api/credit/**` -> Định tuyến tới `credit-wallet-service` (8086)

### 1.2 Tài Liệu Kiểm Thử API
Hệ thống cung cấp sẵn file Postman Collection chứa đầy đủ các kịch bản gọi API ở thư mục gốc của dự án:
- **Tên file**: [CRBT_CMS_ADMIN.postman_collection.json](file:///d:/Microservice-Platform/CRBT_CMS_ADMIN.postman_collection.json)
- **Cách sử dụng**: Import file này vào phần mềm Postman, cấu hình biến môi trường `baseUrl` trỏ về `http://localhost:8080` để thực hiện kiểm thử.

---

## 2. Đặc Tả Endpoint & Dữ Liệu Mẫu (Request/Response)

### 2.1 Đăng Ký Tài Khoản (`POST /api/auth/register`)
Đăng ký tài khoản khách hàng mới.
- **Request Body**:
  ```json
  {
    "email": "user@example.com",
    "password": "Password123!"
  }
  ```
- **Success Response (201 Created)**:
  ```json
  {
    "code": "SUCCESS",
    "message": "User registered",
    "data": {
      "accessToken": "eyJhbGciOiJIUzI1...",
      "refreshToken": "eyJhbGciOiJIUzI1...",
      "expiresIn": 3600,
      "tokenType": "Bearer"
    },
    "timestamp": "2026-06-18T12:00:00Z"
  }
  ```

### 2.2 Yêu Cầu Tạo Nhạc AI (`POST /api/audio/generate`)
Gửi yêu cầu tạo nhạc AI bất đồng bộ.
- **Request Headers**:
  - `Authorization: Bearer <ACCESS_TOKEN>`
- **Request Body**:
  ```json
  {
    "type": "AI",
    "genre": "Pop",
    "mood": "Chill",
    "prompt": "Một bài hát nhẹ nhàng với tiếng piano và sáo trúc"
  }
  ```
- **Success Response (202 Accepted)**:
  ```json
  {
    "code": "SUCCESS",
    "message": "Audio generation job started",
    "data": {
      "jobId": 45892,
      "status": "PENDING",
      "progress": 0,
      "createdAt": "2026-06-18T15:30:00Z"
    },
    "timestamp": "2026-06-18T15:30:00Z"
  }
  ```

### 2.3 Kiểm Tra Trạng Thái Job Tạo Nhạc (`GET /api/audio/job/{jobId}/status`)
Polling trạng thái tiến trình tạo nhạc.
- **Request Headers**:
  - `Authorization: Bearer <ACCESS_TOKEN>`
- **Success Response (200 OK) - Đang xử lý**:
  ```json
  {
    "code": "SUCCESS",
    "message": "Job in progress",
    "data": {
      "jobId": 45892,
      "status": "PROCESSING",
      "progress": 50,
      "step": "Separating beat and vocals",
      "errorMessage": null
    },
    "timestamp": "2026-06-18T15:30:15Z"
  }
  ```
- **Success Response (200 OK) - Hoàn thành**:
  ```json
  {
    "code": "SUCCESS",
    "message": "Job completed successfully",
    "data": {
      "jobId": 45892,
      "status": "COMPLETED",
      "progress": 100,
      "step": "Done",
      "resultUrl": "https://minio.mytel.com.mm/media-audio/generated_45892.mp3"
    },
    "timestamp": "2026-06-18T15:30:45Z"
  }
  ```

### 2.4 Lấy Presigned URL Để Tải Lên Nhạc Gốc (`POST /api/files/presigned-url`)
Tạo link upload trực tiếp từ client lên MinIO S3.
- **Request Headers**:
  - `Authorization: Bearer <ACCESS_TOKEN>`
- **Request Body**:
  ```json
  {
    "fileName": "my_song.mp3",
    "contentType": "audio/mpeg",
    "fileSize": 3450000
  }
  ```
- **Success Response (200 OK)**:
  ```json
  {
    "code": "SUCCESS",
    "message": "Presigned URL generated",
    "data": {
      "fileKey": "media-temp/temp_a8f902_my_song.mp3",
      "uploadUrl": "http://localhost:9000/media-temp/temp_a8f902_my_song.mp3?X-Amz-Algorithm=AWS4-HMAC-SHA256&...",
      "expirySeconds": 300
    },
    "timestamp": "2026-06-18T15:31:00Z"
  }
  ```

---

## 3. Cơ Chế Xác Thực & Ký Bảo Mật (Signature Validation)

### 3.1 Cấu Trúc Payload JWT
Sau khi đăng nhập thành công, hệ thống cấp phát cặp JWT Token. Header xác thực có định dạng `Authorization: Bearer <TOKEN>`.
Payload JWT chứa các claim cơ bản:
```json
{
  "sub": "user@example.com",
  "userId": 10293,
  "roles": ["ROLE_USER"],
  "iat": 1718724000,
  "exp": 1718724900
}
```

### 3.2 Bộ Header Tin Cậy Nội Bộ (X-Headers)
API Gateway sau khi kiểm tra chữ ký JWT thành công sẽ chuyển tiếp các thông tin định danh xuống các Service nội bộ dưới dạng Header HTTP:
- `X-User-Id`: ID nguyên thể của User trong DB (Kiểu số `BIGINT`, ví dụ: `10293`).
- `X-User-Email`: Địa chỉ email.
- `X-User-Roles`: Chuỗi quyền phân cách bằng dấu phẩy (ví dụ: `ROLE_USER,ROLE_PREMIUM`).
- `X-MSISDN`: Số điện thoại thuê bao viễn thông (ví dụ: `95979000123`).

### 3.3 Bảo Mật Xác Thực Webhook Từ Nhà Mạng
Khi nhà mạng (Mytel) bắn Webhook thông báo sự kiện thuê bao soạn SMS mua gói cước/gia hạn về hệ thống tại API `POST /api/webhook/subscription-event`, hệ thống kiểm tra chữ ký số đi kèm ở header để tránh giả mạo:
- **Header gửi kèm**: `X-Signature: <HMAC_SHA256_HEX>`
- **Cơ chế xác minh**:
  - API Gateway sử dụng một chuỗi khóa bí mật dùng chung (`CRBT_SHARED_SECRET`) cấu hình sẵn.
  - Chữ ký được tạo bằng cách tính mã băm HMAC-SHA256 của toàn bộ nội dung JSON body (Request Body) sử dụng khóa bí mật trên.
  - Gateway so khớp chữ ký tự tính toán với giá trị nhận được từ header `X-Signature`. Nếu trùng khớp, request mới được chấp nhận xử lý.

---

## 4. Danh Sách Các Mã Lỗi Nghiệp Vụ Toàn Hệ Thống

Tất cả các exception xảy ra trong hệ thống đều được bắt tại `@RestControllerAdvice` trong module `common-core` và đóng gói thành định dạng JSON chuẩn:

| Mã Lỗi (Error Code) | HTTP Status | Mô Tả Ý Nghĩa | Hướng Xử Lý |
|---|---|---|---|
| `AUTH_USER_NOT_FOUND` | 404 | Không tìm thấy thông tin người dùng | Kiểm tra lại thông tin đăng nhập |
| `AUTH_INVALID_CREDENTIALS`| 401 | Sai tài khoản hoặc mật khẩu | Yêu cầu nhập lại mật khẩu chính xác |
| `AUTH_EMAIL_ALREADY_EXISTS`| 409 | Email đã được đăng ký trước đó | Yêu cầu dùng email khác |
| `AUTH_MSISDN_ALREADY_EXISTS`| 409 | Số điện thoại đã liên kết tài khoản khác | Dùng số thuê bao khác |
| `AUTH_ACCOUNT_LOCKED` | 403 | Tài khoản tạm thời bị khóa bảo mật | Liên hệ Admin hệ thống để mở khóa |
| `INSUFFICIENT_CREDIT` | 402 | Số dư ví credit không đủ tạo nhạc AI | Hướng dẫn thuê bao mua thêm credit |
| `TOO_MANY_JOBS` | 429 | Đã đạt giới hạn 5 job xử lý cùng lúc | Chờ các job trước hoàn tất |
| `AI_UNAVAILABLE` | 503 | Cả API Lyria và luồng Fallback đều lỗi | Trả thông báo hệ thống bận, thử lại sau |
| `WALLET_LOCKED` | 503 | Tranh chấp ví (Redisson Lock timeout) | Đợi 1-2 giây rồi tự động retry |
| `PAY_TRANSACTION_NOT_FOUND`| 404 | Không tìm thấy giao dịch bằng Idempotency Key| Kiểm tra lại mã Idempotency Key |
| `PAY_MPS_UNAVAILABLE` | 503 | Cổng cước nhà mạng viễn thông mất kết nối | Chờ kết nối phục hồi hoặc thử lại sau |
| `PAY_MPS_REJECTED` | 402 | Tài khoản điện thoại không đủ tiền trừ cước| Nạp tiền điện thoại và đăng ký lại |
| `PAY_INVALID_AMOUNT` | 400 | Số tiền hoặc số credit yêu cầu không hợp lệ | Điều chỉnh số tiền dương |

---

## 5. Đặc Tả Tích Hợp Các Hệ Thống Ngoài

Hệ thống tích hợp trực tiếp với **4 đối tác/dịch vụ ngoài** sau:

### 5.1 Google Gemini Lyria 3 (Tạo Nhạc AI)
- **Mục đích**: Nhận tham số thể loại, tâm trạng từ người dùng để sinh ra file âm thanh 30 giây chất lượng cao.
- **API Endpoint**: `https://generativelanguage.googleapis.com/v1beta/models/gemini-lyria-3:generateAudio`
- **Giao thức**: REST HTTPS JSON
- **Cơ chế xác thực**: Truyền tham số khóa `key=<GEMINI_API_KEY>` trên Query Parameter của URL.
- **Request Payload mẫu**:
  ```json
  {
    "audioConfig": {
      "audioEncoding": "LINEAR16",
      "sampleRateHertz": 44100
    },
    "prompt": "Create a upbeat piano and soft drum track in pop genre with a happy mood, length 30s"
  }
  ```

### 5.2 Microsoft Edge TTS (Đọc Lời Thuyết Minh Giọng AI)
- **Mục đích**: Chuyển lời thuyết minh của người dùng thành giọng đọc truyền cảm chất lượng cao rồi stream về backend để mix nhạc nền.
- **API Endpoint**: `wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1`
- **Giao thức**: WebSocket Secure (WSS) truyền dữ liệu dạng nhị phân (binary stream bytes).
- **Cơ chế xác thực**: Sử dụng header mặc định mô phỏng trình duyệt Edge không yêu cầu API Key trực tiếp.
- **Ngôn ngữ hỗ trợ**: Tiếng Việt (`vi-VN-HoaiMyNeural`, `vi-VN-NamMinhNeural`), Tiếng Miến Điện (`my-MM-ThihaNeural`), Tiếng Anh (`en-US-AriaNeural`).

### 5.3 Mytel MPS (Mobile Payment System - Trừ Tiền Điện Thoại)
- **Mục đích**: Trừ tiền trực tiếp vào tài khoản di động của thuê bao khi họ đăng ký gói cước.
- **API Endpoint**: `POST https://mps.mytel.com.mm/api/v2/charge`
- **Giao thức**: REST HTTPS JSON
- **Cơ chế xác thực**: Header `Authorization: Basic <BASE64_MERCHANT_ID_SECRET_KEY>`.
- **Cơ chế Idempotent (Chống trừ tiền trùng)**: Yêu cầu truyền chuỗi `idempotencyKey` duy nhất lên hệ thống MPS. Nếu xảy ra lỗi mạng chập chờn, backend gọi lại với cùng key này sẽ được trả về kết quả giao dịch cũ, không bị trừ tiền lần hai.

### 5.4 Mytone CMS (Hệ Thống Quản Lý Nhạc Chờ Nhà Mạng)
- **Mục đích**: Đẩy file nhạc đã mix lên kho nhạc nhà mạng để kích hoạt nhạc chờ thực tế trên thuê bao.
- **Giao thức**: REST HTTPS JSON + truyền tải file nhị phân.
- **Các API Endpoint**:
  1. **Upload bài hát**: `POST https://cms.mytone.vn/api/v1/ringtones/upload` (Truyền file MP3 128kbps transcode từ MinIO). Trả về mã định danh `songId`.
  2. **Đăng ký cho thuê bao**: `POST https://cms.mytone.vn/api/v1/ringtones/assign` với body:
     ```json
     {
       "msisdn": "95979000123",
       "songId": "ST_890234_YT",
       "action": "ASSIGN"
     }
     ```
