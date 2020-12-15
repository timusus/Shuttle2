cd ./exoplayer
EXOPLAYER_ROOT="$(pwd)"

# Fetch latest exoplayer
git pull

# Update Opus

OPUS_EXT_PATH="${EXOPLAYER_ROOT}/extensions/opus/src/main"

NDK_PATH="/Users/tim/Library/Android/sdk/ndk/21.1.6352462"

cd "${OPUS_EXT_PATH}/jni" && \
git clone https://github.com/xiph/opus.git libopus

cd ${OPUS_EXT_PATH}/jni && ./convert_android_asm.sh

cd "${OPUS_EXT_PATH}"/jni && \
${NDK_PATH}/ndk-build APP_ABI=all -j4

# Update FLAC

FLAC_EXT_PATH="${EXOPLAYER_ROOT}/extensions/flac/src/main"

cd "${FLAC_EXT_PATH}/jni" && \
curl https://ftp.osuosl.org/pub/xiph/releases/flac/flac-1.3.2.tar.xz | tar xJ && \
mv flac-1.3.2 flac

cd "${FLAC_EXT_PATH}"/jni && \
${NDK_PATH}/ndk-build APP_ABI=all -j4