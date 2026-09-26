#!/usr/bin/env bash
# One-time (idempotent) setup of the WSL box for support/scripts/remote-build.sh (#451): a
# user-space JDK matching CI's (Temurin 21) in ~/opt, the box-side Gradle user home under
# ~/s2-builds, and a check that the shared Android SDK already holds every package the build needs.
#
#   support/scripts/remote-build-setup.sh      safe to re-run; a second run changes nothing
#
# The SDK is /opt/android-sdk, a bind mount of the CI runners' SDK volume (see remote-emu.sh), so
# it is off limits for writes: this script never runs sdkmanager against it, and the Gradle user
# home it writes sets android.builder.sdkDownload=false so AGP can't auto-install into it either.
# A missing package is reported with the command the owner would run, and the script fails.
set -euo pipefail

BOX="${REMOTE_BUILD_BOX:-tim@192.168.50.131}"
JDK_FEATURE=21

# Everything the build resolves from the SDK: compileSdk 37, AGP 9.x's default build-tools.
REQUIRED_SDK_PACKAGES=(
    "platforms/android-37.0"
    "build-tools/36.0.0"
    "platform-tools"
)

ssh -o ConnectTimeout=5 -o BatchMode=yes "$BOX" true 2>/dev/null \
    || { echo "remote-build-setup: $BOX is not reachable within 5 s" >&2; exit 3; }

ssh -o BatchMode=yes "$BOX" bash -s -- "$JDK_FEATURE" "${REQUIRED_SDK_PACKAGES[@]}" <<'REMOTE'
set -euo pipefail
jdk_feature="$1"; shift
sdk=/opt/android-sdk
jdk_link="$HOME/opt/jdk-$jdk_feature"
builds="$HOME/s2-builds"
gradle_home="$builds/.gradle-home"

# ---- JDK: Temurin tarball from Adoptium, unpacked into ~/opt, ~/opt/jdk-21 -> the release -------
if [ -x "$jdk_link/bin/java" ]; then
    echo "ok: JDK $("$jdk_link/bin/java" -version 2>&1 | head -1) at $jdk_link"
else
    mkdir -p "$HOME/opt"
    tmp="$(mktemp -d)"
    trap 'rm -rf "$tmp"' EXIT
    curl -fsSL -o "$tmp/jdk.tar.gz" \
        "https://api.adoptium.net/v3/binary/latest/$jdk_feature/ga/linux/x64/jdk/hotspot/normal/eclipse"
    mkdir "$tmp/x"
    tar -xzf "$tmp/jdk.tar.gz" -C "$tmp/x"
    top="$(ls "$tmp/x")"
    [ -d "$HOME/opt/$top" ] || mv "$tmp/x/$top" "$HOME/opt/$top"
    ln -sfn "$HOME/opt/$top" "$jdk_link"
    echo "installed: JDK $("$jdk_link/bin/java" -version 2>&1 | head -1) at $jdk_link -> $top"
fi

# ---- Gradle user home shared by every worktree's build on the box -------------------------------
mkdir -p "$gradle_home"
props="$gradle_home/gradle.properties"
wanted="# Written by support/scripts/remote-build-setup.sh; overrides the project's gradle.properties.
# The SDK is the CI runners' shared volume: never let AGP install into it.
android.builder.sdkDownload=false
# Free an idle daemon's heap after an hour, so a finished session's daemon doesn't sit on the
# memory the emulator lanes need.
org.gradle.daemon.idletimeout=3600000
org.gradle.java.installations.auto-download=false
org.gradle.java.home=$jdk_link
# Remote-build heap and worker cap (#462): shared 23 GB box, up to REMOTE_BUILD_SLOTS concurrent
# builds plus emulator lanes. Smaller than the Mac's -Xmx6g; remote-build.sh's own --max-workers
# (default 6, or 4 with a lane up) is the usual ceiling, this is the floor for a bare ssh call.
org.gradle.jvmargs=-Xmx3g -XX:+HeapDumpOnOutOfMemoryError -Dfile.encoding=UTF-8 -XX:+UseParallelGC -XX:MaxMetaspaceSize=1g
org.gradle.workers.max=6"
if [ -f "$props" ] && [ "$(cat "$props")" = "$wanted" ]; then
    echo "ok: $props"
else
    printf '%s\n' "$wanted" > "$props"
    echo "wrote: $props"
fi

# ---- SDK: check only ----------------------------------------------------------------------------
missing=()
for pkg in "$@"; do
    [ -f "$sdk/$pkg/source.properties" ] || [ -f "$sdk/$pkg/package.xml" ] || missing+=("$pkg")
done
if [ "${#missing[@]}" -gt 0 ]; then
    echo "missing from $sdk: ${missing[*]}" >&2
    echo "the SDK is shared with the CI runners, so this script won't install into it; the owner can run:" >&2
    printf '  %s/cmdline-tools/latest/bin/sdkmanager' "$sdk" >&2
    for pkg in "${missing[@]}"; do printf " '%s'" "${pkg//\//;}" >&2; done
    echo >&2
    exit 1
fi
echo "ok: SDK $sdk has $*"
REMOTE
