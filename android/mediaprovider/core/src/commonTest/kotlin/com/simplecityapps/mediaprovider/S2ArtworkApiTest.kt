package com.simplecityapps.mediaprovider

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class S2ArtworkApiTest {
    @Test
    fun `album urls form-encode the artist and album as URLEncoder does`() {
        S2ArtworkApi.albumArtworkUrl("AC/DC", "Rock & Roll") shouldBe "https://api.shuttlemusicplayer.app/v1/artwork?artist=AC%2FDC&album=Rock+%26+Roll"
        S2ArtworkApi.albumArtworkUrl("Sigur Rós", "...And Justice for All") shouldBe
            "https://api.shuttlemusicplayer.app/v1/artwork?artist=Sigur+R%C3%B3s&album=...And+Justice+for+All"
    }

    @Test
    fun `curly apostrophes in album urls match the straight apostrophe url`() {
        val expected = "https://api.shuttlemusicplayer.app/v1/artwork?artist=DMA%27s&album=How+Many+Dreams%3F"
        S2ArtworkApi.albumArtworkUrl("DMA's", "How Many Dreams?") shouldBe expected
        S2ArtworkApi.albumArtworkUrl("DMA’s", "How Many Dreams?") shouldBe expected
        S2ArtworkApi.albumArtworkUrl("DMA‘s", "How Many Dreams?") shouldBe expected
        S2ArtworkApi.albumArtworkUrl("DMAʼs", "How Many Dreams?") shouldBe expected
    }

    @Test
    fun `typographic quotes in the album are normalised`() {
        S2ArtworkApi.albumArtworkUrl("Artist", "It’s “Live”") shouldBe
            "https://api.shuttlemusicplayer.app/v1/artwork?artist=Artist&album=It%27s+%22Live%22"
        S2ArtworkApi.albumArtworkUrl("Artist", "It's \"Live\"") shouldBe
            "https://api.shuttlemusicplayer.app/v1/artwork?artist=Artist&album=It%27s+%22Live%22"
    }

    @Test
    fun `typographic quotes in artist urls are normalised`() {
        val expected = "https://api.shuttlemusicplayer.app/v1/artwork?artist=DMA%27s"
        S2ArtworkApi.artistArtworkUrl("DMA's") shouldBe expected
        S2ArtworkApi.artistArtworkUrl("DMA’s") shouldBe expected
        S2ArtworkApi.artistArtworkUrl("DMA‘s") shouldBe expected
        S2ArtworkApi.artistArtworkUrl("DMAʼs") shouldBe expected
        S2ArtworkApi.artistArtworkUrl("“DMA”") shouldBe "https://api.shuttlemusicplayer.app/v1/artwork?artist=%22DMA%22"
    }

    @Test
    fun `artist urls encode non-ascii names as UTF-8`() {
        S2ArtworkApi.artistArtworkUrl("Sigur Rós") shouldBe "https://api.shuttlemusicplayer.app/v1/artwork?artist=Sigur+R%C3%B3s"
    }

    @Test
    fun `the authorization header is Basic over the public credential`() {
        S2ArtworkApi.authorization shouldBe "Basic czI6YUVxUktna0NicUFMakVtOUVnN2U3UWk1"
    }
}
