package com.simplecityapps.localmediaprovider.local.repository

import com.simplecityapps.localmediaprovider.local.data.room.dao.toSong
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class AlbumYearTest {
    private fun songs(vararg years: Int?) = years.mapIndexed { index, year -> createSongData(album = "Album", track = index + 1).copy(year = year).toSong() }

    private fun albumYear(vararg years: Int?): Int? {
        val songs = songs(*years)
        return songs.toAlbum(songs.first().albumGroupKey).year
    }

    @Test
    fun `a track with year 0 or 1 does not set the album's year`() {
        albumYear(2005, 0, 1) shouldBe 2005
    }

    @Test
    fun `an album whose tracks all lack a real year has none`() {
        albumYear(0, 1, null) shouldBe null
    }

    @Test
    fun `the album takes its earliest real year`() {
        albumYear(2007, 2005) shouldBe 2005
    }
}
