#!/usr/bin/env bash
# smart-deploy.sh — detect changed modules, build and deploy only them
# Usage: ./scripts/smart-deploy.sh <prev-git-sha>

set -euo pipefail

PREV_HEAD=${1:-$(git rev-parse HEAD~1)}
CURR_HEAD=$(git rev-parse HEAD)
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"

CYAN='\033[0;36m'; GREEN='\033[0;32m'; YELLOW='\033[0;33m'; NC='\033[0m'
step() { echo -e "\n${CYAN}==> $*${NC}"; }
ok()   { echo -e "  ${GREEN}[OK]${NC} $*"; }
warn() { echo -e "  ${YELLOW}[WARN]${NC} $*"; }

cd "$PROJECT_DIR"

step "Detecting changed files ($PREV_HEAD...$CURR_HEAD)"
CHANGED=$(git diff --name-only "$PREV_HEAD" "$CURR_HEAD")
echo "$CHANGED"

# common/* changed → must rebuild everything (all services depend on common JARs)
if echo "$CHANGED" | grep -q "^common/"; then
  warn "common/* changed — rebuilding ALL services"
  mvn clean package -DskipTests --batch-mode
  docker compose build
  docker compose up -d
  ok "Full redeploy done"
  exit 0
fi

# docker-compose.yml changed → recreate containers with new config (no rebuild)
if echo "$CHANGED" | grep -q "^docker-compose.yml"; then
  warn "docker-compose.yml changed — recreating all containers"
  docker compose down --remove-orphans # Add this line to stop and remove old containers
  docker compose up -d
  ok "Containers recreated"
  exit 0
fi

# Map: directory prefix → "compose-service:maven-module"
# Order matters: infra first, then app services
declare -a ORDERED_PREFIXES=(
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
  "python-services/ai-media-worker"
)

declare -A SERVICE_MAP=(
  ["infrastructure/eureka-server"]="eureka-server:infrastructure/eureka-server"
  ["infrastructure/config-server"]="config-server:infrastructure/config-server"
  ["infrastructure/api-gateway"]="api-gateway:infrastructure/api-gateway"
  ["infra-services/auth-service"]="auth-service:infra-services/auth-service"
  ["infra-services/notification-service"]="notification-service:infra-services/notification-service"
  ["infra-services/file-service"]="file-service:infra-services/file-service"
  ["infra-services/audit-log-service"]="audit-log-service:infra-services/audit-log-service"
  ["infra-services/payment-gateway-service"]="payment-gateway-service:infra-services/payment-gateway-service"
  ["infra-services/credit-wallet-service"]="credit-wallet-service:infra-services/credit-wallet-service"
  ["business-services/crbt-campaign-service"]="crbt-campaign-service:business-services/crbt-campaign-service"
  ["business-services/crbt-community-library"]="crbt-community-library:business-services/crbt-community-library"
  ["business-services/audio-generation-service"]="audio-generation-service:business-services/audio-generation-service"
  ["business-services/crbt-credit-transaction-service"]="crbt-credit-transaction-service:business-services/crbt-credit-transaction-service"
  ["business-services/crbt-core-adapter"]="crbt-core-adapter:business-services/crbt-core-adapter"
  ["python-services/ai-media-worker"]="ai-media-worker:python"
)

SERVICES_TO_DEPLOY=()
for prefix in "${ORDERED_PREFIXES[@]}"; do
  if echo "$CHANGED" | grep -q "^${prefix}/"; then
    SERVICES_TO_DEPLOY+=("${SERVICE_MAP[$prefix]}")
  fi
done

if [ ${#SERVICES_TO_DEPLOY[@]} -eq 0 ]; then
  ok "No service code changed — nothing to deploy"
  exit 0
fi

step "Services to deploy: ${SERVICES_TO_DEPLOY[*]}"

for entry in "${SERVICES_TO_DEPLOY[@]}"; do
  service="${entry%%:*}"
  module="${entry##*:}"

  step "Deploying $service"

  if [ "$module" = "python" ]; then
    docker compose build "$service"
    docker compose up -d --no-deps "$service"
  else
    # -am: also build all modules this one depends on
    mvn clean package -DskipTests --batch-mode -pl "$module" -am
    docker compose build "$service"
    docker compose up -d --no-deps "$service"
  fi

  ok "$service deployed"
done

step "Done"
docker compose ps
