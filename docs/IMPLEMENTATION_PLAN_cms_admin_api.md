# Implementation Plan — CMS Admin API (Backend only)

> Scope: API backend cho 2 màn CMS Admin. FE đã có người làm.
> Nguồn nghiệp vụ: `docs/prompt_manager_ui_final.html`, `docs/transaction-management.html`.
> Quyết định scope đã chốt với chủ dự án:
> 1. Prompt Manager: **full per-model + version history + activate-past**.
> 2. Bảng giao dịch: **plumb end-to-end** beforeBalance / afterBalance / model.
> 3. Tab "Quản lý credit theo user": **để mock, làm sau** (tách epic riêng).
> 4. Plan-only — chờ duyệt trước khi code.

---

## 0. Bối cảnh hiện trạng (đã đọc code)

| Thành phần | Hiện trạng | Khoảng cách so với UI |
|---|---|---|
| `crbt-campaign-service` LyriaPromptConfig | 1 active duy nhất, **không** model, **không** version, dùng `updatedAt/updatedBy` | Thiếu: `model`, `version`, `activatedAt`, `deactivatedAt`, `createdBy`; thiếu history + activate-past + get-active-by-model |
| `AdminLyriaPromptController` | chỉ `GET/PUT /campaigns/admin/lyria-prompts/active` | Thiếu list-history, get-active-by-model, save-new-version, activate-version |
| `LyriaPromptProviderImpl` | `findFirstByStatusOrderByIdDesc("ACTIVE")` — model-unaware | Sinh nhạc cần lấy active prompt **theo model** đang dùng |
| `crbt-credit-transaction-service` | history + export đã có, filter direction/reason/fromTs/toTs/page/size OK | Response thiếu `beforeBalance`, `afterBalance`, `model` |
| `CreditChangedEvent` (common-rmq) | `(userId, amount, direction, reason, referenceId, timestamp, isFree, genType)` | Thiếu `beforeBalance`, `afterBalance`, `model` |
| `WalletService` deduct/add | đã có `isFree`, `genType`; biết balance trước/sau trong method | Cần emit before/after + nhận `model` |
| `AmountRequest` + `WalletAmountRequest` (campaign/audio-gen) | có `isFree`, `genType` | Cần thêm `model` |
| Auth `/internal/crbt/user-credit/{msisdn}` | **Đã tồn tại** (`InternalCrbtController`, `UserCreditInternalResponse`) | Không cần làm — chỉ verify path khớp UI |

UI Prompt Manager hiện 100% mock client-side (chưa gọi API). Mục tiêu: cung cấp endpoint + contract rõ để FE wiring.

---

## FEATURE A — Lyria Prompt & Version Manager (`crbt-campaign-service`, port 8090)

### A1. Mô hình dữ liệu (per-model versioning)

Bảng `lyria_prompt_config` mở rộng thành ledger version theo model:

| Cột | Kiểu | Ghi chú |
|---|---|---|
| id | BIGINT PK | giữ nguyên |
| model | VARCHAR(60) NOT NULL | `lyria-3-clip-preview` \| `lyria-3-pro-preview` |
| version | INT NOT NULL | số version tăng dần **trong từng model** |
| prompt_template | TEXT NOT NULL | giữ nguyên |
| keys_json / secondary_instrumentations_json / tempo_grooves_json / acoustic_environments_json | TEXT | giữ nguyên (JsonStringListConverter) |
| status | VARCHAR(20) | `ACTIVE` \| `INACTIVE` |
| created_by | VARCHAR(100) | người tạo (thay cho updated_by) |
| created_at | TIMESTAMP | giữ |
| activated_at | TIMESTAMP NULL | set khi ACTIVE |
| deactivated_at | TIMESTAMP NULL | set khi chuyển INACTIVE |

Bất biến: **mỗi model tối đa 1 row ACTIVE**. Row cũ không xoá (audit log) — chỉ đổi status + set deactivated_at.

**Flyway** (rule 10): `src/main/resources/db/migration/V{n}__alter_lyria_prompt_config_versioning.sql`
- `ALTER TABLE` thêm cột mới.
- Backfill row hiện có: `model='lyria-3-clip-preview'`, `version=1`, `created_by=COALESCE(updated_by,'SYSTEM')`, `activated_at=created_at` nếu ACTIVE.
- (giữ `updated_at/updated_by` để không vỡ, hoặc drop sau — đề xuất giữ).

### A2. Files

