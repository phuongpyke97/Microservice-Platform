# AI Media Worker Operations Manual

## 1. Prerequisites
- Python 3.11 or higher.
- `pip` package manager.
- System dependencies for `librosa` and `spleeter` (e.g., `ffmpeg`, `libsndfile1`).

## 2. Installation & Setup

1. **Navigate to the service directory:**
   ```bash
   cd python-services/ai-media-worker
   ```

2. **Create and activate a virtual environment (Recommended):**
   ```bash
   python -m venv venv
   source venv/bin/activate  # On Windows: venv\Scripts\activate
   ```

3. **Install dependencies:**
   ```bash
   pip install -r requirements.txt
   ```

4. **Generate gRPC Protos:**
   If you modify `ai_media.proto`, or on first setup, generate the Python stubs:
   ```bash
   python scripts/generate_protos.py
   ```

## 3. Running the Service

### 3.1. Local Development
Run via Uvicorn with hot-reload enabled:
```bash
uvicorn main:app --host 0.0.0.0 --port 8765 --reload
```
*Note: The gRPC server will automatically start alongside the FastAPI app.*

### 3.2. Production Deployment
Run using Gunicorn as a process manager with Uvicorn workers:
```bash
gunicorn main:app -w 4 -k uvicorn.workers.UvicornWorker -b 0.0.0.0:8765
```

## 4. Configuration
Configure via environment variables or a `.env` file in the service root.

| Variable | Description | Default |
|---|---|---|
| `AI_WORKER_HTTP_PORT` | HTTP Port (FastAPI) | `8765` |
| `AI_WORKER_GRPC_PORT` | gRPC Port | `50051` |
| `SPLEETER_MODEL` | Spleeter stem model (`spleeter:2stems`, `4stems`, `5stems`) | `spleeter:2stems` |
| `AI_WORKER_TMP_DIR` | Temp dir for audio processing | `/tmp/ai-media-worker` |

## 5. Health Checks & Monitoring

### HTTP Health Check
```bash
curl http://localhost:8765/health
# Expected: {"status": "ok"}
```

### Logging
- Uvicorn and gRPC logs are written to `stdout`/`stderr`.
- In a containerized environment (e.g., Docker Compose), logs are collected by Promtail and sent to Grafana Loki.

## 6. Troubleshooting

**Issue**: Spleeter crashes or returns empty audio.
- **Cause**: Missing `ffmpeg` or permission issues in `AI_WORKER_TMP_DIR`.
- **Fix**: Ensure `ffmpeg` is installed and in the system PATH. Verify write permissions for the temp directory.

**Issue**: gRPC `ImportError` or `ModuleNotFoundError`.
- **Cause**: Protobuf stubs not generated.
- **Fix**: Run `python scripts/generate_protos.py`.

**Issue**: TTS generation fails with edge-tts errors.
- **Cause**: Network connectivity to Microsoft Edge APIs is blocked.
- **Fix**: Verify outbound internet access on port 443.
