# ============================================================
# Script build và deploy toàn bộ hệ thống lên DOCKER
# Yêu cầu: Docker Desktop, Maven, Java 21
# ============================================================

Write-Host "=== MICROSERVICE PLATFORM - DOCKER DEPLOYMENT ===" -ForegroundColor Cyan

# Bước 1: Build toàn bộ Maven project (tạo JAR files)
Write-Host "`n[1/3] Building all Maven modules..." -ForegroundColor Yellow
mvn clean install -DskipTests
if ($LASTEXITCODE -ne 0) {
    Write-Host "Maven build failed. Exiting." -ForegroundColor Red
    exit 1
}

# Bước 2: Dừng và xóa các container cũ (nếu có)
Write-Host "`n[2/3] Stopping and removing old containers..." -ForegroundColor Yellow
docker-compose down

# Bước 3: Build và khởi động toàn bộ hệ thống theo thứ tự
Write-Host "`n[3/3] Building Docker images and starting services..." -ForegroundColor Yellow

# 3.1: Khởi động hạ tầng cơ bản (DB, Cache, MQ)
Write-Host "  -> Starting infrastructure (Postgres, Redis, RabbitMQ, MinIO, Monitoring)..." -ForegroundColor Gray
docker-compose up -d postgres redis rabbitmq minio prometheus grafana loki promtail
Start-Sleep -Seconds 15

# 3.2: Khởi động Eureka Server (BẮT BUỘC ĐẦU TIÊN)
Write-Host "  -> Starting Eureka Server..." -ForegroundColor Gray
docker-compose up -d --build eureka-server
Start-Sleep -Seconds 20

# 3.3: Khởi động Config Server (THỨ HAI)
Write-Host "  -> Starting Config Server..." -ForegroundColor Gray
docker-compose up -d --build config-server
Start-Sleep -Seconds 15

# 3.4: Khởi động các Infra Services
Write-Host "  -> Starting Infra Services..." -ForegroundColor Gray
docker-compose up -d --build auth-service credit-wallet-service file-service notification-service audit-log-service payment-gateway-service
Start-Sleep -Seconds 20

# 3.5: Khởi động các Business Services
Write-Host "  -> Starting Business Services..." -ForegroundColor Gray
docker-compose up -d --build crbt-campaign-service crbt-community-library audio-generation-service crbt-credit-transaction-service crbt-core-adapter
Start-Sleep -Seconds 20

# 3.6: Khởi động Python AI Worker
Write-Host "  -> Starting Python AI Worker..." -ForegroundColor Gray
docker-compose up -d --build ai-media-worker
Start-Sleep -Seconds 10

# 3.7: Khởi động API Gateway (SAU CÙNG)
Write-Host "  -> Starting API Gateway..." -ForegroundColor Gray
docker-compose up -d --build api-gateway

Write-Host "`n=== DEPLOYMENT COMPLETE ===" -ForegroundColor Green
Write-Host "`nChecking service status..." -ForegroundColor Yellow
docker-compose ps

Write-Host "`n=== ACCESS POINTS ===" -ForegroundColor Cyan
Write-Host "Eureka Dashboard: http://localhost:8761 (eureka / eureka-secret)" -ForegroundColor White
Write-Host "API Gateway: http://localhost:8080" -ForegroundColor White
Write-Host "RabbitMQ Management: http://localhost:15672 (guest / guest)" -ForegroundColor White
Write-Host "MinIO Console: http://localhost:9001 (minioadmin / minioadmin)" -ForegroundColor White
Write-Host "Grafana: http://localhost:3000 (admin / admin)" -ForegroundColor White
Write-Host "Prometheus: http://localhost:9090" -ForegroundColor White

Write-Host "`nTo view logs: docker-compose logs -f [service-name]" -ForegroundColor Gray
Write-Host "To stop all: docker-compose down" -ForegroundColor Gray
