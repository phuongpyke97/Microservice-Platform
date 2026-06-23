# TÀI LIỆU BÀN GIAO DATABASE SPECIFICATION

Tài liệu này mô tả chi tiết thiết kế cơ sở dữ liệu, sơ đồ quan hệ thực thể (ERD), cấu trúc bảng, index, dữ liệu nền và cơ chế sao lưu phục hồi của hệ thống **Mytel CRBT Microservice Platform**.

---

## 1. Kiến Trúc Cơ Sở Dữ Liệu (Database-per-Service)

Hệ thống áp dụng pattern **Database-per-Service** sử dụng hệ quản trị cơ sở dữ liệu **PostgreSQL 16**. Mỗi microservice sở hữu một database logic độc lập, không chia sẻ kết nối trực tiếp với các service khác nhằm đảm bảo tính cô lập dữ liệu (Data Isolation) và khả năng mở rộng độc lập. Giao tiếp dữ liệu giữa các service chỉ được thực hiện thông qua REST API (OpenFeign) hoặc Event Broker (RabbitMQ).

### Quy Hoạch Hệ Thống Cơ Sở Dữ Liệu

| Tên Database | Service Sở Hữu | Các Bảng Chính | Vai Trò |
|---|---|---|---|
| **`auth_db`** | `auth_service` | `users`, `user_roles` | Quản lý người dùng, mật khẩu và quyền hạn. |
| **`wallet_db`** | `credit_wallet_service`| `wallets` | Quản lý ví credit tạo nhạc chờ AI. |
| **`campaign_db`** | `crbt-campaign-service`| `campaigns`, `campaign_packages`, `user_subscriptions` | Lưu cấu hình gói cước và đăng ký dịch vụ. |
| **`library_db`** | `crbt-community-library`| `categories`, `ringtones`, `moods`, `ringtone_deleted_history` | Kho nhạc nền cộng đồng, thể loại, tâm trạng. |
| **`audio_gen_db`**| `audio-generation-service`| `audio_jobs` | Quản lý trạng thái và kết quả các job tạo nhạc. |
| **`transaction_db`**| `crbt-credit-transaction-service`| `credit_transactions` | Đối soát dòng tiền credit (Immutable Log). |
| **`adapter_db`** | `crbt-core-adapter` | `ringtone_assignments` | Ánh xạ nhạc chờ giữa userId và songId CMS. |
| **`audit_db`** | `audit-log-service` | `audit_logs`, `lyria_stats` | Lưu nhật ký bảo mật và thống kê Lyria. |
| **`file_db`** | `file-service` | `file_metadata` | Quản lý metadata file upload và S3 key. |
| **`payment_db`** | `payment-gateway-service`| `payment_transactions` | Lưu vết giao dịch trừ cước viễn thông nhà mạng. |

---

## 2. Sơ Đồ Quan Hệ Thực Thể Tổng Thể (ERD)

Dưới đây là sơ đồ quan hệ logic giữa các thực thể chính trong hệ thống (biểu diễn mối liên kết nghiệp vụ, mặc dù ở mức vật lý chúng nằm trên các cơ sở dữ liệu phân tán khác nhau):

```mermaid
erDiagram
    USERS ||--o| WALLETS : "sở hữu (1-1)"
    USERS ||--o| USER_ROLES : "có vai trò (1-n)"
    USERS ||--o| USER_SUBSCRIPTIONS : "đăng ký (1-n)"
    CAMPAIGNS ||--o| CAMPAIGN_PACKAGES : "chứa gói (1-n)"
    CAMPAIGN_PACKAGES ||--o| USER_SUBSCRIPTIONS : "được bán (1-n)"
    USERS ||--o| AUDIO_JOBS : "yêu cầu tạo (1-n)"
    CATEGORIES ||--o| RINGTONES : "phân loại (1-n)"
    MOODS ||--o| RINGTONES : "tâm trạng (1-n)"
    USERS ||--o| CREDIT_TRANSACTIONS : "ghi dòng tiền (1-n)"
    USERS ||--o| RINGTONE_ASSIGNMENTS : "sử dụng nhạc chờ (1-1)"

    USERS {
        bigint id PK
        varchar msisdn UK
        varchar email UK
        varchar password_hash
        int credit_balance
        varchar status
        timestamp created_at
    }

    WALLETS {
        bigint id PK
        bigint user_id FK
        int balance
        bigint version
        timestamp updated_at
    }

    USER_ROLES {
        bigint user_id PK_FK
        varchar role PK
    }

    CAMPAIGN_PACKAGES {
        bigint id PK
        bigint campaign_id FK
        varchar name
        decimal price
        int credit_amount
        int validity_days
    }

    USER_SUBSCRIPTIONS {
        bigint id PK
        bigint user_id FK
        bigint package_id FK
        varchar status
        timestamp expires_at
    }

    RINGTONES {
        bigint id PK
        varchar title
        varchar artist_name
        varchar audio_url
        int duration_seconds
        bigint category_id FK
        bigint mood_id FK
        bigint selection_count
        boolean status
    }
```

