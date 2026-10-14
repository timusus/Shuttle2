#!/usr/bin/env bash
# Matched old-vs-new Android screenshots of the real UI against the Emby test library, for a release
# comparison (and the Remotion video made from manifest.json).
#
#   support/scripts/release-compare.sh --old <apk|git-ref> --new <apk|git-ref>
#                                      [--version old|new|both] [--phase setup|screens|all] [--keep]
#                                      [--screens a,b,...] [--out <dir>] [--build-type debug|release]
#                                      [--flow-timeout <secs>] [--import-timeout <secs>] [--refresh-old] [--fresh]
#
#     --refresh-old      recapture the old build. A full old run is cached under $S2_RC_CACHE (default
#                        ~/.cache/s2-release-compare) by APK sha256 and flows hash and reused without the device.
#     --fresh            full setup (install, onboarding, sign-in, import) even when a matching AVD snapshot exists.
#                        Local AVD only: after setup settles the script saves a snapshot keyed by APK sha256 and the
#                        Emby host, and a rerun loads it instead (the onboarding shots come from the cache).
#
#     --old / --new      an APK path, or a git ref that is built here (a bare branch name means origin/<name>, fetched
#                        first; the current tree when the ref is HEAD and
#                        the tree is clean, else a detached worktree under tmp/release-compare; the APK is cached
#                        per commit and build type)
#     --version          which build(s) to run (default both, old first)
#     --phase            setup: fresh install, demo mode, the onboarding captures, sign in to Emby, wait for the
#                        import. screens: artwork prewarm and the library captures on the app setup left installed.
#                        all (default): both. Run one phase per call; screens takes ~1 min of prewarm plus ~1 min
#                        a screen, so capture them in batches of about 3 with --screens. The lane's AVD is
#                        read-only, so screens needs the lane setup ran on: setup always leaves it up, screens and
#                        all stop it unless --keep.
#     --screens a,b      only capture these ids (the other setup steps still run, so the library exists). Ids:
#                          launch permission analytics source add-source emby-form sign-in import-progress
#                          home artist-list artist-detail album-list album-detail song-list playlist-list
#                          playlist-detail genre-list genre-detail folder-list folder-detail search mini-player now-playing
#                          queue sleep-timer sound lyrics notification settings equaliser
#                        A screen a version doesn't have is null in the manifest (pairing old_only/new_only).
#     --out <dir>        output directory (default build/release-compare/<old>-vs-<new>, from the APK version
#                        or the ref name). Runs merge into it: a capture replaces only its own screen's row.
#     --build-type       how a git ref is built (default debug). release is a minified release build signed with
#                        the debug keystore (CI=true CONFIGURATION=Release), since the Play keystore stays in
#                        release-android. The lane has no Play Store, so a release build never learns it is Pro
#                        and refuses to add a server: debug (always Pro) is the default.
#
# Typical run, one foreground call per line:
#   release-compare.sh --old <apk> --new main --version old --phase setup
#   release-compare.sh --old <apk> --new main --version old --phase screens --keep --screens home,album-list,album-detail
#   ... one screens call per batch of ~3 ids, then the same for --version new
#   adb -s emulator-5554 emu kill (local AVD) or remote-emu.sh stop (lane) when done
#
# Device: a box lane (remote-emu.sh); when the box can't run one (status exit 3) the local AVD $S2_LOCAL_AVD (default
# s2test), booted headless if none is running. Park the AVD's seeded /sdcard/Music/music (move it out of Music) before
# new's setup, or the new build imports it alongside Emby; put it back when done.
#
# Maestro drives the real UI by visible text (flows in support/maestro/release-compare/, shared, with old/ and new/
# overrides) and this script owns the capture under SystemUI demo mode. Setup screens: adb screencap every ~0.5 s until
# 2 consecutive frames are identical. Library screens run in one Maestro session per version: a generated flow runs
# each screen's flow, then takes K frames in a row; afterwards the script takes the first identical pair with no artwork
# placeholder, and the screens that failed rerun in a second session with more frames (still unsettled: a manifest
# warning naming the screen).
# Credentials come from ~/.config/s2-test/emby-appreview.env (URL, USER_NAME, PASSWORD), stay unexported and reach
# Maestro only as MAESTRO_URL/MAESTRO_USER_NAME/MAESTRO_PASSWORD env of that one invocation; Maestro output is scrubbed of them and kept in tmp/release-compare/<run>/, never next to the shots.
#
# Output: <out>/{old,new}/NN-<id>.png, <out>/manifest.json (steps, old/new paths, versions, warnings),
# <out>/montage/NN-<id>.png (old beside new, when ImageMagick is installed), run state in <out>/.state/.
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
CALLER_PWD="$PWD"
cd "$REPO_ROOT" || exit 1
PATH="$PATH:$HOME/Library/Android/sdk/platform-tools"

