# Auth Service API Documentation

## Overview
`auth-service` manages user identity, authentication, and token issuance. It serves all authentication requests from clients.

**Base URL**: `/api/auth` (Routed through API Gateway: `http://localhost:8080/api/auth`)

---

## 1. Register User
Registers a new user using email and password.

- **URL**: `/register`
- **Method**: `POST`
- **Auth Required**: No

### Request Body
```json
{
  "email": "user@example.com",
  "password": "Password123!"
}
```
*Constraints*:
- `email`: Valid email format, required.
- `password`: 8-64 characters, required.

### Success Response
- **Code**: `201 Created`
- **Body**:
```json
{
  "code": "SUCCESS",
  "message": "User registered",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1...",
    "refreshToken": "eyJhbGciOiJIUzI1...",
    "expiresIn": 3600,
    "tokenType": "Bearer"
  },
  "timestamp": "2024-01-01T12:00:00Z"
}
```

### Error Responses
- `409 Conflict`: If `AUTH_EMAIL_ALREADY_EXISTS`.
- `400 Bad Request`: If validation fails.

---

## 2. Login
Authenticates a user and returns access and refresh tokens.

- **URL**: `/login`
- **Method**: `POST`
- **Auth Required**: No

### Request Body
```json
{
  "email": "user@example.com",
  "password": "Password123!"
}
```

### Success Response
- **Code**: `200 OK`
- **Body**:
```json
{
  "code": "SUCCESS",
  "message": "Login successful",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1...",
    "refreshToken": "eyJhbGciOiJIUzI1...",
    "expiresIn": 3600,
    "tokenType": "Bearer"
  },
  "timestamp": "2024-01-01T12:00:00Z"
}
```

### Error Responses
- `401 Unauthorized`: `AUTH_INVALID_CREDENTIALS` (Wrong email/password).
- `403 Forbidden`: `AUTH_ACCOUNT_LOCKED` (User status is not ACTIVE).

---

## 3. Refresh Token
Exchanges a valid refresh token for a new token pair.

- **URL**: `/refresh`
- **Method**: `POST`
- **Auth Required**: No

### Request Body
```json
{
  "refreshToken": "eyJhbGciOiJIUzI1..."
}
```

### Success Response
- **Code**: `200 OK`
- **Body**:
```json
{
  "code": "SUCCESS",
  "message": "Token refreshed",
  "data": {
    "accessToken": "new_eyJhbGciOi...",
    "refreshToken": "new_eyJhbGciOi...",
    "expiresIn": 3600,
    "tokenType": "Bearer"
  },
  "timestamp": "2024-01-01T12:00:00Z"
}
```

### Error Responses
- `401 Unauthorized`: `AUTH_TOKEN_REFRESH_FAILED` (Invalid/expired token).
- `404 Not Found`: `AUTH_USER_NOT_FOUND` (User deleted after token issue).
- `403 Forbidden`: `AUTH_ACCOUNT_LOCKED` (User locked after token issue).

---

## 4. Forgot Password
Initiates the password reset flow. Generates an OTP and publishes an event.

- **URL**: `/forgot-password`
- **Method**: `POST`
- **Auth Required**: No

### Request Body
```json
{
  "email": "user@example.com"
}
```

### Success Response
- **Code**: `200 OK`
- **Body**:
```json
{
  "code": "SUCCESS",
  "message": "Password reset OTP sent",
  "data": null,
  "timestamp": "2024-01-01T12:00:00Z"
}
```

### Error Responses
- `404 Not Found`: `AUTH_USER_NOT_FOUND`.

---

## Common Error Codes
All errors are returned wrapped in the standard `ErrorResponse`:

| Code | HTTP Status | Description |
|---|---|---|
| `AUTH_USER_NOT_FOUND` | 404 | User does not exist |
| `AUTH_INVALID_CREDENTIALS` | 401 | Incorrect email or password |
| `AUTH_EMAIL_ALREADY_EXISTS`| 409 | Email already in use |
| `AUTH_MSISDN_ALREADY_EXISTS`| 409 | Phone number already in use |
| `AUTH_ACCOUNT_LOCKED` | 403 | Account is not ACTIVE |
| `AUTH_TOKEN_INVALID` | 401 | JWT is malformed or expired |
| `AUTH_TOKEN_REFRESH_FAILED`| 401 | Refresh token is invalid or expired |