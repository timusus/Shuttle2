#!/usr/bin/env bash
# #270: editing a song's title through the batch tag editor propagates to the live queue without
# restarting playback -- TagEditorPresenter.save calls playbackManager.updateQueueSongs(...), which
# is expected to swap the ExoPlayer item's metadata in place rather than reload it. Needs a real
# SAF-backed TagLib song (tag writes go through the content:// document, not MediaStore), so uses
# setup_taglib_provider; restored back to the `playback` fixture on exit like m3u-sync.sh.
#
# Two Maestro flows, not one: a real SAF tag write is slow enough that doing both edits back to
# back can outlast the 60 s fixture track's remaining runtime, so the currently-playing song's
# assertions (title updated, position past its pre-edit value, notification) run right after
# tag-edit-playing.yaml alone, before tag-edit-not-playing.yaml's edit risks the track finishing
# and the queue auto-advancing on its own (which is fine -- undisturbed playback, not a bug).
source "$(dirname "$0")/_lib.sh"

device="${MAESTRO_DEVICE:-$("${CHECKS_ROOT}/support/scripts/remote-emu.sh" serial)}"
out="${MAESTRO_OUT:-${CHECKS_ROOT}/tmp/maestro}"
mkdir -p "$out"
maestro_run() {
    MAESTRO_CLI_NO_ANALYTICS=1 MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED=true \
        "${MAESTRO:-$(command -v maestro || echo "$HOME/.maestro/bin/maestro")}" --device "$device" test --test-output-dir "$out" "$1"
}

setup_taglib_provider
trap restore_playback_fixture EXIT

s2 SHUFFLE --ez enabled false >/dev/null
s2 REPEAT --es mode off >/dev/null
s2 PLAY_ALL >/dev/null
wait_for 10 "s['state'] == 'Playing' and s['title'] == 'Taglib One' and s['queueSize'] == 5"
# Let a few seconds of real position build up, so a reset-to-0 after the edit is unmistakable.
sleep 3
before="$(state positionMs)"

# The setup/PLAY_ALL/sleep above is enough idle time for the display to blank (see wake_screen);
# without this the flow's first tap lands on a dark screen.
wake_screen
maestro_run "${CHECKS_ROOT}/support/maestro/tag-edit-playing.yaml" \
    || fail "editing the playing song's title failed (output in ${out})"

wait_for 10 "s['state'] == 'Playing' and s['title'] == 'Taglib One (edited)' and s['queuePosition'] == 0"
after="$(state positionMs)"
[ "$after" -gt "$before" ] || fail "position didn't advance past the pre-edit value (${before} -> ${after} ms) -- looks like the edit restarted playback"
assert_progressing

adb_retry shell dumpsys notification --noredact | grep -q "android.title=String (Taglib One (edited))" \
    || fail "the media notification doesn't show the edited title"

maestro_run "${CHECKS_ROOT}/support/maestro/tag-edit-not-playing.yaml" \
    || fail "editing a queued (not playing) song's title failed (output in ${out})"

# queue_titles reads the queue sheet via a raw uiautomator dump (support/scripts/remote-emu.sh
# dump-texts), which needs the UI idle -- unlike the Maestro taps above, it can't tolerate the
# ticking progress bar, so pause for this one read and resume straight after.
s2 PAUSE >/dev/null
shown="$(queue_titles)"
s2 PLAY >/dev/null
echo "$shown" | grep -q "Taglib Three (edited)" || fail "the queue sheet doesn't show the edited queued (not playing) song: ${shown}"
# Not asserting the now-playing title is still "Taglib One (edited)": the track may have finished
# and auto-advanced to Two while editing Three, which is normal, undisturbed playback, not a bug.
wait_for 5 "s['state'] == 'Playing'"
assert_progressing

pass
