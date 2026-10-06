package com.simplecityapps.playback.mediasession

import android.os.Bundle
import android.provider.MediaStore
import com.simplecityapps.playback.mediasession.VoiceSearch.Focus
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** RS-60: how a search's extras map to a [VoiceSearch]. */
@RunWith(RobolectricTestRunner::class)
class VoiceSearchFromTest {

    @Test
    fun `the any focus is an unstructured search`() {
        val extras = Bundle().apply { putString(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*") }

        VoiceSearch.from("something", extras) shouldBe VoiceSearch("something", Focus.Unstructured)
    }

    @Test
    fun `no extras is an unstructured search`() {
        VoiceSearch.from("something", null) shouldBe VoiceSearch("something", Focus.Unstructured)
    }

    @Test
    fun `a genre focus carries the genre`() {
        val extras = Bundle().apply {
            putString(MediaStore.EXTRA_MEDIA_FOCUS, MediaStore.Audio.Genres.ENTRY_CONTENT_TYPE)
            putString(MediaStore.EXTRA_MEDIA_GENRE, "Folk")
        }

        VoiceSearch.from("folk", extras) shouldBe VoiceSearch("folk", Focus.Genre, genre = "Folk")
    }
}
