# Notification Service UAT Test Cases

## 1. Authentication Notifications
**Goal**: Verify that users receive automated notifications related to their account.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 1.1 | Register a new user with MSISDN and Email. | `UserNotificationListener` receives `UserRegisteredEvent`. | |
| 1.2 | Check logs/output. | "Send welcome notification" log entry appears with correct email and MSISDN. | |
| 1.3 | Initiate "Forgot Password" flow for a user. | `UserNotificationListener` receives `UserPasswordResetEvent`. | |
| 1.4 | Check logs/output. | "Send password reset otp" log entry appears with correct email and OTP code. | |

## 2. Payment Notifications
**Goal**: Verify that users are notified of successful payments.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 2.1 | Trigger a successful payment in `payment-gateway-service`. | `PaymentNotificationListener` receives `PaymentResultEvent` with `status: SUCCESS`. | |
| 2.2 | Check logs/output. | "Send payment confirmation" log entry appears with correct userId and packageCode. | |
| 2.3 | Trigger a failed payment. | `PaymentNotificationListener` receives event with `status: FAILED`. | |
| 2.4 | Check logs/output. | No notification log entry appears (notifications are not sent for failures). | |

## 3. Audio Generation Notifications
**Goal**: Verify that users are notified when their async audio generation is ready.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 3.1 | Complete an audio generation job in `audio-generation-service`. | `AudioNotificationListener` receives `AudioGeneratedEvent` with `status: COMPLETED`. | |
| 3.2 | Check logs/output. | "Send audio ready" log entry appears with correct userId, jobId, and audioUrl. | |
| 3.3 | Fail an audio generation job. | `AudioNotificationListener` receives event with `status: FAILED`. | |
| 3.4 | Check logs/output. | No notification log entry appears (current logic only notifies on success). | |

## 4. Error Handling & Reliability
**Goal**: Verify notification service resilience.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 4.1 | Publish an invalid JSON message to `user.registered` queue. | Message fails parsing; standard DLQ logic applies. | |
| 4.2 | Simulate a transient failure in the notification handler. | Message is retried 3 times then moved to DLQ. | |
| 4.3 | Check RabbitMQ Management UI. | DLQ contains the failed message for manual inspection. | |