# shellcheck source=support/scripts/checks/_timeout_fallback.sh
source "${SCRIPT_DIR}/checks/_timeout_fallback.sh"
# shellcheck source=support/scripts/emu-lane.sh
source "${SCRIPT_DIR}/emu-lane.sh"

# ---- Targets: in the Emby test library with server artwork, shared by both versions so the pairs match ----
ARTIST="Alice In Chains"
ALBUM="Facelift"
TRACK="Man in the Box"
QUERY="Alice"
GENRE="Hard Rock"
PLAYLIST="Recently Added"
# ---------------------------------------------------------------------------------------------

ENV_FILE="$HOME/.config/s2-test/emby-appreview.env"
FLOWS="support/maestro/release-compare"
STEPS=(launch permission analytics source add-source emby-form sign-in import-progress home artist-list artist-detail
    album-list album-detail song-list playlist-list playlist-detail genre-list genre-detail folder-list folder-detail
    search mini-player now-playing queue sleep-timer sound lyrics notification settings equaliser)
SETUP_IDS=(launch permission analytics source add-source emby-form sign-in)
SCREEN_IDS=(home artist-list artist-detail album-list album-detail song-list playlist-list playlist-detail genre-list
    genre-detail folder-list folder-detail search mini-player now-playing queue sleep-timer sound lyrics notification
    settings equaliser)
# screens that start playback: paused before capture, since a playing screen never goes still
PLAYBACK_IDS=" mini-player now-playing queue sleep-timer sound lyrics notification equaliser "
# never captured, with why (manifest not_captured)
SKIPPED=("widget	a home-screen widget needs a launcher setup the lane doesn't have"
    "paywall	the debug build is always Pro, and a release build can't add a server without Play billing")

usage() { awk 'NR>1 && /^#/ {sub(/^# ?/, ""); print; next} NR>1 {exit}' "$0"; }

OLD_SPEC=""; NEW_SPEC=""; SCREENS_ARG=""; OUT=""; BUILD_TYPE=debug; VERSION=both; PHASE=all; KEEP=0
REFRESH_OLD=0; FRESH=0
FLOW_TIMEOUT=240; IMPORT_TIMEOUT=300; SESSION_TIMEOUT=900
while [ $# -gt 0 ]; do
    case "$1" in
        --old) OLD_SPEC="${2:?release-compare: --old needs an APK or git ref}"; shift 2 ;;
        --new) NEW_SPEC="${2:?release-compare: --new needs an APK or git ref}"; shift 2 ;;
        --version) VERSION="${2:?release-compare: --version needs old, new or both}"; shift 2 ;;
        --phase) PHASE="${2:?release-compare: --phase needs setup, screens or all}"; shift 2 ;;
        --keep) KEEP=1; shift ;;
        --refresh-old) REFRESH_OLD=1; shift ;;
        --fresh) FRESH=1; shift ;;
        --screens) SCREENS_ARG="${2:?release-compare: --screens needs a comma-separated list}"; shift 2 ;;
        --out) OUT="${2:?release-compare: --out needs a directory}"; shift 2 ;;
        --build-type) BUILD_TYPE="${2:?release-compare: --build-type needs debug or release}"; shift 2 ;;
        --flow-timeout) FLOW_TIMEOUT="${2:?release-compare: --flow-timeout needs seconds}"; shift 2 ;;
        --import-timeout) IMPORT_TIMEOUT="${2:?release-compare: --import-timeout needs seconds}"; shift 2 ;;
        -h | --help) usage; exit 0 ;;
        *) echo "release-compare: unknown argument '$1'" >&2; usage >&2; exit 2 ;;
    esac
