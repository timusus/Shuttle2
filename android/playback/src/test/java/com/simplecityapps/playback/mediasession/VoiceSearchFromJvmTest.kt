package com.simplecityapps.playback.mediasession

import com.simplecityapps.playback.mediasession.VoiceSearch.Focus
import io.kotest.matchers.shouldBe
import org.junit.Test

class VoiceSearchFromJvmTest {
    @Test
    fun `no extras is an unstructured search`() {
        VoiceSearch.from("something", null) shouldBe VoiceSearch("something", Focus.Unstructured)
    }
}
