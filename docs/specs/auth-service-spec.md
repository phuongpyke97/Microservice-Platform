# Auth Service Specification

## 1. Overview
The `auth-service` is responsible for handling user authentication, registration, JWT token generation/validation, and basic user management logic. It operates as an infrastructure service.

## 2. Technical Stack
- **Framework**: Spring Boot 3.2.x, Java 21
- **Database**: PostgreSQL 16 (Dedicated `auth_db`)
- **Messaging**: RabbitMQ (Publishing events)
- **Security**: JWT (JSON Web Tokens), BCrypt Password Hashing (Strength: 12)
- **Migration**: Flyway

## 3. Database Schema
### `users` table
| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGSERIAL | PRIMARY KEY | Unique user ID |
| `msisdn` | VARCHAR(20) | UNIQUE | Phone number (used for telco flows) |
| `email` | VARCHAR(120) | UNIQUE | User email address |
| `password_hash` | VARCHAR(72) | | BCrypt hashed password |
| `credit_balance` | INT | NOT NULL, DEFAULT 0 | User's virtual credit balance |
| `status` | VARCHAR(20) | NOT NULL, DEFAULT 'ACTIVE' | Account status (`ACTIVE`, `LOCKED`, `DISABLED`) |
| `created_at` | TIMESTAMP | NOT NULL | Creation time |
| `updated_at` | TIMESTAMP | | Last update time |

*Indexes*: `idx_users_msisdn`, `idx_users_email`

### `user_roles` table
| Column | Type | Constraints | Description |
|---|---|---|---|
| `user_id` | BIGINT | PK, FK (users.id) | Reference to `users` |
| `role` | VARCHAR(50) | PK | E.g., 'ADMIN', 'USER' |

## 4. Business Logic Flows

### 4.1. Registration Flow
1. Receives email and raw password.
2. Checks if the email is already in use (Throws `AUTH_EMAIL_ALREADY_EXISTS` if true).
3. Hashes the password using BCrypt.
4. Creates a new `User` record with role `ADMIN` (default for explicit web registration) and `credit_balance` = 0.
5. Saves to the database.
6. Publishes `UserRegisteredEvent` to RabbitMQ.
7. Issues and returns Access Token and Refresh Token.

### 4.2. Login Flow
1. Receives email and raw password.
2. Finds the user by email (Throws `AUTH_INVALID_CREDENTIALS` if not found).
3. Validates that user status is `ACTIVE` (Throws `AUTH_ACCOUNT_LOCKED` if not).
4. Compares raw password with stored hash.
5. Issues and returns Access Token and Refresh Token.

### 4.3. Token Refresh Flow
1. Validates the signature and expiration of the Refresh Token.
2. Parses the `userId` from the token subject.
3. Retrieves the user from the database.
4. Validates that the user is still `ACTIVE`.
5. Issues a new pair of Access and Refresh tokens.

### 4.4. Forgot Password Flow
1. Receives an email.
2. Finds the user by email.
3. Generates a 6-character alphanumeric OTP.
4. Publishes `UserPasswordResetEvent` to RabbitMQ (for notification-service to send emails).

### 4.5. Lazy Subscriber Creation
1. Used internally when a CRBT transaction comes from a phone number (`msisdn`) not yet in the system.
2. If `msisdn` doesn't exist, it creates a new user with role `USER`, `credit_balance` = 2.
3. Publishes `UserRegisteredEvent` to RabbitMQ.

## 5. Event Publishing (RabbitMQ)
All events are published to `RmqExchanges.USER_EVENTS`.

| Event Type | Routing Key | Payload Structure |
|---|---|---|
| **User Registration** | `user.registered` | `UserRegisteredEvent(userId, email, msisdn, timestamp)` |
| **Password Reset** | `user.password.reset` | `UserPasswordResetEvent(userId, email, otp, timestamp)` |

## 6. JWT Configuration
Tokens are stateless. The API Gateway validates tokens, while `auth-service` issues them.
- Access Token TTL: typically short (e.g., 15-60 mins).
- Refresh Token TTL: typically long (e.g., 7-30 days).
- Claims included: `userId` (subject), `email`, `roles`.