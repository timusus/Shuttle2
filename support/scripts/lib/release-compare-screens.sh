# shellcheck shell=bash
# Per-version screen sessions, judging and manifest output for support/scripts/release-compare.sh. Sourced, not run.

# screens <version>: the library screens on the app setup installed.
screens() {
    local ver="$1" id rc ids=()
    PKG="$(state_get "$ver" package)"
    [ -n "$PKG" ] && adb shell pm path "$PKG" 2>/dev/null | grep -q package: ||
        { echo "release-compare: ${ver} isn't installed on this lane: run --phase setup first (and keep the lane up)" >&2; return 1; }
    ensure_installed "$ver" || return 1
    echo "release-compare: == ${ver} screens: ${PKG} =="
    for id in "${SCREEN_IDS[@]}"; do selected "$id" && ids+=("$id"); done
    [ ${#ids[@]} -gt 0 ] || return 0
    forget "$ver" "${ids[@]}" prewarm
    emu_demo_mode on
    # a user browses with something queued, so every screen shows the mini player; paused, since a playing screen never
    # goes still. Each flow's cold start restores the queue.
    start_app
    step "${ver}: start playback" run_flow "$ver" mini-player
    adb shell input keyevent KEYCODE_MEDIA_PAUSE >>"$LOG" 2>&1
    # a version without a screen's flow has no such screen (pairing old_only/new_only)
    local todo=() pending=() next=() attempt k final crashed
    for id in "${ids[@]}"; do flow_path "$ver" "$id" >/dev/null && todo+=("$id"); done
    for attempt in 1 2; do
        # attempt 2 takes many frames so slow covers get their grace, and reports what still fails
        k=3; final=0; [ "$attempt" = 2 ] && { k=24; final=1; }
        [ ${#pending[@]} -eq 0 ] || { todo=("${pending[@]}"); pending=(); }
        [ ${#todo[@]} -gt 0 ] || break
        rm -rf "$FRAMES_DIR"; mkdir -p "$FRAMES_DIR"
        while [ ${#todo[@]} -gt 0 ]; do
            run_session "$ver" "$k" "${todo[@]}"
            next=(); crashed=""
            for id in "${todo[@]}"; do
                if [ -f "${FRAMES_DIR}/${id}-1.png" ]; then
                    shot_dest "$ver" "$id"
                    if judge_frames "$ver" "$id" "$k" "$final" "$DEST"; then record_shot "$ver" "$id"; else pending+=("$id"); fi
                elif [ -z "$crashed" ]; then
                    # a failing flow ends the session: the first screen without frames is it, the rest never ran
                    crashed=1
                    if [ "$final" = 1 ]; then warn "$ver" "$id" "flow failed, see ${WORK}/maestro-${ver}-session.log"; else pending+=("$id"); fi
                else
                    next+=("$id")
                fi
            done
            todo=(${next[@]+"${next[@]}"})
        done
    done
}

FRAMES_DIR="${WORK}/frames"

# unpark_music: the reverse of park_music
unpark_music() {
    adb shell '[ -d /sdcard/rc-parked/music ] && { [ -d /sdcard/Music/music ] || mv /sdcard/rc-parked/music /sdcard/Music/; }' >>"$LOG" 2>&1
}

# local_folder_phase: after every Emby screen, put the AVD's local music back, have the app import it, and capture an
# opened folder. The import is a debug-build broadcast (ACTION_IMPORT_MEDIA, a MediaStore reimport) after a MediaStore
# volume scan, repeated each poll; done once the Folders tab lists a folder with songs. The music is parked again after.
local_folder_phase() {
    local deadline start texts
    selected "$LOCAL_ID" || return 0
    forget new "$LOCAL_ID"
    if [ "$DEVICE" != local ]; then
        printf '%s\t%s\t%s\n' new "$LOCAL_ID" "only captured on the local AVD, which holds the local music" >>"$NOTES"
        return 0
    fi
    echo "release-compare: new: local music phase"
    unpark_music
    adb shell content call --uri content://media/external/file --method scan_volume --arg external_primary >>"$LOG" 2>&1
    start=$(date +%s)
    deadline=$((start + IMPORT_TIMEOUT))
    while [ "$(date +%s)" -lt "$deadline" ]; do
        adb shell am broadcast -a com.simplecityapps.shuttle.debug.ACTION_IMPORT_MEDIA -p "$PKG" >>"$LOG" 2>&1
        sleep 10
        start_app
        run_flow new folder-list optional >/dev/null 2>&1
        texts="$(dump_texts 2>/dev/null)"
        # the count's space is a no-break space
        if grep -qE '[0-9]+[^0-9a-z]+songs?' <<<"$texts"; then
            echo "release-compare: new: local folders listed after $(($(date +%s) - start))s"
            run_flow new "$LOCAL_ID" && capture new "$LOCAL_ID"
            park_music
            return 0
        fi
    done
    warn new "$LOCAL_ID" "no local folder listed after ${IMPORT_TIMEOUT} s"
    park_music
}

# prune_stale: drops shots (and their rows) a renumbered or removed screen left in the output dir
prune_stale() {
    local ver id f keep=" "
    for ver in old new montage; do
        keep=" "
        for id in "${STEPS[@]}"; do keep+="$(printf '%02d' "$(step_no "$id")")-${id}.png "; done
        for f in "${OUT}/${ver}"/*.png; do
            [ -f "$f" ] || continue
            case "$keep" in *" ${f##*/} "*) ;; *) rm -f "$f"; echo "release-compare: removed stale ${ver}/${f##*/}" >>"$LOG" ;; esac
        done
    done
    awk -F'\t' -v out="$OUT" '{ cmd = "[ -f \"" out "/" $3 "\" ]"; if (system(cmd) == 0) print }' "$ROWS" >"${ROWS}.tmp" && mv -f "${ROWS}.tmp" "$ROWS"
}

# flow_path <version> <id>: sets FLOW_FILE to the id's flow (old/new override, else shared); 1 if the version has none
flow_path() {
    if [ -f "${FLOWS}/${1}/${2}.yaml" ]; then FLOW_FILE="${REPO_ROOT}/${FLOWS}/${1}/${2}.yaml"
    elif [ -f "${FLOWS}/${2}.yaml" ]; then FLOW_FILE="${REPO_ROOT}/${FLOWS}/${2}.yaml"
    else return 1; fi
}

# run_session <version> <frames> <ids...>: one Maestro run over the screens' flows in order. After each flow it pauses
# playback or opens the shade where the screen needs it, then takes <frames> screenshots back to back into FRAMES_DIR
# (judge_frames picks from them afterwards, so the session never waits on the script).
run_session() {
    local ver="$1" k="$2" id rc=0 split; shift 2
    local session="${WORK}/session-${ver}.yaml"
    local args=(-e "APP_ID=${PKG}" -e "ARTIST=${ARTIST}" -e "ALBUM=${ALBUM}" -e "ALBUM_TRACK=${ALBUM_TRACK}" -e "PLAYING_ARTIST=${PLAYING_ARTIST}" -e "PLAYING_ALBUM=${PLAYING_ALBUM}" -e "PLAYING_TRACK=${PLAYING_TRACK}" -e "QUERY=${QUERY}"
        -e "GENRE=${GENRE}" -e "PLAYLIST=${PLAYLIST}")
    # a playing screen is never still (progress bars, visualisers), and Maestro has no pause-only key (Play Pause
    # toggles), so a playback screen's flow and its frames are separate runs with an idempotent adb pause between
    {
        printf 'appId: ${APP_ID}\n---\n'
        for id in "$@"; do
            flow_path "$ver" "$id"
            printf -- '- runFlow: %s\n' "$FLOW_FILE"
            case "$PLAYBACK_IDS" in
                *" $id "*) printf -- '#SPLIT\n' ;;
            esac
            [ "$id" = notification ] && printf -- '- swipe:\n    start: 50%%,0%%\n    end: 50%%,70%%\n'
            printf -- '- evalScript: ${output.n = 0}\n- repeat:\n    times: %s\n    commands:\n' "$k"
            printf -- '      - evalScript: ${output.n = output.n + 1}\n      - takeScreenshot: frames/%s-${output.n}\n' "$id"
            [ "$id" = notification ] && printf -- '- swipe:\n    start: 50%%,70%%\n    end: 50%%,0%%\n'
        done
    } >"$session"
    # cut the session at each marker; the header (appId) leads every part
    split="${WORK}/session-${ver}-part"
    rm -f "${split}"-*.yaml
    awk -v p="$split" 'BEGIN { n = 1; f = sprintf("%s-%03d.yaml", p, n) }
        NR <= 2 { hdr = hdr $0 "\n" }
        /^#SPLIT$/ { n++; f = sprintf("%s-%03d.yaml", p, n); printf "%s", hdr > f; next }
        { print > f }' "$session"
    local part parts=0
    for part in "${split}"-*.yaml; do
        [ "$parts" -gt 0 ] && adb shell input keyevent KEYCODE_MEDIA_PAUSE >>"$LOG" 2>&1
        parts=$((parts + 1))
        run_with_timeout "$SESSION_TIMEOUT" maestro_run "$part" "${WORK}/scratch" "${args[@]}" 2>&1 | scrub >>"${WORK}/maestro-${ver}-session.log"
        rc=${PIPESTATUS[0]}
        [ "$rc" = 0 ] || break
        find "${WORK}/scratch" -path '*/takeScreenshot/frames/*.png' -exec mv -f {} "$FRAMES_DIR/" \;
    done
    # Maestro keeps screenshots under its output dir, not at the path given
    find "${WORK}/scratch" -path '*/takeScreenshot/frames/*.png' -exec mv -f {} "$FRAMES_DIR/" \;
    rm -rf -- "${WORK}/scratch"
    return "$rc"
}

# judge_frames <version> <id> <frames> <final> <dest>: copies the first frame identical to the one before it with no
# artwork placeholder to dest. 1 = none yet (the screen goes to the next session). On the final try an identical pair
# with placeholders is kept as is (some albums have no art on the server), else the last frame with a warning.
judge_frames() {
    local ver="$1" id="$2" k="$3" final="$4" dest="$5" i prev="" cur hit="" ph hit_ph=0 last=""
    for ((i = 1; i <= k; i++)); do
        cur="${FRAMES_DIR}/${id}-${i}.png"
        [ -f "$cur" ] || break
        last="$cur"
        if [ -n "$prev" ] && cmp -s "$prev" "$cur" && { [ -z "$hit" ] || ! cmp -s "$hit" "$cur"; }; then
            ph="$(placeholders "$ver" "$cur")"
            if [ "$ph" = 0 ]; then cp "$cur" "$dest"; return 0; fi
            hit="$cur"; hit_ph="$ph"
        fi
        prev="$cur"
    done
    [ "$final" = 1 ] || return 1
    if [ -n "$hit" ]; then
        cp "$hit" "$dest"
        echo "release-compare: ${ver}/${id}: ${hit_ph} artwork placeholder(s) kept" >>"$LOG"
    else
        cp "$last" "$dest"
        warn "$ver" "$id" "frame never settled in ${k} frames (captured the last one)"
    fi
    return 0
}

write_manifest() {
    local id warns not_captured
    warns="$(jq -R -s -c 'split("\n") | map(select(length > 0) | split("\t") | {version: .[0], screen: .[1], message: .[2]})' "$WARNS")"
    not_captured="$({ printf 'both\t%s\n' "${SKIPPED[@]}"; cat "$NOTES"; } |
        jq -R -s -c 'split("\n") | map(select(length > 0) | split("\t") | {version: .[0], screen: .[1], why: .[2]})')"
    {
        for id in "${STEPS[@]}"; do
            jq -n -c --arg n "$(step_no "$id")" --arg id "$id" \
                --arg old "$(awk -F'\t' -v i="$id" '$1 == "old" && $2 == i {p = $3} END {print p}' "$ROWS")" \
                --arg new "$(awk -F'\t' -v i="$id" '$1 == "new" && $2 == i {p = $3} END {print p}' "$ROWS")" \
                '{n: ($n | tonumber), id: $id, caption: "", old: (if $old == "" then null else $old end), new: (if $new == "" then null else $new end)}
                 | .pairing = (if .old != null and .new != null then "both" elif .old != null then "old_only" else "new_only" end)'
        done
    } | jq -s --argjson warnings "$warns" --argjson not_captured "$not_captured" \
        --arg os "$(state_get old spec)" --arg ov "$(state_get old versionName)" --arg oc "$(state_get old versionCode)" --arg osha "$(state_get old sha)" \
        --arg ns "$(state_get new spec)" --arg nv "$(state_get new versionName)" --arg nc "$(state_get new versionCode)" --arg nsha "$(state_get new sha)" \
        --arg run "$RUN" \
        '{run: $run, old: {spec: $os, sha: (if $osha == "" then null else $osha end), versionName: $ov, versionCode: ($oc | tonumber? // $oc)}, new: {spec: $ns, sha: (if $nsha == "" then null else $nsha end), versionName: $nv, versionCode: ($nc | tonumber? // $nc)}, screens: map(select(.old != null or .new != null)), not_captured: $not_captured, warnings: $warnings}' >"${OUT}/manifest.json"
}

write_montages() {
    local id o n
    command -v magick >/dev/null || { echo "release-compare: ImageMagick not installed, no montages"; return 0; }
    mkdir -p "${OUT}/montage"
    for id in "${STEPS[@]}"; do
        o="$(awk -F'\t' -v i="$id" '$1 == "old" && $2 == i {p = $3} END {print p}' "$ROWS")"
        n="$(awk -F'\t' -v i="$id" '$1 == "new" && $2 == i {p = $3} END {print p}' "$ROWS")"
        [ -n "$o" ] && [ -n "$n" ] && [ -f "${OUT}/${o}" ] && [ -f "${OUT}/${n}" ] || continue
        magick montage "${OUT}/${o}" "${OUT}/${n}" -tile 2x1 -geometry '+16+0' -background '#202020' "${OUT}/montage/$(printf '%02d' "$(step_no "$id")")-${id}.png" >>"$LOG" 2>&1
    done
}
