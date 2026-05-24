\*\*MICROSERVICE PLATFORM\*\*

Project Tree

D:\\Microservice-Platform\\

├── pom.xml                         ← Quản lý dependency tập trung (Spring Boot 3.2.x, Java 21)

├── docker-compose.yml              ← Kích hoạt PostgreSQL, Redis Cluster, RabbitMQ, MinIO, Loki

├── .env                            ← Lưu trữ biến môi trường tập trung bí mật

│

├── common/                         ◄ LAYER CHIA SẺ SDK (Tái sử dụng 100%)

│   ├── common-core/                ← ApiResponse, ErrorResponse, PageResponse chung \& Cấu hình Resilience4j

│   ├── common-security/            ← Tự động bóc Header X-User-Id/Roles nạp vào SecurityContext

│   ├── common-ai-sdk/              ← Cấu hình System Prompt cho Lyria 3 \& Metadata giọng đọc Edge TTS

│   └── common-rmq/                 ← Cấu hình RabbitMQ Retry Template, Dead Letter Queue (DLQ)

│

├── infrastructure/                 ◄ LAYER HẠ TẦNG ĐIỀU PHỐI (Tái sử dụng 100%)

│   ├── eureka-server/              ← Service Registry - Quản lý định danh dịch vụ nội bộ (Port 8761)

│   ├── config-server/              ← Centralized Configuration - Quản lý cấu hình tập trung (Port 8888)

│   ├── api-gateway/                ← Validate JWT, giới hạn Rate Limiting, CORS điều phối request (Port 8080)

│   └── monitoring-server/          ← Prometheus + Grafana Loki (Cảnh báo chi phí AI \& Thu gom Log tập trung)

│

├── infra-services/                 ◄ LAYER PLATFORM CORE (Tái sử dụng cho mọi dự án tương lai)

│   ├── auth-service/               ← Quản lý thực thể User, Token Lifecycle, Mã hóa BCrypt (Port 8081)

│   ├── file-service/               ← Quản lý MinIO S3 SDK, cấp phát Presigned URL (Audio whitelist) (Port 8083)

│   ├── notification-service/       ← Worker lắng nghe RabbitMQ để gửi SMS/Email thông báo bất đồng bộ (Port 8082)

│   ├── audit-log-service/          ← \[MỚI] worker lưu vết hành vi bảo mật, biến động cước và nhật ký hệ thống (Port 8084)

│   ├── payment-gateway-service/    ← Tích hợp cổng cước Telco (MPS / Charging Core nhà mạng viễn thông)

│   └── credit-wallet-service/      ← Quản lý ví Credit, khóa chống spam tạo nhạc bằng Redisson Lock

│

├── business-services/              ◄ LAYER NGHIỆP VỤ (Chỉ viết code nghiệp vụ riêng cho dự án CRBT nhạc chờ)

│   ├── crbt-campaign-service/      ← Quản lý gói cước (Gói ngày 249Ks), luật tặng 2 credit, luật gọi AI Lyria

│   ├── crbt-community-library/     ← Kho nhạc cộng đồng, thuật toán random fallback bài hát cũ khi API AI lỗi

│   ├── crbt-credit-transaction-service/ ← \[MỚI] Lưu lịch sử giao dịch số dư chi tiết, phục vụ đối soát cước tài chính

│   ├── crbt-core-adapter/          ← Cổng kết nối định dạng đẩy nhạc chuẩn sang hệ thống CMS Mytone của nhà mạng

│   └── audio-generation-service/   ← Điều phối luồng business DIY/TTS, Async Job Queue \& Tiến trình bóc tách (% loading)

│

└── python-services/                ◄ LAYER WORKER AI CORE (Cô lập hoàn toàn khỏi hệ thống Maven)

&#x20;   └── ai-media-worker/            ← FastAPI + gRPC Server phục vụ tính toán AI chuyên sâu (Port 8765)

&#x20;                                     ├── API 1: NumPy Vectorized nhận file nén từ FE -> Trả mảng tọa độ điệp khúc

&#x20;                                     ├── API 2: Mô hình bóc tách nguồn âm (Spleeter/Demucs) tách Vocal/Beat từ file 40s

&#x20;                                     └── API 3: Gọi WebSocket Microsoft sinh giọng đọc Edge TTS (Stream Bytes)



Tài Liệu Kiến Trúc \& Nghiệp Vụ Toàn Hệ Thống



| \*\*Dự án\*\*      | Mytel CRBT - AI Ringtone Platform                                                            |

| -------------- | -------------------------------------------------------------------------------------------- |

| \*\*Tech Stack\*\* | Spring Boot 3.2.x · Java 21 · FastAPI · PostgreSQL · Redis Cluster · RabbitMQ · MinIO · Loki |

| \*\*Phiên bản\*\*  | v1.0 - Tháng 5 / 2026                                                                        |

| \*\*Trạng thái\*\* | Draft - Chờ Review                                                                           |



\# \*\*Mục Lục\*\*



\# \*\*1\\. Tổng Quan Hệ Thống\*\*



Hệ thống Microservice Platform được xây dựng để phục vụ dự án Mytel CRBT (Customized Ring Back Tone) - nền tảng tạo nhạc chờ thông minh bằng AI cho mạng viễn thông. Kiến trúc được tổ chức theo mô hình phân tầng rõ ràng, đảm bảo khả năng tái sử dụng, mở rộng và bảo trì độc lập từng service.



\## \*\*1.1 Triết Lý Thiết Kế\*\*



Hệ thống tuân theo các nguyên tắc sau:



\- Loose Coupling - mỗi service hoạt động độc lập, giao tiếp qua RabbitMQ (async) hoặc Feign Client (sync). Một service chết không kéo sập service khác nhờ Circuit Breaker Resilience4j.

\- High Cohesion - mỗi service chỉ chịu trách nhiệm một nghiệp vụ cụ thể (file service chỉ lo file, auth service chỉ lo xác thực).

\- Reusability - các tầng Common SDK và Infra-Services có thể tái sử dụng 100% cho bất kỳ dự án nào trong tương lai, không cần viết lại.

\- Zero-Config Dev - chỉ cần 1 lệnh docker-compose up để khởi động toàn bộ hạ tầng phụ trợ.



\## \*\*1.2 Tổng Quan Các Tầng\*\*



| \*\*Tầng\*\*          | \*\*Số Module\*\* | \*\*Mục Đích\*\*                                               |

| ----------------- | ------------- | ---------------------------------------------------------- |

| Project Root      | 3 file        | pom.xml, docker-compose.yml, .env                          |

| Common SDK        | 4 module      | Thư viện dùng chung: response, security, AI, RabbitMQ      |

