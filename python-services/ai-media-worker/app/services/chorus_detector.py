import io
import numpy as np
import librosa


def detect_chorus(audio_data: bytes, sample_rate: float = 22050.0) -> dict:
    """
    Detect the repeating chorus segment using a Self-Similarity Matrix (SSM).
    Returns {'start_time': float, 'end_time': float, 'confidence': float}.
    """
    sr = int(sample_rate)
    audio, sr = librosa.load(io.BytesIO(audio_data), sr=sr, mono=True)

    hop_length = 512
    n_mels = 128
    mel = librosa.feature.melspectrogram(y=audio, sr=sr, n_mels=n_mels, hop_length=hop_length)
    mel_db = librosa.power_to_db(mel, ref=np.max)  # (n_mels, T)

    # L2-normalise each time frame
    frames = mel_db.T  # (T, n_mels)
    norms = np.linalg.norm(frames, axis=1, keepdims=True)
    norms = np.where(norms == 0, 1.0, norms)
    frames = frames / norms

    # Cosine SSM via single matrix multiply  (T x T)
    ssm = frames @ frames.T

    T = ssm.shape[0]
    hop_s = hop_length / sr
    # Minimum chorus length: 15 s; fall back to 5 s for very short clips
    min_frames = max(1, int(min(15.0, audio.shape[0] / sr * 0.2) / hop_s))

    if T <= min_frames:
        # Audio too short to find a chorus — return whole clip
        duration = float(audio.shape[0] / sr)
        proposals = [
            {"start": 0.0, "end": round(duration, 1)},
            {"start": 0.0, "end": round(duration, 1)},
            {"start": 0.0, "end": round(duration, 1)}
        ]
        return {
            "start_time": 0.0,
            "end_time": duration,
            "confidence": 1.0,
            "chorus_proposals": proposals,
            "chours_proposals": proposals
        }

    # Score each lag: mean similarity along the k-th upper diagonal
    lags = range(min_frames, T)
    lag_scores = np.array([np.mean(np.diag(ssm, k)) for k in lags])
    best_idx = int(np.argmax(lag_scores))
    best_lag = best_idx + min_frames
    confidence = float(lag_scores[best_idx])

    # Chorus frame: position with highest similarity at best_lag
    diag = np.diag(ssm, best_lag)
    chorus_start_frame = int(np.argmax(diag))
    chorus_len_frames = min(best_lag, T - chorus_start_frame)

    start_time = float(chorus_start_frame * hop_s)
    end_time = float((chorus_start_frame + chorus_len_frames) * hop_s)
    duration = float(audio.shape[0] / sr)

    # Generate 3 proposals of 45 seconds duration
    prop_duration = 45.0
    if duration < prop_duration:
        proposals = [
            {"start": 0.0, "end": round(duration, 1)},
            {"start": 0.0, "end": round(duration, 1)},
            {"start": 0.0, "end": round(duration, 1)}
        ]
    else:
        # Proposal 1: Primary detected chorus
        s1 = start_time
        if s1 + prop_duration > duration:
            s1 = duration - prop_duration
        s1 = max(0.0, s1)
        e1 = s1 + prop_duration

        # Proposal 2: Offset earlier
        s2 = max(0.0, s1 - 20.0)
        e2 = s2 + prop_duration

        # Proposal 3: Offset later
        s3 = min(duration - prop_duration, s1 + 20.0)
        s3 = max(0.0, s3)
        e3 = s3 + prop_duration

        proposals = [
            {"start": round(s1, 1), "end": round(e1, 1)},
            {"start": round(s2, 1), "end": round(e2, 1)},
            {"start": round(s3, 1), "end": round(e3, 1)}
        ]

    return {
        "start_time": start_time,
        "end_time": end_time,
        "confidence": confidence,
        "chorus_proposals": proposals,
        "chours_proposals": proposals
    }
