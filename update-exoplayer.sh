set -e

cd ./exoplayer
EXOPLAYER_ROOT="$(pwd)"
NDK_PATH="$ANDROID_SDK_ROOT/ndk/21.4.7075529"

echo "Updating FLAC Extension"
# Update FLAC
FLAC_EXT_PATH="${EXOPLAYER_ROOT}/extensions/flac/src/main"
cd "${FLAC_EXT_PATH}/jni" &&
  curl https://ftp.osuosl.org/pub/xiph/releases/flac/flac-1.3.2.tar.xz | tar xJ &&
  rm -rf flac &&
  mv flac-1.3.2 flac
cd "${FLAC_EXT_PATH}"/jni &&
  "${NDK_PATH}"/ndk-build APP_ABI=all -j4

echo "Updating Opus Extension"
# Update Opus
OPUS_EXT_PATH="${EXOPLAYER_ROOT}/extensions/opus/src/main"
cd "${OPUS_EXT_PATH}/jni" &&
  rm -rf libopus &&
  git clone https://github.com/xiph/opus.git libopus
cd "${OPUS_EXT_PATH}"/jni && ./convert_android_asm.sh
cd "${OPUS_EXT_PATH}"/jni &&
  "${NDK_PATH}"/ndk-build APP_ABI=all -j4