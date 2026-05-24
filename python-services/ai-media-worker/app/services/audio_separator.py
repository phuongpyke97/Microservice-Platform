import os
import shutil
import tempfile
import uuid

from app.config import settings

_separator = None


def _get_separator():
    """Lazily build the Spleeter separator (heavy TensorFlow import)."""
    global _separator
    if _separator is None:
        from spleeter.separator import Separator
        _separator = Separator(settings.spleeter_model)
    return _separator


def separate_audio(audio_data: bytes, fmt: str = "wav") -> dict:
    """
    Split audio into vocals + accompaniment using Spleeter (2 stems).
    Returns {'vocals': bytes, 'accompaniment': bytes}.
    """
    work_dir = os.path.join(settings.tmp_dir, uuid.uuid4().hex)
    os.makedirs(work_dir, exist_ok=True)
    input_path = os.path.join(work_dir, f"input.{fmt}")
    try:
        with open(input_path, "wb") as f:
            f.write(audio_data)

        separator = _get_separator()
        separator.separate_to_file(input_path, work_dir)

        # Spleeter writes <work_dir>/input/{vocals,accompaniment}.wav
        stem_dir = os.path.join(work_dir, "input")
        vocals = _read_bytes(os.path.join(stem_dir, "vocals.wav"))
        accompaniment = _read_bytes(os.path.join(stem_dir, "accompaniment.wav"))
        return {"vocals": vocals, "accompaniment": accompaniment}
    finally:
        shutil.rmtree(work_dir, ignore_errors=True)


def _read_bytes(path: str) -> bytes:
    with open(path, "rb") as f:
        return f.read()
