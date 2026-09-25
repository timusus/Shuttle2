#!/usr/bin/env bash
# #380: the S2 Pro paywall opened from Settings > S2 Pro, driven by Maestro on the debug ShellActivity
# (support/maestro/paywall-settings.yaml): the Pro status and benefits show, and back returns to Settings.
source "$(dirname "$0")/_lib.sh"

device="${MAESTRO_DEVICE:-$("${CHECKS_ROOT}/support/scripts/remote-emu.sh" serial)}"
out="${MAESTRO_OUT:-${CHECKS_ROOT}/tmp/maestro}"
mkdir -p "$out"
# A cleared task, so the shell opens on its start tab rather than where a previous run left it.
adb_retry shell am start -W -f 0x10008000 -n "${APP_ID}/com.simplecityapps.shuttle.ui.shell.ShellActivity" >/dev/null 2>&1 || fail "could not launch ShellActivity"
MAESTRO_CLI_NO_ANALYTICS=1 MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED=true \
    "${MAESTRO:-$(command -v maestro || echo "$HOME/.maestro/bin/maestro")}" --device "$device" test --test-output-dir "$out" \
    "${CHECKS_ROOT}/support/maestro/paywall-settings.yaml" || fail "the Maestro flow failed (output in ${out})"
pass
