# File Service UAT Test Cases

## 1. Direct Upload Flow
**Goal**: Verify that authenticated users can upload files directly via multipart/form-data.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 1.1 | Upload a valid 2MB MP3 file via POST `/api/files/upload`. | Returns 200 OK, metadata with status `UPLOADED`, bucket `temp`. | |
| 1.2 | Upload a 6MB file (exceeds limit). | Returns 400 Bad Request, code `FILE_TOO_LARGE`. | |
| 1.3 | Upload a `.exe` file (invalid type). | Returns 400 Bad Request, code `FILE_TYPE_NOT_ALLOWED`. | |
| 1.4 | Check MinIO `temp` bucket. | File exists with the returned `storedKey`. | |
| 1.5 | Check DB `file_metadata` table. | Record exists with correct `userId`, `status=UPLOADED`. | |

## 2. Presigned Upload URL Flow
**Goal**: Verify that clients can obtain presigned URLs for direct MinIO uploads.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 2.1 | GET `/api/files/presigned/upload?originalName=song.mp3&contentType=audio/mpeg`. | Returns 200 OK, presigned PUT URL with 300s TTL. | |
| 2.2 | Use the presigned URL to PUT a file directly to MinIO. | MinIO accepts the upload (200 OK). | |
| 2.3 | Request presigned URL with invalid content type (e.g., `application/exe`). | Returns 400 Bad Request, code `FILE_TYPE_NOT_ALLOWED`. | |

## 3. Confirm File Flow
**Goal**: Verify that files can be moved from temp to target buckets.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 3.1 | Upload a file, then POST `/api/files/{fileId}/confirm` with `targetBucket=audio`. | Returns 200 OK, metadata shows `bucket=audio`, `status=CONFIRMED`. | |
| 3.2 | Check MinIO `audio` bucket. | File exists in `audio` bucket. | |
| 3.3 | Check MinIO `temp` bucket. | File removed from `temp` bucket. | |
| 3.4 | Try to confirm a file that is already `CONFIRMED`. | Returns 409 Conflict, code `FILE_NOT_IN_TEMP`. | |
| 3.5 | Try to confirm with invalid target bucket (e.g., `invalid-bucket`). | Returns 400 Bad Request, code `FILE_INVALID_TARGET_BUCKET`. | |

## 4. Presigned Download URL Flow
**Goal**: Verify that authenticated users can obtain download URLs.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 4.1 | GET `/api/files/{fileId}/presigned/download` for a confirmed file. | Returns 200 OK, presigned GET URL with 300s TTL. | |
| 4.2 | Use the presigned URL to download the file. | File downloads successfully from MinIO. | |
| 4.3 | Request download URL for a non-existent file ID. | Returns 404 Not Found, code `FILE_NOT_FOUND`. | |

## 5. Soft Delete Flow
**Goal**: Verify that files can be marked as deleted without physical removal.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 5.1 | DELETE `/api/files/{fileId}` for a confirmed file. | Returns 200 OK, metadata shows `status=DELETED`. | |
| 5.2 | Check MinIO bucket. | File still exists (soft delete does not remove from storage). | |
| 5.3 | Try to delete the same file again. | Returns 410 Gone, code `FILE_ALREADY_DELETED`. | |
| 5.4 | Try to confirm a deleted file. | Returns 410 Gone, code `FILE_ALREADY_DELETED`. | |

## 6. End-to-End Scenario
**Goal**: Verify complete upload-confirm-download-delete lifecycle.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 6.1 | User uploads `avatar.png` via `/upload`. | File lands in `temp`, status `UPLOADED`. | |
| 6.2 | User confirms file to `image` bucket. | File moves to `image`, status `CONFIRMED`. | |
| 6.3 | User requests download URL. | Presigned URL returned, file downloads successfully. | |
| 6.4 | User deletes the file. | Status changes to `DELETED`, file remains in MinIO. | |