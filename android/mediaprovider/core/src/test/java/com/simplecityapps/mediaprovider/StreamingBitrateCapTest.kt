package com.simplecityapps.mediaprovider

import com.simplecityapps.shuttle.settings.SettingsStore
import com.simplecityapps.shuttle.settings.StreamingQuality
import com.simplecityapps.shuttle.settings.StreamingSettings
import io.kotest.matchers.shouldBe
import org.junit.Test

class StreamingBitrateCapTest {
    private val sharedPreferences = FakeSharedPreferences()
    private val streamingSettings = StreamingSettings(SettingsStore(sharedPreferences))
    private var metered = false
    private val cap = StreamingBitrateCap(streamingSettings) { metered }

    @Test
    fun `streams the original by default on either network`() {
        cap.maxBitrateKbps() shouldBe null
        metered = true
        cap.maxBitrateKbps() shouldBe null
    }

    @Test
    fun `an unmetered network uses the unmetered quality`() {
        streamingSettings.unmeteredQuality.value = StreamingQuality.Kbps320
        streamingSettings.meteredQuality.value = StreamingQuality.Kbps128

        cap.maxBitrateKbps() shouldBe 320
    }

    @Test
    fun `a metered network uses the metered quality`() {
        streamingSettings.unmeteredQuality.value = StreamingQuality.Original
        streamingSettings.meteredQuality.value = StreamingQuality.Kbps128
        metered = true

        cap.maxBitrateKbps() shouldBe 128
    }

    @Test
    fun `a network change applies to the next read`() {
        streamingSettings.meteredQuality.value = StreamingQuality.Kbps192

        cap.maxBitrateKbps() shouldBe null
        metered = true
        cap.maxBitrateKbps() shouldBe 192
    }

    @Test
    fun `a stored value no quality has reads as original`() {
        sharedPreferences.edit().putString(StreamingSettings.MeteredQuality.key, "Kbps999").apply()
        metered = true

        cap.maxBitrateKbps() shouldBe null
    }
}
