# AI Media Worker API Documentation

## Overview
`ai-media-worker` is a Python-based microservice that handles heavy AI media processing tasks. It exposes both a REST API (via FastAPI) for synchronous requests and WebSocket streams, and a gRPC server for high-performance internal microservice communication.

---

## 1. REST API

**Base URL**: `http://localhost:8765` (Not exposed through API Gateway by default)

### 1.1. Health Check
- **URL**: `/health`
- **Method**: `GET`
- **Response**: `200 OK`
```json
{
  "status": "ok"
}
```

### 1.2. Detect Chorus (Highlight Extraction)
Analyzes an audio file to find the most repetitive/catchy segment (chorus).
- **URL**: `/detect-chorus`
- **Method**: `POST`
- **Content-Type**: `multipart/form-data`
- **Body**: `file` (audio file)
- **Response**: `200 OK`
```json
{
  "start_time": 15.5,
  "end_time": 30.5,
  "confidence": 0.85
}
```

### 1.3. Separate Audio (Vocal Removal)
Splits an audio file into vocals and accompaniment tracks using Spleeter.
- **URL**: `/separate-audio`
- **Method**: `POST`
- **Content-Type**: `multipart/form-data`
- **Body**: `file` (audio file)
- **Response**: `200 OK` (Base64-encoded audio bytes)
```json
{
  "vocals": "<base64_encoded_bytes>",
  "accompaniment": "<base64_encoded_bytes>"
}
```

### 1.4. Generate TTS (Text-to-Speech)
Generates spoken audio from text using Microsoft Edge TTS.
- **URL**: `/generate-tts`
- **Method**: `POST`
- **Content-Type**: `application/json`
- **Body**:
```json
{
  "text": "Xin chào",
  "voice": "vi-VN-HoaiMyNeural",
  "output_format": "audio-24khz-48kbitrate-mono-mp3"
}
```
- **Response**: `200 OK` with `Content-Type: audio/mpeg` (Binary MP3 stream).
- **Errors**: `400 Bad Request` if text is empty or voice is unsupported.

### 1.5. Generate TTS (WebSocket Stream)
Streams generated audio chunks back to the client in real-time.
- **URL**: `/ws/tts`
- **Protocol**: WebSocket
- **Client Input** (JSON message):
```json
{
  "text": "Xin chào",
  "voice": "vi-VN-HoaiMyNeural",
  "output_format": "audio-24khz-48kbitrate-mono-mp3"
}
```
- **Server Output**: Binary frames containing audio chunks. Connection is closed when generation is complete.
- **Errors**: Server sends JSON `{"error": "message"}` and closes with code 1008 on bad input.

---

## 2. gRPC API

**Port**: `50051` (Configurable via `AI_WORKER_GRPC_PORT`)
**Protobuf definition**: `ai_media.proto`

### Service: `AiMediaService`

#### 2.1. `DetectChorus`
```protobuf
rpc DetectChorus(DetectChorusRequest) returns (DetectChorusResponse);
```
- **Input**: `audio_data` (bytes), `sample_rate` (float)
- **Output**: `start_time` (float), `end_time` (float), `confidence` (float)

#### 2.2. `SeparateAudio`
```protobuf
rpc SeparateAudio(SeparateAudioRequest) returns (SeparateAudioResponse);
```
- **Input**: `audio_data` (bytes), `format` (string)
- **Output**: `vocals` (bytes), `accompaniment` (bytes)

#### 2.3. `GenerateTts`
```protobuf
rpc GenerateTts(TtsRequest) returns (stream TtsChunk);
```
- **Input**: `text` (string), `voice` (string), `output_format` (string)
- **Output**: Stream of `TtsChunk` containing `audio_data` (bytes)
- **Errors**: Returns `INVALID_ARGUMENT` gRPC status if input is invalid.

---

## 3. Supported TTS Voices
- `vi-VN-HoaiMyNeural` (Vietnamese, Female)
- `vi-VN-NamMinhNeural` (Vietnamese, Male)
- `my-MM-ThihaNeural` (Burmese, Male)
