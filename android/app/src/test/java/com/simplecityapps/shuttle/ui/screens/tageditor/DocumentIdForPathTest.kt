package com.simplecityapps.shuttle.ui.screens.tageditor

import io.kotest.matchers.shouldBe
import org.junit.Test

class DocumentIdForPathTest {
    @Test
    fun `a file inside a granted folder maps to a document id under it`() {
        documentIdForPath("/storage/emulated/0/Music/Album/01.mp3", "primary:Music", "/storage/emulated/0/Music") shouldBe "primary:Music/Album/01.mp3"
    }

    @Test
    fun `a file under a granted volume root maps without a doubled separator`() {
        documentIdForPath("/storage/emulated/0/Music/01.flac", "primary:", "/storage/emulated/0") shouldBe "primary:Music/01.flac"
        documentIdForPath("/storage/1234-ABCD/Music/01.flac", "1234-ABCD:", "/storage/1234-ABCD") shouldBe "1234-ABCD:Music/01.flac"
    }

    @Test
    fun `a file outside the folder, or in a sibling that shares its prefix, doesn't map`() {
        documentIdForPath("/storage/emulated/0/Podcasts/01.mp3", "primary:Music", "/storage/emulated/0/Music") shouldBe null
        documentIdForPath("/storage/emulated/0/Music2/01.mp3", "primary:Music", "/storage/emulated/0/Music") shouldBe null
    }
}