done
[ -n "$OLD_SPEC" ] && [ -n "$NEW_SPEC" ] || { echo "release-compare: --old and --new are required" >&2; usage >&2; exit 2; }
# relative paths mean the caller's cwd, not the repo root we cd to; an existing file is an APK, anything else a git ref
abs_spec() { if [ -f "${CALLER_PWD}/$1" ] || [[ "$1" == *.apk ]]; then printf '%s\n' "${CALLER_PWD}/$1"; else printf '%s\n' "$1"; fi; }
case "$OLD_SPEC" in /*) ;; *) OLD_SPEC="$(abs_spec "$OLD_SPEC")" ;; esac
case "$NEW_SPEC" in /*) ;; *) NEW_SPEC="$(abs_spec "$NEW_SPEC")" ;; esac
case "$OUT" in "" | /*) ;; *) OUT="${CALLER_PWD}/${OUT}" ;; esac
case "$BUILD_TYPE" in debug | release) ;; *) echo "release-compare: --build-type must be debug or release" >&2; exit 2 ;; esac
case "$VERSION" in old | new) VERSIONS=("$VERSION") ;; both) VERSIONS=(old new) ;; *) echo "release-compare: --version must be old, new or both" >&2; exit 2 ;; esac
case "$PHASE" in setup | screens | all) ;; *) echo "release-compare: --phase must be setup, screens or all" >&2; exit 2 ;; esac
command -v jq >/dev/null || { echo "release-compare: jq is required" >&2; exit 1; }
[ -f "$ENV_FILE" ] || { echo "release-compare: no $ENV_FILE (URL, USER_NAME, PASSWORD)" >&2; exit 1; }

SELECTED=("${STEPS[@]}")
if [ -n "$SCREENS_ARG" ]; then
    IFS=',' read -ra SELECTED <<<"$SCREENS_ARG"
    for s in "${SELECTED[@]}"; do
        case " ${STEPS[*]} " in *" $s "*) ;; *) echo "release-compare: unknown screen '$s' (want: ${STEPS[*]})" >&2; exit 2 ;; esac
    done
fi

RUN="$(date +%Y%m%d-%H%M%S)"
WORK="${REPO_ROOT}/tmp/release-compare/${RUN}"
mkdir -p "$WORK"
LOG="${WORK}/run.log"
: >"$LOG"
LANE_STARTED=0
DEVICE=""
LOCAL_AVD="${S2_LOCAL_AVD:-s2test}"
# outside the worktree, so every worktree and run shares the APKs, the old-build shots and the snapshot shots
RC_CACHE="${S2_RC_CACHE:-$HOME/.cache/s2-release-compare}"
EXTRA_WORKTREES=()

# label <spec>: the output-path name of a build: an APK's versionName (first word), else the ref.
label() {
    if [ -f "$1" ]; then local vn; vn="$(emu_apk_info "$1" versionName)"; printf '%s\n' "${vn%% *}"
    else printf '%s\n' "${1//\//-}"; fi
}
[ -n "$OUT" ] || OUT="${REPO_ROOT}/build/release-compare/$(label "$OLD_SPEC")-vs-$(label "$NEW_SPEC")"
STATE="${OUT}/.state"
mkdir -p "$STATE"
ROWS="${STATE}/rows.tsv"     # version<TAB>id<TAB>path
WARNS="${STATE}/warnings.tsv" # version<TAB>id<TAB>message
NOTES="${STATE}/not-captured.tsv" # version<TAB>id<TAB>why: expected gaps, not failures
touch "$ROWS" "$WARNS" "$NOTES"

step() {
    local desc="$1" start rc; shift
    start=$(date +%s)
    "$@" >>"$LOG" 2>&1
    rc=$?
    if [ "$rc" -eq 0 ]; then echo "release-compare: ${desc} -- ok ($(($(date +%s) - start))s)"; return 0; fi
    echo "release-compare: ${desc} -- FAILED ($(($(date +%s) - start))s, see $LOG)" >&2
    return "$rc"
}

warn() { # version id message
    printf '%s\t%s\t%s\n' "$1" "$2" "$3" >>"$WARNS"
    echo "release-compare: WARNING $1/$2: $3" >&2
}

# forget <version> <id...>: drops earlier rows and warnings for these screens, so a re-run replaces them.
forget() {
    local ver="$1" f; shift
    for f in "$ROWS" "$WARNS" "$NOTES"; do
        awk -F'\t' -v v="$ver" -v ids=" $* " '!($1 == v && index(ids, " " $2 " "))' "$f" >"${f}.tmp" && mv -f "${f}.tmp" "$f"
    done
}

cleanup() {
    local wt
    if [ "$LANE_STARTED" = 1 ]; then
        emu_demo_mode off
        # the AVD's seeded music is parked while an app imports (see park_music); a snapshot load reverts the disk, so
        # put it back whenever it's found parked
        [ "$DEVICE" = local ] && [ -n "${ANDROID_SERIAL:-}" ] &&
            adb shell '[ -d /sdcard/rc-parked/music ] && mv /sdcard/rc-parked/music /sdcard/Music/' >>"$LOG" 2>&1
        if [ "$PHASE" = setup ] || [ "$KEEP" = 1 ]; then
            if [ "$DEVICE" = local ]; then echo "release-compare: local AVD left up for the next phase; adb -s ${ANDROID_SERIAL:-emulator-5554} emu kill when done"
            else echo "release-compare: lane left up for the next phase; support/scripts/remote-emu.sh stop when done"; fi
        elif [ "$DEVICE" = local ]; then
            [ -n "${ANDROID_SERIAL:-}" ] && adb emu kill >>"$LOG" 2>&1
        else
            support/scripts/remote-emu.sh stop >>"$LOG" 2>&1 || echo "release-compare: WARNING: remote-emu.sh stop failed, check the lane" >&2
        fi
    fi
    for wt in ${EXTRA_WORKTREES[@]+"${EXTRA_WORKTREES[@]}"}; do git worktree remove --force "$wt" >>"$LOG" 2>&1; done
    git worktree prune >>"$LOG" 2>&1
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

# ---- APKs ----

# resolve_ref <spec>: the commit sha. A bare branch name with an origin/<name> means origin's, since a worktree's
# local branch (main especially) is often behind what was pushed. Main fetches origin first.
resolve_ref() {
    local ref="$1"
    case "$ref" in
        */* | *^* | *~* | *@*) ;;
        *) git show-ref --verify -q "refs/tags/$ref" || ! git show-ref --verify -q "refs/remotes/origin/$ref" || ref="origin/$ref" ;;
    esac
    git rev-parse --verify -q "${ref}^{commit}"
}

