# shellcheck shell=bash
# Imported-state AVD snapshots for support/scripts/release-compare.sh. Sourced, not run.

# the Emby host, hashed: part of the snapshot key without putting it in a name or log
HOST_TAG=""
host_tag() {
    local h="${URL#*://}"
    h="${h%%[/:]*}"
    [ -n "$HOST_TAG" ] || HOST_TAG="$(printf '%s' "$h" | shasum -a 256 | cut -c1-8)"
}

flows_hash() {
    { printf '%s\n' "$ARTIST" "$ALBUM" "$ALBUM_TRACK" "$PLAYING_ARTIST" "$PLAYING_ALBUM" "$PLAYING_TRACK" "$QUERY" "$GENRE" "$PLAYLIST"; cat "${FLOWS}"/*.yaml "${FLOWS}/${1}"/*.yaml; } |
        shasum -a 256 | cut -c1-12
}

# snapshot_key <version> <apk>: names the snapshot and its onboarding-shot cache
snapshot_key() { host_tag; echo "${1}-$(shasum -a 256 "$2" | cut -c1-12)-${HOST_TAG}"; }

snapshot_exists() { adb emu avd snapshot list 2>/dev/null | tr -d '\r' | grep -qF " rc-${1} "; }

# park_music: the AVD's seeded /sdcard/Music/music would be imported beside Emby; cleanup puts it back
park_music() {
    [ "$DEVICE" = local ] || return 0
    # a killed run leaves the music parked: put it back before parking again
    adb shell '[ -d /sdcard/rc-parked/music ] && { [ -d /sdcard/Music/music ] && rm -rf /sdcard/rc-parked/music || mv /sdcard/rc-parked/music /sdcard/Music/; }' >>"$LOG" 2>&1
    adb shell 'mkdir -p /sdcard/rc-parked && [ -d /sdcard/Music/music ] && mv /sdcard/Music/music /sdcard/rc-parked/' >>"$LOG" 2>&1
    return 0
}

# snapshot_restore <version> <apk>: loads the version's imported-state snapshot and its cached setup shots; 1 = do the
# full setup. The shots are the cache's, so the cache must match the current flows.
snapshot_restore() {
    local ver="$1" key dir id f
    [ "$DEVICE" = local ] && [ "$FRESH" = 0 ] || return 1
    key="$(snapshot_key "$ver" "$2")"
    dir="${RC_CACHE}/setup/${key}"
    [ -f "${dir}/complete" ] && [ "$(cat "${dir}/flows")" = "$(flows_hash "$ver")" ] && snapshot_exists "$key" || return 1
    step "${ver}: load snapshot" snapshot_load "rc-${key}" || return 1
    adb shell pm path "$PKG" 2>/dev/null | grep -q package: || { echo "release-compare: ${ver}: snapshot has no ${PKG}, full setup" >&2; return 1; }
    emu_demo_mode on
    for id in "${SETUP_IDS[@]}" import-progress; do
        selected "$id" || continue
        f="$(awk -F'\t' -v i="$id" '$2 == i {print $3}' "${dir}/rows.tsv")"
        [ -n "$f" ] || continue
        mkdir -p "${OUT}/${ver}"
        if [ ! -f "${dir}/shots/$(basename "$f")" ]; then
            warn "$ver" "$id" "cached setup shot missing, screen skipped"
            continue
        fi
        cp "${dir}/shots/$(basename "$f")" "${OUT}/${f}"
        printf '%s\t%s\t%s\n' "$ver" "$id" "$f" >>"$ROWS"
    done
    selected import-progress && awk -F'\t' '$2 == "import-progress"' "${dir}/notes.tsv" >>"$NOTES"
    echo "release-compare: ${ver}: setup restored from snapshot rc-${key}"
}

snapshot_load() {
    adb emu avd snapshot load "$1" | tr -d '\r' | grep -q '^OK' || return 1
    adb wait-for-device
    local deadline=$(($(date +%s) + 120))
    until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; do
        [ "$(date +%s)" -lt "$deadline" ] || { echo "snapshot $1 loaded but sys.boot_completed not set in 120 s"; return 1; }
        sleep 1
    done
}

# snapshot_save <version> <apk>: after a clean, complete setup; replaces the version's older snapshots
snapshot_save() {
    local ver="$1" key dir tmp old
    [ "$DEVICE" = local ] && [ -z "$SCREENS_ARG" ] || return 0
    [ -z "$(awk -F'\t' -v v="$ver" '$1 == v' "$WARNS")" ] || { echo "release-compare: ${ver}: setup had warnings, no snapshot saved"; return 0; }
    key="$(snapshot_key "$ver" "$2")"
    step "${ver}: save snapshot" snapshot_do_save "rc-${key}" || return 0
    for old in $(adb emu avd snapshot list 2>/dev/null | tr -d '\r' | awk -v p="rc-${ver}-" -v k="rc-${key}" 'index($2, p) == 1 && $2 != k {print $2}'); do
        adb emu avd snapshot delete "$old" >>"$LOG" 2>&1
    done
    dir="${RC_CACHE}/setup/${key}"
    tmp="${dir}.tmp"
    rm -rf "$tmp" "$dir"
    mkdir -p "${tmp}/shots"
    awk -F'\t' -v v="$ver" '$1 == v' "$ROWS" >"${tmp}/rows.tsv"
    awk -F'\t' -v v="$ver" '$1 == v' "$NOTES" >"${tmp}/notes.tsv"
    while IFS=$'\t' read -r _ _ f; do
        cp "${OUT}/${f}" "${tmp}/shots/" || { echo "release-compare: ${ver}: setup shot copy failed, cache not marked complete" >&2; return 0; }
    done <"${tmp}/rows.tsv"
    flows_hash "$ver" >"${tmp}/flows"
    : >"${tmp}/complete"
    mv "$tmp" "$dir"
}

snapshot_do_save() { adb emu avd snapshot save "$1" | tr -d '\r' | grep -q '^OK'; }

# import_progress <version>: the flow waits for the version's import progress text, then the first frame is kept.
# An import that finishes before it shows is a manifest note, not a failure.
import_progress() {
    local ver="$1"
    if run_flow "$ver" import-progress optional; then capture "$ver" import-progress live; return 0; fi
    import_progress_missed "$ver"
}

import_progress_missed() {
    selected import-progress || return 0
    printf '%s\t%s\t%s\n' "$1" import-progress "the import finished before its progress showed" >>"$NOTES"
    echo "release-compare: ${1}: import progress not caught (manifest note)"
}

NEW_PROGRESS_SEEN=0
new_import_progress() {
    capture new import-progress live
    # read after the frame, so progress still on screen was on it too
    dump_texts 2>/dev/null | grep -qE 'Scanning your music|Fetching|Syncing' && NEW_PROGRESS_SEEN=1
}

# new_import_progress_check: a progress frame that matches the settled landing showed none.
new_import_progress_check() {
    local p="${OUT}/new/08-import-progress.png" s="${OUT}/new/07-sign-in.png"
    selected import-progress && [ "$NEW_PROGRESS_SEEN" = 0 ] && [ -f "$p" ] && [ -f "$s" ] || return 0
    cmp -s "$p" "$s" || { echo "release-compare: new: import progress frame kept (sync was done by the text check)" >>"$LOG"; return 0; }
    rm -f "$p"
    forget new import-progress
    import_progress_missed new
}

# ensure_installed <version>: screens must shoot the build the manifest names. Refuses when the spec now resolves to
# another commit than setup's (its data came from that build), and reinstalls over the data when the installed APK
# isn't setup's (another run, or a phase on another lane, replaced it).
ensure_installed() {
    local ver="$1" spec want_sha cur_sha apk path dev_hash apk_hash
    spec="$([ "$ver" = old ] && echo "$OLD_SPEC" || echo "$NEW_SPEC")"
    want_sha="$(state_get "$ver" sha)"
    if [ ! -f "$spec" ]; then
        cur_sha="$(resolve_ref "$spec")"
        [ "$cur_sha" = "$want_sha" ] || { echo "release-compare: ${ver}: setup ran on ${want_sha:0:12} but ${spec} is now ${cur_sha:0:12}: run --phase setup again" >&2; return 1; }
    fi
    apk="$(state_get "$ver" apk)"
    [ -n "$apk" ] || apk="$(state_get "$ver" spec)"
    [ -f "$apk" ] || { echo "release-compare: ${ver}: setup's APK ${apk} is gone: run --phase setup again" >&2; return 1; }
    path="$(adb shell pm path "$PKG" 2>/dev/null | tr -d '\r' | sed -n 's/^package://p' | grep 'base\.apk$' | head -1)"
    dev_hash="$(adb shell sha256sum "$path" 2>/dev/null | awk '{print $1}')"
    apk_hash="$(shasum -a 256 "$apk" | awk '{print $1}')"
    [ "$dev_hash" = "$apk_hash" ] && return 0
    echo "release-compare: ${ver}: installed ${PKG} isn't setup's build (versionCode $(state_get "$ver" versionCode), ${want_sha:0:12}); reinstalling"
    step "${ver}: reinstall" adb install -r "$apk"
}
