#!/usr/bin/env bash
# Matched old-vs-new Android screenshots of the real UI against the Emby test library, for a release
# comparison (and the Remotion video made from manifest.json).
#
#   support/scripts/release-compare.sh --old <apk|git-ref> --new <apk|git-ref>
#                                      [--version old|new|both] [--phase setup|screens|all] [--keep]
#                                      [--screens a,b,...] [--out <dir>] [--build-type debug|release]
#                                      [--flow-timeout <secs>] [--import-timeout <secs>]
#
#     --old / --new      an APK path, or a git ref that is built here (the current tree when the ref is HEAD and
#                        the tree is clean, else a detached worktree under tmp/release-compare; the APK is cached
#                        per commit and build type)
#     --version          which build(s) to run (default both, old first)
#     --phase            setup: fresh install, demo mode, the onboarding captures, sign in to Emby, wait for the
#                        import. screens: artwork prewarm and the library captures on the app setup left installed.
#                        all (default): both. Each phase stays under ~8 minutes per version, so run them one call
#                        at a time; the lane's AVD is read-only, so screens needs the lane setup ran on: setup
#                        always leaves it up, screens and all stop it unless --keep.
#     --screens a,b      only capture these ids (the other setup steps still run, so the library exists). Ids:
#                          launch permission analytics source add-source emby-form sign-in import-progress
#                          home artist-list artist-detail album-list album-detail search now-playing settings
#                        Onboarding differs by version: a step a version doesn't have is null in the manifest.
#     --out <dir>        output directory (default build/release-compare/<old>-vs-<new>, from the APK version
#                        or the ref name). Runs merge into it: a capture replaces only its own screen's row.
#     --build-type       how a git ref is built (default debug). release is a minified release build signed with
#                        the debug keystore (CI=true CONFIGURATION=Release), since the Play keystore stays in
#                        release-android. The lane has no Play Store, so a release build never learns it is Pro
#                        and refuses to add a server: debug (always Pro) is the default.
#
# Typical run, one foreground call per line:
#   release-compare.sh --old <apk> --new main --version old --phase setup
#   release-compare.sh --old <apk> --new main --version old --phase screens --keep
#   release-compare.sh --old <apk> --new main --version new --phase setup
#   release-compare.sh --old <apk> --new main --version new --phase screens
#
# Maestro drives the real UI by visible text (flows in support/maestro/release-compare/, shared, with old/ and new/
# overrides) and this script owns the capture: SystemUI demo mode, adb screencap every ~1.5 s until 3 consecutive
# frames are identical and no artwork placeholder is on screen (45 s timeout: a manifest warning naming the screen).
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
# ---------------------------------------------------------------------------------------------

ENV_FILE="$HOME/.config/s2-test/emby-appreview.env"
FLOWS="support/maestro/release-compare"
STEPS=(launch permission analytics source add-source emby-form sign-in import-progress home artist-list artist-detail album-list album-detail search now-playing settings)
SETUP_IDS=(launch permission analytics source add-source emby-form sign-in)
SCREEN_IDS=(home artist-list artist-detail album-list album-detail search now-playing settings)

usage() { awk 'NR>1 && /^#/ {sub(/^# ?/, ""); print; next} NR>1 {exit}' "$0"; }

OLD_SPEC=""; NEW_SPEC=""; SCREENS_ARG=""; OUT=""; BUILD_TYPE=debug; VERSION=both; PHASE=all; KEEP=0
FLOW_TIMEOUT=240; IMPORT_TIMEOUT=300
while [ $# -gt 0 ]; do
    case "$1" in
        --old) OLD_SPEC="${2:?release-compare: --old needs an APK or git ref}"; shift 2 ;;
        --new) NEW_SPEC="${2:?release-compare: --new needs an APK or git ref}"; shift 2 ;;
        --version) VERSION="${2:?release-compare: --version needs old, new or both}"; shift 2 ;;
        --phase) PHASE="${2:?release-compare: --phase needs setup, screens or all}"; shift 2 ;;
        --keep) KEEP=1; shift ;;
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
touch "$ROWS" "$WARNS"

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
    for f in "$ROWS" "$WARNS"; do
        awk -F'\t' -v v="$ver" -v ids=" $* " '!($1 == v && index(ids, " " $2 " "))' "$f" >"${f}.tmp" && mv -f "${f}.tmp" "$f"
    done
}