# resolve_apk <spec> <label>: sets APK_PATH (a global, so EXTRA_WORKTREES survives; no subshell). A file is used as is;
# a git ref is built once per commit.
resolve_apk() {
    local spec="$1" label="$2" sha src apk cached gradle=(support/scripts/remote-build.sh) env_prefix=()
    if [ -f "$spec" ]; then APK_PATH="$spec"; return 0; fi
    sha="$(resolve_ref "$spec")" || { echo "release-compare: '$spec' is neither an APK nor a git ref" >&2; return 1; }
    echo "release-compare: ${label}: ${spec} is ${sha:0:12}"
    cached="${RC_CACHE}/apk/${sha:0:12}-${BUILD_TYPE}.apk"
    if [ -f "$cached" ]; then APK_PATH="$cached"; return 0; fi
    src="$REPO_ROOT"
    if [ "$sha" = "$(git rev-parse HEAD)" ] && [ -n "$(git status --porcelain)" ]; then
        echo "release-compare: WARNING: '$spec' is ${sha:0:12} but the tree has uncommitted changes; they are NOT in this build" >&2
    fi
    if [ "$sha" != "$(git rev-parse HEAD)" ] || [ -n "$(git status --porcelain)" ]; then
        src="${REPO_ROOT}/tmp/release-compare/src-${label}-${sha:0:12}"
        rm -rf "$src"
        git worktree prune
        git worktree add -f --detach "$src" "$sha" >>"$LOG" 2>&1 || { echo "release-compare: git worktree add failed for $spec" >&2; return 1; }
        EXTRA_WORKTREES+=("$src")
        # gitignored, but Gradle needs the SDK path
        [ -f local.properties ] && cp local.properties "$src/"
    fi
    if [ "$BUILD_TYPE" = release ]; then
        # --local: the env reaches Gradle on this Mac only
        env_prefix=(env CI=true CONFIGURATION=Release "KEYSTORE_PATH=$HOME/.android/debug.keystore" KEYSTORE_PASSWORD=android KEYSTORE_ALIAS=androiddebugkey)
        gradle+=(--local)
    fi
    local task=assembleDebug; [ "$BUILD_TYPE" = release ] && task=assembleRelease
    (cd "$src" && step "build ${label} (${spec}, ${BUILD_TYPE})" ${env_prefix[@]+"${env_prefix[@]}"} "${gradle[@]}" -q ":android:app:${task}") >&2 || return 1
    apk="${src}/android/app/build/outputs/apk/${BUILD_TYPE}/app-${BUILD_TYPE}.apk"
    [ -f "$apk" ] || { echo "release-compare: no APK at $apk" >&2; return 1; }
    mkdir -p "$(dirname "$cached")"
    cp "$apk" "$cached"
    APK_PATH="$cached"
}

