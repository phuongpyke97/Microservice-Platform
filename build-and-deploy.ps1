# build-and-deploy.ps1
# Build toan bo Java services -> Docker Compose up

param(
    [switch]$SkipBuild,
    [switch]$NoCacheDocker,
    [switch]$Logs
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$ROOT = $PSScriptRoot

function Write-Step([string]$msg) {
    Write-Host ""
    Write-Host "==> $msg" -ForegroundColor Cyan
}

function Write-OK([string]$msg) {
    Write-Host "  [OK] $msg" -ForegroundColor Green
}

function Write-Fail([string]$msg) {
    Write-Host "  [FAIL] $msg" -ForegroundColor Red
    exit 1
}

# --- 0. Prerequisite ---
Write-Step "Kiem tra prerequisites"

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Fail "Docker chua cai hoac chua co trong PATH"
}
Write-OK "docker"

$mvnCmd = $null
$mvnw = Join-Path $ROOT "mvnw.cmd"
if (Test-Path $mvnw) { $mvnCmd = $mvnw }
else {
    $mvnwUnix = Join-Path $ROOT "mvnw"
    if (Test-Path $mvnwUnix) { $mvnCmd = $mvnwUnix }
    elseif (Get-Command mvn -ErrorAction SilentlyContinue) { $mvnCmd = "mvn" }
    else { Write-Fail "Khong tim thay mvnw hoac mvn trong PATH" }
}
Write-OK "maven -> $mvnCmd"

# --- 1. Maven build ---
if (-not $SkipBuild) {
    Write-Step "Maven build (clean package -DskipTests)"
    Push-Location $ROOT
    try {
        & $mvnCmd clean package -DskipTests --batch-mode
        if ($LASTEXITCODE -ne 0) { Write-Fail "Maven build that bai (exit $LASTEXITCODE)" }
        Write-OK "Maven build thanh cong"
    }
    finally {
        Pop-Location
    }
}
else {
    Write-Host "  [SKIP] Maven build (-SkipBuild)" -ForegroundColor Yellow
}

# --- 2. Kiem tra JAR ---
Write-Step "Kiem tra JAR output"

$modules = @(
    "infrastructure\eureka-server",
    "infrastructure\config-server",
    "infrastructure\api-gateway",
    "infra-services\auth-service",
    "infra-services\notification-service",
    "infra-services\file-service",
    "infra-services\audit-log-service",
    "infra-services\payment-gateway-service",
    "infra-services\credit-wallet-service",
    "business-services\crbt-campaign-service",
    "business-services\crbt-community-library",
    "business-services\audio-generation-service",
    "business-services\crbt-credit-transaction-service",
    "business-services\crbt-core-adapter"
)

$missing = @()
foreach ($mod in $modules) {
    $targetDir = Join-Path $ROOT "$mod\target"
    $jars = @(Get-ChildItem -Path $targetDir -Filter "*.jar" -ErrorAction SilentlyContinue |
              Where-Object { $_.Name -notlike "*-sources.jar" })
    if ($jars.Count -eq 0) {
        $missing += $mod
        Write-Host "  [MISS] $mod" -ForegroundColor Red
    }
    else {
        Write-OK "$mod -> $($jars[0].Name)"
    }
}

if ($missing.Count -gt 0) {
    Write-Fail "$($missing.Count) module thieu JAR. Chay lai khong co -SkipBuild."
}

# --- 2a. Load Docker images from tar files ---
Write-Step "Load MinIO image from minio.tar"
$minioTarPath = Join-Path $ROOT "minio.tar"
if (Test-Path $minioTarPath) {
    Write-Host "  Loading minio.tar..." -ForegroundColor DarkGray
    & docker load -i $minioTarPath
    if ($LASTEXITCODE -ne 0) { Write-Fail "Khong the tai anh tu minio.tar" }
    Write-OK "Tai anh MinIO thanh cong"
} else {
    Write-Fail "Khong tim thay file minio.tar o $minioTarPath"
}

# --- 2a. Load Docker images from tar files ---
Write-Step "Load MinIO image from minio.tar"
$minioTarPath = Join-Path $ROOT "minio.tar"
if (Test-Path $minioTarPath) {
    Write-Host "  Loading minio.tar..." -ForegroundColor DarkGray
    & docker load -i $minioTarPath
    if ($LASTEXITCODE -ne 0) { Write-Fail "Khong the tai anh tu minio.tar" }
    Write-OK "Tai anh MinIO thanh cong"
} else {
    Write-Fail "Khong tim thay file minio.tar o $minioTarPath"
}

# --- 3. Docker Compose build & up ---
Write-Step "Docker Compose build + up"
Push-Location $ROOT
try {
    $buildArgs = @("compose", "build", "--parallel")
    if ($NoCacheDocker) { $buildArgs += "--no-cache" }

    Write-Host "  Running: docker $($buildArgs -join ' ')" -ForegroundColor DarkGray
    & docker @buildArgs
    if ($LASTEXITCODE -ne 0) { Write-Fail "docker compose build that bai" }
    Write-OK "docker compose build xong"

    Write-Host "  Running: docker compose up -d" -ForegroundColor DarkGray
    & docker compose up -d
    if ($LASTEXITCODE -ne 0) { Write-Fail "docker compose up that bai" }
    Write-OK "Tat ca container da khoi dong"
}
finally {
    Pop-Location
}

# --- 4. Status ---
Write-Step "Trang thai container"
& docker compose ps

# --- 5. Logs (optional) ---
if ($Logs) {
    Write-Step "Logs (Ctrl+C de thoat)"
    & docker compose logs -f
}

Write-Host ""
Write-Host "[DONE] Platform dang chay:" -ForegroundColor Green
Write-Host "  API Gateway   -> http://localhost:8080"
Write-Host "  Eureka        -> http://localhost:8761"
Write-Host "  RabbitMQ UI   -> http://localhost:15672  (guest/guest)"
Write-Host "  MinIO Console -> http://localhost:9001   (minioadmin/minioadmin)"
Write-Host "  Grafana       -> http://localhost:3000   (admin/admin)"
Write-Host "  Prometheus    -> http://localhost:9090"
