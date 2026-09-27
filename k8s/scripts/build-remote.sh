#!/usr/bin/env bash
# Build Shuttle2 on a remote k3s cluster (no docker/colima/rsync/java needed
# on this host) as a one-shot Job.
#
# Flow: ensure ns+PVC -> seed pod -> kubectl cp sources in -> delete seed ->
# apply build Job (SDK bootstraps into PVC on first run, Gradle caches) ->
# stream logs -> kubectl cp APKs back.
#
# Usage: ./build-remote.sh [options] [-- extra gradle args]
#   -t, --task TASK    Gradle task(s) (default: :android:app:assembleDebug)
#       --namespace NS Build namespace   (default: shuttle-build)
#       --keep-job     Do not delete the Job after success
#       --no-sync      Reuse workspace already on the PVC
#   -h, --help
set -eo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
K8S_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO_ROOT="$(cd "$K8S_DIR/.." && pwd)"
# shellcheck disable=SC1091
source "$K8S_DIR/config.env"

TASK="$GRADLE_TASK"
NS="$NAMESPACE"
KEEP_JOB=0
DO_SYNC=1
EXTRA_ARGS=()

while [ $# -gt 0 ]; do
    case "$1" in
        -t | --task) TASK="$2"; shift 2 ;;
        --namespace) NS="$2"; shift 2 ;;
        --keep-job) KEEP_JOB=1; shift ;;
        --no-sync) DO_SYNC=0; shift ;;
        --) shift; EXTRA_ARGS=("$@"); break ;;
        -h | --help) sed -n '2,12p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) echo "error: unknown option: $1 (see --help)" >&2; exit 1 ;;
    esac
done

GRADLE_ARGS="$TASK ${EXTRA_ARGS[*]:-}"
GRADLE_ARGS="${GRADLE_ARGS% }"
# Forward the local git tag as version props: the PVC workspace ships without
# .git (714M), so getVersionFromGitTag() would fall back to 1/1.0.0.
if [[ "$GRADLE_ARGS" != *versionCode* ]]; then
  VTAG="$(git -C "$REPO_ROOT" describe --tags --abbrev=0 --match 'v[0-9]*' 2>/dev/null || true)"
  if [[ "$VTAG" =~ ^v([0-9]+)$ ]]; then
    VCODE="${BASH_REMATCH[1]}"
    VNAME="20${VCODE:0:2}.${VCODE:2:2}.${VCODE:4:2}"
    GRADLE_ARGS="$GRADLE_ARGS -PversionCode=$VCODE -PversionName=$VNAME"
  fi
fi

if [ -n "$KUBE_CONTEXT" ]; then CTX=(--context "$KUBE_CONTEXT"); else CTX=(); fi
kc() { "$KUBECTL" "${CTX[@]}" "$@"; }

CACHE_DIR="$K8S_DIR/.cache-remote"
mkdir -p "$CACHE_DIR"

render() { # template out KEY=val...
    local template="$1" out="$2"; shift 2
    local rendered; rendered="$(cat "$template")"
    local pair key val
    for pair in "$@"; do key="${pair%%=*}"; val="${pair#*=}"
        rendered="${rendered//__${key}__/${val}}"; done
    printf '%s\n' "$rendered" > "$out"
}

echo "==> namespace $NS"
kc get namespace "$NS" >/dev/null 2>&1 || kc create namespace "$NS" >/dev/null

echo "==> PVC $CACHE_PVC ($CACHE_SIZE)"
if ! kc get pvc "$CACHE_PVC" -n "$NS" >/dev/null 2>&1; then
    render "$K8S_DIR/templates/pvc.yaml" "$CACHE_DIR/pvc.yaml" \
        "PVC_NAME=$CACHE_PVC" "NAMESPACE=$NS" "CACHE_SIZE=$CACHE_SIZE"
    kc apply -f "$CACHE_DIR/pvc.yaml"
fi