**Sửa:**
- `entity/LyriaPromptConfig.java` — thêm field `model`, `version`, `createdBy`, `activatedAt`, `deactivatedAt`; constructor + getter/setter.
- `repository/LyriaPromptConfigRepository.java` — thêm:
  - `Optional<LyriaPromptConfig> findFirstByModelAndStatusOrderByVersionDesc(String model, String status)`
  - `List<LyriaPromptConfig> findByModelOrderByVersionDesc(String model)`
  - `List<LyriaPromptConfig> findAllByOrderByModelAscVersionDesc()`
  - `Optional<LyriaPromptConfig> findByModelAndVersion(String model, int version)`
  - `Optional<LyriaPromptConfig> findTopByModelOrderByVersionDesc(String model)` (max version)
- `service/LyriaPromptAdminService.java` — viết lại theo model:
  - `getActive(model)` — active của model (fallback stub nếu rỗng).
  - `listHistory(modelFilter)` — `ALL` hoặc 1 model; sort ACTIVE-first rồi version desc (khớp UI `renderHistoryTable`).
  - `saveNewVersion(model, request)` — validate template (7×`%s` + 1×`%d`, giữ logic `countOccurrences`); deactivate active hiện tại của model (set INACTIVE + deactivated_at); tính version = max+1; tạo row ACTIVE mới; `createdBy` = SecurityUtils userId. `@CacheEvict(lyria_prompts)`.
  - `activateVersion(model, version)` — set row đích ACTIVE + activated_at + clear deactivated_at; deactivate active cũ cùng model. `@CacheEvict`.
- `service/LyriaPromptProviderImpl.java` — model-aware:
  - `getActiveConfig(String model)` → `findFirstByModelAndStatusOrderByVersionDesc(model,"ACTIVE")`.
  - Cache key theo model: `@Cacheable(value="lyria_prompts", key="#model")`.
  - **Cần check downstream**: `LyriaPromptProvider` interface (common-ai-sdk) + nơi gọi sinh nhạc (`MusicGenerationService`) để truyền model đang generate. Nếu interface chưa có param model → thêm overload `getTemplate(model)` … giữ method cũ delegate model mặc định để không vỡ test.
- `controller/AdminLyriaPromptController.java` — endpoint mới (giữ `requireAdminRole()`).

**Tạo mới:**
- `dto/response/LyriaPromptVersionResponse.java` (record) — 1 dòng history.
- (tuỳ chọn) `dto/request/ActivateVersionRequest.java` — hoặc dùng path param.

**LyriaPromptResponse.java**: thêm `model`, `version`, `createdBy`, `activatedAt`, `deactivatedAt`.

### A3. Endpoints (base `/campaigns/admin/lyria-prompts`, wrap `ApiResponse<T>`, role ADMIN)

| Method | Path | Mục đích | UI hook |
|---|---|---|---|
| GET | `/active?model=` | active version của model | `onModelChange` |
| GET | `/history?model=ALL\|<model>` | list history (ACTIVE-first, version desc) | tab "Lịch sử Phiên bản" |
| GET | `/versions/{model}/{version}` | xem 1 version cụ thể | nút "Xem" |
| POST | `/` (body có `model` + template + 4 pool) | lưu version mới & kích hoạt | "Lưu Version mới & Kích hoạt" |
| PUT | `/versions/{model}/{version}/activate` | kích hoạt version cũ | nút "Kích hoạt" |

Giữ `GET/PUT /active` (no model) như alias tương thích ngược → map vào model mặc định `lyria-3-clip-preview` (đánh dấu deprecated).

### A4. Contract mapping (lưu ý naming cho FE)

UI JS dùng `keys / instrumentations / grooves / environments`. Backend dùng `keys / secondaryInstrumentations / tempoGrooves / acousticEnvironments`. → **Giữ tên backend** (đã ổn định), tài liệu hoá mapping để FE adapt. `UpdateLyriaPromptRequest` thêm field `model` (NotBlank, validate ∈ 2 model).

Validate template giữ nguyên: đúng 7×`%s` + 1×`%d`, sai → `COMMON_BAD_REQUEST` (đã có message VN).

---

## FEATURE B — Credit Transaction Management (`crbt-credit-transaction-service`, port 8093)

### B1. Plumb end-to-end: beforeBalance / afterBalance / model

Chuỗi thay đổi (theo thứ tự build để không vỡ compile):

1. **common-rmq** `CreditChangedEvent` — thêm `Integer beforeBalance, Integer afterBalance, String model`.
   - Thêm compact/overload constructor giữ chữ ký cũ (`...isFree, genType`) → default null/null/null. Tránh vỡ test & caller cũ.
2. **credit-wallet-service** `WalletService`:
   - `deductCredit(...)`: `before = wallet.getBalance()` (trước `deductBalance`); `after = wallet.getBalance()` (sau). Emit kèm before/after + `model`.
   - `addCredit(...)`: tương tự (before trước `addBalance`).
   - Thêm overload nhận `model`; method cũ delegate `model=null`.
   - `AmountRequest` (+ `WalletController`): thêm `String model` (nullable). Compact constructor giữ tương thích.
