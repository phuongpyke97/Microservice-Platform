# End-to-End UAT Test Flows

This document describes comprehensive end-to-end test scenarios that span multiple services and verify the complete business workflows of the CRBT platform.

---

## Flow 1: New User Registration → Wallet Top-Up → Campaign Subscription

**Goal**: Verify the complete user onboarding and subscription flow.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 1.1 | POST `/api/auth/register` with msisdn, email, password. | Returns 200 OK with JWT tokens. User record created in `auth-service` DB. | |
| 1.2 | Check RabbitMQ. | `UserRegisteredEvent` published to `user.events` exchange. | |
| 1.3 | Check `notification-service` logs. | Welcome notification log entry appears. | |
| 1.4 | Check `audit-log-service` DB. | `REGISTER` action logged with status `SUCCESS`. | |
| 1.5 | Check `credit-wallet-service` DB. | Wallet created for user with initial balance 0. | |
| 1.6 | POST `/api/payments/charge` with `idempotencyKey`, `packageCode=TOPUP_500`, `amountMmk=5000`, `creditAmount=500`. | Returns 200 OK. Payment transaction created with status `SUCCESS`. | |
| 1.7 | Check RabbitMQ. | `PaymentResultEvent` published to `payment.events` exchange. | |
| 1.8 | Check `credit-wallet-service` DB. | Wallet balance increased to 500. | |
| 1.9 | Check `crbt-credit-transaction-service` DB. | CREDIT transaction recorded with amount=500. | |
| 1.10 | Check `notification-service` logs. | Payment confirmation log entry appears. | |
| 1.11 | GET `/campaigns/active`. | Returns list of active campaigns. | |
| 1.12 | POST `/campaigns/subscribe` with `campaignId` and `packageId` (price=100 credits). | Returns 200 OK. Subscription created. | |
| 1.13 | Check `credit-wallet-service` DB. | Wallet balance decreased to 400 (500 - 100). | |
| 1.14 | Check `crbt-credit-transaction-service` DB. | DEBIT transaction recorded with amount=100, reason="Subscribe to package". | |
| 1.15 | Check `crbt-campaign-service` DB. | `user_subscriptions` record created with status `ACTIVE`. | |
| 1.16 | Check `audit-log-service` DB. | `SUBSCRIBE` action logged. | |

---

## Flow 2: Audio Generation → Ringtone Assignment → Mytone Sync

**Goal**: Verify the complete AI audio generation and telco core integration flow.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 2.1 | User has active subscription and sufficient credits. | Precondition verified. | |
| 2.2 | POST `/audio-jobs` with `prompt="Xin chào"` and `voiceId="vi-VN-HoaiMyNeural"`. | Returns 202 Accepted. Job created with status `PENDING`. | |
| 2.3 | Check Redis. | Active job counter incremented: `audio_gen:active_jobs:user:{userId}` = 1. | |
| 2.4 | Poll GET `/audio-jobs/{jobId}`. | Status transitions: `PENDING` → `PROCESSING` → `COMPLETED`. | |
| 2.5 | Check `ai-media-worker` logs. | TTS generation request received and processed. | |
| 2.6 | Check `file-service` / MinIO. | Audio file uploaded to `audio-bucket`. | |
| 2.7 | Check final job details. | `resultUrl` populated with MinIO URL. | |
| 2.8 | Check RabbitMQ. | `AudioGeneratedEvent` published to `audio.events` exchange. | |
| 2.9 | Check `notification-service` logs. | "Audio ready" notification log entry appears. | |
| 2.10 | Check Redis. | Active job counter decremented: `audio_gen:active_jobs:user:{userId}` = 0. | |
| 2.11 | POST `/ringtone-assignments` with `msisdn` and `ringtoneUrl` (from step 2.7). | Returns 202 Accepted. Assignment created with status `PENDING`. | |
| 2.12 | Poll GET `/ringtone-assignments`. | Status transitions: `PENDING` → `SYNCING` → `ACTIVE`. | |
| 2.13 | Check `crbt-core-adapter` logs. | Mytone CMS API call logged. | |
| 2.14 | Check Mytone mock/API. | Assignment request received with transcoded audio URL. | |
| 2.15 | Check final assignment details. | `mytoneTransactionId` populated, status `ACTIVE`. | |

---

## Flow 3: Campaign Auto-Renewal (Scheduled Job)

**Goal**: Verify the daily auto-renewal scheduler correctly renews subscriptions and charges wallets.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 3.1 | User has an active subscription with `nextRenewalDate` = today. | Precondition set up. | |
| 3.2 | User wallet has sufficient balance (e.g., 200 credits, package costs 100). | Precondition verified. | |
| 3.3 | Wait for scheduler to run (cron: `0 0 0 * * *` = midnight). | Scheduler executes `CampaignScheduler.renewSubscriptions()`. | |
| 3.4 | Check `crbt-campaign-service` logs. | "Processing renewal for subscription {id}" log entries appear. | |
| 3.5 | Check `credit-wallet-service` DB. | Wallet balance decreased by package price. | |
| 3.6 | Check `crbt-credit-transaction-service` DB. | DEBIT transaction recorded with reason="Auto-renewal". | |
| 3.7 | Check `user_subscriptions` table. | `nextRenewalDate` updated to tomorrow. | |
| 3.8 | Check `audit-log-service` DB. | `SUBSCRIBE` action logged for the renewal. | |

---

## Flow 4: Insufficient Credits → Subscription Failure

