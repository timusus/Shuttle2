#!/usr/bin/env bash
# Sourced by emu-verify.sh and design-shots.sh: the pieces they share for driving an emulator lane.
#
#   emu_resolve_apk      sets APK: $APK if given, else the cached /tmp/s2-apk/<HEAD>.apk, else builds once
#   emu_lane_up          start a lane, export its adb env, reset (unless NO_RESET=1), install $APK, seed
#   maestro_run <flow> <out-dir> [maestro test args...]
#                        runs one flow with a clean <out-dir> as both cwd and --test-output-dir
#                        (#725: Maestro leaves screenshots at the top level AND in timestamped
#                        subfolders, which pile up across runs), so the caller finds this run's
#                        screenshots, and only this run's, under it
#   maestro_collect_shots <out-dir> <dest-dir> [<rc> <fail-dir>]
#                        moves the named PNGs a run took (not Maestro's failed-step screenshots:
#                        screenshot-*, or step-* from Maestro 2.10)
#                        flat into <dest-dir>, then deletes <out-dir>. With a non-zero <rc> and a
#                        <fail-dir>, <out-dir> (failure screenshots, maestro.log, command JSON) is
#                        moved to <fail-dir> instead, replacing the previous failure there
#
# The caller defines step(), LOG, REPO_ROOT and LANE_STARTED, and sets APK, NO_RESET, NO_SEED,
# REMOTE, REMOTE_BUILD, SEED_FIXTURE (a seed-test-media.sh fixture, default playback) and
# MAESTRO_DEVICE_ARGS (e.g. --device <serial>) as needed.

emu_resolve_apk() {
    local head_sha cache_apk gradle
    if [ -n "${APK:-}" ]; then
        [ -f "$APK" ] || { echo "emu-lane: no APK at $APK" >&2; return 1; }
        echo "emu-lane: using given APK $APK"
        return 0
    fi
    head_sha="$(git rev-parse HEAD)"
    cache_apk="/tmp/s2-apk/${head_sha}.apk"
    if [ -f "$cache_apk" ] && [ -z "$(git status --porcelain)" ]; then
        APK="$cache_apk"
        echo "emu-lane: reusing cached APK for HEAD (${head_sha:0:12}) at $APK"
        return 0
    fi
    gradle=(./gradlew); [ "${REMOTE_BUILD:-0}" = 1 ] && gradle=(support/scripts/remote-build.sh)
    step "building assembleDebug" "${gradle[@]}" :android:app:assembleDebug -q || return 1
    APK=android/app/build/outputs/apk/debug/app-debug.apk
    if [ -z "$(git status --porcelain)" ]; then
        # Only a clean tree may populate the cache: a dirty build is not what HEAD contains.
        mkdir -p /tmp/s2-apk
        cp "$APK" "$cache_apk"
        APK="$cache_apk"
        echo "emu-lane: built and cached APK at $APK"
    else
        echo "emu-lane: built $APK (dirty tree, not cached)"
    fi
}

emu_lane_up() {
    local env_out
    step "remote-emu: start" support/scripts/remote-emu.sh start || return 1
    # shellcheck disable=SC2034 # read by the caller's cleanup trap
    LANE_STARTED=1
    env_out="$(support/scripts/remote-emu.sh env)" || { echo "emu-lane: remote-emu.sh env FAILED" >&2; return 1; }
    echo "$env_out" >>"$LOG"
    eval "$env_out"
    echo "emu-lane: lane env exported"
    if [ "${NO_RESET:-0}" = "1" ]; then
        echo "emu-lane: --no-reset set, skipping remote-emu.sh reset"
    else
        step "remote-emu: reset" support/scripts/remote-emu.sh reset || return 1
    fi
    step "remote-emu: install" support/scripts/remote-emu.sh install "$APK" || return 1
    if [ -n "${REMOTE:-}" ]; then
        step "seed-remote-provider: $REMOTE" support/scripts/seed-remote-provider.sh "$REMOTE" || return 1
        export S2_REMOTE="$REMOTE"
    elif [ "${NO_SEED:-0}" = "1" ]; then
        echo "emu-lane: --no-seed set, skipping seed-test-media.sh"
    else
        step "seed-test-media: ${SEED_FIXTURE:-playback} fixture" support/scripts/seed-test-media.sh "${SEED_FIXTURE:-playback}" --skip-onboarding --if-needed || return 1
    fi
}

# maestro_bin: the Maestro CLI path, or a loud failure (#594). A missing Maestro must stop the run
# with an install hint, not surface as "no such file" mid-flow or invite a uiautomator fallback.
maestro_bin() {
    local bin="${MAESTRO:-$(command -v maestro || echo "$HOME/.maestro/bin/maestro")}"
    if [ ! -x "$bin" ]; then
        echo "emu-lane: maestro not found at '$bin' (tried \$MAESTRO, PATH and $HOME/.maestro/bin/maestro)" >&2
        echo "emu-lane: install the mobile.dev Maestro: brew install mobile-dev-inc/tap/maestro" >&2
        echo "emu-lane: (the homebrew-cask 'maestro' is an unrelated app)" >&2
        return 1
    fi
    printf '%s\n' "$bin"
}

maestro_run() {
    local flow="$1" out="$2" maestro
    shift 2
    case "$flow" in /*) ;; *) flow="${REPO_ROOT}/${flow}" ;; esac
    maestro="$(maestro_bin)" || return 1
    rm -rf -- "$out"
    mkdir -p "$out"
    (
        cd "$out" || exit 1
        MAESTRO_CLI_NO_ANALYTICS=1 MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED=true \
            "$maestro" ${MAESTRO_DEVICE_ARGS[@]+"${MAESTRO_DEVICE_ARGS[@]}"} test --test-output-dir "$out" "$@" "$flow" 2>&1
    )
}

# prune_run_dirs <dir>: drops the timestamped run folders Maestro (and earlier emu-verify runs) left
# directly under <dir>.
prune_run_dirs() {
    find "$1" -mindepth 1 -maxdepth 1 -type d \( -name '20*' -o -name '.run-*' \) -exec rm -rf -- {} + 2>/dev/null
    return 0
}

maestro_collect_shots() {
    local out="$1" dest="$2" rc="${3:-0}" fail_dir="${4:-}" f
    mkdir -p "$dest"
    while IFS= read -r f; do
        mv -f "$f" "$dest/"
    done < <(find "$out" -name '*.png' ! -name 'screenshot-*' ! -name 'step-*' 2>/dev/null)
    if [ "$rc" -ne 0 ] && [ -n "$fail_dir" ] && [ -d "$out" ]; then
        rm -rf -- "$fail_dir"
        mkdir -p "$(dirname "$fail_dir")"
        mv -f "$out" "$fail_dir"
    else
        rm -rf -- "$out"
    fi
}
