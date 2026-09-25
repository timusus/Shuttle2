#!/usr/bin/env bash
# #378: the Compose settings, driven by Maestro on MainActivity
# (support/maestro/settings-compose.yaml), then the stored preferences checked: the switch and the
# theme land under their existing keys and formats. Expects a reset lane (defaults everywhere).
source "$(dirname "$0")/_lib.sh"

device="${MAESTRO_DEVICE:-$("${CHECKS_ROOT}/support/scripts/remote-emu.sh" serial)}"
out="${CHECKS_ROOT}/tmp/maestro"
mkdir -p "$out"

prefs() {
    adb_retry shell run-as "$APP_ID" cat "shared_prefs/${APP_ID}_preferences.xml" 2>/dev/null || true
}

adb_retry shell am start -W -n "${APP_ID}/com.simplecityapps.shuttle.ui.MainActivity" >/dev/null 2>&1 || fail "could not launch MainActivity"
MAESTRO_CLI_NO_ANALYTICS=1 MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED=true \
    "${MAESTRO:-$(command -v maestro || echo "$HOME/.maestro/bin/maestro")}" --device "$device" test --test-output-dir "$out" \
    "${CHECKS_ROOT}/support/maestro/settings-compose.yaml" || fail "the Maestro flow failed (output in ${out})"

stored="$(prefs)"
grep -q '<boolean name="pref_theme_extra_dark" value="true"' <<<"$stored" || fail "pref_theme_extra_dark (Pure black) wasn't stored as true"
grep -q '<string name="pref_theme">2</string>' <<<"$stored" || fail "pref_theme wasn't stored as \"2\" (Dark)"
pass
