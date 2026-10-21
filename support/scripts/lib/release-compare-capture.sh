# shellcheck shell=bash
# Screenshot capture and app setup for support/scripts/release-compare.sh. Sourced, not run.

# placeholders <version> <png>: how many artwork placeholders are on screen. Heuristic: each version's placeholder
# is a flat icon (new: dark disc with a hole; old: grey vinyl record) centred on a flat tile. Mask the icon's exact
# colour, take connected components, and count the near-square ones about as full as the icon whose left side (an
# eighth of a width out) is the tile colour, which rules out text, toolbar icons and real covers. The entries are
# "icon tile min-fill max-fill", sampled from captures.
# shellcheck disable=SC2034  # read by name in placeholders()
PLACEHOLDER_NEW="srgb(93,95,104) srgb(225,226,237) 0.55 0.70"
# old: the album vinyl (fill ~0.64) and the artist header's person icon, whose round head fills ~0.78
# shellcheck disable=SC2034  # read by name in placeholders()
PLACEHOLDER_OLD="srgb(142,142,142) srgb(244,244,244) 0.55 0.80"
PH_GRACE=15
placeholders() {
    local sig icon tile lo hi comps fmt="" n=0 w h x y px
    sig="PLACEHOLDER_$(tr '[:lower:]' '[:upper:]' <<<"$1")"
    [ -n "${!sig}" ] || { echo 0; return; }
    read -r icon tile lo hi <<<"${!sig}"
    comps="$(magick "$2" -alpha off -fuzz 3% -fill black +opaque "$icon" -fill white -opaque "$icon" \
        -define connected-components:verbose=true -define connected-components:area-threshold=400 \
        -connected-components 8 null: 2>/dev/null |
        awk -v lo="$lo" -v hi="$hi" '$NF ~ /255,255,255|gray\(255\)/ { split($2, g, /[x+]/); w = g[1]; h = g[2]
            if (w >= 30 && (w - h) * (w - h) <= (w / 10) * (w / 10) && $4 / (w * h) >= lo && $4 / (w * h) <= hi && g[3] - w / 8 >= 0)
                print w, h, g[3], g[4] }')"
    [ -n "$comps" ] || { echo 0; return; }
    while read -r w h x y; do fmt+="%[pixel:p{$((x - w / 8)),$((y + h / 2))}]\n"; done <<<"$comps"
    while read -r px; do
        [ "$(magick xc:"$px" xc:"$tile" -metric AE -fuzz 3% -compare -format '%[distortion]' info: 2>/dev/null)" = 0 ] && n=$((n + 1))
    done < <(magick "$2" -format "$fmt" info: 2>/dev/null)
    echo "$n"
}

# settle_capture <version> <id> <dest> [live]: the lane's frame once it stops changing and no artwork placeholder
# shows. live screens (a running import) never go still and may end any second, so they get the first frame.
# Placeholders that outlast the grace are kept without a warning: some albums have no art on the server.
settle_capture() {
    local ver="$1" id="$2" dest="$3" live="${4:-}" start now cur="${WORK}/frame.cur" prev="${WORK}/frame.prev" same=0 ph=0 ph_since=""
    start=$(date +%s)
    rm -f "$prev"
    while :; do
        adb exec-out screencap -p >"$cur" 2>/dev/null
        now=$(date +%s)
        if [ -n "$live" ]; then
            mv -f "$cur" "$dest"; return 0
        else
            if [ -f "$prev" ] && cmp -s "$cur" "$prev"; then same=$((same + 1)); else same=0; fi
            mv -f "$cur" "$prev"
            if [ "$same" -ge 1 ] && [ $((now - start)) -ge 1 ]; then
                ph="$(placeholders "$ver" "$prev")"
                if [ "$ph" = 0 ]; then mv -f "$prev" "$dest"; return 0; fi
                # a still frame with placeholders: give the covers PH_GRACE more seconds, then accept it
                [ -n "$ph_since" ] || ph_since=$now
                if [ $((now - ph_since)) -ge "$PH_GRACE" ]; then
                    mv -f "$prev" "$dest"
                    echo "release-compare: ${ver}/${id}: ${ph} artwork placeholder(s) kept after ${PH_GRACE} s" >>"$LOG"
                    return 0
                fi
            fi
            if [ $((now - start)) -ge 45 ]; then
                mv -f "$prev" "$dest"
                warn "$ver" "$id" "frame never settled in 45 s (captured the last one)"
                return 0
            fi
        fi
        sleep 0.5
    done
}

selected() { case " ${SELECTED[*]} " in *" $1 "*) return 0 ;; *) return 1 ;; esac; }

