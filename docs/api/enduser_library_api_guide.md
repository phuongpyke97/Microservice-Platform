# Tài liệu API: Quản lý bản nhạc (AI & DIY)

> Tài liệu này tổng hợp toàn bộ API cần thiết để FE tích hợp 2 màn hình:
> 1. **Màn hình thư viện cá nhân** – dành cho Enduser
> 2. **Màn hình CMS Admin** – quản lý tất cả bản nhạc AI & DIY do Enduser tạo

---

## 1. Thông tin chung

| Mục | Giá trị |
| :--- | :--- |
| **Base URL** | `http://localhost:18080` |
| **Header Auth – Enduser** | `X-CRBT-Token: <JWT_CRBT_TOKEN>` |
| **Header Auth – Admin** | `Authorization: Bearer <JWT_ADMIN_TOKEN>` |
| **Content-Type** | `application/json` |

### Cấu trúc ID thống nhất (Unified ID)
| Tiền tố | Ý nghĩa | Ví dụ |
| :--- | :--- | :--- |
| `AI_` | Bài nhạc tạo bởi AI (Gemini Lyria) | `AI_28` |
| `DIY_` | Bài nhạc DIY do Enduser ghi âm/trộn | `DIY_26` |

---

---

# PHẦN 1 – API dành cho ENDUSER (My Library)

Base path: `/api/campaigns/my-library`
Header yêu cầu: `X-CRBT-Token: <JWT_CRBT_TOKEN>`

---

## 1.1. Lấy danh sách thư viện cá nhân

Trả về tất cả bài nhạc AI + DIY của user đang đăng nhập, sắp xếp `createdAt` DESC.

| | |
| :--- | :--- |
| **Method** | `GET` |
| **Path** | `/api/campaigns/my-library` |

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "OK",
  "data": [
    {
      "id": "AI_28",
      "title": "Happy Pop Beats",
      "source": "AI",
      "tags": ["pop", "happy", "45s"],
      "audioUrl": "http://localhost:9000/media-audio/lyria-mock-track-123.mp3",
      "createdAt": "2026-06-05T16:51:05.977Z"
    },
    {
      "id": "DIY_26",
      "title": "My DIY Track",
      "source": "DIY",
      "tags": ["diy", "mixed"],
      "audioUrl": "http://localhost:9000/media-audio/diy_b.mp3",
      "createdAt": "2026-06-05T16:51:06.764Z"
    }
  ],
  "timestamp": 1780678266946
}
```

**Mô tả các trường trong `data[]`:**

| Trường | Kiểu | Mô tả |
| :--- | :--- | :--- |
| `id` | `String` | ID thống nhất. Format: `AI_<id>` hoặc `DIY_<id>`. Dùng ID này khi gọi API Sửa/Xóa. |
| `title` | `String` | Tên bài hát hiển thị. |
| `source` | `String` | Nguồn tạo: `AI` hoặc `DIY`. |
| `tags` | `Array[String]` | Danh sách nhãn: thể loại, tâm trạng, độ dài. |
| `audioUrl` | `String` | URL stream file âm thanh từ MinIO. |
| `createdAt` | `String` | Thời gian tạo (ISO-8601). |

---

## 1.2. Sửa tên bài hát (Enduser tự sửa của mình)

User chỉ được sửa bài hát do **chính mình** tạo. Sửa bài của người khác → `403 Forbidden`.

| | |
| :--- | :--- |
| **Method** | `PUT` |
| **Path** | `/api/campaigns/my-library/{unifiedId}` |
| **Ví dụ** | `/api/campaigns/my-library/AI_28` |

**Request Body:**
```json
{
  "title": "Tên bài hát mới"
}
```

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "OK",
  "data": {
    "id": "AI_28",
    "title": "Tên bài hát mới",
    "source": "AI",
    "tags": ["pop", "happy", "45s"],
    "audioUrl": "http://localhost:9000/media-audio/lyria-mock-track-123.mp3",
    "createdAt": "2026-06-05T16:51:05.977Z"
  },
  "timestamp": 1780678266999
}
```

**Mã lỗi:**

| HTTP Status | errorCode | Trường hợp |
| :--- | :--- | :--- |
| `403 Forbidden` | `COMMON_FORBIDDEN` | User cố sửa bài của người khác |
| `404 Not Found` | `COMMON_NOT_FOUND` | ID không tồn tại |

---

