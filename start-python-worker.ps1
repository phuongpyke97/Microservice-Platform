# ============================================================
# Script khởi động Python AI Media Worker độc lập
# Yêu cầu: Python 3.11+, FFmpeg (tùy chọn nhưng khuyến nghị)
# ============================================================

Write-Host "=== PYTHON AI MEDIA WORKER STARTUP ===" -ForegroundColor Cyan

$WorkerPath = "$PWD\python-services\ai-media-worker"
if (-not (Test-Path $WorkerPath)) {
    Write-Host "Error: Cannot find python-services\ai-media-worker directory." -ForegroundColor Red
    exit 1
}

cd $WorkerPath

# Bước 1: Tạo môi trường ảo (venv) nếu chưa có
if (-not (Test-Path "venv")) {
    Write-Host "`n[1/3] Creating Python Virtual Environment (venv)..." -ForegroundColor Yellow
    python -m venv venv
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Failed to create venv. Please ensure Python is installed and added to PATH." -ForegroundColor Red
        exit 1
    }
} else {
    Write-Host "`n[1/3] Python Virtual Environment already exists." -ForegroundColor Green
}

# Bước 2: Cài đặt thư viện
Write-Host "`n[2/3] Activating venv and installing dependencies..." -ForegroundColor Yellow
& ".\venv\Scripts\python.exe" -m pip install --upgrade pip setuptools wheel
if ($LASTEXITCODE -ne 0) {
    Write-Host "Failed to upgrade pip/setuptools/wheel." -ForegroundColor Red
    exit 1
}
# grpcio must install from pre-built wheel — source build fails when pkg_resources is missing in pip's temp build env
& ".\venv\Scripts\pip.exe" install --only-binary :all: --no-cache-dir grpcio==1.75.1 grpcio-tools==1.75.1
if ($LASTEXITCODE -ne 0) {
    Write-Host "Failed to install grpcio binaries." -ForegroundColor Red
    exit 1
}
& ".\venv\Scripts\pip.exe" install --no-cache-dir -r requirements.txt
if ($LASTEXITCODE -ne 0) {
    Write-Host "Failed to install dependencies." -ForegroundColor Red
    exit 1
}
# spleeter conflicts with httpx>=0.17 — install without its dependency tree
& ".\venv\Scripts\pip.exe" install --no-deps spleeter==2.1.0
if ($LASTEXITCODE -ne 0) {
    Write-Host "Warning: spleeter install failed. Audio separation may be unavailable." -ForegroundColor Yellow
}

# Bước 3: Generate gRPC stubs từ .proto files
Write-Host "`n[3/4] Generating gRPC stubs..." -ForegroundColor Yellow
& ".\venv\Scripts\python.exe" scripts/generate_protos.py
if ($LASTEXITCODE -ne 0) {
    Write-Host "Failed to generate gRPC stubs." -ForegroundColor Red
    exit 1
}

# Bước 4: Khởi động FastAPI & gRPC Server
Write-Host "`n[4/4] Starting Uvicorn Server (FastAPI + gRPC)..." -ForegroundColor Yellow
Write-Host "HTTP Port: 8765 | gRPC Port: 50051" -ForegroundColor Cyan
Write-Host "Swagger UI: http://localhost:8765/docs`n" -ForegroundColor Green

# Load biến môi trường từ thư mục gốc nếu có (dành cho AI_WORKER_HTTP_PORT / gRPC config)
if (Test-Path "..\..\.env") {
    foreach ($line in Get-Content "..\..\.env") {
        if ($line -match '^([^#\s][^=]*)=(.*)$') {
            $varName = $matches[1].Trim()
            $varValue = $matches[2].Trim().Trim('"').Trim("'")
            Set-Item -Path "env:$varName" -Value $varValue
        }
    }
}

# Chạy server
& ".\venv\Scripts\uvicorn.exe" main:app --host 0.0.0.0 --port 8765 --reload
