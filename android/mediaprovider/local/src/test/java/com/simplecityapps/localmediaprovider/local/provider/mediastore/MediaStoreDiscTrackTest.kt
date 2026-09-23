package com.simplecityapps.localmediaprovider.local.provider.mediastore

import io.kotest.matchers.shouldBe
import org.junit.Test

class MediaStoreDiscTrackTest {
    @Test
    fun `disc 2 track 5 encoded as 2005`() {
        decodeDiscTrack(2005, discNumberColumnValue = null) shouldBe DiscTrack(disc = 2, track = 5)
    }

    @Test
    fun `disc 1 track 1 encoded as 1001`() {
        decodeDiscTrack(1001, discNumberColumnValue = null) shouldBe DiscTrack(disc = 1, track = 1)
    }

    @Test
    fun `unencoded track below 1000 is disc 1`() {
        decodeDiscTrack(12, discNumberColumnValue = null) shouldBe DiscTrack(disc = 1, track = 12)
    }

    @Test
    fun `track 0 is disc 1 track 0`() {
        decodeDiscTrack(0, discNumberColumnValue = null) shouldBe DiscTrack(disc = 1, track = 0)
    }

    @Test
    fun `encoded boundary of exactly 1000 is disc 1 track 0`() {
        decodeDiscTrack(1000, discNumberColumnValue = null) shouldBe DiscTrack(disc = 1, track = 0)
    }

    @Test
    fun `DISC_NUMBER column wins over the encoded track value`() {
        decodeDiscTrack(1001, discNumberColumnValue = "2") shouldBe DiscTrack(disc = 2, track = 1)
    }

    @Test
    fun `DISC_NUMBER as 'n over total' takes the numerator`() {
        decodeDiscTrack(1001, discNumberColumnValue = "2/3") shouldBe DiscTrack(disc = 2, track = 1)
    }

    @Test
    fun `blank DISC_NUMBER falls back to the encoded disc`() {
        decodeDiscTrack(2005, discNumberColumnValue = "") shouldBe DiscTrack(disc = 2, track = 5)
    }

    @Test
    fun `null DISC_NUMBER falls back to the encoded disc`() {
        decodeDiscTrack(2005, discNumberColumnValue = null) shouldBe DiscTrack(disc = 2, track = 5)
    }

    @Test
    fun `garbage DISC_NUMBER falls back to the encoded disc`() {
        decodeDiscTrack(2005, discNumberColumnValue = "unknown") shouldBe DiscTrack(disc = 2, track = 5)
    }

    @Test
    fun `zero or negative DISC_NUMBER falls back to the encoded disc`() {
        decodeDiscTrack(2005, discNumberColumnValue = "0") shouldBe DiscTrack(disc = 2, track = 5)
    }

    @Test
    fun `parseDiscNumber examples`() {
        parseDiscNumber("2") shouldBe 2
        parseDiscNumber("2/3") shouldBe 2
        parseDiscNumber("") shouldBe null
        parseDiscNumber(null) shouldBe null
        parseDiscNumber("unknown") shouldBe null
    }
}
