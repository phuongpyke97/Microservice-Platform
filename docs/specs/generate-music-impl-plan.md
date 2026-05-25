# Kế hoạch Implement: POST /campaigns/generate

> Ngày lập: 2026-05-25
> Trạng thái: READY TO IMPLEMENT

---

## 1. Mô Tả Nghiệp Vụ

User mở app CRBT → menu "AI CRBT (Sáng tác nhạc)" → chọn genre/mood/instrument → gọi API.

### Flow đầy đủ

```
POST /campaigns/generate?genre=Pop&mood=Happy&instrument=Piano
  Header: Authorization: Bearer <CRBT_TOKEN>
  (Gateway đã validate bằng CRBT_SHARED_SECRET, inject X-MSISDN)

[Step 1] Auth & Credit Check
  → Đọc X-MSISDN từ header
  → Blank/missing → 401 UNAUTHORIZED

  → Gọi AuthServiceClient.getUserByMsisdn(msisdn):
      Case A: User TỒN TẠI + credit_balance >= 1  → OK, lấy userId
      Case B: User TỒN TẠI + credit_balance = 0   → 402 INSUFFICIENT_CREDIT (FE → màn hình mua gói)
      Case C: User CHƯA TỒN TẠI                   → auto-create + gán 2 trial credit → OK

[Step 2] Validate Params
  → genre, mood, instrument: NOT blank, max 50 chars
  → Fail → 400 BAD_REQUEST

[Step 3] Cache Lookup (Redis)
  hashKey = SHA256(genre + ":" + mood + ":" + instrument)

  pool = Redis LRANGE "lyria:pool:{hashKey}" 0 -1
         (mỗi entry là JSON: {"url":"...", "owner":"msisdn_nguoi_tao"})
  seen = Redis SMEMBERS "lyria:seen:{msisdn}:{hashKey}"
         (các URL user này đã từng nhận trước đó)

  excluded = seen ∪ { entry.url | entry.owner == msisdn }
             (loại: đã nghe rồi + chính mình tạo ra)

  available = [ entry.url | entry.url NOT IN excluded ]

  --- CACHE HIT ---
  available NOT empty:
    → Random pick 1 URL từ available
    → Redis SADD "lyria:seen:{msisdn}:{hashKey}" {url}
    → Trừ 1 credit (Step 5)
    → Return url

  --- CACHE MISS ---
  available empty:
    → Gọi Lyria 3 API (Step 4)

[Step 4] Gọi Lyria 3 & Upload MinIO (chỉ khi CACHE MISS)
  prompt = LyriaSystemPromptConfig.buildPrompt(genre, mood, instrument)
  bytes  = LyriaClient.generateMusic(prompt)
  url    = FileServiceClient.uploadAudio(bytes, "media-audio")
  → Redis RPUSH "lyria:pool:{hashKey}" JSON{"url": url, "owner": msisdn}
  → Redis SADD  "lyria:seen:{msisdn}:{hashKey}" {url}
  → Trừ 1 credit (Step 5)
  → Return url

[Step 5] Trừ Credit (luôn thực hiện kể cả cache hit)
  → Publish CreditChangedEvent tới RabbitMQ:
      userId   = userId (lấy từ Step 1)
      amount   = 1
      type     = "OUT"
      reason   = "AI Music: {genre}/{mood}/{instrument}"
      refId    = "MUSIC-{uuid}"
  → credit-wallet-service consume → trừ 1 credit_balance

[Step 6] Response
  → 200 OK: ApiResponse<GenerateMusicResponse> { "url": "http://minio.../media-audio/lyria-xxx.mp3" }
```

---

## 2. Logic Cache Chống Trùng Lặp

**Quy tắc loại trừ khi chọn URL từ pool:**
1. `entry.owner == msisdn` → loại (không lấy track chính mình đã tạo ra)
2. `url IN seen_set_của_user` → loại (không lấy track đã nghe trước đó)
3. Chọn **ngẫu nhiên** từ `available` → phá vỡ pattern circular A→B→C→A

