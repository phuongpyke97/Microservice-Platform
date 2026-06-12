# Microservice Platform — CONVENTIONS.md

## Naming Conventions

### Class Names
- **Java**: PascalCase. E.g., `UserService`, `AuthRequest`, `ApiResponse`.
- **Python**: PascalCase. E.g., `AudioSeparatorService`, `TTSRequest`.

### Method Names
- **Java**: camelCase. E.g., `createUser()`, `findByEmailAndStatus()`.
- **Python**: snake_case. E.g., `detect_chorus()`, `synthesize_speech()`.

### Variable Names
- **Java**: camelCase. E.g., `userId`, `audioFile`, `requestBody`.
- **Python**: snake_case. E.g., `user_id`, `audio_file`, `request_body`.

### Database Column Names
- snake_case. E.g., `user_id`, `created_at`, `package_code`.
- Foreign keys: `{referenced_table}_id`. E.g., `user_id`.

### REST Endpoint Paths
- Kebab-case cho resource, số nhiều. E.g., `/api/v1/users`, `/api/v1/audio-jobs`.
- Id của resource trong path: `/api/v1/users/{userId}`.
- Hành động: `/api/v1/users/{userId}/change-password`.

### RabbitMQ
- **Exchange**: Kebab-case. E.g., `user-events-exchange`, `audio-processing-exchange`.
- **Queue**: Kebab-case. E.g., `user-registered-queue`, `audio-generated-dlq`.
- **Routing Key**: Dot-separated. E.g., `user.registered`, `audio.generated.success`.

### MinIO Bucket Names
- Kebab-case, số nhiều. E.g., `media-images`, `media-audio`, `media-temp`, `media-private`.

## Package Structure

Java services PHẢI theo chuẩn `com.platform.{service_name}.{layer}`:

```
src/main/java/com/platform/{service_name}/
├── config/          # Spring @Configuration classes (SecurityConfig, RedisConfig)
├── controller/      # REST API endpoints (@RestController)
├── service/         # Business logic (@Service)
├── repository/      # Spring Data JPA repositories
├── entity/          # JPA entities (@Entity)
├── dto/             # Data Transfer Objects (request/response)
│   ├── request/
│   └── response/
├── event/           # RabbitMQ event models (POJO)
├── listener/        # RabbitMQ message listeners (@RabbitListener)
├── client/          # Feign clients cho gọi service khác (interface)
├── exception/       # Custom exceptions và ErrorCode definitions
└── util/            # General utility classes
```

Ví dụ:
- `auth-service` -> `com.platform.auth.controller`, `com.platform.auth.service`
- `file-service` -> `com.platform.fileservice.controller`, `com.platform.fileservice.entity`

## API Response Format

Mọi API response thành công PHẢI dùng `ApiResponse<T>` từ `common-core`.

```java
// Trong common-core/ApiResponse.java
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
    private long timestamp;

    // Getters, Setters, Constructors
}

// Ví dụ Controller trả về
@GetMapping("/users/{id}")
public ApiResponse<UserResponseDto> getUserById(@PathVariable Long id) {
    UserResponseDto user = userService.getUserById(id);
    return new ApiResponse<>(true, "User fetched successfully", user, System.currentTimeMillis());
}
```

## Error Code Format

Error codes PHẢI theo format `SERVICE_CATEGORY_CODE`.

- `SERVICE`: Tên service (3-5 chữ cái, viết HOA).
- `CATEGORY`: Danh mục lỗi (VD: `AUTH`, `VALIDATION`, `NOT_FOUND`).
- `CODE`: Mô tả cụ thể lỗi.

Ví dụ:
- `AUTH_USER_NOT_FOUND`
- `WALLET_INSUFFICIENT_CREDIT`
- `FILE_UPLOAD_FAILED`
- `AUDIO_TOO_MANY_JOBS`

## Flyway Migration Naming

Flyway migration scripts PHẢI theo chuẩn `V{version}__{description}.sql`.

- `V`: Prefix cố định.
- `{version}`: Số phiên bản, tăng dần. Có thể dùng `1`, `1.1`, `2_0_1`.
- `__` (hai dấu gạch dưới): Dấu phân tách cố định.
- `{description}`: Mô tả ngắn gọn, dùng `_` thay cho khoảng trắng.

Ví dụ:
- `V1__create_users_table.sql`
- `V1_1__add_email_unique_constraint.sql`
- `V2__create_credit_transactions_table.sql`

## Header Extraction (X-User-Id / X-User-Email / X-User-Roles)

API Gateway sẽ validate JWT, sau đó bóc tách các thông tin sau và inject vào header trước khi forward request xuống service nội bộ:

- `X-User-Id`: ID của user (Long)
- `X-User-Email`: Email của user (String)
- `X-User-Roles`: Danh sách vai trò của user, phân cách bởi dấu phẩy (String, ví dụ: `ADMIN,USER`)

Service nội bộ sử dụng `common-security` module để tự động nạp các header này vào `SecurityContextHolder`. Để lấy thông tin:

