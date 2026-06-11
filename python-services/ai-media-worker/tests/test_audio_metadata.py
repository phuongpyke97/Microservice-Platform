import io
import pytest
from fastapi.testclient import TestClient
from main import app

client = TestClient(app)

def test_audio_metadata_endpoint_success(monkeypatch):
    import numpy as np
    import librosa
    # Mock librosa.load to return a 60 seconds audio mock (16000 Hz * 60s = 960000 samples)
    monkeypatch.setattr(librosa, "load", lambda *args, **kwargs: (np.zeros(960000), 16000))

    file_data = b"fake-audio-bytes"
    response = client.post(
        "/audio-metadata",
        files={"file": ("test.mp3", file_data, "audio/mpeg")}
    )
    
    assert response.status_code == 200
    data = response.json()
    assert "duration" in data
    assert data["duration"] == 60.0

def test_audio_metadata_endpoint_failure(monkeypatch):
    import librosa
    def mock_load(*args, **kwargs):
        raise Exception("failed to load")
    monkeypatch.setattr(librosa, "load", mock_load)

    response = client.post(
        "/audio-metadata",
        files={"file": ("test.mp3", b"corrupted", "audio/mpeg")}
    )
    
    assert response.status_code == 400
    assert "detail" in response.json()
