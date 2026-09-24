#!/usr/bin/env bash
# #168: removing a song from a Shuttle/TagLib playlist syncs the change back to its .m3u file on
# disk (LocalPlaylistRepository -> M3uWriter), while an entry the provider never resolved to a song
# is left untouched. Needs a real SAF-backed folder (setup_taglib_provider), so the library ends up
# holding only the taglib fixture; restored back to `playback` on exit so later checks still pass.
#
# The removal itself goes through the REMOVE_PLAYLIST_SONG debug broadcast (same
# PlaylistRepository.removeFromPlaylist call the playlist screen's per-row "Remove" makes), not a
# Maestro tap: reaching that per-row popup needs a double long-press on the same row (the first
# long-press only activates the multi-select toolbar -- see PlaylistDetailFragment's
# onPlaylistSongLongClicked), which Maestro's longPressOn can't reproduce reliably.
source "$(dirname "$0")/_lib.sh"

setup_taglib_provider
trap restore_playback_fixture EXIT

s2 REMOVE_PLAYLIST_SONG --es playlist "'taglib'" --es song "'Taglib Two'" >/dev/null

m3u="$(adb_retry shell cat /sdcard/Music/taglib-seed/taglib.m3u)"

echo "$m3u" | grep -q "Taglib Two" && fail "removed song's line is still in the .m3u"
echo "$m3u" | grep -q "Taglib One" || fail "surviving song 'Taglib One' missing from the .m3u"
echo "$m3u" | grep -q "Taglib Three" || fail "surviving song 'Taglib Three' missing from the .m3u"
echo "$m3u" | grep -q "missing-track.mp3" || fail "unresolved entry 'missing-track.mp3' was dropped from the .m3u"

pass