```java
// Trong service logic
import com.platform.common.security.SecurityUtils;

public class MyService {
    public void doSomething() {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        String currentUserEmail = SecurityUtils.getCurrentUserEmail();
        // List<String> currentUserRoles = SecurityUtils.getCurrentUserRoles();
        // ... sử dụng thông tin user
    }
}
```

Tuyệt đối KHÔNG tự đọc header `X-User-Id`, `X-User-Email`, `X-User-Roles` bằng `HttpServletRequest` trực tiếp.

## Redisson Lock Pattern (credit-wallet-service)

Khi thực hiện các thao tác cộng/trừ credit trong `credit-wallet-service`, PHẢI sử dụng Redisson Distributed Lock để đảm bảo tính toàn vẹn dữ liệu và tránh race condition.

- **Lock Key**: PHẢI theo format `wallet:{userId}`.
- **Timeout**: `3 giây`.

```java
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

@Service
public class CreditWalletService {

    private final RedissonClient redissonClient;

    public CreditWalletService(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    public boolean checkAndDeductCredit(Long userId, int amount) {
        String lockKey = "wallet:" + userId;
        RLock lock = redissonClient.getLock(lockKey);
        try {
            // Thử acquire lock trong 3 giây
            if (lock.tryLock(3, TimeUnit.SECONDS)) {
                try {
                    // Logic kiểm tra và trừ credit
                    // SELECT balance FOR UPDATE
                    // if (balance >= amount) {
                    //    UPDATE balance = balance - amount
                    //    return true;
                    // } else {
                    //    throw new InsufficientCreditException();
                    // }
                } finally {
                    lock.unlock(); // Đảm bảo luôn giải phóng lock
                }
            } else {
                // Không lấy được lock trong 3 giây
                throw new WalletLockedException("Wallet is currently locked for user " + userId);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Lock acquisition interrupted", e);
        }
    }
}
```

## Async Job Pattern (audio-generation-service)

Trong `audio-generation-service`, mọi tác vụ tạo nhạc (DIY/AI) là tác vụ bất đồng bộ. Endpoint API PHẢI trả về `202 Accepted` ngay lập tức với một `jobId` để client theo dõi tiến trình.

- Sử dụng `@Async` với `ThreadPoolTaskExecutor` tùy chỉnh.
- `jobId` dùng để lưu trạng thái và phần trăm tiến trình trong Redis (`job:{jobId}:progress`).
- Client sẽ polling `/api/audio/job/{jobId}/status` để lấy tiến trình.

```java
// Trong AudioGenerationService.java

@Service
public class AudioGenerationService {

    private final TaskExecutor audioJobExecutor;

    public AudioGenerationService(@Qualifier("audioJobExecutor") TaskExecutor audioJobExecutor) {
        this.audioJobExecutor = audioJobExecutor;
    }

    public String generateAudioAsync(GenerateAudioRequest request) {
        String jobId = UUID.randomUUID().toString();
        // Lưu trạng thái khởi tạo job vào Redis
        // RedisTemplate.opsForValue().set("job:" + jobId + ":status", "PENDING");

        audioJobExecutor.execute(() -> {
            try {
                // Thực hiện logic tạo audio dài hơi
                // Ví dụ: gọi AI, tách nhạc, mix audio, upload MinIO
                // Cập nhật tiến trình vào Redis định kỳ: RedisTemplate.opsForValue().set("job:" + jobId + ":progress", "50");

                // Sau khi hoàn thành
                // RedisTemplate.opsForValue().set("job:" + jobId + ":status", "COMPLETED");
                // Gửi RabbitMQ event: audio.generated
            } catch (Exception e) {
                // Xử lý lỗi, cập nhật trạng thái lỗi vào Redis
                // RedisTemplate.opsForValue().set("job:" + jobId + ":status", "FAILED");
            }
        });
        return jobId;
    }
}
```

## Cấu hình `ThreadPoolTaskExecutor` (audio-generation-service)

Cấu hình `audioJobExecutor` trong một `@Configuration` class:

```java
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "audioJobExecutor")
    public TaskExecutor audioJobExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);        // Số thread chạy song song tối thiểu
        executor.setMaxPoolSize(30);       // Số thread tối đa khi queue đầy
        executor.setQueueCapacity(200);      // Kích thước hàng đợi cho các job chờ
        executor.setThreadNamePrefix("AudioJob-");
        executor.initialize();
        return executor;
    }
}

## Deployment Conventions

### Rebuilding and Deploying Specific Services
Khi có sự thay đổi về code hoặc cấu hình trong một hoặc nhiều microservices, câu lệnh để build và chạy lại các service bị ảnh hưởng cần được trả về theo dạng:
```bash
./build-service-and-deploy.sh <modified-service-1> <modified-service-2> ...
```
Điều này giúp hạn chế việc build lại và khởi động lại các service không liên quan, tối ưu hóa tài nguyên hệ thống.

```
