# CMS Admin API — Lyria Prompt Manager & Credit Transaction

> Tài liệu cho bộ phận FE. Tất cả gọi qua **API Gateway** (`http://<gateway-host>:8080`).
> Gateway tự verify JWT (Admin/CMS hoặc CRBT) và inject header nội bộ (`X-User-Id`, `X-User-Roles`, `X-Correlation-ID`).

---

## 0. Quy ước chung

### Envelope thành công — `ApiResponse<T>`
```json
{
  "success": true,
  "message": "OK",
  "data": { },
  "timestamp": 1750000000000
}
```
> ⚠️ FE check `success === true` (KHÔNG có field `code`).

### Envelope phân trang — `PageResponse<T>` (nằm trong `data`)
```json
{
  "content": [ ],
  "page": 0,
  "size": 20,
  "totalElements": 137,
  "totalPages": 7
}
```

### Envelope lỗi — `ErrorResponse`
```json
{
  "errorCode": "COMMON_BAD_REQUEST",
  "message": "Mô tả lỗi cụ thể",
  "timestamp": 1750000000000
}
```

| errorCode | HTTP | Khi nào |
|---|---|---|
| `COMMON_BAD_REQUEST` | 400 | Validate sai (template, model không hợp lệ, date range) |
| `COMMON_UNAUTHORIZED` | 401 | Thiếu `X-User-Id` |
| `COMMON_FORBIDDEN` | 403 | Không có role ADMIN |
| `COMMON_NOT_FOUND` | 404 | Version/model không tồn tại |
| `CREDIT_TRANSACTION_INVALID_DATE_RANGE` | 400 | `fromTs > toTs` |

---

# A. LYRIA PROMPT MANAGER

Service: `crbt-campaign-service`. Yêu cầu **role ADMIN** (gateway inject `X-User-Roles` chứa `ADMIN`). Thiếu → 403.

**Model hợp lệ:** `lyria-3-clip-preview`, `lyria-3-pro-preview`.
**Quy tắc template:** bắt buộc đúng **7 `%s` + 1 `%d`** (8 placeholder theo thứ tự: genre, mood, instrument, bpm, key, secondaryInstrumentation, tempoGroove, acousticEnvironment). Sai → 400.

### Object: `LyriaPromptResponse` (full)
| field | type | mô tả |
|---|---|---|
| id | number | |
| model | string | clip/pro |
| version | number | tăng dần trong từng model |
| promptTemplate | string | có 7 %s + 1 %d |
| keys | string[] | Tonal keys |
| secondaryInstrumentations | string[] | Nhạc cụ đệm phụ |
| tempoGrooves | string[] | Tiết tấu |
| acousticEnvironments | string[] | Không gian âm thanh |
| status | string | `ACTIVE` \| `INACTIVE` |
| createdBy | string | userId admin / `SYSTEM` |
| createdAt | string (ISO-8601) | |
| activatedAt | string (ISO) \| null | |
| deactivatedAt | string (ISO) \| null | |

### Object: `LyriaPromptVersionResponse` (history row — nhẹ, không kèm template/pool)
`{ id, model, version, status, createdBy, createdAt, activatedAt, deactivatedAt }`

---

### A1. Lấy version ACTIVE của 1 model
```
GET /api/campaigns/admin/lyria-prompts/active?model=lyria-3-clip-preview
```
- `model` optional, default `lyria-3-clip-preview`.
- Nếu model chưa có row → trả stub mặc định (status ACTIVE, createdBy SYSTEM).
- **200** → `ApiResponse<LyriaPromptResponse>`

### A2. Lịch sử version (Audit log)
```
GET /api/campaigns/admin/lyria-prompts/history?model=ALL
```
- `model` = `ALL` (mặc định) hoặc 1 model cụ thể.
- Sắp xếp: **ACTIVE lên đầu**, rồi `createdAt` giảm dần.
- **200** → `ApiResponse<LyriaPromptVersionResponse[]>`