---

## 3. Cấu Trúc Bảng & Chỉ Mục Chi Tiết (DDL Specs)

### 3.1 Cơ sở dữ liệu: `auth_db`
#### Bảng: `users`
Lưu trữ thông tin định danh khách hàng.
- **DDL Script**:
  ```sql
  CREATE TABLE users (
      id BIGSERIAL PRIMARY KEY,
      msisdn VARCHAR(20) UNIQUE,
      email VARCHAR(120) UNIQUE,
      password_hash VARCHAR(72),
      credit_balance INT NOT NULL DEFAULT 0,
      status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
      created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
      updated_at TIMESTAMP WITH TIME ZONE
  );
  ```
- **Chỉ mục (Index)**:
  - `idx_users_msisdn` ON `users(msisdn)` -> Tối ưu hóa tra cứu đăng nhập theo số điện thoại thuê bao.
  - `idx_users_email` ON `users(email)` -> Tối ưu hóa đăng nhập bằng email.

#### Bảng: `user_roles`
Phân quyền tài khoản (ROLE_USER, ROLE_ADMIN...).
- **DDL Script**:
  ```sql
  CREATE TABLE user_roles (
      user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      role VARCHAR(50) NOT NULL,
      PRIMARY KEY (user_id, role)
  );
  ```

---

### 3.2 Cơ sở dữ liệu: `wallet_db`
#### Bảng: `wallets`
Quản lý ví credit ảo để tạo nhạc. Sử dụng trường `version` để thực hiện khóa lạc quan (Optimistic Locking) chống lỗi trừ tiền trùng lặp (Double Spending).
- **DDL Script**:
  ```sql
  CREATE TABLE wallets (
      id BIGSERIAL PRIMARY KEY,
      user_id BIGINT NOT NULL UNIQUE,
      balance INT NOT NULL DEFAULT 0,
      version BIGINT NOT NULL DEFAULT 0,
      updated_at TIMESTAMP WITH TIME ZONE
  );
  ```
- **Chỉ mục (Index)**:
  - `idx_wallets_user_id` ON `wallets(user_id)`.

---

### 3.3 Cơ sở dữ liệu: `campaign_db`
#### Bảng: `campaigns`
Lưu trữ thông tin chiến dịch marketing gói cước.
- **DDL Script**:
  ```sql
  CREATE TABLE campaigns (
      id BIGSERIAL PRIMARY KEY,
      name VARCHAR(150) NOT NULL,
      description VARCHAR(500),
      status VARCHAR(20) NOT NULL,
      start_at TIMESTAMPTZ NOT NULL,
      end_at TIMESTAMPTZ NOT NULL,
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      updated_at TIMESTAMPTZ
  );
  CREATE INDEX idx_campaigns_status_dates ON campaigns(status, start_at, end_at);
  ```

#### Bảng: `campaign_packages`
Gói cước chi tiết của từng chiến dịch.
- **DDL Script**:
  ```sql
  CREATE TABLE campaign_packages (
      id BIGSERIAL PRIMARY KEY,
      campaign_id BIGINT NOT NULL REFERENCES campaigns(id),
      name VARCHAR(100) NOT NULL,
      price DECIMAL(12,2) NOT NULL,
      credit_amount INT NOT NULL,
      validity_days INT NOT NULL,
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
  );
  CREATE INDEX idx_campaign_packages_campaign_id ON campaign_packages(campaign_id);
  ```

#### Bảng: `user_subscriptions`
Nhật ký đăng ký dịch vụ của thuê bao.
- **DDL Script**:
  ```sql
  CREATE TABLE user_subscriptions (
      id BIGSERIAL PRIMARY KEY,
      user_id BIGINT NOT NULL,
      package_id BIGINT NOT NULL REFERENCES campaign_packages(id),
      status VARCHAR(20) NOT NULL,
      expires_at TIMESTAMPTZ NOT NULL,
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
  );
  CREATE INDEX idx_user_subscriptions_user_status ON user_subscriptions(user_id, status);
  ```

