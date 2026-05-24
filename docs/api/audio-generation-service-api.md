# Audio Generation Service API Documentation

## Overview
`audio-generation-service` handles asynchronous AI audio generation jobs. It currently supports Text-to-Speech (TTS) via integration with the Python AI Media Worker.

**Base URL**: `/audio-jobs` (Routed through API Gateway: `http://localhost:8080/audio-jobs`)

**Authentication**: All endpoints require a valid JWT (`X-User-Id` header injected by Gateway).

---

## 1. Submit Audio Generation Job
Submits a new asynchronous job to generate audio. Users are limited to a maximum of 5 active (pending/processing) jobs at any time.

- **URL**: `/`
- **Method**: `POST`
- **Auth Required**: Yes

### Request Body
```json
{
  "prompt": "Xin chào, đây là bản tin sáng nay.",
  "voiceId": "vi-VN-HoaiMyNeural"
}
```
*Constraints*:
- `prompt`: Required, max 500 characters. Text to synthesize.
- `voiceId`: Optional. Target voice ID. Defaults to `vi-VN-HoaiMyNeural` if not provided.

### Success Response
- **Code**: `202 Accepted`
- **Body**:
```json
{
  "code": "SUCCESS",
  "message": "SUCCESS",
  "data": {
    "id": 101,
    "prompt": "Xin chào, đây là bản tin sáng nay.",
    "voiceId": "vi-VN-HoaiMyNeural",
    "status": "PENDING",
    "resultUrl": null,
    "errorMessage": null,
    "createdAt": "2024-01-01T12:00:00Z"
  },
  "timestamp": "2024-01-01T12:00:00Z"
}
```

### Error Responses
- `400 Bad Request`: Validation error or Max active jobs limit reached.

---

## 2. List User Jobs
Retrieves all audio generation jobs submitted by the current user, ordered by creation time (descending).

- **URL**: `/`
- **Method**: `GET`
- **Auth Required**: Yes

### Success Response
- **Code**: `200 OK`
- **Body**: Standard `ApiResponse` containing a list of `AudioJobResponse` objects.

---

## 3. Get Job Details
Retrieves the current status and result of a specific job.

- **URL**: `/{jobId}`
- **Method**: `GET`
- **Auth Required**: Yes

### Path Parameters
- `jobId` (long, required): The ID of the job to retrieve.

### Success Response
- **Code**: `200 OK`
- **Body**:
```json
{
  "code": "SUCCESS",
  "message": "SUCCESS",
  "data": {
    "id": 101,
    "prompt": "Xin chào, đây là bản tin sáng nay.",
    "voiceId": "vi-VN-HoaiMyNeural",
    "status": "COMPLETED",
    "resultUrl": "minio://audio-bucket/101.mp3",
    "errorMessage": null,
    "createdAt": "2024-01-01T12:00:00Z"
  },
  "timestamp": "2024-01-01T12:00:05Z"
}
```

### Error Responses
- `404 Not Found`: `COMMON_NOT_FOUND` (Job does not exist).
- `403 Forbidden`: `COMMON_FORBIDDEN` (Job belongs to another user).

---

## Job Status Lifecycle
- `PENDING`: Job is queued and waiting for an available worker thread.
- `PROCESSING`: Job is actively being processed by the AI worker.
- `COMPLETED`: Audio generation succeeded, `resultUrl` is populated.
- `FAILED`: Audio generation failed, `errorMessage` contains the reason.