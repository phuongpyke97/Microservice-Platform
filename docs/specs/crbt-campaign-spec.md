# CRBT Campaign Service Specification

## 1. Overview
`crbt-campaign-service` manages marketing campaigns, subscription packages, and user subscriptions. It integrates with external AI services (Google Lyria 3) for music generation and publishes credit events to the wallet service.

## 2. Technical Stack
- **Framework**: Spring Boot 3.2.x, Java 21
- **Database**: PostgreSQL 16 (Dedicated `campaign_db`)
- **Messaging**: RabbitMQ (Publishing credit events)
- **Scheduling**: Spring `@Scheduled` (Daily auto-renewal at 00:00)
- **External API**: Google Lyria 3 (AI music generation)
- **Port**: 8090

## 3. Database Schema

### `campaigns` table
| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGSERIAL | PRIMARY KEY | Campaign ID |
| `name` | VARCHAR(150) | NOT NULL | Campaign name |
| `description` | VARCHAR(500) | | Campaign description |
| `status` | VARCHAR(20) | NOT NULL | DRAFT, ACTIVE, ENDED |
| `start_at` | TIMESTAMP | NOT NULL | Campaign start time |
| `end_at` | TIMESTAMP | NOT NULL | Campaign end time |
| `created_at` | TIMESTAMP | NOT NULL | Creation timestamp |
| `updated_at` | TIMESTAMP | | Last update timestamp |

### `campaign_packages` table
| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGSERIAL | PRIMARY KEY | Package ID |
| `campaign_id` | BIGINT | FK (campaigns.id) | Parent campaign |
| `name` | VARCHAR(100) | NOT NULL | Package name |
| `price` | DECIMAL(12,2) | NOT NULL | Price in local currency |
| `credit_amount` | INT | NOT NULL | Base credit amount |
| `validity_days` | INT | NOT NULL | Subscription validity period |
| `created_at` | TIMESTAMP | NOT NULL | Creation timestamp |

### `user_subscriptions` table
| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGSERIAL | PRIMARY KEY | Subscription ID |
| `user_id` | BIGINT | NOT NULL | Subscriber user ID |
| `package_id` | BIGINT | FK (campaign_packages.id) | Subscribed package |
| `status` | VARCHAR(20) | NOT NULL | ACTIVE, EXPIRED, CANCELLED |
| `expires_at` | TIMESTAMP | NOT NULL | Expiration timestamp |
| `auto_renew` | BOOLEAN | NOT NULL, DEFAULT true | Auto-renewal flag |
| `created_at` | TIMESTAMP | NOT NULL | Subscription start time |

## 4. Business Logic Flows

### 4.1. Create Campaign Flow
1. Admin creates a campaign with multiple packages.
2. Each package defines: name, price, credit amount, validity days.
3. Campaign status is set to `ACTIVE` (or `DRAFT` for future activation).
4. Packages are persisted with cascade from the parent campaign.

### 4.2. Subscribe to Package Flow
1. User selects a package from an active campaign.
2. Validates: campaign is `ACTIVE`, `endAt` is in the future.
3. Creates a `UserSubscription` record with `expiresAt = now + validityDays`.
4. Calculates credit amount with bonus rule: +10% if package price >= 1000.
5. Publishes `CreditChangedEvent` to RabbitMQ (`credit.changed` routing key).
6. `credit-wallet-service` consumes the event and adds credits to the user's wallet.

### 4.3. Auto-Renewal Flow (Scheduled Daily at 00:00)
1. Scheduler queries all subscriptions with `status=ACTIVE`, `autoRenew=true`, `expiresAt < now`.
2. For each expiring subscription:
   - Extends `expiresAt` by `validityDays`.
   - Calculates credit amount with bonus rule.
   - Publishes `CreditChangedEvent` with reason "Auto-renewal".
3. Logs the number of renewed subscriptions.
4. Failures are logged but do not block other renewals.

### 4.4. AI Music Generation (Lyria Integration)
1. Receives genre, mood, instrument parameters.
2. Calls Google Lyria 3 API via `LyriaService` (Feign client).
3. Returns raw audio bytes to the client.

## 5. Credit Bonus Rule
**Rule**: If package price >= 1000, grant +10% bonus credits.

Example:
- Package: price=1200, creditAmount=100 → User receives 110 credits.
- Package: price=500, creditAmount=50 → User receives 50 credits.

## 6. Event Publishing (RabbitMQ)
Events are published to `RmqExchanges.CREDIT_EVENTS`.

| Event Type | Routing Key | Payload Structure |
|---|---|---|
| **Subscription Credit** | `credit.changed` | `CreditChangedEvent(userId, amount, "IN", reason, referenceId, timestamp)` |
| **Auto-Renewal Credit** | `credit.changed` | `CreditChangedEvent(userId, amount, "IN", "Auto-renewal: {pkgName}", referenceId, timestamp)` |

## 7. Scheduled Jobs
- **Auto-Renewal**: Runs daily at 00:00 (cron: `0 0 0 * * *`).
- Renews all active subscriptions with `autoRenew=true` that have expired.

## 8. External Dependencies
- **Google Lyria 3 API**: For AI music generation.
- **credit-wallet-service**: Consumes credit events to update user balances.
- **crbt-community-library**: Fallback music source if Lyria fails (not yet implemented in this service).