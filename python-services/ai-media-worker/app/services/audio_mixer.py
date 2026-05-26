import os
import subprocess
import tempfile
import uuid
from app.config import settings

def mix_audio_tracks(vocal_bytes: bytes, accompaniment_bytes: bytes, mode: str) -> bytes:
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
            
        # Define volume weights for filter complexes
        # BR-03-03 and BR-03-04: final mix normalize to -18 LUFS
        if mode == 'v1':
            # Voice prominent: voice volume high (1.0), bg volume low (0.25)
            filter_complex = '[0:a]volume=1.0[v];[1:a]volume=0.25[m];[v][m]amix=inputs=2:duration=first[mix];[mix]loudnorm=I=-18:TP=-1.5:LRA=11[out]'
        elif mode == 'v3':
            # Music prominent: voice volume low (0.25), bg volume high (1.0)
            filter_complex = '[0:a]volume=0.25[v];[1:a]volume=1.0[m];[v][m]amix=inputs=2:duration=first[mix];[mix]loudnorm=I=-18:TP=-1.5:LRA=11[out]'
        else:
            # Balanced: voice volume normal (1.0), bg volume balanced (0.6)
            filter_complex = '[0:a]volume=1.0[v];[1:a]volume=0.6[m];[v][m]amix=inputs=2:duration=first[mix];[mix]loudnorm=I=-18:TP=-1.5:LRA=11[out]'
            
        cmd = [
            "ffmpeg", "-y",
            "-i", vocal_path,
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
