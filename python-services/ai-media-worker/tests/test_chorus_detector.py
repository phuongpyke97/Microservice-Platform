import io

import librosa
import numpy as np
import pytest

from app.services import chorus_detector


def test_detect_chorus_short_audio_returns_full_clip(monkeypatch):
    sample_rate = 22050.0
    # Use a very short duration (e.g. 0.02 seconds) to trigger the T <= min_frames check
    duration = 0.02
    def mock_librosa_load(*args, **kwargs):
        return np.zeros(int(sample_rate * duration)), int(sample_rate)

    monkeypatch.setattr(chorus_detector.librosa, "load", mock_librosa_load)

    result = chorus_detector.detect_chorus(b"short-audio-data", sample_rate)

    assert result["start_time"] == 0.0
    assert pytest.approx(result["end_time"]) == duration
    assert result["confidence"] == 1.0
    assert "chorus_proposals" in result
    assert len(result["chorus_proposals"]) == 3


def test_detect_chorus_detects_repeat_segment(monkeypatch):
    # Create a synthetic audio signal with a clear repeating segment
    sr = 22050
    duration = 30  # seconds
    t = np.linspace(0, duration, int(sr * duration), endpoint=False)

    # Segment A: first 5 seconds
    segment_a = np.sin(2 * np.pi * 440 * t[: sr * 5])
    # Segment B: next 5 seconds
    segment_b = np.sin(2 * np.pi * 880 * t[: sr * 5])

    # Full audio: A, B, A, C (where C is some non-repeating part)
    audio = np.concatenate([segment_a, segment_b, segment_a, np.zeros(len(t) - len(segment_a) * 3)])

    # Mock librosa.load to return our synthetic audio
    def mock_librosa_load(*args, **kwargs):
        return audio, sr

    chorus_detector.librosa.load = mock_librosa_load

    # Convert audio to bytes for the function input
    buffer = io.BytesIO()
    import soundfile as sf

    sf.write(buffer, audio, sr, format="WAV")
    audio_data = buffer.getvalue()

    result = chorus_detector.detect_chorus(audio_data, float(sr))

    # Assert basic correctness of the returned structure
    assert "start_time" in result
    assert "end_time" in result
    assert "confidence" in result
    assert "chorus_proposals" in result
    assert len(result["chorus_proposals"]) == 3
    for p in result["chorus_proposals"]:
        assert "start" in p
        assert "end" in p
        assert p["end"] >= p["start"]

