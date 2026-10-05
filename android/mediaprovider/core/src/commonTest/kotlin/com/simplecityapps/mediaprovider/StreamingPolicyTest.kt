package com.simplecityapps.mediaprovider

import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.persistence.putString
import com.simplecityapps.shuttle.settings.SettingsStore
import com.simplecityapps.shuttle.settings.StreamingQuality
import com.simplecityapps.shuttle.settings.StreamingSettings
import com.simplecityapps.shuttle.settings.TranscodeFormat
import com.simplecityapps.shuttle.streaming.DeliveredFormats
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class StreamingPolicyTest {
    private val store = InMemoryKeyValueStore()
    private val streamingSettings = StreamingSettings(SettingsStore(store))
    private var metered = false
    private val cap = StreamingPolicy(streamingSettings, DeliveredFormats()) { metered }

    @Test
    fun `streams the original by default unmetered and 320 kbps metered`() {
        cap.maxBitrateKbps() shouldBe null
        metered = true
        cap.maxBitrateKbps() shouldBe 320
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
    fun `a stored value no quality has reads as the default`() {
        store.putString(StreamingSettings.MeteredQuality.key, "Kbps999")
        metered = true

        cap.maxBitrateKbps() shouldBe 320
    }

    @Test
    fun `downloads keep the original by default, whatever the network`() {
        metered = true

        cap.downloadMaxBitrateKbps() shouldBe null
    }

    @Test
    fun `downloads use the download quality, not the network's`() {
        streamingSettings.downloadQuality.value = StreamingQuality.Kbps192
        streamingSettings.meteredQuality.value = StreamingQuality.Kbps128
        metered = true

        cap.downloadMaxBitrateKbps() shouldBe 192
    }

    @Test
    fun `the transcode format is Auto until chosen`() {
        cap.transcodeFormat() shouldBe TranscodeFormat.Auto
        streamingSettings.transcodeFormat.value = TranscodeFormat.Opus

        cap.transcodeFormat() shouldBe TranscodeFormat.Opus
    }
}
