import base64

from fastapi import APIRouter, HTTPException, UploadFile, WebSocket, WebSocketDisconnect, Response
from fastapi.responses import JSONResponse

from app.services.audio_separator import separate_audio
from app.services.chorus_detector import detect_chorus
from app.services.tts_service import SUPPORTED_VOICES, stream_tts
from app.services.audio_mixer import mix_audio_tracks

router = APIRouter()


@router.get("/health")
async def health():
    return {"status": "ok"}


@router.post("/detect-chorus")
async def detect_chorus_endpoint(file: UploadFile):
    audio_data = await file.read()
    result = detect_chorus(audio_data)
    return result


@router.post("/separate-audio")
async def separate_audio_endpoint(file: UploadFile, exclude_audio: bool = False):
    audio_data = await file.read()
    fmt = (file.filename or "input.wav").rsplit(".", 1)[-1].lower()
    result = separate_audio(audio_data, fmt=fmt)
    response = {
        "has_vocal": result["has_vocal"],
        "vocal_rms": result["vocal_rms"],
    }
    if not exclude_audio:
        response["vocals"] = base64.b64encode(result["vocals"]).decode()
        response["accompaniment"] = base64.b64encode(result["accompaniment"]).decode()
    return response


@router.post("/mix-audio")
async def mix_audio_endpoint(vocal: UploadFile, accompaniment: UploadFile, mode: str = "v2"):
    vocal_bytes = await vocal.read()
    accompaniment_bytes = await accompaniment.read()
    if mode not in ["v1", "v2", "v3"]:
        raise HTTPException(status_code=400, detail="Invalid mode, choose from v1, v2, v3")
    try:
        mixed_bytes = mix_audio_tracks(vocal_bytes, accompaniment_bytes, mode)
        return Response(content=mixed_bytes, media_type="audio/mpeg")
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc))


@router.post("/audio-metadata")
async def audio_metadata_endpoint(file: UploadFile):
    import io
    import librosa
    try:
        audio_data = await file.read()
        y, sr = librosa.load(io.BytesIO(audio_data), sr=None)
        duration = float(y.shape[0] / sr)
        return {"duration": duration}
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Invalid audio file: {str(e)}")


@router.post("/generate-tts")
async def generate_tts_endpoint(payload: dict):
    text = payload.get("text", "")
    voice = payload.get("voice", "")
    output_format = payload.get("output_format", "audio-24khz-48kbitrate-mono-mp3")
    if not text:
        raise HTTPException(status_code=400, detail="text is required")
    if voice not in SUPPORTED_VOICES:
        raise HTTPException(status_code=400, detail=f"Unsupported voice: {voice}")
    chunks = []
    async for chunk in stream_tts(text, voice, output_format):
        chunks.append(chunk)
    return Response(content=b"".join(chunks), media_type="audio/mpeg")


@router.websocket("/ws/tts")
async def tts_ws(websocket: WebSocket):
    await websocket.accept()
    try:
        payload = await websocket.receive_json()
        text = payload.get("text", "")
        voice = payload.get("voice", "")
        output_format = payload.get("output_format", "audio-24khz-48kbitrate-mono-mp3")

        if not text:
            await websocket.send_json({"error": "text is required"})
            await websocket.close(code=1008)
            return

        if voice not in SUPPORTED_VOICES:
            await websocket.send_json({"error": f"Unsupported voice: {voice}"})
            await websocket.close(code=1008)
            return

        async for chunk in stream_tts(text, voice, output_format):
            await websocket.send_bytes(chunk)

        await websocket.close()
    except WebSocketDisconnect:
        pass
    except ValueError as exc:
        try:
            await websocket.send_json({"error": str(exc)})
            await websocket.close(code=1008)
        except Exception:
            pass
