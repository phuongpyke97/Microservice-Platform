# ============================================================
# Script khởi động Microservice Platform - CLEAN VERSION
# ============================================================

Write-Host "=== MICROSERVICE PLATFORM - LOCALHOST STARTUP ===" -ForegroundColor Cyan

# --- BƯỚC 0: LOAD BIẾN MÔI TRƯỜNG VÀO PROCESS CHA ---
if (Test-Path ".env") {
    Write-Host "`n[0/5] Loading environment variables from .env..." -ForegroundColor Yellow
    foreach ($line in Get-Content .env) {
        if ($line -match '^([^#\s][^=]*)=(.*)$') {
            $key = $matches[1].Trim()
            $value = $matches[2].Trim().Trim('"').Trim("'")
            Set-Item -Path "Env:$key" -Value $value
        }
    }
    Write-Host "  .env loaded! All child windows will inherit these variables." -ForegroundColor Green
}

# Bước 1: Khởi động hạ tầng Docker
Write-Host "`n[1/5] Starting infrastructure (Postgres, Redis, RabbitMQ, MinIO)..." -ForegroundColor Yellow
docker-compose up -d postgres redis rabbitmq minio
Start-Sleep -Seconds 5

# Bước 2: Build project
Write-Host "`n[2/5] Building all Maven modules..." -ForegroundColor Yellow
# Thử dùng mvnw nếu có, không thì dùng mvn
$mvn = if (Test-Path "mvnw.cmd") { ".\mvnw.cmd" } else { "mvn" }

try {
    & $mvn clean install -DskipTests
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Build failed. Please check errors above." -ForegroundColor Red
        exit 1
    }
} catch {
    Write-Host "Maven not found. Please ensure Maven is installed (winget install Apache.Maven) or build in IntelliJ first." -ForegroundColor Red
    exit 1
}

# Hàm helper để khởi động service (tự động kế thừa $env)
function Run-Service($module, $wait = 3) {
    Write-Host "  -> Launching $module..." -ForegroundColor Gray
    Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd `"$PWD`"; & $mvn spring-boot:run -pl $module"
    Start-Sleep -Seconds $wait
}

# Bước 3: Khởi động theo thứ tự
Write-Host "`n[3/5] Starting Eureka Server..." -ForegroundColor Yellow
Run-Service "infrastructure/eureka-server" 20

Write-Host "`n[4/5] Starting Config Server..." -ForegroundColor Yellow
Run-Service "infrastructure/config-server" 15

Write-Host "`n[5/5] Starting All Other Services..." -ForegroundColor Yellow
$services = @(
    "infra-services/auth-service",
    "infra-services/credit-wallet-service",
    "infra-services/file-service",
    "infra-services/notification-service",
    "infra-services/audit-log-service",
    "infra-services/payment-gateway-service",
    "business-services/crbt-campaign-service",
    "business-services/crbt-community-library",
    "business-services/audio-generation-service",
    "business-services/crbt-credit-transaction-service",
    "business-services/crbt-core-adapter"
)

foreach ($s in $services) { Run-Service $s 2 }

Start-Sleep -Seconds 10
Write-Host "`nFinal: Starting API Gateway..." -ForegroundColor Yellow
Run-Service "infrastructure/api-gateway"

# Khởi động Python AI Worker
if (Test-Path "python-services/ai-media-worker") {
    Write-Host "`nStarting Python AI Worker..." -ForegroundColor Yellow
    Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$PWD/python-services/ai-media-worker'; uvicorn main:app --host 0.0.0.0 --port 8765 --reload"
}

Write-Host "`n=== SYSTEM STARTUP INITIATED ===" -ForegroundColor Green
Write-Host "All windows will inherit .env from this session." -ForegroundColor Cyan
