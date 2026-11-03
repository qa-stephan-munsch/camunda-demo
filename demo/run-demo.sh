#!/usr/bin/env bash
#
# Starts the Kafka document routing demo in one terminal:
# local Camunda cluster incl. connectors runtime (if needed), Kafka and Kafka UI in Docker,
# build, the document service, and its coloured log. Ctrl+C stops the service
# (the cluster and the Kafka containers keep running).
#
# Usage: demo/run-demo.sh [--no-build] [--verbose]
#   --no-build   start the jar from the last build (faster)
#   --verbose    show every log line, not only the demo's own output, warnings and errors
#
# Works with the bash 3.2 that ships with macOS.

set -euo pipefail

DEMO_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$DEMO_DIR/.." && pwd)"
LOG_DIR="$DEMO_DIR/logs"
VERSION="0.1.0-SNAPSHOT"
CAMUNDA_URL="http://localhost:8080"
CONNECTORS_URL="http://localhost:8086"
KAFKA_UI_URL="http://localhost:8090"
STARTUP_TIMEOUT_SECONDS=120

SERVICE=document-service
COLOR=36   # cyan

BUILD=true
VERBOSE=false
for arg in "$@"; do
  case "$arg" in
    --no-build) BUILD=false ;;
    --verbose) VERBOSE=true ;;
    -h|--help) sed -n '3,12p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "Unknown option: $arg (see --help)" >&2; exit 1 ;;
  esac
done

PIDS=()

info()  { printf '\033[1m▶ %s\033[0m\n' "$*"; }
fail()  { printf '\033[31m✖ %s\033[0m\n' "$*" >&2; exit 1; }

