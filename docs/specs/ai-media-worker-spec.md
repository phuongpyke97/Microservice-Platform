# AI Media Worker Specification

## 1. Overview
`ai-media-worker` is a specialized Python microservice designed to handle compute-intensive AI operations that are not well-suited for the JVM. It provides audio separation, highlight detection, and text-to-speech capabilities to the Java backend services.

## 2. Technical Stack
- **Runtime**: Python 3.11+
- **HTTP Server**: FastAPI + Uvicorn (Port 8765)
- **gRPC Server**: `grpcio.aio` (Port 50051)
- **Audio Processing**: `librosa`, `numpy`
- **Audio Separation**: `spleeter` (TensorFlow)
- **TTS Generation**: `edge-tts` (Microsoft Edge WebSocket API)

## 3. Configuration (Environment Variables)

| Variable | Default | Description |
|---|---|---|
| `AI_WORKER_HTTP_PORT` | `8765` | FastAPI HTTP port |
| `AI_WORKER_GRPC_PORT` | `50051` | gRPC server port |
| `EDGE_TTS_RATE` | `+0%` | TTS speech rate modifier |
| `EDGE_TTS_PITCH` | `+0Hz` | TTS pitch modifier |
| `SPLEETER_MODEL` | `spleeter:2stems` | Spleeter model type |
| `AI_WORKER_TMP_DIR` | `/tmp/ai-media-worker` | Temporary directory for Spleeter file processing |

## 4. Business Logic / AI Services

### 4.1. Text-to-Speech (`tts_service.py`)
- **Library**: Uses `edge-tts` to interact with Microsoft's Edge Read Aloud API.
- **Mechanism**: Async generator streaming audio chunks as they arrive from the WebSocket.
- **Validation**: Enforces strict voice validation against `SUPPORTED_VOICES`.
- **Output format**: Configurable, defaults to `audio-24khz-48kbitrate-mono-mp3`.

### 4.2. Audio Separation (`audio_separator.py`)
- **Library**: Uses Deezer's `spleeter` to isolate vocals and accompaniment.
- **Initialization**: Lazy initialization of the TensorFlow model (`_get_separator()`) to speed up worker startup.
- **Mechanism**:
  1. Creates a unique temporary directory (`uuid4()`).
  2. Writes the input byte array to a temporary file.
  3. Invokes `spleeter.separate_to_file()`.
  4. Reads the generated `vocals.wav` and `accompaniment.wav` back into memory.
  5. Cleans up the temporary directory in a `finally` block to prevent disk space leaks.

### 4.3. Chorus Detection (`chorus_detector.py`)
- **Purpose**: Finds the "hook" or most repetitive part of a song to use as a ringtone.
- **Library**: `librosa`, `numpy`.
- **Mechanism**:
  1. Converts audio to a Mel-spectrogram.
  2. Calculates a Self-Similarity Matrix (SSM) using cosine distance between L2-normalized time frames.
  3. Searches for the maximum mean similarity along the diagonals (representing repeating segments).
  4. Defines minimum chorus length (up to 15s depending on total audio length).
  5. Returns the start time, end time, and a confidence score.
  6. **Fallback**: If the audio is too short to find a chorus, it returns the entire clip.

## 5. Architecture & Threading
- **Asyncio**: The worker is heavily optimized for `asyncio`. gRPC and FastAPI run within the same event loop.
- **Lifespan**: The FastAPI `lifespan` context manager is used to start and gracefully shut down the background gRPC server.
- **CPU Bound tasks**: Spleeter and Librosa operations are CPU bound. In high-concurrency environments, they should ideally be offloaded to `ProcessPoolExecutor`, though currently they execute synchronously within the async endpoints.

## 6. Dependencies
- No database connections.
- Stateless design. Can be scaled horizontally without state coordination.