# step_no <id>: the screen's file/manifest number, its position in STEPS plus the unused slot after folder-list
step_no() {
    local idx=0 s
    for s in "${STEPS[@]}"; do idx=$((idx + 1)); [ "$s" = "$1" ] && break; done
    [ "$idx" -gt 19 ] && idx=$((idx + 1))
    echo "$idx"
}

# shot_dest <version> <id>: sets DEST to the shot's path, creating its directory
shot_dest() {
    mkdir -p "${OUT}/${1}"
    SHOT_REL="${1}/$(printf '%02d' "$(step_no "$2")")-${2}.png"
    DEST="${OUT}/${SHOT_REL}"
}

record_shot() {
    printf '%s\t%s\t%s\n' "$1" "$2" "$SHOT_REL" >>"$ROWS"
    echo "release-compare: ${SHOT_REL}"
}

# capture <version> <id> [live]
capture() {
    local ver="$1" id="$2" live="${3:-}"
    selected "$id" || return 0
    shot_dest "$ver" "$id"
    settle_capture "$ver" "$id" "$DEST" "$live"
    record_shot "$ver" "$id"
}

# wait_import <version>: the version's import-wait flow opens a screen whose texts show the library growing (a count
# or the list itself); done once those texts are unchanged across 3 polls 10 s apart with the target artist present.
wait_import() {
    local ver="$1" deadline sig prev="" stable=0 start
    start=$(date +%s)
    deadline=$((start + IMPORT_TIMEOUT))
    while [ "$(date +%s)" -lt "$deadline" ]; do
        start_app
        run_flow "$ver" import-wait >/dev/null 2>&1
        sig="$(dump_texts 2>/dev/null)"
        if [ -n "$sig" ] && [ "$sig" = "$prev" ] && grep -qF "$IMPORT_MARKER" <<<"$sig"; then stable=$((stable + 1)); else stable=0; fi
        if [ "$stable" -ge 2 ]; then echo "release-compare: ${ver}: library settled after $(($(date +%s) - start))s"; return 0; fi
        prev="$sig"
        sleep 10
    done
    warn "$ver" import "library not stable after ${IMPORT_TIMEOUT} s"
    return 1
}

state_get() { sed -n "s/^$2=//p" "${STATE}/$1.env" 2>/dev/null; }

# setup <version>: fresh install, onboarding captures, Emby sign-in, import.
setup() {
    local ver="$1" spec apk id rc sha="" forget_ids=() ids_all=("${SETUP_IDS[@]}" import-progress)
    spec="$([ "$ver" = old ] && echo "$OLD_SPEC" || echo "$NEW_SPEC")"
    resolve_apk "$spec" "$ver" || return 1
    apk="$APK_PATH"
    [ -f "$spec" ] || sha="$(resolve_ref "$spec")"
    PKG="$(emu_apk_info "$apk" package)"
    echo "release-compare: == ${ver} setup: ${PKG} =="
    # only the screens being re-captured, so rows of the others merge
    for id in "${ids_all[@]}"; do selected "$id" && forget_ids+=("$id"); done
    selected import-progress && forget_ids+=(import)
    [ ${#forget_ids[@]} -eq 0 ] || forget "$ver" "${forget_ids[@]}"
    printf 'spec=%s\nsha=%s\npackage=%s\nversionName=%s\nversionCode=%s\napk=%s\n' "$spec" "$sha" "$PKG" \
        "$(emu_apk_info "$apk" versionName)" "$(emu_apk_info "$apk" versionCode)" "$apk" >"${STATE}/${ver}.env"
    if snapshot_restore "$ver" "$apk"; then return 0; fi
    park_music
    adb uninstall "$PKG" >/dev/null 2>&1
    step "${ver}: install" adb install -r "$apk" || return 1
    emu_demo_mode on
    start_app
    for id in "${SETUP_IDS[@]}"; do
        run_flow "$ver" "$id" "$([ "$id" = sign-in ] && echo creds)"; rc=$?
        [ "$rc" = 2 ] && continue
        [ "$rc" = 1 ] && { echo "release-compare: ${ver}: setup stopped at ${id}" >&2; return 1; }
        capture "$ver" "$id"
        # new syncs for a few seconds once signing in finishes the Welcome; 1.0.10 imports after onboarding
        [ "$id" = sign-in ] && [ "$ver" = new ] && import_progress new
    done
    [ "$ver" = new ] || import_progress "$ver"
    wait_import "$ver" || return 1
    # prewarmed here so the snapshot holds the cached covers and a rerun's screens start warm
    start_app
    step "${ver}: artwork prewarm" run_flow "$ver" prewarm
    snapshot_save "$ver" "$apk"
}
