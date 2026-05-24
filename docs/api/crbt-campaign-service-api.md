# CRBT Campaign Service API Documentation

## Overview
`crbt-campaign-service` handles marketing campaigns, packages, and user subscriptions. It also integrates with AI services (Lyria) for music generation.

**Base URL**: `/campaigns` (Routed through API Gateway: `http://localhost:8080/campaigns`)

**Authentication**: Required for user-specific actions (e.g., subscription).

---

## 1. Create Campaign
Creates a new campaign with multiple packages. Typically an admin action.

- **URL**: `/`
- **Method**: `POST`
- **Auth Required**: No (Admin-level check currently simplified)

### Request Body
```json
{
  "name": "Summer CRBT 2024",
  "description": "Hot summer music campaign",
  "startAt": "2024-06-01T00:00:00Z",
  "endAt": "2024-08-31T23:59:59Z",
  "packages": [
    {
      "name": "Standard Package",
      "price": 5000,
      "creditAmount": 5,
      "validityDays": 30
    },
    {
      "name": "Premium Package",
      "price": 10000,
      "creditAmount": 12,
      "validityDays": 30
    }
  ]
}
```

### Success Response
- **Code**: `200 OK`
- **Body**: Standard `ApiResponse` containing the created campaign metadata and IDs.

---

## 2. Get Active Campaigns
Retrieves currently active campaigns based on current time.

- **URL**: `/active`
- **Method**: `GET`
- **Auth Required**: No

### Success Response
- **Code**: `200 OK`
- **Body**: List of active campaigns.

---

## 3. Subscribe to Package
Subscribes the current user to a specific campaign package.

- **URL**: `/subscribe`
- **Method**: `POST`
- **Auth Required**: Yes (`X-User-Id` required)

### Request Body
```json
{
  "packageId": 1
}
```

### Success Response
- **Code**: `200 OK`
- **Body**:
```json
{
  "code": "SUCCESS",
  "message": "SUCCESS",
  "data": null,
  "timestamp": "2024-01-01T12:00:00Z"
}
```

### Error Responses
- `404 Not Found`: `CAMPAIGN_PACKAGE_NOT_FOUND`.
- `404 Not Found`: `CAMPAIGN_NOT_FOUND` (Campaign ended or inactive).
- `401 Unauthorized`: If user context is missing.

---

## 4. Generate AI Music (Lyria)
Generates music via Google Lyria 3 integration.

- **URL**: `/generate`
- **Method**: `POST`
- **Auth Required**: No

### Query Parameters
- `genre` (string, required): e.g., "Pop", "Lo-fi".
- `mood` (string, required): e.g., "Happy", "Sad".
- `instrument` (string, required): e.g., "Piano", "Guitar".

### Success Response
- **Code**: `200 OK`
- **Body**: Byte array (binary) containing the generated audio data.

---

## Error Codes

| Code | HTTP Status | Description |
|---|---|---|
| `CAMPAIGN_NOT_FOUND` | 404 | Campaign is inactive or does not exist |
| `CAMPAIGN_PACKAGE_NOT_FOUND` | 404 | Target package does not exist |
| `CAMPAIGN_SUBSCRIPTION_NOT_FOUND` | 404 | User subscription not found |