**Ví dụ minh họa:**
```
Pool[hashKey] = [
  {url: "url_A", owner: "0901111111"},
  {url: "url_B", owner: "0902222222"},
  {url: "url_C", owner: "0903333333"}
]

User A (0901111111) request lại:
  excluded = seen_A ∪ {url_A}  →  available = [url_B, url_C]  →  random pick

User B (0902222222) request:
  excluded = seen_B ∪ {url_B}  →  available = [url_A, url_C]  →  random pick
```

**Redis TTL:** Không set TTL mặc định. Giới hạn pool: nếu `LLEN > 100` thì LTRIM giữ 100 entries mới nhất.

---

## 3. Thay Đổi Cần Làm

### 3.1 file-service — Thêm internal endpoint (NEW)

**`FileController.java`** — thêm:
```java
@PostMapping(value = "/internal/upload-audio", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
public ResponseEntity<ApiResponse<String>> uploadAudio(
        @RequestBody byte[] audioBytes,
        @RequestParam(defaultValue = "media-audio") String bucket) {
    String url = fileService.uploadAudioBytes(audioBytes, bucket);
    return ResponseEntity.ok(ApiResponse.success(url));
}
```

**`FileService.java`** — thêm method:
```java
public String uploadAudioBytes(byte[] bytes, String bucket) {
    String objectName = "lyria-" + UUID.randomUUID() + ".mp3";
    // dùng MinIO SDK: minioClient.putObject(...)
    return minioProperties.getEndpoint() + "/" + bucket + "/" + objectName;
}
```

### 3.2 auth-service — Kiểm tra & bổ sung internal endpoints

Cần verify 2 endpoint đã có chưa, nếu chưa thì thêm:

- `GET /internal/users/msisdn/{msisdn}` → trả `{userId, msisdn, creditBalance}`
- `POST /internal/users/trial` body `{"msisdn":"..."}` → tạo user + 2 credit, trả `{userId, msisdn, creditBalance}`

### 3.3 crbt-campaign-service — Files cần tạo/sửa

#### TẠO MỚI

**`client/AuthServiceClient.java`**
```java
@FeignClient(name = "auth-service", fallbackFactory = AuthServiceClientFallback.class)
public interface AuthServiceClient {
    @GetMapping("/internal/users/msisdn/{msisdn}")
    ApiResponse<UserCreditResponse> getUserByMsisdn(@PathVariable String msisdn);

    @PostMapping("/internal/users/trial")
    ApiResponse<UserCreditResponse> createTrialUser(@RequestBody CreateTrialUserRequest request);
}
```

**`client/FileServiceClient.java`**
```java
@FeignClient(name = "file-service", fallbackFactory = FileServiceClientFallback.class)
public interface FileServiceClient {
    @PostMapping(value = "/internal/upload-audio", consumes = "application/octet-stream")
    ApiResponse<String> uploadAudio(@RequestBody byte[] bytes,
                                    @RequestParam(defaultValue = "media-audio") String bucket);
}
```

**`client/dto/UserCreditResponse.java`**
```java
public record UserCreditResponse(Long userId, String msisdn, int creditBalance) {}
```

**`client/dto/CreateTrialUserRequest.java`**
```java
public record CreateTrialUserRequest(String msisdn) {}
```

**`service/MusicGenerationService.java`**
- Implement toàn bộ Steps 1–6
- Dependencies: `AuthServiceClient`, `FileServiceClient`, `LyriaClient`,
  `LyriaSystemPromptConfig`, `RedisTemplate<String, String>`, `RabbitTemplate`
- Method: `public GenerateMusicResponse generate(String msisdn, String genre, String mood, String instrument)`

**`dto/response/GenerateMusicResponse.java`**
```java
public record GenerateMusicResponse(String url) {}
```

**`client/fallback/AuthServiceClientFallback.java`** + **`FileServiceClientFallback.java`**
- Fallback auth: throw `BaseException(CommonErrorCode.COMMON_SERVICE_UNAVAILABLE)`
- Fallback file: throw `BaseException(CampaignErrorCode.CAMPAIGN_FILE_UPLOAD_FAILED)`

#### SỬA

**`exception/CampaignErrorCode.java`** — thêm 2 error code:
```java
CAMPAIGN_INSUFFICIENT_CREDIT("CAMPAIGN_INSUFFICIENT_CREDIT", "Insufficient credits to generate music", 402),
CAMPAIGN_FILE_UPLOAD_FAILED("CAMPAIGN_FILE_UPLOAD_FAILED", "Failed to upload generated audio", 500)
```

