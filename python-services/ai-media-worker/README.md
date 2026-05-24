# ai-media-worker

Python AI media worker exposing FastAPI HTTP endpoints and gRPC APIs.

## Run locally

```bash
pip install -r requirements.txt
python scripts/generate_protos.py
uvicorn main:app --host 0.0.0.0 --port 8765 --reload
```

## HTTP endpoints

- `GET /health`
- `POST /detect-chorus` with multipart `file`
- `POST /separate-audio` with multipart `file`
- `WS /ws/tts` with JSON `{ "text": "...", "voice": "vi-VN-HoaiMyNeural", "output_format": "audio-24khz-48kbitrate-mono-mp3" }`

## gRPC

Generate stubs first:

```bash
python scripts/generate_protos.py
```

Default HTTP port: `8765`. Default gRPC port: `50051`.
