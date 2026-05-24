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
RUN pip install --no-cache-dir -r requirements.txt

# Copy protobuf definitions and generate stubs
COPY proto ./proto
COPY scripts ./scripts
RUN mkdir -p generated && python scripts/generate_protos.py

# Copy the rest of the application code
COPY . .

EXPOSE 8765
EXPOSE 50051

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8765"]