# ---- Maestro ----

# scrub: stdin to stdout with the credential values replaced (awk, so they stay off every command line).
scrub() {
    RC_SECRETS="${URL}"$'\n'"${USER_NAME}"$'\n'"${PASSWORD}" awk 'BEGIN { n = split(ENVIRON["RC_SECRETS"], s, "\n") }
        { for (i = 1; i <= n; i++) { v = s[i]; if (v == "") continue; while ((j = index($0, v)) > 0) $0 = substr($0, 1, j - 1) "***" substr($0, j + length(v)) } print }'
}

# run_flow <version> <id> [creds|optional]: runs <id>'s flow (old/new override, else shared); 2 if the version has
# none. creds hands it the server credentials; optional means a failure is expected sometimes, so no warning.
run_flow() {
    local ver="$1" id="$2" mode="${3:-}" creds="" flow rc args
    [ "$mode" = creds ] && creds=1
    if [ -f "${FLOWS}/${ver}/${id}.yaml" ]; then flow="${FLOWS}/${ver}/${id}.yaml"
    elif [ -f "${FLOWS}/${id}.yaml" ]; then flow="${FLOWS}/${id}.yaml"
    else return 2; fi
    args=(-e "APP_ID=${PKG}" -e "ARTIST=${ARTIST}" -e "ALBUM=${ALBUM}" -e "TRACK=${TRACK}" -e "QUERY=${QUERY}"
        -e "GENRE=${GENRE}" -e "PLAYLIST=${PLAYLIST}")
    # MAESTRO_-prefixed env vars reach flows as ${MAESTRO_*}; set only for this one invocation, never on argv
    if [ -n "$creds" ]; then
        MAESTRO_URL="$URL" MAESTRO_USER_NAME="$USER_NAME" MAESTRO_PASSWORD="$PASSWORD" \
            run_with_timeout "$FLOW_TIMEOUT" maestro_run "$flow" "${WORK}/scratch" "${args[@]}" 2>&1 | scrub >"${WORK}/maestro-${ver}-${id}.log"
        rc=${PIPESTATUS[0]}
    else
        run_with_timeout "$FLOW_TIMEOUT" maestro_run "$flow" "${WORK}/scratch" "${args[@]}" 2>&1 | scrub >"${WORK}/maestro-${ver}-${id}.log"
        rc=${PIPESTATUS[0]}
    fi
    rm -rf -- "${WORK}/scratch"
    if [ "$rc" -ne 0 ]; then
        [ "$mode" = optional ] || warn "$ver" "$id" "flow failed (rc ${rc}), see ${WORK}/maestro-${ver}-${id}.log"
        return 1
    fi
}