### A3. Xem chi tiết 1 version (nút "Xem")
```
GET /api/campaigns/admin/lyria-prompts/versions/{model}/{version}
```
Ví dụ: `/api/campaigns/admin/lyria-prompts/versions/lyria-3-pro-preview/2`
- **200** → `ApiResponse<LyriaPromptResponse>` · **404** nếu không tồn tại.

### A4. Lưu version mới & kích hoạt ("Lưu Version mới & Kích hoạt")
```
POST /api/campaigns/admin/lyria-prompts
Content-Type: application/json
```
Body — `UpdateLyriaPromptRequest`:
```json
{
  "model": "lyria-3-clip-preview",
  "promptTemplate": "You are Lyria 3 ... genre=%s, mood=%s, instrument=%s ... %d BPM, key of %s ... %s ... %s ... %s.",
  "keys": ["C major", "G major"],
  "secondaryInstrumentations": ["solo acoustic guitar and soft flute"],
  "tempoGrooves": ["relaxed groove with a slow tempo"],
  "acousticEnvironments": ["intimate lo-fi living room session vibe"]
}
```
- Tạo version = (max version của model) + 1, set ACTIVE, deactivate version ACTIVE cũ của model đó.
- Validate: `model` ∈ 2 model; mỗi list không rỗng; template 7 %s + 1 %d.
- **200** → `ApiResponse<LyriaPromptResponse>` (version mới) · **400** sai validate.

### A5. Kích hoạt lại version cũ (nút "Kích hoạt")
```
PUT /api/campaigns/admin/lyria-prompts/versions/{model}/{version}/activate
```
- Set version đích ACTIVE (+`activatedAt`, clear `deactivatedAt`), deactivate version ACTIVE hiện tại cùng model.
- **200** → `ApiResponse<LyriaPromptResponse>` · **404** nếu version không tồn tại.

---

# B. CREDIT TRANSACTION MANAGEMENT

Service: `crbt-credit-transaction-service`. 
- **Với USER**: Lịch sử giao dịch chỉ trả về các giao dịch của chính mình (được xác định dựa trên token/JWT).
- **Với ADMIN**: Có thể tra cứu giao dịch của người dùng khác bằng cách truyền thêm tham số `msisdn`. Nếu không truyền `msisdn`, hệ thống sẽ trả về tất cả giao dịch trên toàn hệ thống (Global search).

### Object: `CreditTransactionResponse`
| field | type | mô tả |
|---|---|---|
| id | number | |
| userId | number | |
| amount | number | biến động (luôn dương) |
| direction | string | `ADD` \| `DEDUCT` |
| beforeBalance | number \| null | số dư trước (giao dịch cũ = null → hiện "-") |
| afterBalance | number \| null | số dư sau |
| reason | string | |
| referenceId | string \| null | |
| timestamp | number | epoch millis (thời điểm giao dịch) |
| createdAt | string (ISO) | thời điểm ghi ledger |
| isFree | boolean | AI music: miễn phí/mất phí |
| genType | string | `AI` \| `DIY` \| `OTHER` |
| model | string \| null | model AI (vd `lyria-3-pro-preview`), null nếu không phải AI |

### B0. Tra cứu thông tin ví theo MSISDN (nếu cần)
```
GET /auth-service/internal/crbt/user-credit/{msisdn}
```
> Trả **raw object** (KHÔNG wrap ApiResponse):
```json
{ "userId": 105, "msisdn": "84987000105" }
```
- **404** nếu không có user cho MSISDN.

