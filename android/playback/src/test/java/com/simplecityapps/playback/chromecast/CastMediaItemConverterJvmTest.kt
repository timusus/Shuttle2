package com.simplecityapps.playback.chromecast

import io.kotest.matchers.shouldBe
import org.junit.Test

class CastMediaItemConverterJvmTest {
    @Test
    fun `a Wi-Fi address reads lowest byte first`() {
        CastMediaItemConverter.formatIpAddress(0x1401A8C0) shouldBe "192.168.1.20"
    }
}