---

### 3.4 Cơ sở dữ liệu: `library_db`
#### Bảng: `categories`
Thể loại nhạc (Pop, Rock, EDM...).
- **DDL Script**:
  ```sql
  CREATE TABLE categories (
      id BIGSERIAL PRIMARY KEY,
      name VARCHAR(50) NOT NULL UNIQUE,
      description VARCHAR(200),
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      updated_at TIMESTAMPTZ
  );
  ```

#### Bảng: `moods`
Tâm trạng nhạc chờ (Vui, Chill, Buồn, Hype...).
- **DDL Script**:
  ```sql
  CREATE TABLE moods (
      id BIGSERIAL PRIMARY KEY,
      name VARCHAR(50) NOT NULL UNIQUE,
      description VARCHAR(200),
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      updated_at TIMESTAMPTZ
  );
  ```

#### Bảng: `ringtones`
Kho nhạc chờ chi tiết.
- **DDL Script**:
  ```sql
  CREATE TABLE ringtones (
      id BIGSERIAL PRIMARY KEY,
      title VARCHAR(150) NOT NULL,
      artist_name VARCHAR(100) NOT NULL,
      audio_url VARCHAR(500) NOT NULL,
      cover_image_url VARCHAR(500),
      duration_seconds INT NOT NULL,
      featured BOOLEAN NOT NULL DEFAULT FALSE,
      category_id BIGINT NOT NULL REFERENCES categories(id),
      mood_id BIGINT NOT NULL REFERENCES moods(id),
      status BOOLEAN NOT NULL DEFAULT TRUE,
      selection_count BIGINT NOT NULL DEFAULT 0,
      deleted BOOLEAN NOT NULL DEFAULT FALSE,
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      updated_at TIMESTAMPTZ
  );
  ```
- **Chỉ mục (Index)**:
  - `idx_ringtones_category_id`, `idx_ringtones_mood_id`.
  - `idx_ringtones_status`, `idx_ringtones_deleted`.
  - `idx_ringtones_selection_count` -> Tối ưu hóa truy vấn bảng xếp hạng bài hát được nghe nhiều nhất.

---

### 3.5 Cơ sở dữ liệu: `audio_gen_db`
#### Bảng: `audio_jobs`
Theo dõi các job tạo nhạc bất đồng bộ.
- **DDL Script**:
  ```sql
  CREATE TABLE audio_jobs (
      id BIGSERIAL PRIMARY KEY,
      user_id BIGINT NOT NULL,
      prompt VARCHAR(500) NOT NULL,
      voice_id VARCHAR(50),
      status VARCHAR(20) NOT NULL,
      result_url TEXT,
      error_message VARCHAR(500),
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      updated_at TIMESTAMPTZ
  );
  CREATE INDEX idx_audio_jobs_user_id ON audio_jobs(user_id);
  CREATE INDEX idx_audio_jobs_status ON audio_jobs(status);
  ```

---

### 3.6 Cơ sở dữ liệu: `transaction_db`
#### Bảng: `credit_transactions`
Ghi nhận lịch sử chi tiết biến động số dư ví credit. Bảng này có đặc tính **chỉ ghi thêm (Append-Only)**, không hỗ trợ UPDATE hay DELETE để đảm bảo tính pháp lý đối soát.
- **DDL Script**:
  ```sql
  CREATE TABLE credit_transactions (
      id BIGSERIAL PRIMARY KEY,
      user_id BIGINT NOT NULL,
      amount INT NOT NULL,
      direction VARCHAR(20) NOT NULL, -- IN / OUT
      reason VARCHAR(100) NOT NULL,   -- TRIAL_GRANT, PACKAGE_DAILY_249, AUDIO_GENERATE
      reference_id VARCHAR(100),      -- Liên kết mã giao dịch payment hoặc jobId
      timestamp BIGINT NOT NULL,      -- Epoch milliseconds
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
  );
  CREATE INDEX idx_credit_transactions_user_id ON credit_transactions(user_id);
  CREATE INDEX idx_credit_transactions_timestamp ON credit_transactions(timestamp);
  ```

---

