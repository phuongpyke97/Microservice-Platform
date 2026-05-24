# Notification Service Specification

## 1. Overview
`notification-service` is a pure event consumer responsible for sending notifications to users via email, SMS, or push notifications. It has **no REST API** — all operations are triggered by RabbitMQ events from other services.

## 2. Technical Stack
- **Framework**: Spring Boot 3.2.x, Java 21
- **Database**: None (stateless)
- **Messaging**: RabbitMQ (Consumer only)
- **Port**: 8082 (HTTP port exists but no endpoints exposed)

## 3. Business Logic / Notification Types

### 3.1. Welcome Notification
- **Trigger**: `UserRegisteredEvent` from `auth-service`
- **Action**: Send welcome email/SMS to new user
- **Implementation**: Currently logs to console (placeholder for actual email/SMS integration)

### 3.2. Password Reset OTP
- **Trigger**: `UserPasswordResetEvent` from `auth-service`
- **Action**: Send OTP code via email
- **Implementation**: Currently logs to console

### 3.3. Payment Confirmation
- **Trigger**: `PaymentResultEvent` from `payment-gateway-service`
- **Action**: Send payment success notification
- **Condition**: Only sent if `status=SUCCESS`
- **Implementation**: Currently logs to console

### 3.4. Audio Generation Ready
- **Trigger**: `AudioGeneratedEvent` from `audio-generation-service`
- **Action**: Notify user that their audio is ready for download
- **Condition**: Only sent if `status=COMPLETED`
- **Implementation**: Currently logs to console

## 4. RabbitMQ Integration

### Consumers
| Queue | Exchange | Routing Key | Event Type | Handler |
|---|---|---|---|---|
| `user.registered` | `user.events` | `user.registered` | `UserRegisteredEvent` | `UserNotificationListener.onUserRegistered()` |
| `user.password.reset` | `user.events` | `user.password.reset` | `UserPasswordResetEvent` | `UserNotificationListener.onPasswordReset()` |
| `notification.payment` | `payment.events` | `payment.result` | `PaymentResultEvent` | `PaymentNotificationListener.onPaymentResult()` |
| `audio.generated` | `audio.events` | `audio.generated` | `AudioGeneratedEvent` | `AudioNotificationListener.onAudioGenerated()` |

All queues are DLQ-enabled via `common-rmq`.

### Publisher
`notification-service` does **not** publish any events.

## 5. Future Integration Points
The current implementation logs notifications to the console. In production, this service should integrate with:
- **Email**: SMTP server or transactional email service (e.g., SendGrid, AWS SES)
- **SMS**: Telco SMS gateway or service (e.g., Twilio, Vonage)
- **Push Notifications**: Firebase Cloud Messaging (FCM) or Apple Push Notification Service (APNS)

## 6. Error Handling
- All listeners use the common RabbitMQ retry mechanism (3 retries with exponential backoff).
- Failed notifications are routed to the DLQ after exhausting retries.
- No user-facing errors — failures are logged and monitored.

## 7. Monitoring & Observability
- Logs are collected by Promtail and sent to Grafana Loki.
- Key metrics to monitor:
  - Notification processing rate
  - DLQ message count (indicates persistent failures)
  - Listener lag (RabbitMQ queue depth)
