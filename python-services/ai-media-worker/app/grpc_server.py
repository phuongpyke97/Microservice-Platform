import grpc

from app.config import settings
from app.services.audio_separator import separate_audio
from app.services.chorus_detector import detect_chorus
from app.services.tts_service import stream_tts

try:
    from generated import ai_media_pb2, ai_media_pb2_grpc
except ImportError as exc:
    ai_media_pb2 = None
    ai_media_pb2_grpc = None
    _PROTO_IMPORT_ERROR = exc
else:
    _PROTO_IMPORT_ERROR = None


class AiMediaServicer(ai_media_pb2_grpc.AiMediaServiceServicer if ai_media_pb2_grpc else object):
    async def DetectChorus(self, request, context):
        result = detect_chorus(request.audio_data, request.sample_rate)
        return ai_media_pb2.DetectChorusResponse(
            start_time=result["start_time"],
            end_time=result["end_time"],
            confidence=result["confidence"],
        )

    async def SeparateAudio(self, request, context):
        result = separate_audio(request.audio_data, request.format or "wav")
        return ai_media_pb2.SeparateAudioResponse(
            vocals=result["vocals"],
            accompaniment=result["accompaniment"],
        )

    async def GenerateTts(self, request, context):
        try:
            async for chunk in stream_tts(request.text, request.voice, request.output_format):
                yield ai_media_pb2.TtsChunk(audio_data=chunk)
        except ValueError as exc:
            await context.abort(grpc.StatusCode.INVALID_ARGUMENT, str(exc))


async def serve_grpc(port: int = settings.grpc_port):
    if _PROTO_IMPORT_ERROR is not None:
        raise RuntimeError(
            "gRPC stubs missing. Run `python scripts/generate_protos.py` from ai-media-worker."
        ) from _PROTO_IMPORT_ERROR

    server = grpc.aio.server()
    ai_media_pb2_grpc.add_AiMediaServiceServicer_to_server(AiMediaServicer(), server)
    server.add_insecure_port(f"[::]:{port}")
    await server.start()
    return server