## 4. Dữ Liệu Nền Khởi Tạo (Database Seed Data)

Dữ liệu danh mục nền được tự động nạp thông qua Flyway migration script khi khởi động các service tương ứng.

### 4.1 Thể loại nhạc (`library_db.categories`)
```sql
INSERT INTO categories (name, description) VALUES
('Pop', 'Thể loại nhạc trẻ thịnh hành, dễ nghe, giai điệu ngọt ngào'),
('Rock', 'Nhạc Rock mạnh mẽ, nhịp điệu nhanh, guitar điện cuốn hút'),
('EDM', 'Nhạc điện tử sôi động, tiết tấu dồn dập, phù hợp tiệc tùng'),
('Instrumental', 'Nhạc không lời, hòa tấu sáo, piano, guitar thư giãn'),
('V-Pop', 'Thể loại nhạc trẻ Việt Nam đương đại');
```

### 4.2 Tâm trạng nhạc chờ (`library_db.moods`)
```sql
INSERT INTO moods (name, description) VALUES
('Vui', 'Vui vẻ, sôi động, tích cực'),
('Buồn', 'Trầm lắng, u sầu, nhiều cảm xúc'),
('Chill', 'Thư giãn, nhẹ nhàng, êm dịu'),
('Hype', 'Hưng phấn, năng lượng cao'),
('Lãng mạn', 'Ngọt ngào, sâu lắng, tình cảm'),
('Thư giãn', 'Bình yên, thoải mái, giảm căng thẳng'),
('Năng động', 'Khỏe khoắn, tươi vui, nhiều năng lượng');
```

---

## 5. Xác Nhận Thiết Kế: Loại Bỏ Stored Procedure / Function

### Triết lý thiết kế hệ thống
Hệ thống **không sử dụng** Stored Procedure hay Function ở mức Cơ sở dữ liệu. Toàn bộ logic xử lý nghiệp vụ, kiểm tra ràng buộc và giao dịch tài chính được chuyển hoàn toàn lên tầng ứng dụng (Java Spring Boot Services).

### Lý do kỹ thuật:
1. **Khả năng Scaling độc lập**: Tầng ứng dụng rất dễ scale ngang (chạy nhiều container song song qua Docker/K8s). Cơ sở dữ liệu là tài nguyên khó scale nhất. Di chuyển tính toán từ DB lên Application Layer giúp giảm tải tối đa cho PostgreSQL.
2. **Quản lý mã nguồn tập trung**: Code SQL của Stored Procedure nằm trong DB, rất khó quản lý phiên bản (version control), khó thực hiện Unit Test và debug hơn nhiều so với viết logic bằng Java/Kotlin.
3. **Tránh nghẽn khóa (Lock Contention)**: Các transaction dài trong Stored Procedure dễ dẫn đến khóa chết (Deadlock) hoặc khóa chờ lâu. Thay vào đó, hệ thống sử dụng cơ chế Khóa phân tán ngoài bộ nhớ **Redisson Distributed Lock** trên Redis, đảm bảo việc xử lý luồng diễn ra an toàn, không block DB PostgreSQL.

---

## 6. Hướng Dẫn Sao Lưu (Backup) & Khôi Phục (Restore)

### 6.1 Lệnh Sao Lưu (Backup)
Sao lưu cơ sở dữ liệu định dạng nén tối ưu `.dump` bằng công cụ `pg_dump`:

```bash
# Sao lưu toàn bộ Database logic của thư viện nhạc (library_db)
pg_dump -h <HOST_IP> -p 5432 -U postgres -F c -b -v -f /var/backup/library_db_20260618.dump library_db

# Giải thích tham số:
# -F c: Định dạng custom nén (phù hợp khôi phục nhanh)
# -b: Bao gồm cả các blob data lớn
# -v: Chế độ verbose, hiển thị chi tiết tiến trình
```

### 6.2 Lệnh Khôi Phục (Restore)
Khôi phục dữ liệu từ file `.dump` bằng công cụ `pg_restore`:

```bash
# Tạo cơ sở dữ liệu trống trước
createdb -h <HOST_IP> -p 5432 -U postgres library_db

# Khôi phục dữ liệu từ file dump
pg_restore -h <HOST_IP> -p 5432 -U postgres -d library_db -v /var/backup/library_db_20260618.dump

# Giải thích tham số:
# -d: Tên Database mục tiêu khôi phục dữ liệu vào
```