start_app() {
    adb shell am force-stop "$PKG" >/dev/null 2>&1
    # explicit component: debug builds also have LeakCanary's launcher, which monkey may pick
    adb shell am start -n "${PKG}/com.simplecityapps.shuttle.ui.MainActivity" >>"$LOG" 2>&1
    sleep 3
}

# local_avd_up: the running local emulator, else a headless boot of $LOCAL_AVD; exports ANDROID_SERIAL. Explicit
# serials throughout, since a phone may be plugged in too. The AVD's data persists, so installed apps survive a restart.
local_emulator() { adb devices | awk '$1 ~ /^emulator-/ && $2 == "device" {print $1; exit}'; }
local_avd_up() {
    local serial deadline
    serial="$(local_emulator)"
    if [ -z "$serial" ]; then
        nohup "$HOME/Library/Android/sdk/emulator/emulator" -avd "$LOCAL_AVD" -no-window -no-audio -no-boot-anim \
            -no-snapshot-save </dev/null >"${WORK}/emulator.log" 2>&1 &
        disown
    fi
    deadline=$(($(date +%s) + 420))
    until [ -n "$serial" ] && [ "$(adb -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; do
        [ "$(date +%s)" -lt "$deadline" ] || { echo "local AVD didn't boot in 420 s"; return 1; }
        sleep 5
        serial="$(local_emulator)"
    done
    export ANDROID_SERIAL="$serial"
    local s
    for s in window_animation_scale transition_animation_scale animator_duration_scale; do adb shell settings put global "$s" 0; done
}

# dump_texts: every visible text and content-desc, one per line (the import poll compares these).
dump_texts() {
    adb shell uiautomator dump /sdcard/rc-dump.xml >/dev/null 2>&1 || return 1
    adb exec-out cat /sdcard/rc-dump.xml | grep -oE '(text|content-desc)="[^"]+"'
}

# ---- Capture ----

# placeholders <version> <png>: how many artwork placeholders are on screen. Heuristic: each version's placeholder
# is a flat icon (new: dark disc with a hole; old: grey vinyl record) centred on a flat tile. Mask the icon's exact
# colour, take connected components, and count the near-square ones about as full as the icon whose left side (an
# eighth of a width out) is the tile colour, which rules out text, toolbar icons and real covers. The entries are
# "icon tile min-fill max-fill", sampled from captures.
PLACEHOLDER_NEW="srgb(93,95,104) srgb(225,226,237) 0.55 0.70"
# old: the album vinyl (fill ~0.64) and the artist header's person icon, whose round head fills ~0.78
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

# shot_dest <version> <id>: sets DEST to the shot's path, creating its directory
shot_dest() {
    local idx=0 s
    for s in "${STEPS[@]}"; do idx=$((idx + 1)); [ "$s" = "$2" ] && break; done
    mkdir -p "${OUT}/${1}"
    SHOT_REL="${1}/$(printf '%02d' "$idx")-${2}.png"
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
        if [ -n "$sig" ] && [ "$sig" = "$prev" ] && grep -qF "$ARTIST" <<<"$sig"; then stable=$((stable + 1)); else stable=0; fi
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
        # new lands on the Library, syncing for a few seconds once signing in finishes the Welcome: a Maestro start is
        # too slow to catch it, so the progress is the frame right after sign-in; 1.0.10 imports after onboarding
        [ "$id" = sign-in ] && [ "$ver" = new ] && new_import_progress
        capture "$ver" "$id"
        [ "$id" = sign-in ] && [ "$ver" = new ] && new_import_progress_check
    done
    [ "$ver" = new ] || import_progress "$ver"
    wait_import "$ver" || return 1
    # prewarmed here so the snapshot holds the cached covers and a rerun's screens start warm
    start_app
    step "${ver}: artwork prewarm" run_flow "$ver" prewarm
    snapshot_save "$ver" "$apk"
}

# ---- Imported-state snapshots (local AVD only: the lane's AVD is read-only) ----

# the Emby host, hashed: part of the snapshot key without putting it in a name or log
HOST_TAG=""
host_tag() {
    local h="${URL#*://}"
    h="${h%%[/:]*}"
    [ -n "$HOST_TAG" ] || HOST_TAG="$(printf '%s' "$h" | shasum -a 256 | cut -c1-8)"
}

flows_hash() {
    { printf '%s\n' "$ARTIST" "$ALBUM" "$TRACK" "$QUERY" "$GENRE" "$PLAYLIST"; cat "${FLOWS}"/*.yaml "${FLOWS}/${1}"/*.yaml; } |
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
    local ver="$1" k="$2" id session="${WORK}/session-${ver}.yaml" rc=0 split; shift 2
    local args=(-e "APP_ID=${PKG}" -e "ARTIST=${ARTIST}" -e "ALBUM=${ALBUM}" -e "TRACK=${TRACK}" -e "QUERY=${QUERY}"
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
    local nn=0 id warns not_captured
    warns="$(jq -R -s -c 'split("\n") | map(select(length > 0) | split("\t") | {version: .[0], screen: .[1], message: .[2]})' "$WARNS")"
    not_captured="$({ printf 'both\t%s\n' "${SKIPPED[@]}"; cat "$NOTES"; } |
        jq -R -s -c 'split("\n") | map(select(length > 0) | split("\t") | {version: .[0], screen: .[1], why: .[2]})')"
    {
        for id in "${STEPS[@]}"; do
            nn=$((nn + 1))
            jq -n -c --arg n "$(printf '%02d' "$nn")" --arg id "$id" \
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
    local id nn=0 o n
    command -v magick >/dev/null || { echo "release-compare: ImageMagick not installed, no montages"; return 0; }
    mkdir -p "${OUT}/montage"
    for id in "${STEPS[@]}"; do
        nn=$((nn + 1))
        o="$(awk -F'\t' -v i="$id" '$1 == "old" && $2 == i {p = $3} END {print p}' "$ROWS")"
        n="$(awk -F'\t' -v i="$id" '$1 == "new" && $2 == i {p = $3} END {print p}' "$ROWS")"
        [ -n "$o" ] && [ -n "$n" ] && [ -f "${OUT}/${o}" ] && [ -f "${OUT}/${n}" ] || continue
        magick montage "${OUT}/${o}" "${OUT}/${n}" -tile 2x1 -geometry '+16+0' -background '#202020' "${OUT}/montage/$(printf '%02d' "$nn")-${id}.png" >>"$LOG" 2>&1
    done
}

# ---- Old-build cache ----

# The old build never changes, so a full old capture is kept by its APK's sha256 and the flows' hash.
old_cache_dir() { echo "${RC_CACHE}/old-shots/$(shasum -a 256 "$1" | cut -c1-16)-$(flows_hash old)"; }

# old_cache_restore: the cached shots, rows and state in place of an old run; 1 = no usable cache
old_cache_restore() {
    local dir ids=()
    [ "$REFRESH_OLD" = 0 ] && [ "$PHASE" = all ] && [ -z "$SCREENS_ARG" ] || return 1
    resolve_apk "$OLD_SPEC" old || return 1
    dir="$(old_cache_dir "$APK_PATH")"
    [ -f "${dir}/complete" ] || return 1
    forget old "${STEPS[@]}" import
    rm -rf "${OUT}/old"
    cp -R "${dir}/old" "${OUT}/old"
    cat "${dir}/rows.tsv" >>"$ROWS"
    cat "${dir}/notes.tsv" >>"$NOTES"
    cp "${dir}/old.env" "${STATE}/old.env"
    echo "release-compare: old: reused the cached capture (${dir##*/}; --refresh-old recaptures)"
}

