package com.simplecityapps.shuttle.model

import io.kotest.matchers.shouldBe
import kotlin.test.Test

// The rest of SongFolder's cases are in :android:app's SongFolderTest; these run on iOS too.
class SongFolderPercentDecodeTest {
    @Test
    fun `decodes multi byte escapes and keeps malformed ones`() {
        SongFolder.locate("file:///storage/emulated/0/In%C3%A8s%20Quarrow/100%zz.mp3") shouldBe
            SongFolder.Location(listOf("primary", "Inès Quarrow"), "100%zz.mp3")
    }

    @Test
    fun `keeps literal characters outside the basic plane beside escapes`() {
        SongFolder.locate("file:///storage/emulated/0/🎵%20Mix/a.mp3") shouldBe
            SongFolder.Location(listOf("primary", "🎵 Mix"), "a.mp3")
    }
}
