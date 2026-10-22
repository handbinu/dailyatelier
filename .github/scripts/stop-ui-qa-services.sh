#!/usr/bin/env bash
set -euo pipefail

workspace="${GITHUB_WORKSPACE:-$(pwd)}"
pid_dir="${CI_PID_DIR:-${workspace}/ci-logs/pids}"

stop_process() {
  local name="$1"
  local pid_file="${pid_dir}/${name}.pid"

  [[ -f "${pid_file}" ]] || return 0

  local pid
  pid="$(<"${pid_file}")"
  if kill -0 "${pid}" 2>/dev/null; then
    kill "${pid}" 2>/dev/null || true
    for _ in $(seq 1 15); do
      kill -0 "${pid}" 2>/dev/null || return 0
      sleep 1
    done
    kill -9 "${pid}" 2>/dev/null || true
  fi
}

stop_process frontend
stop_process backend