# old_cache_save: after a full, clean old run
old_cache_save() {
    local dir tmp
    [ "$PHASE" = all ] && [ -z "$SCREENS_ARG" ] && [ -z "$(awk -F'\t' '$1 == "old"' "$WARNS")" ] || return 0
    dir="$(old_cache_dir "$(state_get old apk)")"
    tmp="${dir}.tmp"
    rm -rf "$tmp" "$dir"
    mkdir -p "$tmp"
    cp -R "${OUT}/old" "${tmp}/old"
    awk -F'\t' '$1 == "old"' "$ROWS" >"${tmp}/rows.tsv"
    awk -F'\t' '$1 == "old"' "$NOTES" >"${tmp}/notes.tsv"
    cp "${STATE}/old.env" "${tmp}/old.env"
    : >"${tmp}/complete"
    mv "$tmp" "$dir"
}

# ---- Main ----

# not exported: only run_flow hands these to Maestro (export -n in case the file says `export`)
. "$ENV_FILE"
export -n URL USER_NAME PASSWORD 2>/dev/null
for v in URL USER_NAME PASSWORD; do [ -n "${!v:-}" ] || { echo "release-compare: $v missing from $ENV_FILE" >&2; exit 1; }; done
# an *.apk spec that isn't a file would otherwise be tried as a git ref, with a confusing error
for spec in "$OLD_SPEC" "$NEW_SPEC"; do
    case "$spec" in *.apk) [ -f "$spec" ] || { echo "release-compare: no such APK: $spec" >&2; exit 1; } ;; esac
