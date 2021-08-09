package com.simplecityapps.mediaprovider

import org.junit.Test

class M3uParserTest {

    private val m3uParser = M3uParser()

    @Test
    fun testExample1() {
        val m3uPlaylist = m3uParser.parse("", "", javaClass.classLoader!!.getResourceAsStream("example1.m3u")!!)
        assert(m3uPlaylist.entries.size == 2)

        assert(m3uPlaylist.entries[0].location == "C:\\Documents and Settings\\I\\My Music\\Sample.mp3")
        assert(m3uPlaylist.entries[0].duration == 123)
        assert(m3uPlaylist.entries[0].artist == "Sample artist")
        assert(m3uPlaylist.entries[0].track == "Sample title")

        assert(m3uPlaylist.entries[1].location == "C:\\Documents and Settings\\I\\My Music\\Greatest Hits\\Example.ogg")
        assert(m3uPlaylist.entries[1].duration == 321)
        assert(m3uPlaylist.entries[1].artist == "Example Artist")
        assert(m3uPlaylist.entries[1].track == "Example title")
    }

    @Test
    fun testExample2() {
        val m3uPlaylist = m3uParser.parse("", "", javaClass.classLoader!!.getResourceAsStream("example2.m3u")!!)
        assert(m3uPlaylist.entries.size == 1)

        assert(m3uPlaylist.entries[0].location == "C:\\Music")
        assert(m3uPlaylist.entries[0].duration == null)
        assert(m3uPlaylist.entries[0].artist == null)
        assert(m3uPlaylist.entries[0].track == null)
    }

    @Test
    fun testExample3() {
        val m3uPlaylist = m3uParser.parse("", "", javaClass.classLoader!!.getResourceAsStream("example3.m3u")!!)
        assert(m3uPlaylist.entries.size == 2)

        assert(m3uPlaylist.entries[0].location == "Sample.mp3")
        assert(m3uPlaylist.entries[0].duration == 123)
        assert(m3uPlaylist.entries[0].artist == "Sample artist")
        assert(m3uPlaylist.entries[0].track == "Sample title")

        assert(m3uPlaylist.entries[1].location == "Greatest Hits\\Example.ogg")
        assert(m3uPlaylist.entries[1].duration == 321)
        assert(m3uPlaylist.entries[1].artist == "Example Artist")
        assert(m3uPlaylist.entries[1].track == "Example title")
    }

    @Test
    fun testExample4() {
        val m3uPlaylist = m3uParser.parse("", "", javaClass.classLoader!!.getResourceAsStream("example4.m3u")!!)
        assert(m3uPlaylist.entries.size == 7)
        assert(m3uPlaylist.entries[0].location == "Alternative\\Band - Song.mp3")
    }

    @Test
    fun testExample5() {
        val m3uPlaylist = m3uParser.parse("", "", javaClass.classLoader!!.getResourceAsStream("example5.m3u")!!)
        assert(m3uPlaylist.entries.size == 7)

        assert(m3uPlaylist.entries[0].location == "Alice in Chains_Jar of Flies_01_Rotten Apple.mp3")
        assert(m3uPlaylist.entries[0].duration == 419)
        assert(m3uPlaylist.entries[0].artist == "Alice in Chains")
        assert(m3uPlaylist.entries[0].track == "Rotten Apple")

        assert(m3uPlaylist.entries[1].location == "Alice in Chains_Jar of Flies_02_Nutshell.mp3")
        assert(m3uPlaylist.entries[1].duration == 260)
        assert(m3uPlaylist.entries[1].artist == "Alice in Chains")
        assert(m3uPlaylist.entries[1].track == "Nutshell")
    }
}