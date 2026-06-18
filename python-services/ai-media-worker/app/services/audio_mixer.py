import os
import random
import subprocess
import tempfile
import uuid
from app.config import settings

def mix_audio_tracks(
    vocal_bytes: bytes,
    accompaniment_bytes: bytes,
    mode: str,
    start_time: float = 0.0,
    end_time: float = 0.0,
    seed: int | None = None,
) -> bytes:
    """
    Mix vocal (or TTS) and accompaniment bytes using FFmpeg.
    mode can be 'v1' (voice prominent), 'v2' (balanced), or 'v3' (music prominent).

    `seed` drives per-generation variation so that re-generating the same inputs
    produces an audibly different result. When seed is None a random one is used.
    Randomized aspects: vocal entry delay, music tempo, a light treble/EQ jitter
    and small volume jitter. The mix is still normalized to -18 LUFS at the end.

    Returns the mixed MP3 bytes.
    """
    # Deterministic-but-varying RNG. Different seed -> different mix.
    rng = random.Random(seed if seed is not None else uuid.uuid4().int)

    work_dir = os.path.join(settings.tmp_dir, f"mix_{uuid.uuid4().hex}")
    os.makedirs(work_dir, exist_ok=True)

    vocal_path = os.path.join(work_dir, "vocal.mp3")
    bg_path = os.path.join(work_dir, "accompaniment.wav")
    out_path = os.path.join(work_dir, "output.mp3")

    try:
        # Write inputs
        with open(vocal_path, "wb") as f:
            f.write(vocal_bytes)
        with open(bg_path, "wb") as f:
            f.write(accompaniment_bytes)

        # Check if we should crop accompaniment
        should_crop = (end_time > start_time)
        bg_input_args = []
        if should_crop:
            duration = end_time - start_time
            bg_input_args = ["-ss", f"{start_time:.3f}", "-t", f"{duration:.3f}"]

        # Define base volume weights for filter complexes
        # BR-03-03 and BR-03-04: final mix normalize to -18 LUFS
        v_vol = 1.0
        m_vol = 0.6
        if mode == 'v1':
            v_vol = 1.0
            m_vol = 0.25
        elif mode == 'v3':
            v_vol = 0.25
            m_vol = 1.0

        # --- Per-generation randomization removed -----------------------------
        # Fixed to ensure the output duration matches the input duration (40s is 40s)
        # Vocal entry delay: fixed to 5000ms
        delay_ms = 5000
        # Music tempo: fixed to 1.0 (no tempo changes to keep duration exact)
        tempo = 1.0
        # Light treble: fixed to 0.0
        treble_g = 0.0
        # Volume levels: fixed to the base volumes for the selected mode
        v_vol_final = v_vol
        m_vol_final = m_vol
        # ----------------------------------------------------------------------

        vocal_chain = f"adelay={delay_ms}:all=1,volume={v_vol_final}"
        music_chain = f"volume={m_vol_final},atempo={tempo},treble=g={treble_g}"

        # Apply mix duration behaviour
        if should_crop:
            # We crop the accompaniment. The output mix duration should match the accompaniment's duration.
            # In amix, the first input determines the output duration with duration=first.
            # So we feed accompaniment [m] as the first input to amix: [m][v]amix.
            filter_complex = (
                f"[0:a]{vocal_chain}[v];"
                f"[1:a]{music_chain}[m];"
                f"[m][v]amix=inputs=2:duration=first[mix];"
                f"[mix]loudnorm=I=-18:TP=-1.5:LRA=11[out]"
            )
        else:
            # Original behavior (e.g. without crop), output duration is determined by vocal duration.
            # Vocal [v] is the first input to amix: [v][m]amix.
            filter_complex = (
                f"[0:a]{vocal_chain}[v];"
                f"[1:a]{music_chain}[m];"
                f"[v][m]amix=inputs=2:duration=first[mix];"
                f"[mix]loudnorm=I=-18:TP=-1.5:LRA=11[out]"
            )

        cmd = [
            "ffmpeg", "-y",
            "-i", vocal_path,
        ] + bg_input_args + [
            "-i", bg_path,
            "-filter_complex", filter_complex,
            "-map", "[out]",
            "-acodec", "libmp3lame",
            "-ab", "128k",
            out_path
        ]

        # Run FFmpeg command
        result = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True)

        with open(out_path, "rb") as f:
            return f.read()
    except subprocess.CalledProcessError as e:
        stderr_msg = e.stderr.decode(errors='replace')
        raise RuntimeError(f"FFmpeg mixing failed: {stderr_msg}") from e
    finally:
        # Clean up files and work dir
        for p in [vocal_path, bg_path, out_path]:
            if os.path.exists(p):
                try:
                    os.remove(p)
                except OSError:
                    pass
        try:
            os.rmdir(work_dir)
        except OSError:
            pass
