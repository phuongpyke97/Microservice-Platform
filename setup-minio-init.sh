#!/usr/bin/env bash
# ==============================================================================
# Setup MinIO Buckets and Lifecycle Policies
# ==============================================================================

set -e

# Check if .env file exists in the current directory
if [ -f .env ]; then
    echo "Loading MinIO configuration from .env..."
    MINIO_USER=$(grep -oP '^MINIO_ROOT_USER=\K.*' .env | tr -d '\r')
    MINIO_PASS=$(grep -oP '^MINIO_ROOT_PASSWORD=\K.*' .env | tr -d '\r')
else
    echo "Error: .env file not found in current directory!"
    exit 1
fi

# Fallback values if variables are empty
MINIO_USER=${MINIO_USER:-admin}
MINIO_PASS=${MINIO_PASS:-Crbt2026}

echo "Initializing MinIO Buckets..."

# Run minio/mc client container to configure buckets
docker run --rm --network host minio/mc sh -c "
  echo 'Setting up alias...'
  mc alias set myminio http://localhost:9000 ${MINIO_USER} ${MINIO_PASS}

  echo 'Creating buckets...'
  mc mb --ignore-existing myminio/media-images
  mc mb --ignore-existing myminio/media-audio
  mc mb --ignore-existing myminio/media-temp
  mc mb --ignore-existing myminio/media-private
  mc mb --ignore-existing myminio/media-audio-lib

  echo 'Setting public read-write policies...'
  mc anonymous set public myminio/media-images
  mc anonymous set public myminio/media-audio
  mc anonymous set public myminio/media-temp
  mc anonymous set public myminio/media-private
  mc anonymous set public myminio/media-audio-lib

  echo 'Setting lifecycle policy (expire in 1 day) for media-temp...'
  # Remove existing lifecycle rules if any to prevent duplicate rules
  mc ilm rule rm --all --force myminio/media-temp 2>/dev/null || true
  mc ilm rule add myminio/media-temp --expire-days 1

  echo 'MinIO initialization complete!'
"