## 1.3. Xóa bài hát (Soft Delete)

Ẩn bài hát khỏi thư viện của user. File MinIO và bản ghi gốc **vẫn giữ lại** để tránh lỗi nhạc chờ đang sử dụng.

| | |
| :--- | :--- |
| **Method** | `DELETE` |
| **Path** | `/api/campaigns/my-library/{unifiedId}` |
| **Ví dụ** | `/api/campaigns/my-library/DIY_26` |

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "OK",
  "data": null,
  "timestamp": 1780678266884
}
```

**Mã lỗi:**

| HTTP Status | errorCode | Trường hợp |
| :--- | :--- | :--- |
| `403 Forbidden` | `COMMON_FORBIDDEN` | User cố xóa bài của người khác |
| `404 Not Found` | `COMMON_NOT_FOUND` | ID không tồn tại hoặc đã bị xóa |

---

---

# PHẦN 2 – API dành cho CMS ADMIN

Base path: `/api/campaigns/admin/music-items`
Header yêu cầu: `Authorization: Bearer <JWT_ADMIN_TOKEN>`

> ⚠️ Tất cả các API Admin đều yêu cầu role `ROLE_ADMIN`. Nếu dùng token Enduser thường sẽ nhận `403 Forbidden`.

---

## 2.1. Tìm kiếm & Lọc bản nhạc (Search & Filter)

Tìm kiếm toàn bộ bản nhạc AI & DIY của mọi user theo nhiều tiêu chí. Hỗ trợ phân trang.

| | |
| :--- | :--- |
| **Method** | `GET` |
| **Path** | `/api/campaigns/admin/music-items` |

**Query Parameters (Bộ lọc):**

| Tham số | Kiểu | Bắt buộc | Mô tả |
| :--- | :--- | :--- | :--- |
| `startTime` | `String` | Không | Lọc từ thời gian (ISO-8601). VD: `2026-06-01T00:00:00Z` |
| `endTime` | `String` | Không | Lọc đến thời gian (ISO-8601). VD: `2026-06-05T23:59:59Z` |
| `source` | `String` | Không | Lọc theo loại nhạc: `AI` hoặc `DIY` |
| `userId` | `Long` | Không | Lọc theo User ID người tạo |
| `msisdn` | `String` | Không | Lọc/tìm kiếm theo số điện thoại người tạo |
| `search` | `String` | Không | Tìm kiếm theo tên bài hát (`title`) |
| `page` | `Integer` | Không | Trang số (mặc định: `0`) |
| `size` | `Integer` | Không | Số bản ghi/trang (mặc định: `10`) |

**Ví dụ request:**
```
GET /api/campaigns/admin/music-items?source=DIY&msisdn=0912345678&page=0&size=20
```

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "OK",
  "data": [
    {
      "id": "DIY_26",
      "title": "DIY User B Title",
      "source": "DIY",
      "tags": ["diy", "mixed"],
      "audioUrl": "http://localhost:9000/media-audio/diy_b.mp3",
      "createdAt": "2026-06-05T16:51:06.764Z"
    }
  ],
  "timestamp": 1780678266946
}
```

---

## 2.2. Xem chi tiết bản nhạc

Lấy thông tin chi tiết của một bài nhạc cụ thể (AI hoặc DIY).

| | |
| :--- | :--- |
| **Method** | `GET` |
| **Path** | `/api/campaigns/admin/music-items/{unifiedId}` |
| **Ví dụ** | `/api/campaigns/admin/music-items/AI_28` |

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "OK",
  "data": {
    "id": "AI_28",
    "title": "Happy Pop Beats",
    "source": "AI",
    "tags": ["pop", "happy", "45s"],
    "audioUrl": "http://localhost:9000/media-audio/lyria-mock-track-123.mp3",
    "createdAt": "2026-06-05T16:51:05.977Z"
  },
  "timestamp": 1780678266999
}
```

---

## 2.3. Import / Tạo bản nhạc (Admin tạo cho user)

Admin tạo trực tiếp một bản ghi bài nhạc (AI hoặc DIY) vào hệ thống, gán cho một user cụ thể.

| | |
| :--- | :--- |
| **Method** | `POST` |
| **Path** | `/api/campaigns/admin/music-items` |

**Request Body:**
```json
{
  "title": "Tên bài hát Admin tạo",
  "source": "AI",
  "tags": ["pop", "chill"],
  "audioUrl": "http://localhost:9000/media-audio/admin_track.mp3",
  "msisdn": "0912345678"
}
```

**Mô tả trường trong Request:**

| Trường | Kiểu | Bắt buộc | Mô tả |
| :--- | :--- | :--- | :--- |
| `title` | `String` | Có | Tên bài hát |
| `source` | `String` | Có | Loại nhạc: `AI` hoặc `DIY` |
| `tags` | `Array[String]` | Không | Danh sách nhãn phân loại |
| `audioUrl` | `String` | Có | URL file âm thanh trên MinIO |
| `msisdn` | `String` | Có | Số điện thoại user được gán bài nhạc |

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "OK",
  "data": {
    "id": "AI_30",
    "title": "Tên bài hát Admin tạo",
    "source": "AI",
    "tags": ["pop", "chill"],
    "audioUrl": "http://localhost:9000/media-audio/admin_track.mp3",
    "createdAt": "2026-06-06T00:00:00.000Z"
  },
  "timestamp": 1780678266999
}
```