| Infrastructure    | 4 service     | Eureka, Config, API Gateway, Monitoring                    |

| Infra-Services    | 6 service     | Auth, Notification, File, Audit, Payment, Credit Wallet    |

| Business-Services | 5 service     | Campaign, Community Library, Audio Gen, Adapter, Credit Tx |

| Python AI Worker  | 1 service     | FastAPI + gRPC: dò điệp khúc, tách âm, TTS                 |



\*\*TẦNG 1 - COMMON SDK (Tái Sử Dụng 100%)\*\*



\# \*\*2\\. Common SDK\*\*



Các module trong tầng này được đóng gói thành file .jar và nhúng vào mọi service Java khác. Mục tiêu là không bao giờ viết lại boilerplate giữa các service.



\## \*\*2.1 common-core\*\*



Mục đích: Chuẩn hóa toàn bộ interface phản hồi và xử lý lỗi cho hệ thống.



| \*\*Thành phần chính\*\* | ApiResponse\&lt;T\&gt; - wrapper chuẩn cho mọi response thành công                                                                                                                             |

| -------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |

|                      | ErrorResponse - cấu trúc lỗi thống nhất với errorCode, message, timestamp                                                                                                                    |

|                      | PageResponse\&lt;T\&gt; - phân trang chuẩn hóa cho list API                                                                                                                                    |

|                      | GlobalExceptionHandler - bắt tất cả exception, trả về ErrorResponse đúng format                                                                                                              |

| \*\*Circuit Breaker\*\*  | Resilience4j tích hợp sẵn. Khi một service gọi downstream bị lỗi quá ngưỡng (50%), Circuit Breaker tự động mở, chặn request tiếp theo và trả fallback ngay lập tức, tránh timeout dây chuyền |

| \*\*Lý do dùng\*\*       | Thay vì mỗi service tự define exception handler riêng, dùng chung 1 class → đảm bảo FE nhận được format nhất quán                                                                            |



\## \*\*2.2 common-security\*\*



Mục đích: Xử lý xác thực JWT một lần duy nhất, inject thông tin user vào context.



| \*\*JwtAuthenticationFilter\*\* | Bộ lọc Spring Security tự động chạy trước mọi request. Đọc header X-User-Id, X-User-Email, X-User-Roles do API Gateway inject vào                                    |

| --------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------- |

| \*\*SecurityContext\*\*         | Sau khi bóc tách, nạp vào SecurityContextHolder - các service chỉ cần gọi SecurityUtils.getCurrentUserId() là lấy được userId, không cần đọc lại header              |

| \*\*Lý do thiết kế\*\*          | API Gateway đã validate JWT. Các service nội bộ chỉ cần trust header do Gateway bắn xuống, không cần validate JWT lần 2 - giảm latency, không cần chia sẻ secret key |



\## \*\*2.3 common-ai-sdk\*\*



Mục đích: Tập trung System Prompt và cấu hình AI để thay đổi dễ dàng.



| \*\*Lyria System Prompt\*\* | Chuỗi system prompt tối ưu cho Google Gemini Lyria 3, điều chỉnh định dạng output, thời lượng (30s), chất lượng audio                        |

| ----------------------- | -------------------------------------------------------------------------------------------------------------------------------------------- |

| \*\*TTS Metadata\*\*        | Danh sách giọng đọc Edge TTS hỗ trợ (tiếng Việt, Anh, Miến Điện), tham số rate/pitch/volume                                                  |

| \*\*Lý do tập trung\*\*     | Thay vì hardcode prompt trong audio-generation-service, đặt ở đây để PM/AI engineer có thể update prompt mà không cần redeploy service chính |



\## \*\*2.4 common-rmq\*\*



Mục đích: Cấu hình RabbitMQ nhất quán - mọi service dùng chung cấu hình retry/DLQ.



| \*\*Retry Template\*\*    | Khi gửi message thất bại: tự động thử lại 3 lần với backoff exponential (1s, 2s, 4s)                           |

| --------------------- | -------------------------------------------------------------------------------------------------------------- |

| \*\*Dead Letter Queue\*\* | Message sau 3 lần retry vẫn lỗi → chuyển vào DLQ. Từ DLQ có thể replay thủ công hoặc alert                     |

| \*\*Exchange config\*\*   | Định nghĩa sẵn tên exchange/queue chuẩn: user.registered, user.password.reset, audio.completed...              |

| \*\*Lý do dùng\*\*        | Không để mất message do mạng chập chờn. DLQ là lưới an toàn cuối cùng - không một sự kiện nghiệp vụ nào bị rơi |



\*\*TẦNG 2 - INFRASTRUCTURE (Điều Phối Hạ Tầng)\*\*



\# \*\*3\\. Infrastructure Layer\*\*



Tầng này quản lý toàn bộ hoạt động điều phối nội bộ: đăng ký service, phân phối cấu hình, xác thực request đầu vào, và giám sát hệ thống. Không chứa logic nghiệp vụ.



\## \*\*3.1 eureka-server - Port 8761\*\*



| \*\*Vai trò\*\*          | Service Registry - danh bạ điện thoại của hệ thống                                                                            |

| -------------------- | ----------------------------------------------------------------------------------------------------------------------------- |

| \*\*Cơ chế hoạt động\*\* | Mọi service Spring Boot khi khởi động tự gửi POST /register lên Eureka. Eureka lưu tên service + địa chỉ IP:Port vào registry |

| \*\*Heartbeat\*\*        | Mỗi 30 giây, service gửi heartbeat. Nếu mất heartbeat 90 giây → Eureka tự xóa service khỏi registry                           |

| \*\*Load balancing\*\*   | API Gateway dùng Spring Cloud LoadBalancer tra cứu Eureka để lấy danh sách instance còn sống, tự động round-robin             |

| \*\*Dashboard\*\*        | Giao diện web tại /eureka - bảo mật bằng Basic Auth - hiển thị toàn bộ service đang chạy                                      |

