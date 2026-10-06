package com.simplecityapps.playback.mediasession

import com.simplecityapps.imageloading.coil.NowPlayingArtwork
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.Test

class ArtworkBitmapLoaderKeyTest {
    private val artwork = NowPlayingArtwork(model = Any(), cacheKey = "artistImage:a|song:b")

    @Test
    fun `the key follows what the artwork resolves to`() {
        val other = NowPlayingArtwork(model = Any(), cacheKey = "artistImage:a|song:c")

        ArtworkBitmapLoader.sessionArtworkKey(artwork, localOnly = false, wifiOnly = true) shouldNotBe
            ArtworkBitmapLoader.sessionArtworkKey(other, localOnly = false, wifiOnly = true)
    }

    @Test
    fun `the settings that decide whether a remote image is reachable are part of the key`() {
        val base = ArtworkBitmapLoader.sessionArtworkKey(artwork, localOnly = false, wifiOnly = true)

        ArtworkBitmapLoader.sessionArtworkKey(artwork, localOnly = true, wifiOnly = true) shouldNotBe base
        ArtworkBitmapLoader.sessionArtworkKey(artwork, localOnly = false, wifiOnly = false) shouldNotBe base
        ArtworkBitmapLoader.sessionArtworkKey(artwork, localOnly = false, wifiOnly = true) shouldBe base
    }
}
