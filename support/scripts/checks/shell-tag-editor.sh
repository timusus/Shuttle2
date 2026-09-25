#!/usr/bin/env bash
# #377: the Compose shell's song info and tag editor, driven by taps with Maestro on the debug
# ShellActivity (support/maestro/shell-tag-editor.yaml): a song's info opens from its row menu on the
# Songs tab, and its title edited from the selection's Edit Tags shows in the list once written. The
# new title is non-ASCII (#388), so the check then reads it back from the file and after a rescan. Tag
# writes need a real SAF-backed TagLib song, so this uses setup_taglib_provider and restores the
# `playback` fixture on exit like tag-edit-queued.sh.
source "$(dirname "$0")/_lib.sh"

device="${MAESTRO_DEVICE:-$("${CHECKS_ROOT}/support/scripts/remote-emu.sh" serial)}"
out="${MAESTRO_OUT:-${CHECKS_ROOT}/tmp/maestro}"
mkdir -p "$out"

setup_taglib_provider
trap restore_playback_fixture EXIT

wake_screen
adb shell am start -W -f 0x10008000 -n "${APP_ID}/com.simplecityapps.shuttle.ui.shell.ShellActivity" >/dev/null 2>&1 || fail "could not launch ShellActivity"
MAESTRO_CLI_NO_ANALYTICS=1 MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED=true \
    "${MAESTRO:-$(command -v maestro || echo "$HOME/.maestro/bin/maestro")}" --device "$device" test --test-output-dir "$out" \
    "${CHECKS_ROOT}/support/maestro/shell-tag-editor.yaml" || fail "the Maestro flow failed (output in ${out})"

# The title the flow typed, as the file now holds it (taglib1.mp3 is "Taglib One")
title="Café — Ünïcødé 日本語"
adb_retry pull /sdcard/Music/taglib-seed/taglib1.mp3 "${out}/taglib1.mp3" >/dev/null || fail "could not pull the edited file"
written="$(ffprobe -v error -show_entries format_tags=title -of default=nw=1:nk=1 "${out}/taglib1.mp3")"
[ "$written" = "$title" ] || fail "the file's title reads back as '${written}', not '${title}'"

# A rescan reads the title back from the file into the library
s2 IMPORT >/dev/null
wait_for 30 "not s['libraryImporting']"
wake_screen
MAESTRO_CLI_NO_ANALYTICS=1 MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED=true \
    "${MAESTRO:-$(command -v maestro || echo "$HOME/.maestro/bin/maestro")}" --device "$device" test --test-output-dir "$out" \
    "${CHECKS_ROOT}/support/maestro/shell-tag-editor-rescanned.yaml" || fail "the Songs tab doesn't show '${title}' after a rescan (output in ${out})"
pass
