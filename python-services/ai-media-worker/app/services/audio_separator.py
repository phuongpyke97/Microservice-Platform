import os
import shutil
import tempfile
import uuid
import httpx

# Monkeypatch httpx to follow redirects by default (needed because spleeter's model downloader uses httpx without follow_redirects=True, which fails on modern httpx versions)
_original_client_init = httpx.Client.__init__
def _patched_client_init(self, *args, **kwargs):
    kwargs['follow_redirects'] = True
    _original_client_init(self, *args, **kwargs)
httpx.Client.__init__ = _patched_client_init

_original_async_client_init = httpx.AsyncClient.__init__
def _patched_async_client_init(self, *args, **kwargs):
    kwargs['follow_redirects'] = True
    _original_async_client_init(self, *args, **kwargs)
httpx.AsyncClient.__init__ = _patched_async_client_init


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
    Returns {'vocals': bytes, 'accompaniment': bytes, 'has_vocal': bool, 'vocal_rms': float}.
    """
    import librosa
    import numpy as np
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
        vocals_path = os.path.join(stem_dir, "vocals.wav")
        vocals = _read_bytes(vocals_path)
        accompaniment = _read_bytes(os.path.join(stem_dir, "accompaniment.wav"))

        # Calculate RMS of vocals to detect if there is vocal
        v_audio, sr = librosa.load(vocals_path, sr=16000)
        rms = librosa.feature.rms(y=v_audio)
        mean_rms = float(np.mean(rms))
        has_vocal = mean_rms > 0.02

        return {
            "vocals": vocals,
            "accompaniment": accompaniment,
            "has_vocal": has_vocal,
            "vocal_rms": mean_rms
        }
    finally:
        shutil.rmtree(work_dir, ignore_errors=True)


def _read_bytes(path: str) -> bytes:
    with open(path, "rb") as f:
        return f.read()
