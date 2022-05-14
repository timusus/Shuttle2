package com.simplecityapps.mediaprovider

import org.junit.Assert.assertEquals
import org.junit.Test

class M3uParserTest {

    private val m3uParser = M3uParser()

    @Test
    fun testExample1() {
        val m3uPlaylist = m3uParser.parse("", "", javaClass.classLoader!!.getResourceAsStream("example1.m3u")!!)
        assertEquals(2, m3uPlaylist.entries.size)

        assertEquals("Documents and Settings/I/My Music/Sample.mp3", m3uPlaylist.entries[0].location)
        assertEquals(123, m3uPlaylist.entries[0].duration)
        assertEquals("Sample artist", m3uPlaylist.entries[0].artist)
        assertEquals("Sample title", m3uPlaylist.entries[0].track)

        assertEquals("Documents and Settings/I/My Music/Greatest Hits/Example.ogg", m3uPlaylist.entries[1].location)
        assertEquals(321, m3uPlaylist.entries[1].duration)
        assertEquals("Example Artist", m3uPlaylist.entries[1].artist)
        assertEquals("Example title", m3uPlaylist.entries[1].track)
    }

    @Test
    fun testExample2() {
        val m3uPlaylist = m3uParser.parse("", "", javaClass.classLoader!!.getResourceAsStream("example2.m3u")!!)
        assertEquals(1, m3uPlaylist.entries.size)

        assertEquals("Music", m3uPlaylist.entries[0].location)
        assertEquals(null, m3uPlaylist.entries[0].duration)
        assertEquals(null, m3uPlaylist.entries[0].artist)
        assertEquals(null, m3uPlaylist.entries[0].track)
    }

    @Test
    fun testExample3() {
        val m3uPlaylist = m3uParser.parse("", "", javaClass.classLoader!!.getResourceAsStream("example3.m3u")!!)
        assertEquals(2, m3uPlaylist.entries.size)

        assertEquals("Sample.mp3", m3uPlaylist.entries[0].location)
        assertEquals(123, m3uPlaylist.entries[0].duration)
        assertEquals("Sample artist", m3uPlaylist.entries[0].artist)
        assertEquals("Sample title", m3uPlaylist.entries[0].track)

        assertEquals("Greatest Hits/Example.ogg", m3uPlaylist.entries[1].location)
        assertEquals(321, m3uPlaylist.entries[1].duration)
        assertEquals("Example Artist", m3uPlaylist.entries[1].artist)
        assertEquals("Example title", m3uPlaylist.entries[1].track)
    }

    @Test
    fun testExample4() {
        val m3uPlaylist = m3uParser.parse("", "", javaClass.classLoader!!.getResourceAsStream("example4.m3u")!!)
        assertEquals(7, m3uPlaylist.entries.size)
        assertEquals("Alternative/Band - Song.mp3", m3uPlaylist.entries[0].location)
    }

    @Test
    fun testExample5() {
        val m3uPlaylist = m3uParser.parse("", "", javaClass.classLoader!!.getResourceAsStream("example5.m3u")!!)
        assertEquals(7, m3uPlaylist.entries.size)

        assertEquals("Alice in Chains_Jar of Flies_01_Rotten Apple.mp3", m3uPlaylist.entries[0].location)
        assertEquals(419, m3uPlaylist.entries[0].duration)
        assertEquals("Alice in Chains", m3uPlaylist.entries[0].artist)
        assertEquals("Rotten Apple", m3uPlaylist.entries[0].track)

        assertEquals("Alice in Chains_Jar of Flies_02_Nutshell.mp3", m3uPlaylist.entries[1].location)
        assertEquals(260, m3uPlaylist.entries[1].duration)
        assertEquals("Alice in Chains", m3uPlaylist.entries[1].artist)
        assertEquals("Nutshell", m3uPlaylist.entries[1].track)
    }
}