---

## 2.4. Sửa thông tin bản nhạc (Admin Edit)

Admin được phép sửa thông tin bất kỳ bài nhạc nào của bất kỳ user nào (cả AI lẫn DIY).

| | |
| :--- | :--- |
| **Method** | `PUT` |
| **Path** | `/api/campaigns/admin/music-items/{unifiedId}` |
| **Ví dụ** | `/api/campaigns/admin/music-items/DIY_26` |

**Request Body:**
```json
{
  "title": "Tên bài hát Admin đã sửa"
}
```

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "OK",
  "data": {
    "id": "DIY_26",
    "title": "Tên bài hát Admin đã sửa",
    "source": "DIY",
    "tags": ["diy", "mixed"],
    "audioUrl": "http://localhost:9000/media-audio/diy_b.mp3",
    "createdAt": "2026-06-05T16:51:06.764Z"
  },
  "timestamp": 1780678266999
}
```

---

## 2.5. Xóa bản nhạc (Soft Delete / Hard Delete)

Admin có thể xóa mềm (mặc định) hoặc xóa cứng hoàn toàn khỏi database.

| | |
| :--- | :--- |
| **Method** | `DELETE` |
| **Path** | `/api/campaigns/admin/music-items/{unifiedId}` |
| **Ví dụ** | `/api/campaigns/admin/music-items/DIY_26?hard=true` |

**Query Parameters:**

| Tham số | Kiểu | Mặc định | Mô tả |
| :--- | :--- | :--- | :--- |
| `hard` | `Boolean` | `false` | `false` = xóa mềm (ẩn khỏi thư viện). `true` = xóa cứng hoàn toàn khỏi DB. |

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "OK",
  "data": null,
  "timestamp": 1780678266884
}
```

---

---

# PHẦN 3 – Bảng so sánh quyền hạn Admin vs Enduser

| Hành động | Enduser (chính bài của mình) | Enduser (bài của người khác) | Admin |
| :--- | :---: | :---: | :---: |
| Xem danh sách thư viện cá nhân | ✅ | ✗ | ✅ |
| Sửa tên bài hát | ✅ | ❌ `403` | ✅ |
| Xóa bài hát (Soft Delete) | ✅ | ❌ `403` | ✅ |
| Tìm kiếm toàn bộ hệ thống | ❌ | ❌ | ✅ |
| Xem chi tiết bất kỳ bài nào | ❌ | ❌ | ✅ |
| Tạo bài nhạc cho user | ❌ | ❌ | ✅ |
| Hard Delete | ❌ | ❌ | ✅ |

---

# PHẦN 4 – Mã lỗi chung

| HTTP Status | errorCode | Mô tả |
| :--- | :--- | :--- |
| `400 Bad Request` | `COMMON_BAD_REQUEST` | Dữ liệu đầu vào không hợp lệ hoặc ID không đúng định dạng |
| `402 Payment Required` | `INSUFFICIENT_TOKENS` | Số dư token/credit của user không đủ để tạo nhạc AI |
| `403 Forbidden` | `COMMON_FORBIDDEN` | Không có quyền thực hiện thao tác này |
| `404 Not Found` | `COMMON_NOT_FOUND` | Bài hát không tồn tại hoặc đã bị xóa |
| `500 Internal Server Error` | `COMMON_INTERNAL_ERROR` | Lỗi hệ thống nội bộ |

---

*Ngày cập nhật: 2026-06-06*
