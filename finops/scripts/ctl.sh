#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVICE_DIR="$(dirname "${SCRIPT_DIR}")"
ROOT_DIR="$(dirname "${SERVICE_DIR}")"

yaml_val() {
    local key="$1" file="${SERVICE_DIR}/config.yaml"
    [ -f "${file}" ] || return 0
    YAML_FILE="${file}" YAML_KEY="${key}" node - <<'NODE' 2>/dev/null || true
const fs = require('fs');
const wanted = process.env.YAML_KEY.split('.');
const stack = [];

function resolveSpringPlaceholder(value) {
  const match = value.match(/^\$\{([A-Za-z_][A-Za-z0-9_]*)(?::([^}]*))?\}$/);
  if (!match) {
    return value;
  }
  const envValue = process.env[match[1]];
  if (envValue !== undefined && envValue !== '') {
    return envValue;
  }
  return match[2] ?? '';
}

for (const rawLine of fs.readFileSync(process.env.YAML_FILE, 'utf8').split(/\r?\n/)) {
  const withoutComment = rawLine.replace(/\s+#.*$/, '');
  if (!withoutComment.trim() || withoutComment.trimStart().startsWith('#')) {
    continue;
  }
  const match = withoutComment.match(/^(\s*)([^:]+):\s*(.*)$/);
  if (!match) {
    continue;
  }
  const indent = match[1].length;
  const key = match[2].trim();
  let value = match[3].trim();
  while (stack.length && stack[stack.length - 1].indent >= indent) {
    stack.pop();
  }
  const path = [...stack.map((entry) => entry.key), key];
  if (path.join('.') === wanted.join('.') && value) {
    value = value.replace(/^['"]|['"]$/g, '');
    value = resolveSpringPlaceholder(value);
    process.stdout.write(value);
    process.exit(0);
  }
  if (!value) {
    stack.push({ indent, key });
  }
}
NODE
}

FINOPS_PORT="${FINOPS_PORT:-$(yaml_val server.port)}"
FINOPS_PORT="${FINOPS_PORT:-8097}"
FINOPS_GATEWAY_BASE_URL="${FINOPS_GATEWAY_BASE_URL:-$(yaml_val finops.gateway.base-url)}"
FINOPS_GATEWAY_BASE_URL="${FINOPS_GATEWAY_BASE_URL:-http://127.0.0.1:3000}"
FINOPS_GATEWAY_SECRET_KEY="${FINOPS_GATEWAY_SECRET_KEY:-$(yaml_val finops.gateway.secret-key)}"
FINOPS_GATEWAY_USER_ID="${FINOPS_GATEWAY_USER_ID:-$(yaml_val finops.gateway.user-id)}"
MVN="${MVN:-mvn}"

if ! command -v "${MVN}" &>/dev/null; then
    for candidate in /tmp/apache-maven-3.9.6/bin/mvn /usr/local/bin/mvn; do
        if [ -x "${candidate}" ]; then
            MVN="${candidate}"
            break
        fi
    done
fi

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[0;33m'; NC='\033[0m'
log_info()  { echo -e "${GREEN}[INFO]${NC}  $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }
log_ok()    { echo -e "${GREEN}[OK]${NC}    $1"; }
log_fail()  { echo -e "${RED}[FAIL]${NC}  $1"; }

LOG_DIR="${SERVICE_DIR}/logs"
PID_FILE="${LOG_DIR}/finops.pid"
DAEMON_HELPER="${ROOT_DIR}/scripts/lib/service-daemon.sh"

# shellcheck source=/dev/null
source "${DAEMON_HELPER}"

check_port() { daemon_port_has_listener "$1"; }

stop_port() {
    local port=$1 name=$2
    if check_port "${port}"; then
        daemon_stop_listener_port "${port}" "${name}" || true
    fi
}

wait_http_ok() {
    local name="$1" url="$2" attempts="${3:-30}" delay="${4:-1}"
    for ((i=1; i<=attempts; i++)); do
        curl -fsS "${url}" >/dev/null 2>&1 && return 0
        sleep "${delay}"
    done
    log_error "${name} health check failed: ${url}"
    return 1
}

build_service() {
    local jar="${SERVICE_DIR}/target/finops.jar"
    if [ -f "${jar}" ]; then
        local newest_src
        newest_src="$(find "${SERVICE_DIR}/src" -type f \( -name '*.java' -o -name '*.yaml' -o -name '*.yml' \) -newer "${jar}" -print -quit 2>/dev/null)"
        if [ -z "${newest_src}" ] && [ ! "${SERVICE_DIR}/config.yaml" -nt "${jar}" ]; then
            log_info "JAR is up-to-date, skipping build"
            return 0
        fi
    fi

    log_info "Building finops..."
    cd "${SERVICE_DIR}"
    "${MVN}" package -DskipTests -q || {
        log_error "Maven build failed"
        return 1
    }
}

do_startup() {
    local mode="${1:-foreground}"

    if [ "${mode}" = "background" ] && daemon_is_running "${PID_FILE}"; then
        local existing_pid
        existing_pid="$(daemon_read_pid "${PID_FILE}")"
        if curl -fsS "http://127.0.0.1:${FINOPS_PORT}/actuator/health" >/dev/null 2>&1; then
            log_info "finops already running (PID: ${existing_pid})"
            return 0
        fi
        log_warn "Managed finops process exists but health check failed; restarting"
        daemon_stop "${PID_FILE}" "finops" 5 || true
    fi

    if check_port "${FINOPS_PORT}" && ! daemon_is_running "${PID_FILE}"; then
        log_warn "finops port ${FINOPS_PORT} is occupied without a managed pidfile; using legacy port-based stop"
        stop_port "${FINOPS_PORT}" "finops"
    fi

    build_service
    local jar="${SERVICE_DIR}/target/finops.jar"
    [ -f "${jar}" ] || { log_error "JAR not found: ${jar}"; return 1; }

    log_info "Starting finops at http://127.0.0.1:${FINOPS_PORT}"
    cd "${SERVICE_DIR}"

    if [ "${mode}" = "background" ]; then
        local log_file="${LOG_DIR}/finops.log"
        SERVICE_PID="$(daemon_start "${PID_FILE}" "${log_file}" env \
            CONFIG_PATH="${SERVICE_DIR}/config.yaml" \
            FINOPS_GATEWAY_BASE_URL="${FINOPS_GATEWAY_BASE_URL}" \
            FINOPS_GATEWAY_SECRET_KEY="${FINOPS_GATEWAY_SECRET_KEY}" \
            FINOPS_GATEWAY_USER_ID="${FINOPS_GATEWAY_USER_ID}" \
            java -Dserver.port="${FINOPS_PORT}" -jar "${jar}")"
        if ! kill -0 "${SERVICE_PID}" 2>/dev/null; then
            log_error "Failed to start finops"
            return 1
        fi
        if ! wait_http_ok "finops" "http://127.0.0.1:${FINOPS_PORT}/actuator/health" 40 1; then
            daemon_stop "${PID_FILE}" "finops" 5 || true
            return 1
        fi
        log_info "finops started (PID: ${SERVICE_PID}, log: ${log_file})"
    else
        exec env \
            CONFIG_PATH="${SERVICE_DIR}/config.yaml" \
            FINOPS_GATEWAY_BASE_URL="${FINOPS_GATEWAY_BASE_URL}" \
            FINOPS_GATEWAY_SECRET_KEY="${FINOPS_GATEWAY_SECRET_KEY}" \
            FINOPS_GATEWAY_USER_ID="${FINOPS_GATEWAY_USER_ID}" \
            java -Dserver.port="${FINOPS_PORT}" -jar "${jar}"
    fi
}

do_shutdown() {
    daemon_stop "${PID_FILE}" "finops" 20 || true
    if ! daemon_wait_for_port_release "${FINOPS_PORT}" 20 0.1 && check_port "${FINOPS_PORT}" && ! daemon_is_running "${PID_FILE}"; then
        log_warn "finops port ${FINOPS_PORT} is occupied without a managed pidfile; using legacy port-based stop"
        stop_port "${FINOPS_PORT}" "finops"
    fi
    rm -f "${PID_FILE}" 2>/dev/null || true
}

do_status() {
    if daemon_is_running "${PID_FILE}"; then
        local pid
        pid="$(daemon_read_pid "${PID_FILE}")"
        if curl -fsS "http://127.0.0.1:${FINOPS_PORT}/actuator/health" >/dev/null 2>&1; then
            log_ok "finops running (http://localhost:${FINOPS_PORT}, PID: ${pid})"
        else
            log_warn "finops process running (PID: ${pid}) but health check failed"
            return 1
        fi
    elif check_port "${FINOPS_PORT}"; then
        log_warn "finops port open on ${FINOPS_PORT} but service is unmanaged (missing/stale pidfile)"
        return 1
    else
        log_fail "finops not running on port ${FINOPS_PORT}"
        return 1
    fi
}

do_restart() {
    do_shutdown
    do_startup "${MODE}"
}

usage() {
    cat <<EOF_USAGE
Usage: $(basename "$0") <action> [--foreground|--background]

Actions:
  startup     Start finops
  shutdown    Stop finops
  status      Check finops status
  restart     Restart finops
EOF_USAGE
    exit 1
}

ACTION="${1:-}"
[ -z "${ACTION}" ] && usage
shift

MODE="background"
for arg in "$@"; do
    case "${arg}" in
        --background) MODE="background" ;;
        --foreground) MODE="foreground" ;;
    esac
done

case "${ACTION}" in
    startup) do_startup "${MODE}" ;;
    shutdown) do_shutdown ;;
    status) do_status ;;
    restart) do_restart ;;
    -h|--help|help) usage ;;
    *) log_error "Unknown action: ${ACTION}"; usage ;;
esac
