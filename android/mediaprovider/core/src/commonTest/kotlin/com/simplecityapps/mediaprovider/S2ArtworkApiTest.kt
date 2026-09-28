package com.simplecityapps.mediaprovider

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class S2ArtworkApiTest {
    @Test
    fun `album urls form-encode the artist and album as URLEncoder does`() {
        S2ArtworkApi.albumArtworkUrl("AC/DC", "Rock & Roll") shouldBe "https://api.shuttlemusicplayer.app/v1/artwork?artist=AC%2FDC&album=Rock+%26+Roll"
        S2ArtworkApi.albumArtworkUrl("DMA’s", "...And Justice for All") shouldBe
            "https://api.shuttlemusicplayer.app/v1/artwork?artist=DMA%E2%80%99s&album=...And+Justice+for+All"
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
