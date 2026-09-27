package com.simplecityapps.provider.plex.http

import io.kotest.matchers.shouldBe
import java.net.URLEncoder
import kotlin.test.Test

class FormUrlEncodingTest {
    @Test
    fun `formUrlEncode matches URLEncoder for the transcode query values`() {
        listOf(
            "",
            "/library/metadata/1234",
            "add-transcode-target(type=musicProfile&context=streaming&protocol=hls&container=mpegts&audioCodec=aac)",
            "Tim's Pixel 8 Pro",
            "S2 Music Player",
            "2026.09.27",
            "a.b-c*d_e~f+g%h/i:j?k#l[m]n@o!p\$q,r;s=t",
            "Café Ünïcödé 日本語 🎵",
            "\t\n\u0000\u007f"
        ).forEach { value ->
            formUrlEncode(value) shouldBe URLEncoder.encode(value, "UTF-8")
        }
    }
}