done
# so a branch name resolves to what was pushed (resolve_ref); offline keeps the last fetch
if [ ! -f "$OLD_SPEC" ] || [ ! -f "$NEW_SPEC" ]; then
    git fetch -q origin >>"$LOG" 2>&1 || echo "release-compare: WARNING: git fetch origin failed, refs are as last fetched" >&2
fi
echo "release-compare: ${VERSIONS[*]} / ${PHASE}, output ${OUT}, logs ${WORK}"

OLD_FROM_CACHE=0
case " ${VERSIONS[*]} " in
    *" old "*) if old_cache_restore; then OLD_FROM_CACHE=1; VERSIONS=(${VERSIONS[@]/old/}); fi ;;
esac
# the cached old capture needs no device
if [ ${#VERSIONS[@]} -eq 0 ]; then
    write_manifest
    write_montages
    echo "release-compare: manifest ${OUT}/manifest.json, warnings: $(wc -l <"$WARNS" | tr -d ' ')"
    exit 0
fi

# armed first so the EXIT trap stops a partially started lane
LANE_STARTED=1
support/scripts/remote-emu.sh status >>"$LOG" 2>&1
case $? in
    0) DEVICE=lane ;;
    3) DEVICE=local; echo "release-compare: the box can't run a lane (see $LOG), using the local AVD ${LOCAL_AVD}" ;;
    *) echo "release-compare: remote-emu.sh status failed (see $LOG)" >&2; exit 1 ;;
esac
if [ "$DEVICE" = lane ]; then
    step "remote-emu: start" support/scripts/remote-emu.sh start || exit 1
    eval "$(support/scripts/remote-emu.sh env)"
    MAESTRO_DEVICE_ARGS=(--device "$(support/scripts/remote-emu.sh serial)")
    # The lane's tether (Gnirehtet) asks to set up a VPN on a fresh boot; the dialog would sit over the first frames.
    support/scripts/remote-emu.sh tap-text OK >>"$LOG" 2>&1 || true
else
    step "local AVD: start" local_avd_up || exit 1
    MAESTRO_DEVICE_ARGS=(--device "$ANDROID_SERIAL")
fi

failed=0
for ver in "${VERSIONS[@]}"; do
    vfailed=0
    if [ "$PHASE" != screens ]; then setup "$ver" || { echo "release-compare: ${ver} setup incomplete" >&2; failed=1; continue; }; fi
    if [ "$PHASE" != setup ]; then screens "$ver" || { echo "release-compare: ${ver} screens incomplete" >&2; failed=1; vfailed=1; }; fi
    [ "$ver" = old ] && [ "$vfailed" = 0 ] && old_cache_save
done
write_manifest
write_montages
echo "release-compare: manifest ${OUT}/manifest.json, warnings: $(wc -l <"$WARNS" | tr -d ' ')"
[ -s "$WARNS" ] && cat "$WARNS" >&2
exit "$failed"
