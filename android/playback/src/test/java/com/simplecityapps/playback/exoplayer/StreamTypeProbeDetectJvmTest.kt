package com.simplecityapps.playback.exoplayer

import io.kotest.matchers.shouldBe
import org.junit.Test

class StreamTypeProbeDetectJvmTest {
    @Test
    fun `detects hls from the content type`() {
        StreamTypeProbe.detect("application/vnd.apple.mpegurl", ByteArray(0)) shouldBe StreamType.Hls
        StreamTypeProbe.detect("Application/X-MpegURL; charset=utf-8", ByteArray(0)) shouldBe StreamType.Hls
    }

    @Test
    fun `detects hls from the body when the content type does not say`() {
        StreamTypeProbe.detect("application/octet-stream", "#EXTM3U\n".toByteArray()) shouldBe StreamType.Hls
        StreamTypeProbe.detect(null, "﻿#EXTM3U\n".toByteArray()) shouldBe StreamType.Hls
    }

    @Test
    fun `anything else is progressive`() {
        StreamTypeProbe.detect("audio/mpeg", "ID3\u0004".toByteArray()) shouldBe StreamType.Progressive
        StreamTypeProbe.detect(null, "#EXT".toByteArray()) shouldBe StreamType.Progressive
    }
}
