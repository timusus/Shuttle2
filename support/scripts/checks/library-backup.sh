#!/usr/bin/env bash
# Settings > Library backup and restore, end to end on the seeded `playback` fixture. Stats, a
# favourite and a 3-song playlist are written to the app database, backed up through the system
# create-document picker (support/maestro/settings/library-backup.yaml), wiped with `pm clear` and a
# reseed, then restored through the open-document picker (library-restore.yaml). Asserts the stats,
# favourite and playlist come back, that a song bumped above its backed-up count keeps the higher
# count (restore merges), and that restoring a second time changes nothing (no duplicate playlist or
# members). Also screenshots the rows (light and dark), both pickers, and the success and failure
# snackbars into $SHOTS (default tmp/maestro/library-backup-shots).
source "$(dirname "$0")/_lib.sh"

SHOTS="${SHOTS:-${CHECKS_ROOT}/tmp/maestro/library-backup-shots}"
mkdir -p "$SHOTS"
export MAESTRO_OUT="$SHOTS"  # takeScreenshot may only write inside the test output dir
out="$MAESTRO_OUT"
BACKUP_FILE=/sdcard/Download/shuttle-library-backup.json
GARBAGE_FILE=/sdcard/Download/garbage.json
trap 'adb shell cmd uimode night no >/dev/null 2>&1 || true' EXIT

# Runs a flow from support/maestro/settings with the shots dir; extra args are -e KEY=VALUE pairs.
run_flow() {
    local flow="$1"; shift
    maestro_flow "$@" "${CHECKS_ROOT}/support/maestro/settings/${flow}.yaml" \
        || fail "Maestro flow ${flow} failed (output in ${out})"
}

# q "<sql>": runs it on the app's song.db (the debug build is run-as debuggable).
q() { adb shell run-as "$APP_ID" sqlite3 databases/song.db "\"$1\"" | tr -d '\r'; }

# Song stats and the playlist, in a stable text form. Song ids differ after a wipe, so key by name.
stats() { q "select name,playCount,ifnull(lastPlayed,''),ifnull(lastCompleted,''),ifnull(favouritedAt,'') from songs order by name"; }
playlist_dump() { q "select p.name,(select group_concat(s.name,',') from (select s2.name from playlist_song_join j join songs s2 on s2.id=j.songId where j.playlistId=p.id order by j.sortOrder) s) from playlists p where p.name='Backup Test'"; }
playlist_count() { q "select count(*) from playlists where name='Backup Test'"; }
member_count() { q "select count(*) from playlist_song_join j join playlists p on p.id=j.playlistId where p.name='Backup Test'"; }
song_id() { q "select id from songs where name='$1'"; }

# 0. A known start: fresh app data and the fixture imported by the same provider the wipe step reseeds
# with (backup identity includes the provider, so a different one would not match).
restore_playback_fixture

# 1. Light and dark screenshots of the two rows.
adb shell cmd uimode night no >/dev/null
run_flow library-settings-shots -e NN=01 -e MODE=light
adb shell cmd uimode night yes >/dev/null
run_flow library-settings-shots -e NN=02 -e MODE=dark
adb shell cmd uimode night no >/dev/null
echo "  settings rows asserted, light + dark screenshots taken"

# 2. State: stats, one favourite, a 3-song playlist.
adb shell am force-stop "$APP_ID"
q "delete from playlists"
q "update songs set playCount=0,lastPlayed=null,lastCompleted=null,favouritedAt=null"
q "update songs set playCount=3,lastPlayed=1700000003000,lastCompleted=1700000002000 where name='Playback One'"
q "update songs set playCount=5,lastPlayed=1700000005000,lastCompleted=1700000004000 where name='Playback Two'"
q "update songs set favouritedAt=1700000006000 where name='Playback Three'"
q "insert into playlists(name,sortOrder,sortDescending,mediaProvider) values('Backup Test','Position',0,'MediaStore')"
pid="$(q "select id from playlists where name='Backup Test'")"
n=0
for song in "Playback Five" "Playback One" "Playback Three"; do
    q "insert into playlist_song_join(playlistId,songId,sortOrder) values($pid,$(song_id "$song"),$n)"
    n=$((n + 1))
done
before="$(stats)"
before_playlist="$(playlist_dump)"
[ "$before_playlist" = "Backup Test|Playback Five,Playback One,Playback Three" ] || fail "playlist setup wrote: ${before_playlist}"
echo "  state written: $(echo "$before" | grep -c 'Playback') songs, playlist ${before_playlist#*|}"

# 3. Back up.
adb shell rm -f "$BACKUP_FILE"
adb shell "echo 'this is not a backup' > ${GARBAGE_FILE}"
run_flow library-backup
adb shell "test -s ${BACKUP_FILE}" || fail "no backup file at ${BACKUP_FILE}"
adb shell cat "$BACKUP_FILE" | python3 -c 'import json,sys; d=json.load(sys.stdin); assert d' || fail "the backup file is not valid JSON"
echo "  backup saved: $(adb shell stat -c %s "$BACKUP_FILE" | tr -d '\r') bytes"

# 4. Wipe, reseed, confirm the stats are gone.
adb shell pm clear "$APP_ID" >/dev/null
"${CHECKS_ROOT}/support/scripts/seed-test-media.sh" playback --skip-onboarding >/dev/null
wiped="$(stats)"
[ "$wiped" != "$before" ] || fail "wipe did not clear the stats"
[ "$(playlist_count)" = "0" ] || fail "playlist survived the wipe"
echo "  wiped: playlists $(playlist_count), songs reseeded $(echo "$wiped" | grep -c 'Playback')"

# 5. A device-side play count above the backed-up one must survive the merge.
adb shell am force-stop "$APP_ID"
q "update songs set playCount=9 where name='Playback Two'"

# 6. Restore and assert.
run_flow library-restore -e PICKER_SHOT=05-open-picker -e DONE_SHOT=06-restore-success-snackbar
expected="$(printf '%s\n' "$before" | sed 's/^Playback Two|5|/Playback Two|9|/')"
after="$(stats)"
[ "$after" = "$expected" ] || fail "stats after restore differ. expected:
${expected}
observed:
${after}"
[ "$(playlist_dump)" = "$before_playlist" ] || fail "playlist after restore: $(playlist_dump), expected ${before_playlist}"
echo "  restore: stats, favourite and playlist match; bumped song kept its higher count (9)"

# 7. Restore again: nothing changes, no duplicates.
run_flow library-restore -e PICKER_SHOT=07-open-picker-again -e DONE_SHOT=07-restore-again-snackbar
[ "$(stats)" = "$expected" ] || fail "stats changed on the second restore"
[ "$(playlist_count)" = "1" ] || fail "playlist count after second restore: $(playlist_count), expected 1"
[ "$(member_count)" = "3" ] || fail "playlist members after second restore: $(member_count), expected 3"
[ "$(playlist_dump)" = "$before_playlist" ] || fail "playlist order changed on the second restore: $(playlist_dump)"
echo "  second restore: no duplicate playlist or members"

# 8. A file that is not a backup.
run_flow library-restore-failure
adb shell rm -f "$GARBAGE_FILE"
pass
