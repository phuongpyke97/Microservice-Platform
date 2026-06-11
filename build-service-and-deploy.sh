#!/usr/bin/env bash
# build-service-and-deploy.sh
# Build and deploy ONLY custom Java services (excludes databases, rabbitmq, redis, etc.)
#
# Usage:
#   ./build-service-and-deploy.sh              # build and deploy ALL services
#   ./build-service-and-deploy.sh auth-service # build and deploy ONLY auth-service

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Colors
CYAN='\033[0;36m'; GREEN='\033[0;32m'; RED='\033[0;31m'; YELLOW='\033[0;33m'; GRAY='\033[0;90m'; NC='\033[0m'

step()  { echo -e "\n${CYAN}==> $*${NC}"; }
ok()    { echo -e "  ${GREEN}[OK]${NC} $*"; }
fail()  { echo -e "  ${RED}[FAIL]${NC} $*"; exit 1; }
info()  { echo -e "  ${GRAY}$*${NC}"; }

# Export Java 21 path
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"

# Map of service names to their directory path
declare -A SERVICE_MAP=(
    ["eureka-server"]="infrastructure/eureka-server"
    ["config-server"]="infrastructure/config-server"
    ["api-gateway"]="infrastructure/api-gateway"
    ["auth-service"]="infra-services/auth-service"
    ["notification-service"]="infra-services/notification-service"
    ["file-service"]="infra-services/file-service"
    ["audit-log-service"]="infra-services/audit-log-service"
    ["payment-gateway-service"]="infra-services/payment-gateway-service"
    ["credit-wallet-service"]="infra-services/credit-wallet-service"
    ["crbt-campaign-service"]="business-services/crbt-campaign-service"
    ["crbt-community-library"]="business-services/crbt-community-library"
    ["audio-generation-service"]="business-services/audio-generation-service"
    ["crbt-credit-transaction-service"]="business-services/crbt-credit-transaction-service"
    ["crbt-core-adapter"]="business-services/crbt-core-adapter"
)

# Determine Maven Command
MVN_CMD=""
if [[ -f "$ROOT/mvnw" ]]; then
    chmod +x "$ROOT/mvnw"
    MVN_CMD="$ROOT/mvnw"
elif command -v mvn &>/dev/null; then
    MVN_CMD="mvn"
else
    fail "Khong tim thay mvnw hoac mvn trong PATH"
fi

# Parse arguments
TARGET_SERVICES=()
MAVEN_PROJECTS=()

if [[ $# -eq 0 ]]; then
    # No arguments: build all services
    for svc in "${!SERVICE_MAP[@]}"; do
        TARGET_SERVICES+=("$svc")
        MAVEN_PROJECTS+=("${SERVICE_MAP[$svc]}")
    done
    step "Yeu cau: Build & Deploy TAT CA các service"
else
    # Arguments specified: validate and map
    step "Yeu cau: Build & Deploy chi cac service sau:"
    for arg in "$@"; do
        if [[ -n "${SERVICE_MAP[$arg]:-}" ]]; then
            TARGET_SERVICES+=("$arg")
            MAVEN_PROJECTS+=("${SERVICE_MAP[$arg]}")
            info "  - $arg"
        else
            fail "Service '$arg' khong hop le. Cac service hop le la: \n$(echo "${!SERVICE_MAP[@]}" | tr ' ' '\n' | sed 's/^/  - /')"
        fi
    done
fi

# --- 1. Maven build ---
step "Maven clean package (skipTests)"
cd "$ROOT"
if [[ $# -eq 0 ]]; then
    # Full build
    "$MVN_CMD" clean package -DskipTests --batch-mode
else
    # Build only specified modules and their dependencies (-am)
    IFS=','
    project_list="${MAVEN_PROJECTS[*]}"
    unset IFS
    "$MVN_CMD" clean package -pl "$project_list" -am -DskipTests --batch-mode
fi
ok "Maven build thanh cong"

# --- 2. Verify built JARs ---
step "Kiem tra cac file JAR"
for svc in "${TARGET_SERVICES[@]}"; do
    path="${SERVICE_MAP[$svc]}"
    target_dir="$ROOT/$path/target"
    jar=$(find "$target_dir" -maxdepth 1 -name "*.jar" ! -name "*-sources.jar" 2>/dev/null | head -1)
    if [[ -z "$jar" ]]; then
        fail "Khong tim thay file JAR cho service: $svc o $target_dir"
    else
        ok "$svc -> $(basename "$jar")"
    fi
done

# --- 3. Docker Compose build & up ---
step "Docker Compose build + up (Chi chay cac service Java)"
cd "$ROOT"

info "Building docker images: docker compose build --parallel ${TARGET_SERVICES[*]}"
docker compose build --parallel "${TARGET_SERVICES[@]}" || fail "Docker compose build that bai"
ok "Docker compose build xong"

info "Running: docker compose up -d ${TARGET_SERVICES[*]}"
docker compose up -d "${TARGET_SERVICES[@]}" || fail "Docker compose up that bai"
ok "Cac service da khoi dong/restart thanh cong!"

# --- 4. Status ---
step "Trang thai cac container dang chay"
docker compose ps "${TARGET_SERVICES[@]}"

echo -e "\n${GREEN}[DONE] Da build & deploy xong cac service Java!${NC}"
