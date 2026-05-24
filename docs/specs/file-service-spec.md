# File Service Specification

## 1. Overview
`file-service` is an infrastructure service responsible for managing file uploads, storage, and metadata. It abstracts MinIO (S3-compatible object storage) and provides a unified API for other services to handle media assets (audio, images).

## 2. Technical Stack
- **Framework**: Spring Boot 3.2.x, Java 21
- **Storage**: MinIO (S3 API compatible)
- **Database**: PostgreSQL 16 (dedicated `file_db`)
- **Migration**: Flyway
- **Port**: 8083

## 3. Database Schema

### `file_metadata` table
| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGSERIAL | PRIMARY KEY | Metadata record ID |
| `user_id` | BIGINT | NOT NULL | Owner user ID |
| `original_name` | VARCHAR(255) | NOT NULL | Original filename |
| `stored_key` | VARCHAR(255) | NOT NULL, UNIQUE | MinIO object key (UUID-prefixed) |
| `bucket` | VARCHAR(64) | NOT NULL | Current MinIO bucket |
| `content_type` | VARCHAR(100) | NOT NULL | MIME type |
| `size_bytes` | BIGINT | NOT NULL | File size in bytes |
| `status` | VARCHAR(20) | NOT NULL | UPLOADED, CONFIRMED, DELETED |
| `created_at` | TIMESTAMP | NOT NULL | Upload timestamp |
| `updated_at` | TIMESTAMP | | Last update |

## 4. MinIO Bucket Strategy

| Bucket | Purpose | TTL | Notes |
|---|---|---|---|
| `temp` | Initial upload landing | Auto-purge after 24h (suggested) | All new uploads land here |
| `audio` | Confirmed audio files | Persistent | Music, voice, sound effects |
| `image` | Confirmed image files | Persistent | Avatars, thumbnails |

## 5. File Status State Machine
```
[Upload] -----> UPLOADED ----confirm()----> CONFIRMED
                  |                            |
                  +----softDelete()--+         |
                                     v         v
                                  DELETED <-- softDelete()
```

## 6. Business Logic Flows

### 6.1. Direct Upload Flow
1. Client sends multipart/form-data file to `/upload`.
2. Validate: size <= 5MB, content type in allowed list.
3. Generate unique object key: `{UUID}-{originalName}`.
4. Stream upload to MinIO `temp` bucket.
5. Persist `FileMetadata` with status `UPLOADED`.
6. Return metadata to client.

### 6.2. Presigned Upload Flow
1. Client requests presigned URL via `/presigned/upload`.
2. Validate content type.
3. Generate object key.
4. Generate presigned PUT URL (TTL: 5 min) for `temp` bucket.
5. Client uploads directly to MinIO using the URL.
6. Client (or business service) calls `/confirm` afterwards (typically with explicit metadata persistence).

> **Note**: The current implementation does NOT auto-persist metadata for presigned uploads. Business services that use this flow must explicitly call confirm after upload.

### 6.3. Confirm File Flow
1. Validate: file exists, status is `UPLOADED`, source bucket is `temp`, target bucket is valid (`audio`/`image`).
2. Copy object from `temp` to target bucket in MinIO.
3. Remove object from `temp` bucket.
4. Update metadata: bucket = target, status = `CONFIRMED`.

### 6.4. Soft Delete Flow
1. Locate file metadata by ID.
2. If not already `DELETED`, set status = `DELETED`.
3. **Do NOT** remove the object from MinIO (intentional — for audit / recovery).
4. Background job (out of scope) may purge later.

## 7. Constraints
- **Max File Size**: 5 MB
- **Allowed Content Types**: `image/jpeg`, `image/png`, `audio/mpeg`, `audio/wav`, `audio/ogg`
- **Presigned URL TTL**: 300 seconds (5 minutes)
- **Object Key Format**: `{UUID}-{originalName}`

## 8. Security Considerations
- All endpoints require JWT (validated by Gateway).
- `userId` extracted from `X-User-Id` header injected by Gateway.
- Direct download via MinIO is gated by presigned URL signatures (short TTL).
- No ACL on file-service level — any authenticated user can download any file by ID. Recommend tightening to owner-only or role-based in future.

## 9. Configuration Properties (application.yml via config-server)
```yaml
minio:
  endpoint: http://minio:9000
  access-key: ${MINIO_ACCESS_KEY}
  secret-key: ${MINIO_SECRET_KEY}
  bucket-temp: temp
  bucket-audio: audio
  bucket-image: image
```