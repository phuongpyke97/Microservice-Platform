#!/usr/bin/env bash
# build-and-deploy.sh
# Build toan bo Java services -> Docker Compose up (Linux/macOS)
#
# Usage:
#   ./build-and-deploy.sh              # full build + deploy
#   ./build-and-deploy.sh --skip-build # bo qua maven, chi deploy
#   ./build-and-deploy.sh --no-cache   # docker build khong dung cache
#   ./build-and-deploy.sh --logs       # follow logs sau khi up
#   ./build-and-deploy.sh --skip-build --no-cache --logs

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

SKIP_BUILD=false
NO_CACHE=false
FOLLOW_LOGS=false

for arg in "$@"; do
    case "$arg" in
        --skip-build) SKIP_BUILD=true ;;
        --no-cache)   NO_CACHE=true ;;
        --logs)       FOLLOW_LOGS=true ;;
        *) echo "[WARN] Unknown arg: $arg" ;;
    esac
done

# Colors
CYAN='\033[0;36m'; GREEN='\033[0;32m'; RED='\033[0;31m'; YELLOW='\033[0;33m'; GRAY='\033[0;90m'; NC='\033[0m'

step()  { echo -e "\n${CYAN}==> $*${NC}"; }
ok()    { echo -e "  ${GREEN}[OK]${NC} $*"; }
fail()  { echo -e "  ${RED}[FAIL]${NC} $*"; exit 1; }
skip()  { echo -e "  ${YELLOW}[SKIP]${NC} $*"; }
info()  { echo -e "  ${GRAY}$*${NC}"; }

# Java 21
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"

# --- 0. Prerequisites ---
step "Kiem tra prerequisites"

command -v docker &>/dev/null || fail "Docker chua cai hoac chua co trong PATH"
ok "docker $(docker --version | awk '{print $3}' | tr -d ',')"

MVN_CMD=""
if [[ -f "$ROOT/mvnw" ]]; then
    chmod +x "$ROOT/mvnw"
    MVN_CMD="$ROOT/mvnw"
elif command -v mvn &>/dev/null; then
    MVN_CMD="mvn"
else
    fail "Khong tim thay mvnw hoac mvn trong PATH"
fi
ok "maven -> $MVN_CMD"

# --- 1. Maven build ---
if [[ "$SKIP_BUILD" == "false" ]]; then
    step "Maven build (clean package -DskipTests)"
    cd "$ROOT"
    "$MVN_CMD" clean package -DskipTests --batch-mode
    ok "Maven build thanh cong"
else
    skip "Maven build (--skip-build)"
fi

# --- 2. Kiem tra JAR ---
step "Kiem tra JAR output"

modules=(
    "infrastructure/eureka-server"
    "infrastructure/config-server"
    "infrastructure/api-gateway"
    "infra-services/auth-service"
    "infra-services/notification-service"
    "infra-services/file-service"
    "infra-services/audit-log-service"
    "infra-services/payment-gateway-service"
    "infra-services/credit-wallet-service"
    "business-services/crbt-campaign-service"
    "business-services/crbt-community-library"
    "business-services/audio-generation-service"
    "business-services/crbt-credit-transaction-service"
    "business-services/crbt-core-adapter"
)

missing=()
for mod in "${modules[@]}"; do
    target_dir="$ROOT/$mod/target"
    jar=$(find "$target_dir" -maxdepth 1 -name "*.jar" ! -name "*-sources.jar" 2>/dev/null | head -1)
    if [[ -z "$jar" ]]; then
        missing+=("$mod")
        echo -e "  ${RED}[MISS]${NC} $mod"
    else
        ok "$mod -> $(basename "$jar")"
    fi
done

if [[ ${#missing[@]} -gt 0 ]]; then
    fail "${#missing[@]} module thieu JAR. Chay lai khong co --skip-build."
fi

# --- 2a. Load Docker images from tar files ---
step "Load MinIO image from minio.tar"
minio_tar_path="$ROOT/minio.tar"
if [[ -f "$minio_tar_path" ]]; then
    info "Loading minio.tar..."
    docker load -i "$minio_tar_path" || fail "Khong the tai anh tu minio.tar"
    ok "Tai anh MinIO thanh cong"
else
    fail "Khong tim thay file minio.tar o $minio_tar_path"
fi

# --- 3. Docker Compose build & up ---
step "Docker Compose build + up"
cd "$ROOT"

build_args=("compose" "build" "--parallel")
[[ "$NO_CACHE" == "true" ]] && build_args+=("--no-cache")

info "Running: docker ${build_args[*]}"
docker "${build_args[@]}" || fail "docker compose build that bai"
ok "docker compose build xong"

info "Running: docker compose up -d"
docker compose up -d || fail "docker compose up that bai"
ok "Tat ca container da khoi dong"

# --- 4. Status ---
step "Trang thai container"
docker compose ps

# --- 5. Logs (optional) ---
if [[ "$FOLLOW_LOGS" == "true" ]]; then
    step "Logs (Ctrl+C de thoat)"
    docker compose logs -f
fi

echo -e "\n${GREEN}[DONE] Platform dang chay:${NC}"
echo "  API Gateway   -> http://localhost:8080"
echo "  Eureka        -> http://localhost:8761"
echo "  RabbitMQ UI   -> http://localhost:15672  (guest/guest)"
echo "  MinIO Console -> http://localhost:9001   (minioadmin/minioadmin)"
echo "  Grafana       -> http://localhost:3000   (admin/admin)"
echo "  Prometheus    -> http://localhost:9090"