**Goal**: Verify graceful handling when user lacks sufficient credits.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 4.1 | User wallet balance = 50 credits. | Precondition set. | |
| 4.2 | POST `/campaigns/subscribe` with package price = 100 credits. | Returns 400 Bad Request, code `WALLET_INSUFFICIENT_CREDIT`. | |
| 4.3 | Check `credit-wallet-service` DB. | Wallet balance unchanged (still 50). | |
| 4.4 | Check `crbt-campaign-service` DB. | No new subscription record created. | |
| 4.5 | Check `crbt-credit-transaction-service` DB. | No DEBIT transaction recorded. | |

---

## Flow 5: Concurrent Audio Job Limit Enforcement

**Goal**: Verify the per-user 5-active-job limit is enforced correctly.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 5.1 | Submit 5 audio generation jobs in rapid succession. | All 5 return 202 Accepted. | |
| 5.2 | Check Redis. | `audio_gen:active_jobs:user:{userId}` = 5. | |
| 5.3 | Submit a 6th job while previous 5 are still pending/processing. | Returns 400 Bad Request with "Max 5 active jobs" error. | |
| 5.4 | Wait for one job to complete. | Job status becomes `COMPLETED`. | |
| 5.5 | Check Redis. | Counter decremented to 4. | |
| 5.6 | Submit a new job. | Returns 202 Accepted. Counter incremented to 5. | |

---

## Flow 6: Payment Idempotency & Wallet Credit

**Goal**: Verify payment idempotency prevents duplicate charges and credits.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 6.1 | User wallet balance = 100 credits. | Precondition set. | |
| 6.2 | POST `/api/payments/charge` with `idempotencyKey=tx-001`, `creditAmount=500`. | Returns 200 OK. Transaction created with status `SUCCESS`. | |
| 6.3 | Check `payment-gateway-service` DB. | Transaction record exists with `idempotencyKey=tx-001`. | |
| 6.4 | Check `credit-wallet-service` DB. | Wallet balance increased to 600 (100 + 500). | |
| 6.5 | POST `/api/payments/charge` again with the exact same `idempotencyKey=tx-001`. | Returns 200 OK immediately with the existing transaction data. | |
| 6.6 | Check `payment-gateway-service` DB. | No new transaction record created. | |
| 6.7 | Check `credit-wallet-service` DB. | Wallet balance unchanged (still 600, not 1100). | |
| 6.8 | Check `crbt-credit-transaction-service` DB. | Only one CREDIT transaction recorded for `tx-001`. | |

---

## Flow 7: Forgot Password → OTP Verification

**Goal**: Verify the password reset flow with OTP.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 7.1 | POST `/api/auth/forgot-password` with `email`. | Returns 200 OK. OTP generated and stored. | |
| 7.2 | Check RabbitMQ. | `UserPasswordResetEvent` published to `user.events` exchange. | |
| 7.3 | Check `notification-service` logs. | "Send password reset otp" log entry appears with correct email and OTP. | |
| 7.4 | POST `/api/auth/reset-password` with `email`, `otp`, and `newPassword`. | Returns 200 OK. Password updated. | |
| 7.5 | POST `/api/auth/login` with new password. | Returns 200 OK with JWT tokens. | |
| 7.6 | Check `audit-log-service` DB. | `PASSWORD_RESET` action logged. | |

---

## Flow 8: Community Library Fallback

**Goal**: Verify users can browse and use pre-made ringtones from the community library.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 8.1 | GET `/library/categories`. | Returns list of categories (e.g., Pop, Rock, Classical). | |
| 8.2 | GET `/library/ringtones/search?categoryId=1`. | Returns ringtones in category 1. | |
| 8.3 | GET `/library/ringtones/random`. | Returns a single random ringtone. | |
| 8.4 | Select a ringtone URL from the response. | URL noted. | |
| 8.5 | POST `/ringtone-assignments` with the library ringtone URL. | Returns 202 Accepted. Assignment created. | |
| 8.6 | Poll GET `/ringtone-assignments`. | Status becomes `ACTIVE` after Mytone sync. | |

---

## Flow 9: Circuit Breaker & Fallback

**Goal**: Verify circuit breaker behavior when external services fail.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 9.1 | Stop Mytone mock/API. | Mytone unavailable. | |
| 9.2 | POST `/ringtone-assignments` with valid data. | Returns 202 Accepted (local record created). | |
| 9.3 | Poll GET `/ringtone-assignments`. | Status becomes `FAILED`, `errorMessage` contains "Mytone unavailable". | |
| 9.4 | Check `crbt-core-adapter` logs. | Circuit breaker fallback triggered. | |
| 9.5 | Restart Mytone mock/API. | Mytone available again. | |
| 9.6 | Submit a new assignment. | After circuit breaker recovers, status becomes `ACTIVE`. | |

---

## Flow 10: Full User Journey (Registration → Subscription → Audio → Assignment)

**Goal**: Verify the complete end-to-end user journey in one continuous flow.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 10.1 | Register new user. | User created, wallet initialized. | |
| 10.2 | Top up wallet with 1000 credits. | Payment successful, wallet credited. | |
| 10.3 | Subscribe to VIP campaign (100 credits). | Subscription active, wallet debited. | |
| 10.4 | Generate custom audio via TTS. | Audio job completes, file stored in MinIO. | |
| 10.5 | Assign generated audio as ringtone. | Assignment synced to Mytone, status `ACTIVE`. | |
| 10.6 | Verify all audit logs. | All actions logged: REGISTER, PAYMENT, SUBSCRIBE, AUDIO_GENERATION, RINGTONE_ASSIGNMENT. | |
| 10.7 | Verify all notifications sent. | Welcome, payment confirmation, audio ready notifications logged. | |
| 10.8 | Verify wallet balance. | Final balance = 1000 - 100 = 900 credits. | |
| 10.9 | Export credit transaction history as CSV. | CSV contains all CREDIT and DEBIT transactions. | |
