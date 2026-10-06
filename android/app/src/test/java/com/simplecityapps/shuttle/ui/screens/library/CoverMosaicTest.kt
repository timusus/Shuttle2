package com.simplecityapps.shuttle.ui.screens.library

import com.simplecityapps.createSong
import org.junit.Assert.assertEquals
import org.junit.Test

class CoverMosaicTest {
    private val covers = (1..4L).map { createSong(id = it, name = "Track $it", album = "Album $it") }

    @Test
    fun `two covers make a checkerboard`() {
        assertEquals(listOf(1L, 2L, 2L, 1L), mosaicCells(covers.take(2)).map { it.id })
    }

    @Test
    fun `three covers repeat the first in the last cell`() {
        assertEquals(listOf(1L, 2L, 3L, 1L), mosaicCells(covers.take(3)).map { it.id })
    }

    @Test
    fun `four covers fill the grid as they are`() {
        assertEquals(listOf(1L, 2L, 3L, 4L), mosaicCells(covers).map { it.id })
    }
}
