# Toolchain image for building Shuttle2 as one-shot Jobs.
# Mirrors the OffBookPlus builder: Temurin JDK 21 + Android SDK (API 37).
# Used only by clusters WITH a container runtime to build images
# (docker/colima). Clusters without one (e.g. remote k3s) should use
# k8s/scripts/build-remote.sh, which bootstraps the SDK at Job runtime
# from eclipse-temurin:21-jdk-noble and needs no image build.
#
# Shuttle2 needs: compileSdk 37, AGP 9.4.1, Gradle 9.7.x, JDK 21.
# No NDK: the KMP iOS targets are not built by :android:app:assembleDebug.

FROM eclipse-temurin:21-jdk-noble

ENV ANDROID_HOME=/opt/android-sdk \
    CMDLINE_TOOLS_VERSION=13114758
ENV PATH="${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${PATH}"

RUN apt-get update \
    && apt-get install -y --no-install-recommends unzip curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*

RUN mkdir -p "${ANDROID_HOME}/cmdline-tools" \
    && curl -fsSL -o /tmp/cmdline-tools.zip \
    "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip" \
    && unzip -q /tmp/cmdline-tools.zip -d "${ANDROID_HOME}/cmdline-tools" \
    && mv "${ANDROID_HOME}/cmdline-tools/cmdline-tools" "${ANDROID_HOME}/cmdline-tools/latest" \
    && rm /tmp/cmdline-tools.zip

RUN yes | sdkmanager --licenses > /dev/null \
    && sdkmanager --install \
    "platform-tools" \
    "platforms;android-37.0" \
    "platforms;android-37.1" \
    "build-tools;37.0.0" \
    > /dev/null \
    && yes | sdkmanager --licenses > /dev/null

# aapt2 is x86_64-only; on arm64 hosts it runs through qemu-user, which needs
# the amd64 loader/libs. Last heavy layer so refreshes skip SDK downloads.
RUN dpkg --add-architecture amd64 \
    && sed -i 's/^Types: deb$/Types: deb\nArchitectures: arm64/' /etc/apt/sources.list.d/ubuntu.sources \
    && echo "deb [arch=amd64] http://archive.ubuntu.com/ubuntu noble main universe" > /etc/apt/sources.list.d/amd64.list \
    && echo "deb [arch=amd64] http://archive.ubuntu.com/ubuntu noble-updates main universe" >> /etc/apt/sources.list.d/amd64.list \
    && apt-get update \
    && apt-get install -y --no-install-recommends libc6:amd64 libstdc++6:amd64 zlib1g:amd64 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace

CMD ["bash"]
