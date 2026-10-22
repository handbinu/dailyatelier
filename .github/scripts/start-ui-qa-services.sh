#!/usr/bin/env bash
set -euo pipefail

workspace="${GITHUB_WORKSPACE:-$(pwd)}"
log_dir="${CI_LOG_DIR:-${workspace}/ci-logs}"
pid_dir="${CI_PID_DIR:-${log_dir}/pids}"
mysql_host="${MYSQL_HOST:-127.0.0.1}"
mysql_port="${MYSQL_PORT:-3306}"
mysql_user="${DB_USERNAME:?DB_USERNAME is required}"
mysql_password="${DB_PASSWORD:?DB_PASSWORD is required}"

mkdir -p "${log_dir}" "${pid_dir}"

wait_for_mysql() {
  local attempt
  for attempt in $(seq 1 60); do
    if MYSQL_PWD="${mysql_password}" mysqladmin \
      --protocol=TCP --host="${mysql_host}" --port="${mysql_port}" --user="${mysql_user}" ping --silent; then
      return 0
    fi
    sleep 1
  done

  echo "MySQL readiness timed out after 60 seconds." >&2
  return 1
}

wait_for_url() {
  local url="$1"
  local name="$2"
  local attempt
  for attempt in $(seq 1 90); do
    if curl --fail --silent --show-error "${url}" >/dev/null; then
      return 0
    fi
    sleep 1
  done

  echo "${name} readiness timed out: ${url}" >&2
  return 1
}

wait_for_mysql

(
  cd "${workspace}/backend"
  bash ./gradlew bootRun
) >"${log_dir}/backend.log" 2>&1 &
echo $! >"${pid_dir}/backend.pid"

wait_for_url "${UI_QA_API_BASE_URL:-http://127.0.0.1:8080}/api/arts" "Backend API"

(
  cd "${workspace}/frontend"
  npm run dev -- --host 127.0.0.1 --port 5173 --strictPort
) >"${log_dir}/frontend.log" 2>&1 &
echo $! >"${pid_dir}/frontend.pid"

wait_for_url "${UI_QA_BASE_URL:-http://127.0.0.1:5173}" "Frontend"
