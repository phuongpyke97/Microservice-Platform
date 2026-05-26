from typing import AsyncIterator

import edge_tts

from app.config import settings

SUPPORTED_VOICES = [
    "vi-VN-HoaiMyNeural",
    "vi-VN-NamMinhNeural",
    "my-MM-ThihaNeural",
    "my-MM-NilarNeural",
    "en-US-JennyNeural",
    "en-US-GuyNeural",
]


async def stream_tts(
    text: str,
    voice: str,
    output_format: str = "audio-24khz-48kbitrate-mono-mp3",
) -> AsyncIterator[bytes]:
    if voice not in SUPPORTED_VOICES:
        raise ValueError(f"Unsupported voice: {voice}. Choose from {SUPPORTED_VOICES}")

    communicate = edge_tts.Communicate(
        text,
        voice,
        rate=settings.edge_tts_rate,
        pitch=settings.edge_tts_pitch,
    )
    async for chunk in communicate.stream():
        if chunk["type"] == "audio":
            yield chunk["data"]