if [ "$DO_SYNC" -eq 1 ]; then
    SEED="seed-$(date +%Y%m%d-%H%M%S)-$RANDOM"
    render "$K8S_DIR/templates/pod-seed.yaml" "$CACHE_DIR/$SEED.yaml" \
        "POD_NAME=$SEED" "NAMESPACE=$NS" "SEED_IMAGE=$SEED_IMAGE" "PVC_NAME=$CACHE_PVC"
    echo "==> seed pod $SEED"
    kc apply -f "$CACHE_DIR/$SEED.yaml" >/dev/null
    kc wait --for=condition=Ready "pod/$SEED" -n "$NS" --timeout=180s >/dev/null
    echo "==> copying sources (tar via kubectl cp)"
    # kubectl cp needs a tar binary in the target; install it first.
    kc exec "$SEED" -n "$NS" -- sh -c "apk add --no-cache tar > /dev/null 2>&1 || true; rm -rf /mnt/workspace; mkdir -p /mnt/workspace"
    TAR="$CACHE_DIR/sources.tar"
    tar -cf "$TAR" \
        --exclude=.git --exclude=.gradle --exclude=.kotlin --exclude=.idea \
        --exclude='*/build' --exclude=k8s/.cache-remote --exclude=k8s/.workspace \
        --exclude=local.properties --exclude=.DS_Store \
        -C "$REPO_ROOT" .
    kc cp "$TAR" "$NS/$SEED:/mnt/sources.tar" >/dev/null
    kc exec "$SEED" -n "$NS" -- sh -c "tar -xf /mnt/sources.tar -C /mnt/workspace && rm /mnt/sources.tar && echo 'sdk.dir=/mnt/sdk' > /mnt/workspace/local.properties && ls /mnt/workspace | head -5"
    kc delete pod "$SEED" -n "$NS" --wait=true >/dev/null
    rm -f "$TAR" "$CACHE_DIR/$SEED.yaml"
else
    echo "==> reusing workspace on PVC (--no-sync)"
fi

JOB="shuttle-build-$(date +%Y%m%d-%H%M%S)-$RANDOM"
render "$K8S_DIR/templates/job-build-remote.yaml" "$CACHE_DIR/$JOB.yaml" \
    "JOB_NAME=$JOB" "NAMESPACE=$NS" "DEADLINE=$JOB_DEADLINE_SECONDS" \
    "BASE_IMAGE=$REMOTE_BASE_IMAGE" "GRADLE_ARGS=$GRADLE_ARGS" \
    "GRADLE_OPTS_VALUE=$GRADLE_OPTS_VALUE" "CPU_LIMIT=$JOB_CPU_LIMIT" \
    "MEM_LIMIT=$JOB_MEM_LIMIT" "PVC_NAME=$CACHE_PVC"

echo "==> job $JOB (task: $GRADLE_ARGS)"
kc apply -f "$CACHE_DIR/$JOB.yaml" >/dev/null

echo "==> waiting for job completion (deadline ${JOB_DEADLINE_SECONDS}s)"
RC=""
START="$(date +%s)"
while true; do
    SUCCEEDED="$(kc get job "$JOB" -n "$NS" -o jsonpath='{.status.succeeded}' 2>/dev/null || true)"
    FAILED="$(kc get job "$JOB" -n "$NS" -o jsonpath='{.status.failed}' 2>/dev/null || true)"
    if [ "$SUCCEEDED" = "1" ]; then RC=0; break; fi
    if [ -n "$FAILED" ] && [ "$FAILED" != "0" ]; then RC=1; break; fi
    NOW="$(date +%s)"
    if [ $((NOW - START)) -ge "$JOB_DEADLINE_SECONDS" ]; then RC=124; break; fi
    sleep 30
done

if [ $RC -eq 0 ]; then
    echo "==> build succeeded; fetching APKs"
    OUT="$REPO_ROOT/android/app/build/outputs"
    mkdir -p "$OUT"
    POD="$(kc get pods -n "$NS" -l job-name="$JOB" -o jsonpath='{.items[0].metadata.name}')"
    kc cp "$NS/$POD:/mnt/workspace/android/app/build/outputs" "$OUT-tmp" >/dev/null 2>&1 || true
    if [ -d "$OUT-tmp" ]; then rm -rf "$OUT"; mv "$OUT-tmp" "$OUT"; fi
    find "$OUT" -type f \( -name '*.apk' -o -name '*.aab' \) 2>/dev/null | head -10
    kc logs -n "$NS" -l job-name="$JOB" > "$CACHE_DIR/last-build.log" 2>/dev/null || true
    [ "$KEEP_JOB" -eq 0 ] && kc delete job "$JOB" -n "$NS" >/dev/null
    rm -f "$CACHE_DIR/$JOB.yaml"
    echo "==> done"
else
    if [ "$RC" = "124" ]; then echo "error: timed out after ${JOB_DEADLINE_SECONDS}s; job kept: $JOB" >&2
    else echo "error: build FAILED; job kept: $JOB" >&2; fi
    echo "--- last 40 log lines ---" >&2
    kc logs -n "$NS" -l job-name="$JOB" --tail=40 2>/dev/null >&2 || true
    echo "  full logs: $KUBECTL -n $NS logs -l job-name=$JOB" >&2
    exit 1
fi
