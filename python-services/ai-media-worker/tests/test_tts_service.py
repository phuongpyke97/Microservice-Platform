import pytest

from app.services import tts_service


class FakeCommunicate:
    def __init__(self, text, voice, rate, pitch):
        self.text = text
        self.voice = voice
        self.rate = rate
        self.pitch = pitch

    async def stream(self):
        yield {"type": "WordBoundary", "offset": 0}
        yield {"type": "audio", "data": b"chunk-1"}
        yield {"type": "audio", "data": b"chunk-2"}


@pytest.mark.asyncio
async def test_stream_tts_yields_only_audio_chunks(monkeypatch):
    monkeypatch.setattr(tts_service.edge_tts, "Communicate", FakeCommunicate)

    chunks = []
    async for chunk in tts_service.stream_tts("hello", "vi-VN-HoaiMyNeural"):
        chunks.append(chunk)

    assert chunks == [b"chunk-1", b"chunk-2"]


@pytest.mark.asyncio
async def test_stream_tts_rejects_unknown_voice():
    with pytest.raises(ValueError, match="Unsupported voice"):
        chunks = []
        async for chunk in tts_service.stream_tts("hello", "en-US-JennyNeural"):
            chunks.append(chunk)
