#!/usr/bin/env bash
# Toggles the "USB DAC direct output" playback setting (support/maestro/settings-usb-dac-direct-output.yaml)
# and checks playback is unaffected. The emulator has no USB DAC to exercise BitPerfectOutput's
# mixer selection, so this only proves the setting exists (API 34+; hidden below it) and that
# turning it on and off doesn't break normal playback.
source "$(dirname "$0")/_lib.sh"

device="${MAESTRO_DEVICE:-$("${CHECKS_ROOT}/support/scripts/remote-emu.sh" serial)}"
out="${CHECKS_ROOT}/tmp/maestro"
mkdir -p "$out"

api_level="$(adb_retry shell getprop ro.build.version.sdk | tr -d '[:space:]')"

run_flow() {
    MAESTRO_CLI_NO_ANALYTICS=1 MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED=true \
        "${MAESTRO:-$(command -v maestro || echo "$HOME/.maestro/bin/maestro")}" --device "$device" test --test-output-dir "$out" \
        "${CHECKS_ROOT}/support/maestro/settings-usb-dac-direct-output.yaml"
}

bit_perfect_pref() {
    adb_retry shell run-as "$APP_ID" cat "shared_prefs/${APP_ID}_preferences.xml" 2>/dev/null | grep -o 'name="pref_bit_perfect_usb"[^/]*' || true
}

start_playback
s2 PAUSE >/dev/null

if [ "$api_level" -lt 34 ]; then
    run_flow || fail "the Maestro flow failed asserting the setting is absent below API 34 (output in ${out})"
    pass
    exit 0
fi

before="$(bit_perfect_pref)"
run_flow || fail "the Maestro flow failed toggling the setting on (output in ${out})"
after_on="$(bit_perfect_pref)"
echo "$after_on" | grep -q 'value="true"' || fail "pref_bit_perfect_usb did not turn on (before: '${before}', after: '${after_on}')"

s2 PLAY >/dev/null
wait_for 5 "s['state'] == 'Playing'"
assert_progressing

s2 PAUSE >/dev/null
run_flow || fail "the Maestro flow failed toggling the setting off (output in ${out})"
after_off="$(bit_perfect_pref)"
echo "$after_off" | grep -q 'value="false"' || fail "pref_bit_perfect_usb did not turn back off (${after_off})"

pass