**`controller/CampaignController.java`** — sửa endpoint `/generate`:
```java
// Xóa inject LyriaService, thêm MusicGenerationService
@PostMapping("/generate")
public ApiResponse<GenerateMusicResponse> generate(
        @RequestHeader(value = "X-MSISDN", required = false) String msisdn,
        @RequestParam @NotBlank @Size(max = 50) String genre,
        @RequestParam @NotBlank @Size(max = 50) String mood,
        @RequestParam @NotBlank @Size(max = 50) String instrument) {
    if (msisdn == null || msisdn.isBlank()) {
        throw new BaseException(CommonErrorCode.COMMON_UNAUTHORIZED);
    }
    return ApiResponse.success(musicGenerationService.generate(msisdn, genre, mood, instrument));
}
```

**`service/LyriaService.java`** — XÓA (logic chuyển hết vào `MusicGenerationService`)

---

## 4. Thứ Tự Implement Ngày Mai

```
[1] Kiểm tra auth-service
    → Tìm file AuthController / UserService
    → Xem đã có /internal/users/msisdn/{msisdn} và /internal/users/trial chưa
    → Nếu thiếu: thêm vào auth-service

[2] Thêm file-service internal endpoint
    → FileService.uploadAudioBytes()
    → FileController POST /internal/upload-audio

[3] Build 2 service trên
    → ./mvnw clean install -DskipTests -pl infra-services/file-service
    → ./mvnw clean install -DskipTests -pl infra-services/auth-service

[4] Tạo DTOs trong crbt-campaign-service
    → UserCreditResponse, CreateTrialUserRequest, GenerateMusicResponse

[5] Tạo AuthServiceClient + FileServiceClient + 2 fallback classes

[6] Tạo MusicGenerationService (full logic Steps 1-6)
    → Chú ý: Redis key pattern đúng
    → Chú ý: JSON serialize/deserialize pool entries {"url","owner"}
    → Chú ý: Random.nextInt(available.size())

[7] Sửa CampaignErrorCode (thêm 2 error mới)

[8] Sửa CampaignController.generate() + xóa LyriaService

[9] Build crbt-campaign-service
    → ./mvnw clean install -DskipTests -pl business-services/crbt-campaign-service

[10] Integration test thủ công
    → credit = 0 → expect 402
    → msisdn mới (chưa trong DB) → auto-create + 2 credit → expect 200 + url
    → MISS cache → Lyria call → MinIO → url trong Redis pool
    → HIT cùng params user khác → random url từ pool (không phải của chính mình)
    → Lấy lại cùng params với chính mình → skip own URL, lấy URL của người khác
```

---

## 5. Các Điểm Rủi Ro

| Rủi ro | Mức | Xử lý |
|--------|-----|-------|
| auth-service down khi check credit | CRITICAL | Fallback throw 503, KHÔNG cho generate |
| Lyria API timeout / fail | HIGH | @CircuitBreaker đã có. Nếu fail → throw CAMPAIGN_FILE_UPLOAD_FAILED hoặc domain exception |
| file-service down khi upload | HIGH | Fallback throw CAMPAIGN_FILE_UPLOAD_FAILED |
| Race condition: 2 request cùng user cùng params lúc MISS | MEDIUM | Lyria có thể bị gọi 2 lần → 2 URL vào pool (chấp nhận được về mặt nghiệp vụ) |
| Pool Redis lớn vô hạn | LOW | LLEN check trước RPUSH, nếu > 100 thì LTRIM |
| JSON deserialize pool entry lỗi | LOW | Dùng try-catch khi parse, skip entry lỗi |

---

## 6. Không Thay Đổi

- Gateway CRBT token validation + X-MSISDN injection — giữ nguyên
- `LyriaClient.generateMusic(prompt)` — giữ nguyên signature
- `CampaignService` (subscribe, renew) — không đụng vào
- `CampaignScheduler` — không đụng vào
- `LyriaSystemPromptConfig.buildPrompt()` — giữ nguyên
