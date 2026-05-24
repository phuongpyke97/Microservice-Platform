# Auth Service UAT Test Cases

## 1. User Registration Flow
**Goal**: Verify that a new user can register successfully.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 1.1 | Send POST to `/api/auth/register` with new email and valid password. | Returns 201 Created, `accessToken` and `refreshToken` present. | |
| 1.2 | Send POST to `/api/auth/register` with an already registered email. | Returns 409 Conflict with code `AUTH_EMAIL_ALREADY_EXISTS`. | |
| 1.3 | Send POST to `/api/auth/register` with invalid email format. | Returns 400 Bad Request. | |
| 1.4 | Check DB for the new user record. | User exists with `credit_balance` = 0 and status `ACTIVE`. | |
| 1.5 | Check RabbitMQ for `UserRegisteredEvent`. | Event published to `user-events-exchange`. | |

## 2. User Login Flow
**Goal**: Verify user authentication and token issuance.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 2.1 | Send POST to `/api/auth/login` with correct credentials. | Returns 200 OK, `accessToken` and `refreshToken` issued. | |
| 2.2 | Send POST to `/api/auth/login` with wrong password. | Returns 401 Unauthorized, code `AUTH_INVALID_CREDENTIALS`. | |
| 2.3 | Send POST to `/api/auth/login` for a non-existent email. | Returns 401 Unauthorized, code `AUTH_INVALID_CREDENTIALS`. | |
| 2.4 | Manually set user status to `LOCKED` in DB, then try login. | Returns 403 Forbidden, code `AUTH_ACCOUNT_LOCKED`. | |

## 3. Token Refresh Flow
**Goal**: Verify that tokens can be rotated using the refresh token.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 3.1 | Send POST to `/api/auth/refresh` with a valid `refreshToken`. | Returns 200 OK, new `accessToken` and `refreshToken` pair. | |
| 3.2 | Send POST to `/api/auth/refresh` with an expired or malformed token. | Returns 401 Unauthorized, code `AUTH_TOKEN_REFRESH_FAILED`. | |
| 3.3 | Use the newly issued `accessToken` to call a protected endpoint (via Gateway). | Request is authorized. | |

## 4. Forgot Password Flow
**Goal**: Verify the password reset initiation.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 4.1 | Send POST to `/api/auth/forgot-password` with valid email. | Returns 200 OK, message "Password reset OTP sent". | |
| 4.2 | Check RabbitMQ for `UserPasswordResetEvent`. | Event contains a 6-char OTP. | |
| 4.3 | Send POST to `/api/auth/forgot-password` with invalid email. | Returns 404 Not Found, code `AUTH_USER_NOT_FOUND`. | |
