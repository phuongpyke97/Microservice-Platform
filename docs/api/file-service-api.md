# File Service API Documentation

## Overview
`file-service` manages file uploads, downloads, and metadata using MinIO (S3-compatible storage). It supports direct multipart uploads and presigned URL flows.

**Base URL**: `/api/files` (Routed through API Gateway: `http://localhost:8080/api/files`)

**Authentication**: All endpoints require valid JWT (X-User-Id header injected by Gateway).

---

## 1. Upload File (Direct Multipart)
Uploads a file directly to the temp bucket via multipart/form-data.

- **URL**: `/upload`
- **Method**: `POST`
- **Auth Required**: Yes
- **Content-Type**: `multipart/form-data`

### Request
- **Form Field**: `file` (MultipartFile)

### Success Response
- **Code**: `200 OK`
- **Body**:
```json
{
  "code": "SUCCESS",
  "message": "File uploaded",
  "data": {
    "id": 1,
    "userId": 123,
    "originalName": "song.mp3",
    "storedKey": "uuid-song.mp3",
    "bucket": "temp",
    "contentType": "audio/mpeg",
    "sizeBytes": 2048000,
    "status": "UPLOADED"
  },
  "timestamp": "2024-01-01T12:00:00Z"
}
```

### Error Responses
- `400 Bad Request`: `FILE_TOO_LARGE` (>5MB) or `FILE_TYPE_NOT_ALLOWED`.
- `500 Internal Server Error`: `FILE_UPLOAD_FAILED` (MinIO error).

---

## 2. Get Presigned Upload URL
Returns a presigned PUT URL for client-side direct upload to MinIO.

- **URL**: `/presigned/upload`
- **Method**: `GET`
- **Auth Required**: No (but recommended to track via userId if needed)

### Query Parameters
- `originalName` (string, required): Original filename.
- `contentType` (string, required): MIME type (e.g., `audio/mpeg`).

### Success Response
- **Code**: `200 OK`
- **Body**:
```json
{
  "code": "SUCCESS",
  "message": "SUCCESS",
  "data": {
    "objectKey": "uuid-song.mp3",
    "url": "http://minio:9000/temp/uuid-song.mp3?X-Amz-...",
    "expiresInSeconds": 300
  },
  "timestamp": "2024-01-01T12:00:00Z"
}
```

### Error Responses
- `400 Bad Request`: `FILE_TYPE_NOT_ALLOWED`.

---

## 3. Get Presigned Download URL
Returns a presigned GET URL for downloading a file.

- **URL**: `/{fileId}/presigned/download`
- **Method**: `GET`
- **Auth Required**: Yes

### Path Parameters
- `fileId` (long, required): File metadata ID.

### Success Response
- **Code**: `200 OK`
- **Body**:
```json
{
  "code": "SUCCESS",
  "message": "SUCCESS",
  "data": {
    "objectKey": "uuid-song.mp3",
    "url": "http://minio:9000/audio/uuid-song.mp3?X-Amz-...",
    "expiresInSeconds": 300
  },
  "timestamp": "2024-01-01T12:00:00Z"
}
```

### Error Responses
- `404 Not Found`: `FILE_NOT_FOUND`.

---

## 4. Confirm File
Moves a file from the temp bucket to a target bucket (audio/image) and marks it as CONFIRMED.

- **URL**: `/{fileId}/confirm`
- **Method**: `POST`
- **Auth Required**: Yes

### Path Parameters
- `fileId` (long, required): File metadata ID.

### Request Body
```json
{
  "targetBucket": "audio"
}
```
*Valid values*: `audio`, `image`.

### Success Response
- **Code**: `200 OK`
- **Body**:
```json
{
  "code": "SUCCESS",
  "message": "File confirmed",
  "data": {
    "id": 1,
    "userId": 123,
    "originalName": "song.mp3",
    "storedKey": "uuid-song.mp3",
    "bucket": "audio",
    "contentType": "audio/mpeg",
    "sizeBytes": 2048000,
    "status": "CONFIRMED"
  },
  "timestamp": "2024-01-01T12:00:00Z"
}
```

### Error Responses
- `404 Not Found`: `FILE_NOT_FOUND`.
- `409 Conflict`: `FILE_NOT_IN_TEMP` (File not in UPLOADED state).
- `410 Gone`: `FILE_ALREADY_DELETED`.
- `400 Bad Request`: `FILE_INVALID_TARGET_BUCKET`.

---

## 5. Delete File (Soft Delete)
Marks a file as DELETED (does not remove from MinIO).

- **URL**: `/{fileId}`
- **Method**: `DELETE`
- **Auth Required**: Yes

### Path Parameters
- `fileId` (long, required): File metadata ID.

### Success Response
- **Code**: `200 OK`
- **Body**:
```json
{
  "code": "SUCCESS",
  "message": "File deleted",
  "data": {
    "id": 1,
    "userId": 123,
    "originalName": "song.mp3",
    "storedKey": "uuid-song.mp3",
    "bucket": "audio",
    "contentType": "audio/mpeg",
    "sizeBytes": 2048000,
    "status": "DELETED"
  },
  "timestamp": "2024-01-01T12:00:00Z"
}
```

### Error Responses
- `404 Not Found`: `FILE_NOT_FOUND`.
- `410 Gone`: `FILE_ALREADY_DELETED`.

---

## Common Error Codes

| Code | HTTP Status | Description |
|---|---|---|
| `FILE_NOT_FOUND` | 404 | File metadata does not exist |
| `FILE_TOO_LARGE` | 400 | File exceeds 5MB limit |
| `FILE_TYPE_NOT_ALLOWED` | 400 | Content type not in allowed list |
| `FILE_UPLOAD_FAILED` | 500 | MinIO operation failed |
| `FILE_ALREADY_DELETED` | 410 | File is already marked as DELETED |
| `FILE_NOT_IN_TEMP` | 409 | File must be UPLOADED to confirm |
| `FILE_INVALID_TARGET_BUCKET` | 400 | Target bucket must be `audio` or `image` |

---

## File Constraints
- **Max Size**: 5 MB
- **Allowed Types**: `image/jpeg`, `image/png`, `audio/mpeg`, `audio/wav`, `audio/ogg`
- **Presigned URL TTL**: 300 seconds (5 minutes)