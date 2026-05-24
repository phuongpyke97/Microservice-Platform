# Audio Generation Service Specification

## 1. Overview
`audio-generation-service` is a business service responsible for handling user-initiated audio generation requests. It uses an asynchronous job-based model to manage long-running AI tasks and integrates with the `ai-media-worker` (Python) for heavy processing.

## 2. Technical Stack
- **Framework**: Spring Boot 3.2.x, Java 21
- **Database**: PostgreSQL 16
- **Cache**: Redis (Rate limiting and progress tracking)
- **Async Execution**: Spring `@Async` with dedicated `TaskExecutor`
- **Messaging**: RabbitMQ (Publishing completion events)
- **Communication**: Feign Client / gRPC (to Python worker)
- **Port**: 8092

## 3. Database Schema

### `audio_jobs` table
| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGSERIAL | PRIMARY KEY | Job ID |
| `user_id` | BIGINT | NOT NULL | Owner user ID |
| `prompt` | VARCHAR(500) | NOT NULL | Generation prompt (e.g., TTS text) |
| `voice_id` | VARCHAR(50) | | Target voice identifier |
| `status` | VARCHAR(20) | NOT NULL | PENDING, PROCESSING, COMPLETED, FAILED |
| `result_url` | VARCHAR(500) | | URL to the generated file in MinIO |
| `error_message` | VARCHAR(500) | | Error description if status is FAILED |
| `created_at` | TIMESTAMP | NOT NULL | Creation timestamp |
| `updated_at` | TIMESTAMP | | Last update timestamp |

## 4. Concurrency Control (Redis)
To prevent system overload and abuse, the service implements a per-user limit on concurrent active jobs using Redis.

- **Limit**: Max 5 active jobs per user.
- **Implementation**: `INCR` on `audio_gen:active_jobs:user:{userId}` before job submission. If > 5, the request is rejected.
- **Cleanup**: The counter is decremented when a job reaches a terminal state (`COMPLETED` or `FAILED`).

## 5. Job Lifecycle & Async Flow

### 5.1. Submission Phase
1. User calls `/audio-jobs` POST.
2. Service increments active job count in Redis.
3. Service persists `AudioJob` with status `PENDING`.
4. Service triggers `@Async` method `processJobAsync` and returns `202 Accepted` immediately.

### 5.2. Processing Phase (Async)
1. Thread pool picks up the job.
2. Status updated to `PROCESSING`.
3. Progress message stored in Redis: `audio_gen:job_progress:{jobId}`.
4. Service calls `AiMediaWorkerClient.generateTts(...)`.
5. AI worker (Python) generates audio and returns binary data.
6. Service uploads binary data to MinIO (via internal flow or `file-service`).
7. Resulting MinIO URL is saved to the job.
8. Status updated to `COMPLETED`.
9. `AudioGeneratedEvent` is published to RabbitMQ.
10. Redis active job counter is decremented.

### 5.3. Error Handling
- If any step in the async phase fails, status is set to `FAILED`.
- Error message is captured for user visibility.
- Redis active job counter is decremented to allow new submissions.

## 6. Event Publishing (RabbitMQ)
Events are published to `RmqExchanges.AUDIO_EVENTS`.

| Event Type | Routing Key | Payload Structure |
|---|---|---|
| **Audio Generated** | `audio.generated` | `AudioGeneratedEvent(userId, jobId, resultUrl, status, timestamp)` |

## 7. Performance Considerations
- Uses a separate `TaskExecutor` for `@Async` to avoid blocking Spring Boot's main thread pool.
- Redis progress tracking allows users to see real-time updates without heavy DB polling.
- Large binary data is handled via streams where possible.

## 8. Dependencies
- **ai-media-worker**: Required for TTS and audio processing.
- **file-service/MinIO**: Required for permanent storage of generated assets.
- **RabbitMQ**: Required for notifying downstream services (e.g., notification-service).
- **Redis Cluster**: Required for distributed counters and progress caching.