### B1. Lịch sử giao dịch (phân trang)
```
GET /api/credits/history?msisdn=&direction=&reason=&fromTs=&toTs=&page=0&size=20
```
| query | type | mô tả |
|---|---|---|
| msisdn | string optional | Số điện thoại cần tra cứu (chỉ ADMIN được phép truyền, USER truyền sẽ bị 403) |
| direction | string optional | `ADD` \| `DEDUCT` |
| reason | string optional | match LIKE (chứa) |
| fromTs | string optional | Ngày bắt đầu dạng String (ví dụ: `2026-06-23` hoặc `2026-06-23 00:00:00`) |
| toTs | string optional | Ngày kết thúc dạng String (ví dụ: `2026-06-23` hoặc `2026-06-23 23:59:59`) |
| page | number | mặc định 0 |
| size | number | mặc định 20 |

- Sort: `timestamp` giảm dần.
- **200** → `ApiResponse<PageResponse<CreditTransactionResponse>>`
- **400** nếu range thời gian không hợp lệ.
- **401** thiếu Token đăng nhập.

Ví dụ response:
```json
{
  "success": true,
  "message": "OK",
  "data": {
    "content": [
      {
        "id": 1042, "userId": 105, "amount": 1, "direction": "DEDUCT",
        "beforeBalance": 12, "afterBalance": 11,
        "reason": "AI Music: Pop/Happy/Guitar", "referenceId": "MUSIC-482910",
        "timestamp": 1750000000000, "createdAt": "2026-06-22T10:00:02Z",
        "isFree": false, "genType": "AI", "model": "lyria-3-pro-preview"
      }
    ],
    "page": 0, "size": 20, "totalElements": 137, "totalPages": 7
  },
  "timestamp": 1750000000000
}
```

### B2. Export CSV đối soát
```
GET /api/credits/export?msisdn=&direction=&reason=&fromTs=&toTs=
```
- Trả `text/csv` (attachment `credit_transactions.csv`), **không phân trang** (toàn bộ theo filter).
- Hỗ trợ các filter `msisdn`, `direction`, `reason`, `fromTs`, `toTs` tương tự API lịch sử giao dịch.
- Header cột (đúng thứ tự — cột 1 `ID`, cột 2 `User ID`):
```
ID,User ID,Before Balance,After Balance,Amount,Direction,Gen Type,Model,Is Free,Reason,Reference ID,Timestamp,Created At
```
- **401** thiếu Token đăng nhập · **400** định dạng ngày hoặc range sai.

---

## C. Ghi chú cho FE

1. **Bỏ mock** trong 2 file HTML (`prompt_manager_ui_final.html`, `transaction-management.html`) — dùng API thật theo doc này.
2. Prompt UI dùng tên `instrumentations/grooves/environments` → map sang API: `secondaryInstrumentations/tempoGrooves/acousticEnvironments`.
3. Tx table: ADMIN chỉ cần truyền trực tiếp `msisdn` vào query parameter của API `/api/credits/history` hoặc `/api/credits/export` để tra cứu giao dịch của người dùng khác, không cần thông qua bước đổi MSISDN -> userId (B0) trên FE như trước.
4. Auth header: gateway tự inject từ JWT — FE chỉ cần gửi token đăng nhập (Admin cho phần A; CRBT/Admin cho phần B). ADMIN không cần tự ý chỉnh sửa header `X-User-Id` để xem thông tin người dùng khác nữa, mà sử dụng tham số `msisdn`.
5. Date: `fromTs`/`toTs` truyền dạng String (ví dụ: `yyyy-MM-dd` hoặc `yyyy-MM-dd HH:mm:ss`). Đối với trường hợp chỉ truyền ngày (vd: `2026-06-23`), hệ thống sẽ tự động hiểu `fromTs` là từ `00:00:00` và `toTs` là đến `23:59:59` của ngày đó. Các trường thời gian trả về trong Response (`timestamp`) vẫn giữ nguyên dạng epoch milliseconds.

---

## D. Ngoài phạm vi đợt này
Tab "Quản lý credit theo user" (purchased/used/remaining/gói cước) — **chưa có API**, sẽ làm epic riêng (aggregate wallet + subscription).
