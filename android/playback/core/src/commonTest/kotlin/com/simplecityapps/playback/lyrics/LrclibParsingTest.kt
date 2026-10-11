package com.simplecityapps.playback.lyrics

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

class LrclibParsingTest {
    private fun record(json: String) = LrclibClient.parseRecord(Json.parseToJsonElement(json).jsonObject)

    @Test
    fun `plain lyrics are shown as they are`() {
        record("""{"duration":200.0,"instrumental":false,"plainLyrics":"one\ntwo","syncedLyrics":null}""").displayText() shouldBe "one\ntwo"
    }

    @Test
    fun `synced lyrics are shown without their time stamps`() {
        val parsed = record("""{"duration":200.0,"instrumental":false,"plainLyrics":null,"syncedLyrics":"[00:01.00] one\n[01:02.345][01:30.00] two\n[02:00]"}""")

        parsed.synced shouldBe "[00:01.00] one\n[01:02.345][01:30.00] two\n[02:00]"
        parsed.displayText() shouldBe "one\ntwo"
    }

    @Test
    fun `an instrumental has no text`() {
        record("""{"duration":200.0,"instrumental":true,"plainLyrics":null,"syncedLyrics":null}""").displayText() shouldBe null
    }

    @Test
    fun `search takes the first result with lyrics within two seconds`() {
        val body = """[
            {"duration":300.0,"plainLyrics":"wrong length"},
            {"duration":201.0,"plainLyrics":null,"syncedLyrics":null},
            {"duration":201.5,"plainLyrics":"right"},
            {"duration":200.0,"plainLyrics":"later"}
        ]"""

        LrclibClient.parseSearch(body, durationSeconds = 200)?.displayText() shouldBe "right"
    }

    @Test
    fun `search with nothing close finds nothing`() {
        LrclibClient.parseSearch("""[{"duration":300.0,"plainLyrics":"x"}]""", durationSeconds = 200) shouldBe null
        LrclibClient.parseSearch("[]", durationSeconds = 200) shouldBe null
    }
}
