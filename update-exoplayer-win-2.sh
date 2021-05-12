cd ./exoplayer
EXOPLAYER_ROOT="$(pwd)"
NDK_PATH="C:\\Users\\tim\\AppData\\Local\\Android\\Sdk\\ndk\\21.3.6528147"

# Update Opus
OPUS_EXT_PATH="${EXOPLAYER_ROOT}/extensions/opus/src/main"

echo "Finalising"
cd "${OPUS_EXT_PATH}"/jni && \
${NDK_PATH}\\ndk-build.cmd APP_ABI=all -j4
$SHELL