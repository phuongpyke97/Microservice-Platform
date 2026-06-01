import os
import shutil
import tempfile
import uuid
import httpx

# ---------------------------------------------------------------------------
# Monkeypatch httpx to follow redirects by default.
#
# Spleeter's model downloader (github.py) uses TWO httpx call patterns:
#   1. httpx.Client(http2=True)          → for downloading the model archive
#   2. httpx.get(url)                    → for fetching checksum.json
#
# Modern httpx (>=0.20) defaults follow_redirects=False. GitHub releases
# respond with 302→objects.githubusercontent.com, so both patterns fail
# without this patch.
# ---------------------------------------------------------------------------

# Patch 1: httpx.Client.__init__  (covers pattern 1 — Client instances)
_original_client_init = httpx.Client.__init__
def _patched_client_init(self, *args, **kwargs):
    kwargs.setdefault('follow_redirects', True)
    _original_client_init(self, *args, **kwargs)
httpx.Client.__init__ = _patched_client_init

# Patch 2: httpx.AsyncClient.__init__
_original_async_client_init = httpx.AsyncClient.__init__
def _patched_async_client_init(self, *args, **kwargs):
    kwargs.setdefault('follow_redirects', True)
    _original_async_client_init(self, *args, **kwargs)
httpx.AsyncClient.__init__ = _patched_async_client_init

# Patch 3: httpx.get() — module-level function (covers pattern 2 — checksum)
_original_httpx_get = httpx.get
def _patched_httpx_get(*args, **kwargs):
    kwargs.setdefault('follow_redirects', True)
    return _original_httpx_get(*args, **kwargs)
httpx.get = _patched_httpx_get

# Patch 4: httpx.request() — underlying function for all module-level calls
_original_httpx_request = httpx.request
def _patched_httpx_request(*args, **kwargs):
    kwargs.setdefault('follow_redirects', True)
    return _original_httpx_request(*args, **kwargs)
httpx.request = _patched_httpx_request

# Patch 5: httpx.stream() — used by spleeter's Client.stream inside download()
_original_httpx_stream = httpx.stream
def _patched_httpx_stream(*args, **kwargs):
    kwargs.setdefault('follow_redirects', True)
    return _original_httpx_stream(*args, **kwargs)
httpx.stream = _patched_httpx_stream


from app.config import settings


_separator = None


def _get_separator():
    """Lazily build the Spleeter separator (heavy TensorFlow import)."""
    global _separator
    if _separator is None:
        import sys
        import tensorflow as tf
        try:
            import tensorflow_estimator.python.estimator.api._v1.estimator as estimator
            tf.estimator = estimator
            sys.modules['tensorflow.estimator'] = estimator
        except Exception:
            pass
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
