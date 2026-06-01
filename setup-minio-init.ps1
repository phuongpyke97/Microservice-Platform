# ==============================================================================
# Setup MinIO Buckets and Lifecycle Policies (Windows PowerShell)
# ==============================================================================

$ErrorActionPreference = "Stop"

# Check if .env file exists in the current directory
$envFile = Join-Path $PSScriptRoot ".env"
if (Test-Path $envFile) {
    Write-Host "Loading MinIO configuration from .env..." -ForegroundColor Cyan
    $envContent = Get-Content $envFile
    $MINIO_USER = ($envContent | Where-Object { $_ -match "^MINIO_ROOT_USER=" }) -replace "^MINIO_ROOT_USER=", "" | ForEach-Object { $_.Trim() }
    $MINIO_PASS = ($envContent | Where-Object { $_ -match "^MINIO_ROOT_PASSWORD=" }) -replace "^MINIO_ROOT_PASSWORD=", "" | ForEach-Object { $_.Trim() }
} else {
    Write-Host "Error: .env file not found in $PSScriptRoot!" -ForegroundColor Red
    exit 1
}

# Fallback values if variables are empty
if ([string]::IsNullOrWhiteSpace($MINIO_USER)) { $MINIO_USER = "admin" }
if ([string]::IsNullOrWhiteSpace($MINIO_PASS)) { $MINIO_PASS = "Crbt2026" }

Write-Host "Initializing MinIO Buckets..." -ForegroundColor Green
Write-Host "  User: $MINIO_USER" -ForegroundColor DarkGray

# Run minio/mc client container to configure buckets
$mcScript = @"
echo 'Setting up alias...'
mc alias set myminio http://host.docker.internal:9000 $MINIO_USER $MINIO_PASS

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
mc ilm rule rm --all --force myminio/media-temp 2>/dev/null || true
mc ilm rule add myminio/media-temp --expire-days 1

echo 'MinIO initialization complete!'
"@

docker run --rm --entrypoint=sh minio/mc -c $mcScript

if ($LASTEXITCODE -eq 0) {
    Write-Host "`nMinIO setup completed successfully!" -ForegroundColor Green
} else {
    Write-Host "`nMinIO setup failed with exit code $LASTEXITCODE" -ForegroundColor Red
    exit $LASTEXITCODE
}