stop_services() {
  trap - EXIT INT TERM
  if [ ${#PIDS[@]} -gt 0 ]; then
    echo
    info "Stopping $SERVICE"
    exec 2>/dev/null   # bash would report every stopped log pipeline as "Terminated"
    kill "${PIDS[@]}" 2>/dev/null || true
    # the log pipeline: $! only covers its last process, so stop all direct children
    pkill -P $$ 2>/dev/null || true
    wait 2>/dev/null || true
  fi
}
trap stop_services EXIT
trap 'exit 130' INT TERM

# --- Preconditions ------------------------------------------------------------

command -v java >/dev/null || fail "java not found (Java 21+ required)"
command -v curl >/dev/null || fail "curl not found"
command -v docker >/dev/null || fail "docker not found (Docker Desktop or Colima required)"
docker info >/dev/null 2>&1 || fail "Docker is not running"

# Docker Desktop brings the compose plugin; Homebrew installs the standalone docker-compose
if docker compose version >/dev/null 2>&1; then
  COMPOSE=(docker compose)
elif command -v docker-compose >/dev/null; then
  COMPOSE=(docker-compose)
else
  fail "Docker Compose not found (docker compose plugin or docker-compose)"
fi

# exactly the command this script uses to start the service, so greps or tails on the jar name don't match
SERVICE_COMMAND="^java -jar .*/$SERVICE-$VERSION\.jar$"
if pgrep -f "$SERVICE_COMMAND" >/dev/null; then
  fail "$SERVICE is already running: stop it first (pgrep -fl '$SERVICE_COMMAND')"
fi

# --- Camunda cluster ----------------------------------------------------------

cluster_up() { [ "$(curl -s -o /dev/null -w '%{http_code}' "$CAMUNDA_URL/v2/topology")" = "200" ]; }
connectors_up() { [ "$(curl -s -o /dev/null -w '%{http_code}' "$CONNECTORS_URL/actuator/health")" = "200" ]; }

if cluster_up; then
  info "Camunda cluster is running at $CAMUNDA_URL"
else
  command -v c8ctl >/dev/null || fail "No cluster at $CAMUNDA_URL and c8ctl not found (npm install -g @camunda8/cli)"
  info "Starting local Camunda cluster (c8ctl cluster start, first run downloads it)"
  if [ -z "${JAVA_HOME:-}" ] && [ -x /usr/libexec/java_home ]; then
    export JAVA_HOME="$(/usr/libexec/java_home)"
  fi
  c8ctl cluster start
  for _ in $(seq 1 60); do cluster_up && break; sleep 2; done
  cluster_up || fail "Cluster did not come up, see: c8ctl cluster logs"
fi

# the Kafka connectors of the process run in the connectors runtime that c8run starts next to the cluster
for _ in $(seq 1 60); do connectors_up && break; sleep 2; done
connectors_up || fail "Connectors runtime not reachable at $CONNECTORS_URL, see: c8ctl cluster logs"

# --- Kafka --------------------------------------------------------------------

info "Starting Kafka and Kafka UI (${COMPOSE[*]} up, first run downloads the images)"
"${COMPOSE[@]}" -f "$DEMO_DIR/docker-compose.yml" up -d --wait || fail "Kafka did not start, see: ${COMPOSE[*]} -f demo/docker-compose.yml logs"

# --- Build --------------------------------------------------------------------

if $BUILD; then
  info "Building (./mvnw install -DskipTests)"
  (cd "$PROJECT_DIR" && ./mvnw -q install -DskipTests) || fail "Build failed"
fi

# --- Start service ------------------------------------------------------------

mkdir -p "$LOG_DIR"

wait_until_started() {
  local service=$1 log=$2 pid=$3
  for _ in $(seq 1 "$STARTUP_TIMEOUT_SECONDS"); do
    grep -q "Started .*Application" "$log" 2>/dev/null && return 0
    if ! kill -0 "$pid" 2>/dev/null || grep -qE "APPLICATION FAILED TO START|Exception in thread \"main\"" "$log" 2>/dev/null; then
      tail -20 "$log" >&2
      fail "$service failed to start (full log: $log)"
    fi
    sleep 1
  done
  fail "$service did not start within ${STARTUP_TIMEOUT_SECONDS}s (log: $log)"
}

jar="$PROJECT_DIR/$SERVICE/target/$SERVICE-$VERSION.jar"
[ -f "$jar" ] || fail "$jar not found: run without --no-build"
log="$LOG_DIR/$SERVICE.log"
info "Starting $SERVICE (deploys document-routing)"
java -jar "$jar" > "$log" 2>&1 &
PIDS+=($!)
wait_until_started "$SERVICE" "$log" "$!"

# --- Log ----------------------------------------------------------------------

cat <<EOF

$(printf '\033[1;32m✔ Demo is running.\033[0m') Log: demo/logs  ·  Operate: $CAMUNDA_URL/operate (demo/demo)  ·  Kafka UI: $KAFKA_UI_URL

  Send a document from another terminal in the repository root (or produce one in Kafka UI, topic documents-in):
    demo/send-document.sh demo/samples/invoice.json
    demo/send-document.sh demo/samples/complaint.json

  Read the results in Kafka UI: Topics → documents-out → Messages.

  Ctrl+C stops $SERVICE.

EOF

# Turns "2026-10-26T13:46:55.873+01:00  INFO 123 --- [app] [thread] d.q.i.x.Logger  : message" into
# "[service] message"; without --verbose, only the demo's own loggers, warnings and errors pass.
format_log() {
  local name=$1 color=$2
  awk -v name="$name" -v color="$color" -v verbose="$VERBOSE" '
    {
      line = $0
      level = ""; logger = ""; msg = line
      if (match(line, /^[0-9T:.+-]+ +[A-Z]+ [0-9]+ --- \[[^]]*\] \[[^]]*\] [^ ]+ *: /)) {
        split(line, parts, " +")
        level = parts[2]
        msg = substr(line, RLENGTH + 1)
        head = substr(line, 1, RLENGTH)
        sub(/ *: $/, "", head)
        n = split(head, words, " +")
        logger = words[n]
      }
      own = (logger ~ /^d\.q\.i\./)
      problem = (level == "WARN" || level == "ERROR")
      if (verbose == "true" || own || problem) {
        prefix = sprintf("\033[%sm%-18s\033[0m", color, "[" name "]")
        if (problem) msg = "\033[31m" level " " msg "\033[0m"
        printf "%s %s\n", prefix, msg
        fflush()
      }
    }'
}

# start at the end of the file: startup lines were already checked above
tail -n 0 -F "$log" 2>/dev/null | format_log "$SERVICE" "$COLOR" &
PIDS+=($!)

# Stay in the foreground until Ctrl+C or until the service dies.
while true; do
  if ! kill -0 "${PIDS[0]}" 2>/dev/null; then
    fail "$SERVICE stopped unexpectedly, see demo/logs/$SERVICE.log"
  fi
  sleep 2
done
