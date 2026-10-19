# shellcheck shell=bash
# APK resolution and the old-build cache for support/scripts/release-compare.sh. Sourced, not run.

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
    local spec="$1" label="$2" sha src apk cached gradle=("${SCRIPT_DIR}/remote-build.sh") env_prefix=()
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
    # trees from before the daemon JVM was pinned run Gradle 8 on JAVA_HOME, which can't read the default JDK 25
    if [ ! -f "$src/gradle/gradle-daemon-jvm.properties" ]; then
        env_prefix+=(env "JAVA_HOME=$(/usr/libexec/java_home -v 21)")
        [[ " ${gradle[*]} " == *" --local "* ]] || gradle+=(--local)
    fi
    local task=assembleDebug; [ "$BUILD_TYPE" = release ] && task=assembleRelease
    (cd "$src" && step "build ${label} (${spec}, ${BUILD_TYPE})" ${env_prefix[@]+"${env_prefix[@]}"} "${gradle[@]}" -q ":android:app:${task}") >&2 || return 1
    apk="${src}/android/app/build/outputs/apk/${BUILD_TYPE}/app-${BUILD_TYPE}.apk"
    [ -f "$apk" ] || { echo "release-compare: no APK at $apk" >&2; return 1; }
    mkdir -p "$(dirname "$cached")"
    cp "$apk" "$cached"
    APK_PATH="$cached"
}

# The old build never changes, so a full old capture is kept by its APK's sha256 and the flows' hash.
old_cache_dir() { echo "${RC_CACHE}/old-shots/$(shasum -a 256 "$1" | cut -c1-16)-$(flows_hash old)"; }

# old_cache_restore: the cached shots, rows and state in place of an old run; 1 = no usable cache
old_cache_restore() {
    local dir
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
