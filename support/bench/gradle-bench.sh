#!/usr/bin/env bash
# Usage: support/bench/gradle-bench.sh [--warmups N] [--iterations N] <scenario>...
# Runs each scenario from gradle.scenarios in its own gradle-profiler invocation under one build lease.
# Output lands in /tmp/gradle-bench/<scenario>/ (benchmark.csv, profile logs). Run from the repo root.
set -euo pipefail
warm=1 iter=3
while [[ ${1:-} == --* ]]; do
  case $1 in
    --warmups) warm=$2; shift 2 ;;
    --iterations) iter=$2; shift 2 ;;
    *) echo "unknown $1" >&2; exit 2 ;;
  esac
done
for s in "$@"; do
  lease --class build -- gradle-profiler --benchmark --project-dir . \
    --scenario-file support/bench/gradle.scenarios --output-dir "/tmp/gradle-bench/$s" \
    --warmups "$warm" --iterations "$iter" "$s"
done
