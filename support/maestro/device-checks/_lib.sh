# Shared helpers for the support/maestro/device-checks/*.sh wrappers (#452). Sourced, not run.
# Builds on support/scripts/checks/_lib.sh (s2, wait_for, state, start_playback, screenshot, ...).
# Run a wrapper through emu-verify, whose --check resolves under support/scripts/checks/:
#   support/scripts/emu-verify.sh --check ../../maestro/device-checks/<name>
# shellcheck source=support/scripts/checks/_lib.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/../../scripts/checks/_lib.sh"

DC_DIR="${CHECKS_ROOT}/support/maestro/device-checks"
DC_OUT="${MAESTRO_OUT:-${CHECKS_ROOT}/tmp/maestro}"

# dc_maestro <flow.yaml> [maestro test args...]: runs a flow from this directory on the lane.
dc_maestro() {
    local flow="$1"
    shift
    mkdir -p "$DC_OUT"
    MAESTRO_CLI_NO_ANALYTICS=1 MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED=true \
        "${MAESTRO:-$(command -v maestro || echo "$HOME/.maestro/bin/maestro")}" \
        --device "${MAESTRO_DEVICE:-$("${CHECKS_ROOT}/support/scripts/remote-emu.sh" serial)}" \
        test --test-output-dir "$DC_OUT" "$@" "${DC_DIR}/${flow}" || fail "${flow} failed (output in ${DC_OUT})"
}

# app_crashes: the app's crash and ANR lines in the logcat buffer, if any.
app_crashes() {
    adb_retry logcat -d -b crash,main -v brief 2>/dev/null \
        | grep -E "FATAL EXCEPTION|ANR in ${APP_ID}|Process: ${APP_ID}" || true
}
