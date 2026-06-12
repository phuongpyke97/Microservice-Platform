import os
import subprocess
import tempfile
import uuid
from app.config import settings

def mix_audio_tracks(vocal_bytes: bytes, accompaniment_bytes: bytes, mode: str, start_time: float = 0.0, end_time: float = 0.0) -> bytes:
    """
    Mix vocal (or TTS) and accompaniment bytes using FFmpeg.
    mode can be 'v1' (voice prominent), 'v2' (balanced), or 'v3' (music prominent).
    Returns the mixed MP3 bytes.
    """
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

        # Define volume weights for filter complexes
        # BR-03-03 and BR-03-04: final mix normalize to -18 LUFS
        v_vol = "1.0"
        m_vol = "0.6"
        if mode == 'v1':
            v_vol = "1.0"
            m_vol = "0.25"
        elif mode == 'v3':
            v_vol = "0.25"
            m_vol = "1.0"

        # Apply 5s delay to vocal track and select mix duration behaviour
        if should_crop:
            # We crop the accompaniment. The output mix duration should match the accompaniment's duration.
            # In amix, the first input determines the output duration with duration=first.
            # So we feed accompaniment [m] as the first input to amix: [m][v]amix.
            filter_complex = f"[0:a]adelay=5000:all=1,volume={v_vol}[v];[1:a]volume={m_vol}[m];[m][v]amix=inputs=2:duration=first[mix];[mix]loudnorm=I=-18:TP=-1.5:LRA=11[out]"
        else:
            # Original behavior (e.g. without crop), output duration is determined by vocal duration.
            # Vocal [v] is the first input to amix: [v][m]amix.
            filter_complex = f"[0:a]adelay=5000:all=1,volume={v_vol}[v];[1:a]volume={m_vol}[m];[v][m]amix=inputs=2:duration=first[mix];[mix]loudnorm=I=-18:TP=-1.5:LRA=11[out]"
            
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