3. **Callers truyền model** (chỉ AI music mới có model thực, còn lại null/"-"):
   - `crbt-campaign-service` `WalletAmountRequest` (client dto) + nơi gọi deduct trong `MusicGenerationService` → set `model` = model đang generate.
   - `audio-generation-service` `WalletAmountRequest` + `AudioGenerationService` → set model nếu là AI music; DIY/khác để null.
4. **crbt-credit-transaction-service**:
   - `entity/CreditTransaction` — thêm cột `before_balance` (INT null), `after_balance` (INT null), `model` (VARCHAR(60) null) + getter; constructor mở rộng (giữ constructor cũ delegate null).
   - `service/CreditTransactionService.save(event)` — map 3 field mới từ event.
   - `dto/response/CreditTransactionResponse` — thêm `beforeBalance`, `afterBalance`, `model`.
   - `service ... toResponse()` — map 3 field.
   - **Flyway** `V{n}__add_balance_model_to_credit_transactions.sql` — `ALTER TABLE credit_transactions ADD COLUMN before_balance INT, after_balance INT, model VARCHAR(60)`.

### B2. CSV export — giữ thứ tự để FE injection không vỡ

UI `triggerRealCSVDownload` chèn MSISDN **sau cột thứ 2** và thay chuỗi `"User ID"` ở header. Vì vậy **bắt buộc**: cột 1 = `ID`, cột 2 = `User ID`. Thêm cột mới (Before/After/Model) **sau** đó.

Header mới đề xuất:
`ID,User ID,Before Balance,After Balance,Amount,Direction,Gen Type,Model,Is Free,Reason,Reference ID,Timestamp,Created At`

Sửa `exportCsv()` trong `CreditTransactionService` thêm 3 cột, giữ ID/User ID đầu.

### B3. History endpoint — không đổi chữ ký

`GET /credit-transactions/history` đã khớp UI (direction/reason/fromTs/toTs/page/size, header `X-User-Id`, sort timestamp desc). Sau B1 response tự có đủ field. **Verify**: `ApiResponse` envelope có `code="SUCCESS"` + `data.content/totalElements/totalPages` (UI check `resData.code === "SUCCESS"`). Nếu envelope dùng field khác (vd `success` boolean) → cần xác nhận với FE hoặc chuẩn hoá. (PageResponse.from đã cho content/totalElements/totalPages.)

---

## Thứ tự thực thi (build-safe)

1. `common-rmq` `CreditChangedEvent` (+overload) → build common-rmq.
2. `credit-wallet-service`: AmountRequest + WalletService + WalletController.
3. Callers: campaign + audio-gen `WalletAmountRequest` + service set model.
4. `crbt-credit-transaction-service`: entity + Flyway + service + response + CSV.
5. `crbt-campaign-service` Prompt: entity + Flyway + repo + admin service + provider + controller + DTOs.
6. (Feature A và B độc lập — có thể song song sau bước 1.)

## Test (rule: 80%, JUnit5 + Mockito + AssertJ)

- `LyriaPromptAdminService`: saveNewVersion (version++ đúng, deactivate cũ), activateVersion (chỉ 1 ACTIVE/model), validate template fail, listHistory sort ACTIVE-first.
- `CreditTransactionService`: save map before/after/model; query filter; exportCsv header/thứ tự cột.
- `WalletService`: emit event chứa before/after đúng (before = after±amount).
- Cập nhật test cũ dùng `new CreditChangedEvent(...)` 6-arg → vẫn pass nhờ overload (đã verify 2 test file dùng chữ ký cũ).

## Rủi ro / cần xác nhận khi code

- **R1**: `LyriaPromptProvider` interface trong `common-ai-sdk` — đổi sang model-aware có thể ảnh hưởng `LyriaSystemPromptConfig` + test. Giải pháp: thêm overload, giữ method cũ.
- **R2**: Model nào đang được chọn lúc generate (`MusicGenerationService`) — cần xác định biến model để vừa truyền vào provider (lấy prompt đúng) vừa truyền vào wallet deduct (lưu vào tx).
- **R3**: `ApiResponse` field name (`code` vs `success`) so với UI tx — verify trước khi kết luận B3 xong.
- **R4**: Service có bật Flyway chưa (một số service có thể ddl-auto). Kiểm tra `application.yml`/config-server trước khi thêm migration.

## Ngoài scope đợt này
- Tab "Quản lý credit theo user" (aggregate wallet + subscription) — epic riêng.
- FE wiring (đội khác).
