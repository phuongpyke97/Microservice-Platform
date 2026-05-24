# Audio Generation Service UAT Test Cases

## 1. Job Submission
**Goal**: Verify that authenticated users can submit valid generation jobs.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 1.1 | POST `/audio-jobs` with valid prompt and voiceId. | Returns 202 Accepted, response with `status: PENDING`. | |
| 1.2 | Check DB. | New `audio_jobs` record created. | |
| 1.3 | Check Redis `audio_gen:active_jobs:user:{userId}`. | Counter incremented to 1. | |
| 1.4 | POST with empty prompt. | Returns 400 Bad Request. | |
| 1.5 | POST with prompt > 500 chars. | Returns 400 Bad Request. | |

## 2. Concurrency Limit
**Goal**: Verify the per-user 5-active-job limit.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 2.1 | Submit 5 jobs in rapid succession. | All 5 return 202 Accepted. | |
| 2.2 | Submit a 6th job while previous 5 are pending. | Returns 400 Bad Request with "Max 5 active jobs". | |
| 2.3 | Wait for jobs to complete; submit a new one. | Returns 202 Accepted. | |

## 3. Async Job Processing
**Goal**: Verify jobs transition through statuses correctly.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 3.1 | Submit a valid job. | Status starts as `PENDING`. | |
| 3.2 | Poll GET `/audio-jobs/{jobId}` periodically. | Status changes from `PENDING` → `PROCESSING` → `COMPLETED`. | |
| 3.3 | Verify final state. | `resultUrl` is populated, `errorMessage` is null. | |
| 3.4 | Check RabbitMQ. | `AudioGeneratedEvent` published. | |

## 4. Failure Handling
**Goal**: Verify graceful handling of AI worker failures.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 4.1 | Stop ai-media-worker; submit a job. | Status becomes `PROCESSING` then `FAILED`. | |
| 4.2 | Check job details. | `errorMessage` contains useful error info. | |
| 4.3 | Check Redis counter. | Decremented properly so user can submit more jobs. | |

## 5. Job Listing & Retrieval
**Goal**: Verify users can manage their job history.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 5.1 | GET `/audio-jobs`. | Returns 200 OK with user's jobs sorted by `createdAt` desc. | |
| 5.2 | GET `/audio-jobs/{jobId}` for own job. | Returns 200 OK with job details. | |
| 5.3 | GET `/audio-jobs/{jobId}` for another user's job. | Returns 403 Forbidden, code `COMMON_FORBIDDEN`. | |
| 5.4 | GET `/audio-jobs/{jobId}` for non-existent job. | Returns 404 Not Found, code `COMMON_NOT_FOUND`. | |

## 6. End-to-End Scenario
**Goal**: Verify complete TTS workflow.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 6.1 | User submits prompt "Hello world" with voiceId. | Job created (PENDING). | |
| 6.2 | Async worker picks up the job. | Status `PROCESSING`. | |
| 6.3 | AI worker returns audio bytes; service uploads to MinIO. | Status `COMPLETED`, `resultUrl` populated. | |
| 6.4 | User can fetch the audio file from `resultUrl`. | File downloads successfully. | |