cleanup() {
    local wt
    if [ "$LANE_STARTED" = 1 ]; then
        emu_demo_mode off
        if [ "$PHASE" = setup ] || [ "$KEEP" = 1 ]; then
            echo "release-compare: lane left up for the next phase; support/scripts/remote-emu.sh stop when done"
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

# resolve_apk <spec> <label>: sets APK_PATH (a global, so EXTRA_WORKTREES survives; no subshell). A file is used as is;
# a git ref is built once per commit.
resolve_apk() {
    local spec="$1" label="$2" sha src apk cached gradle=(support/scripts/remote-build.sh) env_prefix=()
    if [ -f "$spec" ]; then APK_PATH="$spec"; return 0; fi
    sha="$(git rev-parse --verify "${spec}^{commit}" 2>/dev/null)" || { echo "release-compare: '$spec' is neither an APK nor a git ref" >&2; return 1; }
    cached="${REPO_ROOT}/tmp/release-compare/apk/${sha:0:12}-${BUILD_TYPE}.apk"
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

# run_flow <version> <id> [with-credentials]: runs <id>'s flow (old/new override, else shared); 2 if the version has none.
run_flow() {
    local ver="$1" id="$2" creds="${3:-}" flow rc args
    if [ -f "${FLOWS}/${ver}/${id}.yaml" ]; then flow="${FLOWS}/${ver}/${id}.yaml"
    elif [ -f "${FLOWS}/${id}.yaml" ]; then flow="${FLOWS}/${id}.yaml"
    else return 2; fi
    args=(-e "APP_ID=${PKG}" -e "ARTIST=${ARTIST}" -e "ALBUM=${ALBUM}" -e "TRACK=${TRACK}" -e "QUERY=${QUERY}")
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
        warn "$ver" "$id" "flow failed (rc ${rc}), see ${WORK}/maestro-${ver}-${id}.log"
        return 1
    fi
}

start_app() {
    adb shell am force-stop "$PKG" >/dev/null 2>&1
    # explicit component: debug builds also have LeakCanary's launcher, which monkey may pick
    adb shell am start -n "${PKG}/com.simplecityapps.shuttle.ui.MainActivity" >>"$LOG" 2>&1
    sleep 3
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
# shows. live screens (a running import) never go still, so they get one frame after the dwell. Placeholders that
# outlast the timeout are kept with a warning: the server has no art for some albums.
settle_capture() {
    local ver="$1" id="$2" dest="$3" live="${4:-}" start now cur="${WORK}/frame.cur" prev="${WORK}/frame.prev" same=0 ph=0 ph_since=""
    start=$(date +%s)
    rm -f "$prev"
    while :; do
        adb exec-out screencap -p >"$cur" 2>/dev/null
        now=$(date +%s)
        if [ -n "$live" ]; then
            if [ $((now - start)) -ge 3 ]; then mv -f "$cur" "$dest"; return 0; fi
        else
            if [ -f "$prev" ] && cmp -s "$cur" "$prev"; then same=$((same + 1)); else same=0; fi
            mv -f "$cur" "$prev"
            if [ "$same" -ge 2 ] && [ $((now - start)) -ge 3 ]; then
                ph="$(placeholders "$ver" "$prev")"
                if [ "$ph" = 0 ]; then mv -f "$prev" "$dest"; return 0; fi
                # a still frame with placeholders: give the covers PH_GRACE more seconds, then accept it
                [ -n "$ph_since" ] || ph_since=$now
                if [ $((now - ph_since)) -ge "$PH_GRACE" ]; then
                    mv -f "$prev" "$dest"
                    warn "$ver" "$id" "${ph} artwork placeholder(s) still on screen after ${PH_GRACE} s settled (none on the server?)"
                    return 0
                fi
            fi
            if [ $((now - start)) -ge 45 ]; then
                mv -f "$prev" "$dest"
                warn "$ver" "$id" "frame never settled in 45 s (captured the last one)"
                return 0
            fi
        fi
        sleep 1.5
    done
}

selected() { case " ${SELECTED[*]} " in *" $1 "*) return 0 ;; *) return 1 ;; esac; }

# capture <version> <id> [live]
capture() {
    local ver="$1" id="$2" live="${3:-}" nn idx=0 s dest
    for s in "${STEPS[@]}"; do idx=$((idx + 1)); [ "$s" = "$id" ] && break; done
    selected "$id" || return 0
    nn="$(printf '%02d' "$idx")"
    mkdir -p "${OUT}/${ver}"
    dest="${OUT}/${ver}/${nn}-${id}.png"
    settle_capture "$ver" "$id" "$dest" "$live"
    printf '%s\t%s\t%s\n' "$ver" "$id" "${ver}/${nn}-${id}.png" >>"$ROWS"
    echo "release-compare: ${ver}/${nn}-${id}.png"
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
        sig="$(support/scripts/remote-emu.sh dump-texts 2>/dev/null | sed 's/ *\[[0-9,]*\]\[[0-9,]*\]//g')"
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
    [ -f "$spec" ] || sha="$(git rev-parse --verify "${spec}^{commit}")"
    PKG="$(emu_apk_info "$apk" package)"
    echo "release-compare: == ${ver} setup: ${PKG} =="
    # only the screens being re-captured, so rows of the others merge
    for id in "${ids_all[@]}"; do selected "$id" && forget_ids+=("$id"); done
    selected import-progress && forget_ids+=(import)
    [ ${#forget_ids[@]} -eq 0 ] || forget "$ver" "${forget_ids[@]}"
    printf 'spec=%s\nsha=%s\npackage=%s\nversionName=%s\nversionCode=%s\n' "$spec" "$sha" "$PKG" \
        "$(emu_apk_info "$apk" versionName)" "$(emu_apk_info "$apk" versionCode)" >"${STATE}/${ver}.env"
    adb uninstall "$PKG" >/dev/null 2>&1
    step "${ver}: install" adb install -r "$apk" || return 1
    emu_demo_mode on
    start_app
    for id in "${SETUP_IDS[@]}"; do
        run_flow "$ver" "$id" "$([ "$id" = sign-in ] && echo creds)"; rc=$?
        [ "$rc" = 2 ] && continue
        [ "$rc" = 1 ] && { echo "release-compare: ${ver}: setup stopped at ${id}" >&2; return 1; }
        capture "$ver" "$id"
    done
    capture "$ver" import-progress live
    wait_import "$ver"
}

# screens <version>: the library screens on the app setup installed.
screens() {
    local ver="$1" id rc ids=()
    PKG="$(state_get "$ver" package)"
    [ -n "$PKG" ] && adb shell pm path "$PKG" 2>/dev/null | grep -q package: ||
        { echo "release-compare: ${ver} isn't installed on this lane: run --phase setup first (and keep the lane up)" >&2; return 1; }
    echo "release-compare: == ${ver} screens: ${PKG} =="
    for id in "${SCREEN_IDS[@]}"; do selected "$id" && ids+=("$id"); done
    [ ${#ids[@]} -gt 0 ] || return 0
    forget "$ver" "${ids[@]}" prewarm
    emu_demo_mode on
    start_app
    step "${ver}: artwork prewarm" run_flow "$ver" prewarm
    for id in "${ids[@]}"; do
        start_app
        run_flow "$ver" "$id"; rc=$?
        [ "$rc" = 0 ] || continue
        # the full player is only still while paused
        [ "$id" = now-playing ] && { adb shell input keyevent KEYCODE_MEDIA_PAUSE; sleep 1; }
        capture "$ver" "$id"
    done
}

write_manifest() {
    local nn=0 id warns
    warns="$(jq -R -s -c 'split("\n") | map(select(length > 0) | split("\t") | {version: .[0], screen: .[1], message: .[2]})' "$WARNS")"
    {
        for id in "${STEPS[@]}"; do
            nn=$((nn + 1))
            jq -n -c --arg n "$(printf '%02d' "$nn")" --arg id "$id" \
                --arg old "$(awk -F'\t' -v i="$id" '$1 == "old" && $2 == i {p = $3} END {print p}' "$ROWS")" \
                --arg new "$(awk -F'\t' -v i="$id" '$1 == "new" && $2 == i {p = $3} END {print p}' "$ROWS")" \
                '{n: ($n | tonumber), id: $id, caption: "", old: (if $old == "" then null else $old end), new: (if $new == "" then null else $new end)}'
        done
    } | jq -s --argjson warnings "$warns" \
        --arg os "$(state_get old spec)" --arg ov "$(state_get old versionName)" --arg oc "$(state_get old versionCode)" --arg osha "$(state_get old sha)" \
        --arg ns "$(state_get new spec)" --arg nv "$(state_get new versionName)" --arg nc "$(state_get new versionCode)" --arg nsha "$(state_get new sha)" \
        --arg run "$RUN" \
        '{run: $run, old: {spec: $os, sha: (if $osha == "" then null else $osha end), versionName: $ov, versionCode: ($oc | tonumber? // $oc)}, new: {spec: $ns, sha: (if $nsha == "" then null else $nsha end), versionName: $nv, versionCode: ($nc | tonumber? // $nc)}, screens: map(select(.old != null or .new != null)), warnings: $warnings}' >"${OUT}/manifest.json"
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

# ---- Main ----

# not exported: only run_flow hands these to Maestro (export -n in case the file says `export`)
. "$ENV_FILE"
export -n URL USER_NAME PASSWORD 2>/dev/null
for v in URL USER_NAME PASSWORD; do [ -n "${!v:-}" ] || { echo "release-compare: $v missing from $ENV_FILE" >&2; exit 1; }; done
# an *.apk spec that isn't a file would otherwise be tried as a git ref, with a confusing error
for spec in "$OLD_SPEC" "$NEW_SPEC"; do
    case "$spec" in *.apk) [ -f "$spec" ] || { echo "release-compare: no such APK: $spec" >&2; exit 1; } ;; esac
done
echo "release-compare: ${VERSIONS[*]} / ${PHASE}, output ${OUT}, logs ${WORK}"

# armed first so the EXIT trap stops a partially started lane
LANE_STARTED=1
step "remote-emu: start" support/scripts/remote-emu.sh start || exit 1
eval "$(support/scripts/remote-emu.sh env)"
MAESTRO_DEVICE_ARGS=(--device "$(support/scripts/remote-emu.sh serial)")
# The lane's tether (Gnirehtet) asks to set up a VPN on a fresh boot; the dialog would sit over the first frames.
support/scripts/remote-emu.sh tap-text OK >>"$LOG" 2>&1 || true

failed=0
for ver in "${VERSIONS[@]}"; do
    if [ "$PHASE" != screens ]; then setup "$ver" || { echo "release-compare: ${ver} setup incomplete" >&2; failed=1; continue; }; fi
    if [ "$PHASE" != setup ]; then screens "$ver" || { echo "release-compare: ${ver} screens incomplete" >&2; failed=1; }; fi
done
write_manifest
write_montages
echo "release-compare: manifest ${OUT}/manifest.json, warnings: $(wc -l <"$WARNS" | tr -d ' ')"
[ -s "$WARNS" ] && cat "$WARNS" >&2
exit "$failed"
