# Báo Cáo Phân Tích PRD/Plan vs Implementation

Qua việc đối chiếu `docs/PLAN.md` và `docs/Microservice_Platform_Architecture.md` với mã nguồn hiện tại, dưới đây là chi tiết các requirement đã được cài đặt, chưa hoàn thiện, và những requirement hoàn toàn bị bỏ sót.

## 1. Yêu Cầu Đã Implement Đầy Đủ

- **Architecture Foundation**: Toàn bộ 11 Java services, 1 Python worker, Eureka, Config Server, Gateway đều khởi động thành công.
- **Hybrid Auth Model**: API Gateway hỗ trợ cả CRBT Token (`CrbtTokenFilter`) và JWT (`JwtAuthFilter`), inject đúng header. Auth service lazy-create được tài khoản theo MSISDN, gán 2 credit trial.
- **Wallet & Payment**: Wallet service dùng Redisson Lock (`wallet:{userId}`) chính xác, ngăn trừ âm. Payment có Idempotency key.
- **Observability**: Prometheus, Grafana Loki, Micrometer Tracing đã được tích hợp qua `common-core`. 
- **Notification & Audit**: Notification không mở cổng HTTP, chỉ consume RMQ. Audit lưu asynchronous vào DB riêng.
- **Python AI Worker**: Chứa đủ FastAPI + gRPC, implement đủ 3 chức năng: chorus detector, separator, TTS.
- **Microservice Communication**: Các service đều dùng Feign, có Resilience4j @CircuitBreaker.

## 2. Bảng Gap (Thiếu sót và Sai logic)

Dưới đây là các điểm sai lệch so với spec, sắp xếp theo mức độ nghiêm trọng:

| Requirement | Status | File liên quan | Ghi chú |
|---|---|---|---|
| **[Campaign] Scheduler auto-renew 00:00 (T8.7)** | 🔴 CHƯA LÀM | `CampaignService.java` / `Scheduler` | Hoàn toàn vắng bóng logic gia hạn hàng đêm. Lỗi nghiêm trọng ảnh hưởng doanh thu (critical). |
| **[Campaign] Rule engine credit_bonus (T8.3)** | 🔴 CHƯA LÀM | `CampaignService.java` | Luồng subscribe hiện đang fix cứng lấy `pkg.getCreditAmount()`, không có engine xử lý luật tặng thêm. |
| **[Audio Gen] Luồng DIY: Dò điệp khúc + Tách âm (T9.5)** | 🟡 THIẾU LOGIC | `AudioGenerationService.java` | Service hiện tại chỉ gọi `generateTts` của AI Worker, hoàn toàn bỏ qua việc gọi API 1 (Chorus) và API 2 (Separate). |
| **[Audio Gen] Redis counter & Progress tracking (T9.3, T9.6)** | 🟡 LÀM SAI | `AudioGenerationService.java` | Spec yêu cầu dùng Redis để đếm concurrent job và push progress. Code thực tế đang dùng câu lệnh SQL `countActiveJobsByUserId()`. |
| **[Audio Gen] AsyncConfig parameters (T9.1)** | 🟢 SAI CONFIG | `AsyncConfig.java` | Spec yêu cầu core 10/max 30/queue 200. Code đang để core 4/max 8/queue 50. |
| **[Library] Cache Lyria kết quả & Fallback random API (T7.9, T7.10, T7.11)** | 🔴 CHƯA LÀM | `RingtoneService.java`, `RingtoneController.java` | Chưa cài đặt Redis cache theo `hash(genre+mood+instrument)`. Không có hàm `getRandomRingtone` hay API fallback. |
| **[Core Adapter] Tải MinIO + Transcode ID3 (T9.10)** | 🔴 CHƯA LÀM | `RingtoneAssignmentService.java` | Đang gửi thẳng original URL sang CMS, chưa hề có logic tải về, chuyển đuôi MP3 128kbps và gán ID3 Tags. |
| **[Core Adapter] Retry DLQ (T9.12)** | 🟡 THIẾU LOGIC | `MytoneCmsClient.java` | Đã có `@Retry` nhưng chưa config exponential backoff và chưa kết nối với Dead Letter Queue khi cạn lượt retry. |
| **[Credit Tx] Export CSV/Excel (T7.5)** | 🔴 CHƯA LÀM | `CreditTransactionController.java` | Mới có API xem lịch sử phân trang, chưa có hàm xuất file báo cáo. |

## 3. Tổng Kết
* **Tầng Core & Infra (S1 → S5)**: Triển khai cực kỳ tốt, đúng 100% so với architecture.
* **Tầng Python Worker (S6)**: Đạt yêu cầu.
* **Tầng Business (S7 → S9)**: Thiếu rất nhiều logic liên kết (Business flow). Cụ thể là luồng AI chưa được lưu cache, luồng DIY bị cắt xén, luồng Core Adapter thiếu bước xử lý media (transcode), và Campaign thiếu hẳn module auto-renew.

**Ưu tiên fix**: 
1. `Campaign Scheduler` (Auto renew quyết định dòng tiền).
2. `AudioGenerationService DIY Flow` (Gắn kết các API lại với nhau thay vì chỉ dùng TTS).
3. `Core Adapter Transcode` (CMS Mytone sẽ reject file nếu sai chuẩn).