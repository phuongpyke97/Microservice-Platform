import os

import pytest

from app.services import audio_separator


class FakeSeparator:
    def __init__(self, model):
        self.model = model

    def separate_to_file(self, input_path, work_dir):
        # Mimic Spleeter output layout: <work_dir>/<input_stem>/{vocals,accompaniment}.wav
        stem = os.path.splitext(os.path.basename(input_path))[0]
        out_dir = os.path.join(work_dir, stem)
        os.makedirs(out_dir, exist_ok=True)
        with open(os.path.join(out_dir, "vocals.wav"), "wb") as f:
            f.write(b"vocals-bytes")
        with open(os.path.join(out_dir, "accompaniment.wav"), "wb") as f:
            f.write(b"accomp-bytes")


def test_separate_audio_returns_stems_and_cleans_tmp(monkeypatch, tmp_path):
    monkeypatch.setattr(audio_separator.settings, "tmp_dir", str(tmp_path))
    monkeypatch.setattr(audio_separator, "_separator", None)
    monkeypatch.setattr(
        audio_separator, "_get_separator", lambda: FakeSeparator("spleeter:2stems")
    )
    import numpy as np
    import librosa
    monkeypatch.setattr(librosa, "load", lambda *args, **kwargs: (np.zeros(16000), 16000))
    monkeypatch.setattr(librosa.feature, "rms", lambda *args, **kwargs: np.zeros((1, 10)))

    result = audio_separator.separate_audio(b"raw-audio", fmt="wav")

    assert result["vocals"] == b"vocals-bytes"
    assert result["accompaniment"] == b"accomp-bytes"

    # work_dir should be cleaned up after call
    remaining = list(tmp_path.iterdir())
    assert remaining == [], f"Tmp dir not cleaned: {remaining}"


def test_separate_audio_cleans_tmp_on_failure(monkeypatch, tmp_path):
    monkeypatch.setattr(audio_separator.settings, "tmp_dir", str(tmp_path))
    monkeypatch.setattr(audio_separator, "_separator", None)

    class BrokenSeparator:
        def separate_to_file(self, input_path, work_dir):
            raise RuntimeError("separator boom")

    monkeypatch.setattr(audio_separator, "_get_separator", lambda: BrokenSeparator())

    with pytest.raises(RuntimeError, match="separator boom"):
        audio_separator.separate_audio(b"raw", fmt="wav")

    assert list(tmp_path.iterdir()) == []
