FROM python:3.11-slim

WORKDIR /app

# Install build dependencies for librosa and soundfile
# libsndfile1 for soundfile, ffmpeg for general audio processing
RUN apt-get update && apt-get install -y --no-install-recommends \
    libsndfile1 \
    ffmpeg \
    build-essential \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt \
    && pip install --no-cache-dir spleeter==2.4.2 --no-deps

# Pre-download spleeter 2stems model at build time.
# Uses spleeter's own ModelProvider with httpx redirect fix applied,
# so the tar extraction path matches exactly what spleeter expects.
ENV MODEL_PATH=/app/pretrained_models
COPY scripts/download_spleeter_model.py /tmp/download_spleeter_model.py
RUN python /tmp/download_spleeter_model.py && rm /tmp/download_spleeter_model.py

# Copy protobuf definitions and generate stubs
COPY proto ./proto
COPY scripts ./scripts
RUN mkdir -p generated && python scripts/generate_protos.py

# Copy the rest of the application code
COPY . .

EXPOSE 8765
EXPOSE 50051

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8765"]
