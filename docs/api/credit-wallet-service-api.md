# Credit Wallet Service API Documentation

## Overview
`credit-wallet-service` manages user credit balances, supporting operations like adding and deducting credits. It uses Redisson for distributed locking to ensure consistency during concurrent transactions.

**Base URL**: `/api/wallet` (Routed through API Gateway: `http://localhost:8080/api/wallet`)

**Authentication**: All endpoints require valid JWT (X-User-Id header injected by Gateway).

---

## 1. Get My Wallet Balance
Retrieves the credit balance for the authenticated user.

- **URL**: `/me`
- **Method**: `GET`
- **Auth Required**: Yes (requires `X-User-Id` header)

### Success Response
- **Code**: `200 OK`
- **Body**:
```json
{
  "code": "SUCCESS",
  "message": "SUCCESS",
  "data": {
    "userId": 123,
    "balance": 500
  },
  "timestamp": "2024-01-01T12:00:00Z"
}
```

### Error Responses
- `404 Not Found`: `WALLET_NOT_FOUND` (User's wallet does not exist).

---

## 2. Deduct Credit
Deducts a specified amount from a user's credit balance. Requires `ADMIN` role or internal call from trusted service.

- **URL**: `/{userId}/deduct`
- **Method**: `POST`
- **Auth Required**: Yes

### Path Parameters
- `userId` (long, required): Target user ID.

### Request Body
```json
{
  "amount": 100,
  "reason": "Purchase a new song",
  "referenceId": "ORDER-12345"
}
```
*Constraints*:
- `amount`: Must be a positive integer (min 1).
- `reason`: Short description of the transaction.
- `referenceId`: Optional, external reference for the transaction.

### Success Response
- **Code**: `200 OK`
- **Body**:
```json
{
  "code": "SUCCESS",
  "message": "Credit deducted",
  "data": {
    "userId": 123,
    "balance": 400
  },
  "timestamp": "2024-01-01T12:00:00Z"
}
```

### Error Responses
- `404 Not Found`: `WALLET_NOT_FOUND`.
- `400 Bad Request`: `WALLET_INVALID_AMOUNT` (amount <= 0) or `WALLET_INSUFFICIENT_CREDIT`.
- `409 Conflict`: `WALLET_LOCK_TIMEOUT` (Failed to acquire distributed lock).

---

## 3. Add Credit
Adds a specified amount to a user's credit balance. Requires `ADMIN` role or internal call from trusted service.

- **URL**: `/{userId}/add`
- **Method**: `POST`
- **Auth Required**: Yes

### Path Parameters
- `userId` (long, required): Target user ID.

### Request Body
```json
{
  "amount": 200,
  "reason": "Top-up from payment gateway",
  "referenceId": "PAYMENT-ABC"
}
```
*Constraints*:
- `amount`: Must be a positive integer (min 1).
- `reason`: Short description of the transaction.
- `referenceId`: Optional, external reference for the transaction.

### Success Response
- **Code**: `200 OK`
- **Body**:
```json
{
  "code": "SUCCESS",
  "message": "Credit added",
  "data": {
    "userId": 123,
    "balance": 600
  },
  "timestamp": "2024-01-01T12:00:00Z"
}
```

### Error Responses
- `400 Bad Request`: `WALLET_INVALID_AMOUNT`.
- `409 Conflict`: `WALLET_LOCK_TIMEOUT`.

---

## Common Error Codes

| Code | HTTP Status | Description |
|---|---|---|
| `WALLET_NOT_FOUND` | 404 | Wallet for the user does not exist |
| `WALLET_INSUFFICIENT_CREDIT` | 400 | User has less credit than requested deduction |
| `WALLET_LOCK_TIMEOUT` | 409 | Failed to acquire distributed lock for wallet transaction |
| `WALLET_INVALID_AMOUNT` | 400 | Amount for add/deduct must be positive |