| \*\*Vì sao cần\*\*       | Với 15+ service, không thể hardcode địa chỉ IP. Eureka cho phép service tìm nhau theo tên (lb://auth-service)                 |



\## \*\*3.2 config-server - Port 8888\*\*



| \*\*Vai trò\*\*          | Kho cấu hình tập trung - thay thế file application.yml riêng lẻ trong từng service                                                                                       |

| -------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |

| \*\*Cơ chế hoạt động\*\* | Các service gọi config-server khi khởi động để lấy cấu hình (DB connection, Redis URL, API keys...). Config Server đọc file từ filesystem (native profile) hoặc Git repo |

| \*\*Môi trường\*\*       | Dev: native profile - đọc file .yml trên local. Prod: có thể trỏ đến Git private repo                                                                                    |

| \*\*Mã hóa bí mật\*\*    | Hỗ trợ mã hóa giá trị nhạy cảm trong file config bằng {cipher} prefix                                                                                                    |

| \*\*Lý do dùng\*\*       | Thay vì deploy lại service để đổi config, chỉ cần update file trên Config Server. 15 service dùng chung 1 điểm quản lý                                                   |



\## \*\*3.3 api-gateway - Port 8080\*\*



| \*\*Vai trò\*\*        | Cổng duy nhất nhận request từ Client. Không service nào được mở cổng trực tiếp ra ngoài                                                             |

| ------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------- |

| \*\*Xác thực JWT\*\*   | Interceptor tại Gateway validate JWT bằng public key. Nếu hợp lệ, bóc tách userId/email/roles rồi inject vào header trước khi forward xuống service |

| \*\*Rate Limiting\*\*  | Dùng Redis Token Bucket: tối đa 100 request/phút/IP. Vượt ngưỡng → 429 Too Many Requests                                                            |

| \*\*CORS\*\*           | Cấu hình allowed origins, methods, headers tập trung - service nội bộ không cần tự handle CORS                                                      |

| \*\*Routing\*\*        | Định tuyến theo prefix: /api/auth/\\\*\\\* → auth-service, /api/files/\\\*\\\* → file-service, /api/audio/\\\*\\\* → audio-generation-service                   |

| \*\*Không có DB\*\*    | Stateless hoàn toàn. Redis chỉ dùng cho rate limiting counter                                                                                       |

| \*\*Lý do thiết kế\*\* | Single point of entry giúp bảo mật dễ quản lý. JWT validate 1 lần, không lặp lại ở mỗi service                                                      |



\## \*\*3.4 monitoring-server\*\*



| \*\*Prometheus\*\*   | Scrape metrics từ mọi service qua /actuator/prometheus. Alert rule: nếu chi phí gọi Lyria API > ngưỡng/ngày → bắn cảnh báo Telegram/Slack        |

| ---------------- | ------------------------------------------------------------------------------------------------------------------------------------------------ |

| \*\*Grafana Loki\*\* | Thu gom tất cả log từ 15+ container về 1 giao diện. Hỗ trợ query theo service, trace ID, thời gian - không cần SSH vào từng container để xem log |

| \*\*Alerting\*\*     | Cảnh báo tự động qua Telegram khi: CPU > 80%, error rate > 1%, AI cost vượt budget hàng ngày                                                     |



\*\*TẦNG 3 - INFRA-SERVICES (Platform Core)\*\*



\# \*\*4\\. Infra-Services (Platform Core)\*\*



Các service trong tầng này xử lý các nghiệp vụ nền tảng phổ biến - xác thực người dùng, file storage, thông báo, ghi nhật ký, thanh toán, ví credit. Tất cả đều tái sử dụng được cho dự án khác mà không cần thay đổi.



\## \*\*4.1 auth-service - Port 8081\*\*



Quản lý toàn bộ vòng đời xác thực và phân quyền người dùng.



| \*\*Nghiệp vụ\*\*   | \*\*Mô tả chi tiết\*\*                                                                                                                                     |

| --------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------ |

| Đăng ký         | Nhận {username, email, password} → validate → hash password BCrypt strength 12 → lưu DB → phát sự kiện user.registered lên RabbitMQ                    |

| Đăng nhập       | Nhận {email, password} → tra DB → verify BCrypt → tạo Access Token (JWT, hết hạn 15 phút) + Refresh Token (hết hạn 7 ngày, hash SHA-256 trước khi lưu) |

| Refresh Token   | Client gửi Refresh Token → service hash → so khớp DB → cấp cặp token mới. Token cũ bị vô hiệu hóa (rotation)                                           |

| Đổi mật khẩu    | Xác thực mật khẩu cũ → hash mật khẩu mới → lưu DB → phát sự kiện user.password.changed → logout tất cả session                                         |

| Quên mật khẩu   | Tạo OTP ngẫu nhiên 6 số → lưu Redis TTL 10 phút → phát sự kiện yêu cầu gửi email/SMS → user nhập OTP để đặt lại                                        |

| Sự kiện phát ra | user.registered → notification-service gửi email chào mừng, audit-log-service ghi nhật ký đăng ký                                                      |



\## \*\*4.2 notification-service - Port 8082\*\*



Worker gửi thông báo bất đồng bộ. Không mở cổng HTTP ra ngoài API Gateway - chỉ giao tiếp qua RabbitMQ.



| \*\*Lắng nghe sự kiện\*\* | \*\*Hành động\*\*                                                                                 |

| --------------------- | --------------------------------------------------------------------------------------------- |

| user.registered       | Gửi email HTML chào mừng với tên người dùng, link xác thực tài khoản                          |

| user.password.reset   | Gửi email chứa OTP 6 số + thời gian hết hạn                                                   |

| subscription.success  | Gửi SMS xác nhận đăng ký gói cước thành công, số credit tặng kèm                              |

| audio.generated       | Push notification: bài nhạc của bạn đã sẵn sàng                                               |

| Retry logic           | Gửi thất bại → retry 3 lần (1s, 2s, 4s). Sau 3 lần vẫn lỗi → chuyển DLQ → alert kỹ thuật viên |



\## \*\*4.3 file-service - Port 8083\*\*



Quản lý toàn bộ vòng đời file trong hệ thống - từ upload đến phục vụ và xóa.



| \*\*Nghiệp vụ\*\*                       | \*\*Mô tả chi tiết\*\*                                                                                                                                                                                         |

| ----------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |

| Upload qua API (file nhỏ ≤5MB)      | Client POST multipart → service validate type/size → upload MinIO → trả về {fileKey, url}. Whitelist: jpg, png, mp3, wav, ogg                                                                              |

| Upload qua Presigned URL (file lớn) | Client xin POST /presigned-url → service tạo URL PUT MinIO TTL 5 phút → client PUT thẳng file lên MinIO (không qua backend) → client gọi POST /confirm → service move file từ bucket temp sang bucket đích |

| Phục vụ file (Stream)               | File audio trong bucket media-audio có Public URL. MinIO hỗ trợ HTTP Range Request → client có thể seek đến giây bất kỳ mà không load toàn bộ                                                              |

| Presigned GET URL                   | File private: service tạo presigned GET URL hết hạn sau 1 giờ. Sau đó URL tự hết hiệu lực                                                                                                                  |

| Soft Delete                         | Đánh dấu trường status = DELETED trong DB, không xóa vật lý trên MinIO. Cho phép phục hồi nếu xóa nhầm                                                                                                     |

| Cấu trúc Bucket                     | media-images (ảnh, public) \\| media-audio (nhạc, public) \\| media-temp (chờ confirm, auto-delete 24h) \\| media-private (nội bộ)                                                                            |



\## \*\*4.4 audit-log-service - Port 8084 \\\[MỚI\\]\*\*



Worker lưu vết hành vi bất đồng bộ cho mục đích bảo mật và đối soát. Không ảnh hưởng đến luồng chính.



| \*\*Cơ chế\*\*           | Lắng nghe RabbitMQ: login.failed, password.changed, subscription.charged, credit.deducted, admin.action                            |

| -------------------- | ---------------------------------------------------------------------------------------------------------------------------------- |

| \*\*Lưu trữ\*\*          | DB riêng biệt (audit\_db) - không share với service khác. Bảo vệ tính toàn vẹn nhật ký                                              |

| \*\*Nội dung log\*\*     | userId, action, timestamp, IP nguồn, trạng thái, metadata JSON                                                                     |

| \*\*Truy vấn\*\*         | API nội bộ /audit/query cho team security tra cứu lịch sử theo userId/action/timerange                                             |

| \*\*Lý do tách riêng\*\* | Nếu audit ghi trực tiếp trong luồng chính sẽ làm chậm response. Tách async qua RabbitMQ: luồng chính không chờ, nhật ký vẫn đầy đủ |



\## \*\*4.5 payment-gateway-service\*\*



| \*\*Vai trò\*\*        | Cầu nối giữa hệ thống và Charging Core (MPS) của nhà mạng Mytel để trừ tiền trực tiếp vào tài khoản điện thoại |

| ------------------ | -------------------------------------------------------------------------------------------------------------- |

| \*\*Luồng charge\*\*   | Nhận yêu cầu charge → gọi MPS API → nhận kết quả SUCCESS/FAILED → phát sự kiện payment.result lên RabbitMQ     |

| \*\*Xử lý lỗi\*\*      | Idempotency key - mỗi transaction có ID duy nhất. Nếu retry, MPS nhận ra đã xử lý và không trừ tiền 2 lần      |

| \*\*Reconciliation\*\* | Ghi log toàn bộ request/response MPS để đối soát hàng ngày với nhà mạng                                        |



\## \*\*4.6 credit-wallet-service\*\*



| \*\*Vai trò\*\*        | Quản lý ví tín dụng nội bộ (credit) - tiền ảo để tạo nhạc AI, tặng kèm khi mua gói                                                |

| ------------------ | --------------------------------------------------------------------------------------------------------------------------------- |

| \*\*Cộng credit\*\*    | Khi payment.success: tặng credit theo gói (VD: Gói 249K/ngày → 2 credit)                                                          |

| \*\*Trừ credit\*\*     | Khi tạo nhạc: trừ 1 credit/lần. Dùng Redisson Distributed Lock trên Redis để lock ví trong khi trừ - đảm bảo không bao giờ trừ âm |

| \*\*Redisson Lock\*\*  | Lock key = wallet:{userId}. Timeout 3 giây. Nếu 2 request đến cùng lúc, request thứ 2 chờ lock giải phóng - tránh race condition  |

| \*\*Kiểm tra trước\*\* | Trước khi tạo job audio, kiểm tra số dư > 0. Nếu không đủ → trả lỗi 402 INSUFFICIENT\_CREDIT ngay, không tạo job                   |



\*\*TẦNG 4 - BUSINESS-SERVICES (Nghiệp Vụ CRBT)\*\*



\# \*\*5\\. Business-Services (CRBT Domain)\*\*



Tầng nghiệp vụ chứa toàn bộ logic riêng của dự án Mytel CRBT. Đây là phần duy nhất thay đổi theo từng dự án - các tầng bên dưới giữ nguyên.



\## \*\*5.1 crbt-campaign-service - Port 8090\*\*



Quản lý gói cước và điều phối gọi AI Lyria.



| \*\*Nghiệp vụ\*\*       | \*\*Mô tả chi tiết\*\*                                                                                                                                         |

| ------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------- |

| Định nghĩa gói cước | Cấu hình gói: tên, giá (VD: 249K/ngày), số credit tặng kèm, thời hạn hiệu lực. Lưu trong DB, admin có thể thêm/sửa qua CMS                                 |

| Đăng ký gói         | User chọn gói → gọi payment-gateway-service charge → nhận payment.success → gọi credit-wallet-service cộng credit → lưu subscription record                |

| Luật tặng credit    | Rule engine: mỗi gói có credit\_bonus\_rules. VD: gói ngày tặng 2 credit, gói tuần tặng 15 credit. Dễ config mà không cần code lại                           |

| Gọi Lyria API       | Nhận {genre, mood, instrument} từ audio-generation-service → ghép System Prompt từ common-ai-sdk → gọi Gemini Lyria 3 → nhận WAV/MP3 30 giây → trả kết quả |

| Kiểm tra hạn mức    | Trước khi gọi Lyria: kiểm tra daily quota. Nếu vượt ngưỡng → kích hoạt fallback sang crbt-community-library                                                |



\## \*\*5.2 crbt-community-library - Port 8091\*\*



Kho nhạc nền dùng chung và cơ chế fallback khi AI lỗi.



| \*\*Kho nhạc cộng đồng\*\* | Lưu trữ các bài nhạc đã được tạo trước đó (đặc biệt là nhạc AI phổ biến). Phân loại theo genre/mood/instrument                                     |

| ---------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------- |

| \*\*Cache AI result\*\*    | Mỗi lần Lyria tạo nhạc thành công → lưu vào library theo key = hash(genre+mood+instrument). Request tương tự → trả cache thay vì gọi Lyria lần nữa |

| \*\*Fallback algorithm\*\* | Nếu Lyria lỗi hoặc vượt quota: lấy ngẫu nhiên bài có genre/mood gần nhất trong library. Độ phủ ≥ 95% yêu cầu từ cache                              |

| \*\*Tỷ lệ cache hit\*\*    | Mục tiêu: 70% request được phục vụ từ cache, không tốn API call Lyria                                                                              |

| \*\*API nội bộ\*\*         | GET /library/fallback?genre=pop\&mood=happy → trả file URL ngẫu nhiên. Chỉ gọi từ audio-generation-service                                          |



\## \*\*5.3 crbt-credit-transaction-service - Port 8093 \\\[MỚI\\]\*\*



Dịch vụ tách biệt lưu lịch sử biến động số dư chi tiết cho mục đích tài chính.



| \*\*Vấn đề giải quyết\*\*  | credit-wallet-service biết số dư hiện tại nhưng không lưu lịch sử chi tiết từng giao dịch. Service này bổ sung phần thiếu đó                      |

| ---------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------- |

| \*\*Ghi nhận giao dịch\*\* | Mỗi thay đổi số dư phát sự kiện credit.changed → service này lắng nghe và lưu: userId, amount, direction (IN/OUT), reason, referenceId, timestamp |

| \*\*Reference ID\*\*       | Mỗi giao dịch có mã tham chiếu duy nhất liên kết với payment transaction → dễ đối soát với nhà mạng                                               |

| \*\*Báo cáo đối soát\*\*   | API xuất report: /transactions/export?month=2026-05 → CSV/Excel với đầy đủ dòng tiền. Gửi cho bộ phận tài chính hàng tháng                        |

| \*\*Immutable records\*\*  | Không được sửa/xóa record sau khi tạo - đảm bảo tính pháp lý của nhật ký tài chính                                                                |



\## \*\*5.4 crbt-core-adapter - Port 8094\*\*



Cầu nối giữa hệ thống và CMS Mytone của nhà mạng để kích hoạt nhạc chờ.



| \*\*Vai trò\*\*             | Đóng gói file âm thanh đúng format kỹ thuật mà CMS Mytone yêu cầu (codec, bitrate, metadata ID3 tags)                                           |

| ----------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------- |

| \*\*Luồng xử lý\*\*         | Nhận audio URL + subscriber info → tải file từ MinIO → normalize format → POST lên CMS Mytone API → nhận song\_id → lưu mapping userId ↔ song\_id |

| \*\*Đăng ký nhạc chờ\*\*    | Sau khi upload thành công: gọi CMS API đăng ký bài hát cho thuê bao → thuê bao bắt đầu nghe nhạc này khi có người gọi đến                       |

| \*\*Error handling\*\*      | Nếu CMS Mytone lỗi: retry với exponential backoff. Sau 5 lần → chuyển vào DLQ chờ xử lý thủ công                                                |

| \*\*Tách biệt hoàn toàn\*\* | Nếu Mytone thay đổi API, chỉ cần sửa adapter này - không ảnh hưởng các service khác                                                             |



\## \*\*5.5 audio-generation-service - Port 8092\*\*



Trung tâm điều phối toàn bộ vòng đời tạo nhạc - từ request đến hoàn thành.



| \*\*Nghiệp vụ\*\*        | \*\*Mô tả chi tiết\*\*                                                                                                                                           |

| -------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------ |

| Nhận request         | POST /audio/generate với {type: DIY\\|AI, params}. Trả 202 Accepted + jobId ngay lập tức (không chờ xử lý xong). Client dùng jobId để theo dõi tiến trình     |

| Kiểm soát concurrent | Mỗi user tối đa 5 job đang chạy cùng lúc. Request thứ 6 nhận 429 TOO\_MANY\_JOBS. Dùng Redis counter per userId                                                |

| Thread Pool Async    | @Async với custom ThreadPoolTaskExecutor: 10 thread core, 30 thread max, queue 200. Job chạy nền không blocking request thread                               |

| Luồng DIY            | User upload file nhạc → gọi ai-media-worker API 1 (dò điệp khúc) → API 2 (tách vocal/beat) → nhận text TTS → gọi API 3 (Edge TTS) → mix audio → upload MinIO |

| Luồng AI             | User chọn genre/mood → check credit-wallet → check community-library cache → gọi crbt-campaign (Lyria) → nhận audio → upload MinIO → đẩy crbt-core-adapter   |

| Progress tracking    | Worker cập nhật phần trăm vào Redis: job:{jobId}:progress. Client polling GET /audio/job/{jobId}/status để lấy % realtime. WebSocket nếu muốn push           |

| Hoàn thành           | Job xong → phát sự kiện audio.generated → notification-service thông báo → crbt-core-adapter đăng ký nhạc chờ → credit-transaction-service ghi nhật ký       |



\*\*TẦNG 5 - PYTHON AI WORKER (Lõi Tính Toán AI)\*\*



\# \*\*6\\. Python AI Worker - ai-media-worker (Port 8765)\*\*



Service Python hoàn toàn tách biệt khỏi hệ sinh thái Maven Java. Dùng FastAPI + gRPC để phục vụ 3 nhóm tính toán AI chuyên sâu. Java service gọi sang Python qua gRPC (hiệu năng cao hơn REST cho binary data).



\## \*\*6.1 API 1 - Dò Điệp Khúc\*\*



| \*\*Endpoint\*\*   | gRPC: ChorusDetector.Detect() \\| HTTP: POST /api/v1/chorus                                                                                                                                    |

| -------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |

| \*\*Input\*\*      | File audio nén nhẹ: Mono, 16kHz, 32kbps (client nén trước khi gửi để giảm bandwidth)                                                                                                          |

| \*\*Thuật toán\*\* | NumPy vectorized: tính Self-Similarity Matrix (SSM) của spectrogram. Các đoạn có pattern tương tự nhau → điệp khúc. Trả về 3 timestamp (giây) đánh dấu điểm bắt đầu 3 đoạn điệp khúc hay nhất |

| \*\*Hiệu năng\*\*  | < 0.4 giây cho bài nhạc 3-5 phút nhờ vectorized NumPy (không dùng vòng lặp Python)                                                                                                            |

| \*\*Output\*\*     | { choruses: \\\[ {start: 64.2, confidence: 0.95}, {start: 128.5, confidence: 0.88}, {start: 45.1, confidence: 0.81} \\] }                                                                        |

| \*\*Ứng dụng\*\*   | FE hiển thị 3 gợi ý cắt nhạc cho user chọn - user không cần nghe toàn bộ bài                                                                                                                  |



\## \*\*6.2 API 2 - Tách Nguồn Âm\*\*



| \*\*Endpoint\*\*        | gRPC: AudioSeparator.Separate() \\| HTTP: POST /api/v1/separate                                              |

| ------------------- | ----------------------------------------------------------------------------------------------------------- |

| \*\*Input\*\*           | Đoạn audio chất lượng cao (file gốc user upload, thời lượng tối đa 40 giây)                                 |

| \*\*Model AI\*\*        | Spleeter (Deezer) hoặc Demucs (Meta). Demucs chất lượng cao hơn nhưng chậm hơn - chọn model dựa theo config |

| \*\*Output\*\*          | 2 file audio tách biệt: vocal.wav (chỉ giọng hát) + beat.wav (chỉ nhạc nền không lời)                       |

| \*\*Ứng dụng\*\*        | Cho phép user giữ lại beat nhạc gốc của mình, thêm lớp thuyết minh TTS vào - tạo nhạc chờ DIY độc đáo       |

| \*\*Thời gian xử lý\*\* | \~15-45 giây tùy model và độ dài file. Job async - không block                                               |



\## \*\*6.3 API 3 - Text-to-Speech (Edge TTS)\*\*



| \*\*Endpoint\*\*         | gRPC: TTSService.Synthesize() \\| HTTP: POST /api/v1/tts                                                                   |

| -------------------- | ------------------------------------------------------------------------------------------------------------------------- |

| \*\*Input\*\*            | { text: string, voice: string, rate: int, pitch: int }                                                                    |

| \*\*Kết nối\*\*          | WebSocket liên tục tới server Microsoft Edge TTS. Stream bytes về ngay khi có - không chờ toàn bộ file                    |

| \*\*Giọng đọc hỗ trợ\*\* | Tiếng Việt: vi-VN-HoaiMyNeural, vi-VN-NamMinhNeural. Tiếng Miến Điện: my-MM-ThihaNeural. Tiếng Anh và nhiều ngôn ngữ khác |

| \*\*Output\*\*           | Binary stream của file audio MP3/WAV - trả về realtime qua gRPC streaming                                                 |

| \*\*Ứng dụng\*\*         | Module DIY: user nhập lời thuyết minh → TTS đọc thành giọng → mixer ghép vào nhạc nền                                     |



\# \*\*7\\. Luồng Dữ Liệu Chính\*\*



Phần này mô tả 3 luồng quan trọng nhất trong hệ thống.



\## \*\*7.1 Luồng Tạo Nhạc AI (Module A)\*\*



| \*\*Bước\*\* | \*\*Thành phần\*\*    | \*\*Hành động\*\*                                                                                           |

| -------- | ----------------- | ------------------------------------------------------------------------------------------------------- |

| 1        | Client            | Gửi POST /api/audio/generate với {type: AI, genre: pop, mood: happy, instrument: piano}                 |

| 2        | API Gateway       | Validate JWT → inject X-User-Id → forward tới audio-generation-service                                  |

| 3        | audio-generation  | Trả 202 Accepted + jobId ngay. Kiểm tra: concurrent job ≤ 5, credit > 0. Tạo job async                  |

| 4        | credit-wallet     | Lock Redisson → trừ 1 credit → unlock. Nếu credit = 0 → trả lỗi, không tạo job                          |

| 5        | community-library | Tra cache hash(pop+happy+piano). Cache HIT → trả file URL ngay (bỏ qua bước 6). Cache MISS → tiếp tục   |

| 6        | crbt-campaign     | Ghép System Prompt → gọi Google Gemini Lyria 3 → nhận audio 30s → lưu vào community-library cache       |

| 7        | file-service      | Upload audio lên MinIO bucket media-audio → nhận Public URL                                             |

| 8        | crbt-core-adapter | Normalize format → POST lên CMS Mytone API → nhận song\_id → đăng ký nhạc chờ cho thuê bao               |

| 9        | RabbitMQ events   | audio.generated → notification-service push thông báo. credit.deducted → credit-transaction lưu lịch sử |



\## \*\*7.2 Luồng DIY (Module B)\*\*



| \*\*Bước\*\* | \*\*Thành phần\*\*           | \*\*Hành động\*\*                                                                         |

| -------- | ------------------------ | ------------------------------------------------------------------------------------- |

| 1        | Client                   | Upload file nhạc gốc (MP3/WAV ≤ 10MB) qua file-service Presigned URL                  |

| 2        | audio-generation         | Xác nhận upload → tạo DIY job. Trả 202 Accepted + jobId                               |

| 3        | ai-media-worker API 1    | Gửi file nén Mono 16kHz → nhận 3 tọa độ điệp khúc. FE hiển thị cho user chọn đoạn cắt |

| 4        | ai-media-worker API 2    | Gửi đoạn audio user chọn → Demucs tách thành vocal.wav + beat.wav                     |

| 5        | ai-media-worker API 3    | Gửi text lời thuyết minh do user nhập → Edge TTS stream bytes giọng đọc MP3           |

| 6        | audio-generation (mixer) | Mix: beat.wav + voice.mp3 với chuẩn LUFS. Tạo 2-3 preview với mức âm lượng khác nhau  |

| 7        | file-service + adapter   | Upload kết quả lên MinIO → crbt-core-adapter đẩy sang CMS Mytone                      |



\## \*\*7.3 Luồng Xác Thực \& Khởi Tạo User (CRBT Token Verification)\*\*



Subscriber KHÔNG đăng ký/đăng nhập qua hệ thống mình. Họ authen bên hệ thống CRBT nhà mạng. Khi gọi API, họ truyền CRBT Token vào. API Gateway dùng shared secret xin từ nhà mạng để verify. User nội bộ được tạo lazy theo MSISDN - chỉ tạo khi subscriber gọi API lần đầu.



| Subscriber (App Mytel)                                                          |

| ------------------------------------------------------------------------------- |

| → Gọi API bất kỳ, kèm: Authorization: Bearer \&lt;CRBT\_TOKEN\&gt;                 |

| │                                                                               |

| ↓                                                                               |

| API Gateway :8080                                                               |

| ├── Gọi CRBT Verify Endpoint của nhà mạng (dùng shared\_secret từ Config Server) |

| │ └── Nhà mạng trả: { msisdn, status: ACTIVE, subscriptionType }                |

| ├── Token hợp lệ → inject X-MSISDN, X-Subscription-Type vào header              |

| └── Forward request xuống service đích                                          |

| │                                                                               |

| ↓                                                                               |

| auth-service :8081 \\\[chỉ chạy lần đầu hoặc khi sync\\]                           |

| ├── Tra DB: user với msisdn đã tồn tại?                                         |

| │ ├── ĐÃ CÓ → load user, trả X-User-Id                                          |

| │ └── CHƯA CÓ → tạo mới:                                                        |

| │ ├── msisdn = X-MSISDN                                                         |

| │ ├── credit\_balance = 2 ← dùng thử 2 lần miễn phí                              |

| │ └── RabbitMQ → audit-log-service (ghi nhật ký tạo user)                       |

| └── Trả X-User-Id nội bộ → các service downstream dùng                          |



\## \*\*7.4 Luồng Tạo Nhạc Beat với Gemini Lyria 3 (Module A)\*\*



| Client → POST /api/audio/generate { type: AI, genre: pop, mood: happy, instrument: piano } |

| ------------------------------------------------------------------------------------------ |

| → API Gateway \\\[verify CRBT token → inject X-MSISDN, X-User-Id\\]                           |

| → audio-generation-service → 202 Accepted + jobId ngay lập tức                             |

| │                                                                                          |

| ├── \\\[GUARD\\] credit-wallet-service                                                        |

| │ └── Redisson Lock(wallet:{userId})                                                       |

| │ ├── credit > 0 → OK, tiếp tục                                                            |

| │ └── credit = 0 → 402 INSUFFICIENT\_CREDIT, dừng                                           |

| │                                                                                          |

| ├── \\\[GUARD\\] Redis counter job:{userId}:active                                            |

| │ ├── ≤ 5 job → OK                                                                         |

| │ └── > 5 job → 429 TOO\_MANY\_JOBS, dừng                                                    |

| │                                                                                          |

| ├── crbt-community-library                                                                 |

| │ └── Tra cache key = hash(genre+mood+instrument)                                          |

| │ ├── HIT → trả file URL ngay, bỏ qua Lyria                                                |

| │ └── MISS → tiếp tục                                                                      |

| │                                                                                          |

| ├── crbt-campaign-service                                                                  |

| │ ├── Kiểm tra daily quota Lyria                                                           |

| │ ├── Ghép System Prompt từ common-ai-sdk                                                  |

| │ ├── Gọi Google Gemini Lyria 3 → nhận WAV/MP3 30s                                         |

| │ └── Lưu kết quả vào community-library cache                                              |

| │                                                                                          |

| ├── file-service → upload MinIO (media-audio) → Public URL                                 |

| ├── crbt-core-adapter → normalize → POST CMS Mytone → đăng ký nhạc chờ                     |

| │                                                                                          |

| └── RabbitMQ async:                                                                        |

| ├── audio.generated → notification-service (push app)                                      |

| ├── credit.deducted → crbt-credit-transaction-service                                      |

| └── audio.completed → audit-log-service                                                    |

|                                                                                            |

| FALLBACK khi Lyria lỗi:                                                                    |

| Circuit Breaker (Resilience4j) > 50% lỗi trong 10s → OPEN                                  |

| → crbt-community-library.getFallback(genre, mood)                                          |

| ├── Tìm thấy → trả bài cũ, user không hay biết                                             |

| └── Không có → 503 AI\_UNAVAILABLE (< 5% trường hợp)                                        |



\## \*\*7.5 Luồng Tạo Nhạc DIY (Module B)\*\*



| Client → POST /api/audio/generate { type: DIY }                     |

| ------------------------------------------------------------------- |

| → audio-generation-service → 202 Accepted + jobId                   |

| │                                                                   |

| ├── \\\[GUARD\\] credit-wallet-service: trừ 1 credit (Redisson Lock)   |

| │                                                                   |

| ├── BƯỚC 1 - Upload nhạc gốc                                        |

| │ └── file-service: Client xin Presigned PUT URL (TTL 5 phút)       |

| │ → Client PUT file thẳng MinIO (không qua backend)                 |

| │ → Client POST /confirm → file move vào media-temp                 |

| │                                                                   |

| ├── BƯỚC 2 - Dò điệp khúc                                           |

| │ └── ai-media-worker gRPC API 1                                    |

| │ → Nén Mono 16kHz 32kbps → NumPy SSM                               |

| │ → Trả 3 timestamp điệp khúc (< 0.4s)                              |

| │ → FE hiển thị cho user chọn đoạn cắt                              |

| │                                                                   |

| ├── BƯỚC 3 - Tách nguồn âm                                          |

| │ └── ai-media-worker gRPC API 2                                    |

| │ → Spleeter / Demucs (tối đa 40s)                                  |

| │ → Trả vocal.wav + beat.wav                                        |

| │                                                                   |

| ├── BƯỚC 4 - Text-to-Speech (nếu user nhập lời thuyết minh)         |

| │ └── ai-media-worker gRPC API 3                                    |

| │ → WebSocket → Edge TTS → stream binary bytes                      |

| │ → Trả voice.mp3                                                   |

| │                                                                   |

| ├── BƯỚC 5 - Mix audio                                              |

| │ └── internal mixer: beat.wav + voice.mp3 → chuẩn LUFS             |

| │ → 2-3 preview mức âm lượng khác nhau                              |

| │                                                                   |

| ├── file-service → upload MinIO (media-audio)                       |

| └── crbt-core-adapter → CMS Mytone                                  |

|                                                                     |

| PROGRESS TRACKING:                                                  |

| Redis SET job:{jobId}:progress → 10% / 30% / 50% / 70% / 90% / 100% |

| Client polling: GET /api/audio/job/{jobId}/status                   |

| → { status: PROCESSING, progress: 50, step: "Separating audio" }    |



\## \*\*7.6 Luồng Nạp Credit (Add Credit Flow)\*\*



\### \*\*7.6a - Credit Dùng Thử (Trial)\*\*



| Subscriber lần đầu gọi API → auth-service tạo user mới                 |

| ---------------------------------------------------------------------- |

| └── credit\_balance = 2 (có thể config)                                 |

| └── crbt-credit-transaction-service ghi: +2 credit, reason=TRIAL\_GRANT |



\### \*\*7.6b - Mua Gói qua SMS / USSD\*\*



| Subscriber soạn tin mua gói: "MUA NHAC 249" → 9084                             |

| ------------------------------------------------------------------------------ |

| → Hệ thống CRBT nhà mạng charge cước                                           |

| → Nhà mạng gọi Webhook về:                                                     |

| POST /api/webhook/subscription-event                                           |

| Body: { msisdn, packageCode, status: SUCCESS, chargedAmount }                  |

| │                                                                              |

| ↓                                                                              |

| API Gateway \\\[verify webhook signature bằng shared secret\\]                    |

| ↓                                                                              |

| crbt-campaign-service                                                          |

| ├── Tra packageCode: DAILY\_249 → 3 credit/ngày \\| WEEKLY\_1119 → 21 credit/tuần |

| ├── credit-wallet-service: Redisson Lock → cộng credit vào ví                  |

| ├── RabbitMQ:                                                                  |

| │ ├── crbt-credit-transaction-service ghi: +N credit, reason=PACKAGE\\\_{code}   |

| │ └── notification-service gửi SMS xác nhận cộng credit                        |

| └── Lưu subscription: msisdn, package, startDate, endDate, autoRenew=true      |



\### \*\*7.6c - Tự Động Gia Hạn (Auto-Renew)\*\*



| Scheduler (crbt-campaign-service) chạy 00:00 mỗi đêm:   |

| ------------------------------------------------------- |

| ├── Tra DB: subscription nào đến hạn renew?             |

| ├── Nhà mạng tự charge (auto-renew phía CRBT)           |

| │ ├── SUCCESS → webhook callback → cộng credit như 7.6b |

| │ └── FAILED → không cộng, SMS thông báo hết hạn        |

| └── Subscription hết hạn không renew → status = EXPIRED |



\## \*\*7.7 Luồng Kiểm Tra \& Trừ Credit Khi Generate\*\*



| credit-wallet-service.checkAndDeduct(userId, amount=1)            |

| ----------------------------------------------------------------- |

| │                                                                 |

| ├── Acquire Redisson Lock: key=wallet:{userId}, timeout=3s        |

| │ └── Không lấy được lock → 503 WALLET\_LOCKED                     |

| │                                                                 |

| ├── SELECT balance FOR UPDATE                                     |

| │                                                                 |

| ├── balance >= 1?                                                 |

| │ ├── YES → UPDATE balance = balance - 1 → release lock → SUCCESS |

| │ └── NO → release lock → 402 INSUFFICIENT\_CREDIT                 |

| │                                                                 |

| └── Phát credit.deducted:                                         |

| { userId, amount:1, reason: AUDIO\_GENERATE, jobId, timestamp }    |

| → crbt-credit-transaction-service lưu lịch sử                     |



\## \*\*7.8 Luồng Đẩy Nhạc Lên CMS Mytone (Activate Ringtone)\*\*



| crbt-core-adapter nhận { audioUrl, msisdn, songTitle }      |

| ----------------------------------------------------------- |

| │                                                           |

| ├── Tải file từ MinIO → bộ nhớ tạm                          |

| ├── Transcode: MP3, 128kbps, 30s, ID3 tags (title + msisdn) |

| │                                                           |

| ├── POST CMS Mytone Upload API                              |

| │ ├── SUCCESS → nhận song\_id                                |

| │ └── FAILED → retry 3 lần (5s / 15s / 45s)                 |

| │ → DLQ + alert kỹ thuật viên                               |

| │                                                           |

| ├── POST CMS Mytone Register API                            |

| │ └── Đăng ký song\_id cho MSISDN → nhạc chờ kích hoạt       |

| │                                                           |

| └── Lưu mapping: { userId, msisdn, songId, activatedAt }    |



\## \*\*7.9 Luồng Xem Lịch Sử \& Đối Soát Credit\*\*



| Client → GET /api/credit/history?page=1\&size=20                 |

| --------------------------------------------------------------- |

| → crbt-credit-transaction-service                               |

| └── Trả danh sách giao dịch:                                    |

| { type: DEDUCT, amount:1, reason: AUDIO\_GENERATE, jobId, time } |

| { type: ADD, amount:3, reason: PACKAGE\_DAILY\_249, refId, time } |

| { type: ADD, amount:2, reason: TRIAL\_GRANT, time }              |

|                                                                 |

| Admin / Finance team:                                           |

| → GET /api/credit/export?month=2026-05                          |

| └── Xuất CSV/Excel toàn bộ giao dịch → đối soát với nhà mạng    |



\## \*\*7.10 Luồng Fallback \& Xử Lý Lỗi Hệ Thống\*\*



| SCENARIO 1: Gemini Lyria timeout / quota exceeded       |

| ------------------------------------------------------- |

| Circuit Breaker > 50% lỗi trong 10s → OPEN              |

| → community-library.getFallback(genre, mood)            |

| ├── Tìm thấy → trả bài cũ, không lỗi với user           |

| └── Không có → 503 AI\_UNAVAILABLE                       |

|                                                         |

| SCENARIO 2: CMS Mytone API down                         |

| crbt-core-adapter retry 3 lần → DLQ                     |

| → User đã có file nhạc, chỉ chưa kích hoạt làm nhạc chờ |

| → Push: "Nhạc đang được kích hoạt, vui lòng chờ"        |

|                                                         |

| SCENARIO 3: ai-media-worker (Python) chết               |

| gRPC → Connection refused                               |

| Circuit Breaker OPEN sau 5 lỗi liên tiếp                |

| → Job DIY: 503 AI\_WORKER\_UNAVAILABLE                    |

| → Job AI: không ảnh hưởng (không cần Python worker)     |

| → Prometheus alert → Slack/Telegram ngay                |

|                                                         |

| SCENARIO 4: credit-wallet-service quá tải               |

| Redisson Lock timeout sau 3s → 503 WALLET\_LOCKED        |

| → Client retry sau 1-2s                                 |



\# \*\*8\\. Port Map \& Data Stores\*\*



\## \*\*8.1 Bảng Quy Hoạch Port\*\*



| \*\*Service\*\*                     | \*\*Port\*\* | \*\*Layer\*\*      | \*\*Ghi chú\*\*             |

| ------------------------------- | -------- | -------------- | ----------------------- |

| api-gateway                     | 8080     | Infrastructure | Cổng duy nhất từ Client |

| eureka-server                   | 8761     | Infrastructure | Service Registry        |

| config-server                   | 8888     | Infrastructure | Centralized config      |

| auth-service                    | 8081     | Infra-Services | JWT, BCrypt             |

| notification-service            | 8082     | Infra-Services | RabbitMQ consumer only  |

| file-service                    | 8083     | Infra-Services | MinIO, Presigned URL    |

| audit-log-service \\\[NEW\\]       | 8084     | Infra-Services | Async audit trail       |

| payment-gateway-service         | 8085     | Infra-Services | Telco MPS/Charging      |

| credit-wallet-service           | 8086     | Infra-Services | Redisson Lock           |

| crbt-campaign-service           | 8090     | Business       | Gói cước, Lyria API     |

| crbt-community-library          | 8091     | Business       | Kho nhạc, Fallback      |

| audio-generation-service        | 8092     | Business       | Async job, 5 max/user   |

| crbt-credit-transaction \\\[NEW\\] | 8093     | Business       | Lịch sử đối soát        |

| crbt-core-adapter               | 8094     | Business       | CMS Mytone adapter      |

| ai-media-worker (FastAPI)       | 8765     | Python AI      | gRPC + HTTP             |

| PostgreSQL                      | 5432     | Data Store     | Primary DB              |

| Redis Cluster                   | 6379     | Data Store     | Cache, Lock, Rate Limit |

| RabbitMQ                        | 5672     | Data Store     | AMQP, 15672 mgmt UI     |

| MinIO                           | 9000     | Data Store     | 9001 console UI         |



\## \*\*8.2 Nguyên Tắc Phân Chia Port\*\*



\- 808x → Infrastructure (Gateway, Eureka, Config, Auth, Notification, File, Audit, Payment, Wallet)

\- 809x → Business Services CRBT (Campaign, Library, Audio Gen, Credit Tx, Core Adapter)

\- 876x → Python AI Services (ai-media-worker)

\- 54xx / 63xx / 56xx / 90xx → External Data Stores (PostgreSQL, Redis, RabbitMQ, MinIO)



\_Tài liệu được soạn thảo từ dữ liệu kiến trúc dự án Mytel CRBT. Phiên bản v1.0 - Tháng 5/2026.\_



\_Mọi thắc mắc hoặc đề xuất cập nhật, vui lòng liên hệ Tech Lead để review và phê duyệt.\_

