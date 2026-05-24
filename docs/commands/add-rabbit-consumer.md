# Command Template: Add RabbitMQ Consumer

## Description

Prompt template để thêm một RabbitMQ consumer mới vào một service Java đã tồn tại. Bao gồm listener class theo pattern `common-rmq`, DLQ config và retry config.

## Usage

```text
Thêm RabbitMQ consumer mới vào service {SERVICE_NAME}. Lắng nghe event {EVENT_NAME} từ exchange {EXCHANGE} và queue {QUEUE}. Output ra listener class theo common-rmq pattern, DLQ config, retry config.
```

## Placeholders

- `{SERVICE_NAME}`: Tên service đích, ví dụ `notification-service`
- `{EVENT_NAME}`: Tên sự kiện (PascalCase), ví dụ `UserRegisteredEvent`
- `{EXCHANGE}`: Tên exchange (kebab-case), ví dụ `user-events-exchange`
- `{QUEUE}`: Tên queue (kebab-case), ví dụ `user-registered-queue`

## Rules

- Listener class đặt trong package `{PACKAGE_NAME}.listener`
- Event POJO đặt trong package `{PACKAGE_NAME}.event` hoặc có thể dùng từ `common-rmq` nếu là sự kiện chung
- Sử dụng `@RabbitListener` và `@Retryable`
- Dead Letter Queue (DLQ) và retry logic PHẢI theo cấu hình từ `common-rmq`

## Example Output Structure (Concept)

```java
// {SERVICE_NAME}/src/main/java/{PACKAGE_NAME}/event/{EVENT_NAME}.java
package {PACKAGE_NAME}.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Ví dụ event POJO, có thể dùng từ common-rmq nếu là sự kiện chung
@Data
@NoArgsConstructor
@AllArgsConstructor
public class {EVENT_NAME} {
    private Long userId;
    private String email;
    // ... các field khác của event
}
```

```java
// {SERVICE_NAME}/src/main/java/{PACKAGE_NAME}/listener/{EVENT_NAME}Listener.java
package {PACKAGE_NAME}.listener;

import {PACKAGE_NAME}.event.{EVENT_NAME};
import {PACKAGE_NAME}.service.{SERVICE_NAME}Service;
import com.platform.common.rmq.RmqConstants; // Hoặc dùng RmqExchanges, RmqQueues nếu có
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.retry.annotation.Retryable;
import org.springframework.retry.annotation.Backoff;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class {EVENT_NAME}Listener {

    private final {SERVICE_NAME}Service {service_name}Service;

    public {EVENT_NAME}Listener({SERVICE_NAME}Service {service_name}Service) {
        this.{service_name}Service = {service_name}Service;
    }

    @RabbitListener(queues = RmqConstants.{QUEUE_CONSTANT_NAME}) // Ví dụ
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public void handle{EVENT_NAME}({EVENT_NAME} event) {
        log.info("Received {} event: {}", event.getClass().getSimpleName(), event);
        // Logic xử lý event
        {service_name}Service.process{EVENT_NAME}(event);
    }

    // Nếu không muốn dùng @Retryable, có thể dùng try-catch và gửi thủ công vào DLQ
    // Tuy nhiên, common-rmq sẽ cung cấp RetryTemplate để config chung
}
```

## DLQ and Retry Configuration (common-rmq pattern)

Cấu hình RabbitMQ cho DLQ và retry sẽ được tự động inject thông qua `common-rmq`. Service chỉ cần định nghĩa queue và listener. `common-rmq` đảm bảo:

- Mỗi queue chính `{QUEUE}` sẽ có một Dead Letter Exchange (`{QUEUE}.dlx`) và một Dead Letter Queue (`{QUEUE}.dlq`).
- Message lỗi sau khi retry thất bại (ví dụ 3 lần với backoff 1s, 2s, 4s) sẽ tự động chuyển vào `{QUEUE}.dlq`.
- Có thể dùng `RmqConstants` (hoặc `RmqExchanges`, `RmqQueues`) từ `common-rmq` để định nghĩa tên exchange/queue thống nhất.

```java
// Ví dụ trong common-rmq/RmqConstants.java
public class RmqConstants {
    public static final String USER_EVENTS_EXCHANGE = "user-events-exchange";
    public static final String USER_REGISTERED_QUEUE = "user-registered-queue";
    public static final String USER_REGISTERED_ROUTING_KEY = "user.registered";

    // Định nghĩa DLX và DLQ cho từng queue nếu cần, hoặc cấu hình tự động trong common-rmq
    public static final String USER_REGISTERED_DLQ = "user-registered-dlq";
    public static final String USER_REGISTERED_DLX = "user-registered-dlx";
}
```
