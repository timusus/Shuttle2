#!/usr/bin/env bash
# Usage: lease --class build -- env -u LEASE_HELD lease --class build -- support/bench/gradle-bench-all.sh <scenario>...
# Runs scenarios with 2 warm-ups / 6 iterations, backing up and restoring ~/.gradle/gradle.properties and
# (the caller edits build.gradle.kts for -Ps2.testHeap and reverts it) on every exit path. Output: /tmp/gradle-bench/<scenario>/.
set -uo pipefail
props=$HOME/.gradle/gradle.properties
cp "$props" /tmp/gradle.properties.benchbak
restore() { cp /tmp/gradle.properties.benchbak "$props"; }
trap restore EXIT
mkdir -p /tmp/gradle-bench/gc
for _ in $(seq 120); do
  [[ $(sysctl -n vm.loadavg | awk '{print int($2)}') -lt 4 ]] && break
  sleep 10
done
[[ $(sysctl -n vm.loadavg | awk '{print int($2)}') -lt 4 ]] || { echo "not quiet"; exit 3; }
sysctl vm.swapusage
for s in "$@"; do
  echo "== $s $(date +%T) load $(sysctl -n vm.loadavg)"
  LEASE_HELD=1 gradle-profiler --benchmark --project-dir . --scenario-file support/bench/gradle.scenarios \
    --output-dir "/tmp/gradle-bench/$s" --warmups 2 --iterations 6 "$s" > "/tmp/gradle-bench/$s.log" 2>&1 \
    || echo "FAILED $s"
done
