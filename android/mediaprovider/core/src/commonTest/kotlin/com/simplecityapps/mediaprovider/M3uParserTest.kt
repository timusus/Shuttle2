package com.simplecityapps.mediaprovider

import kotlin.test.Test
import kotlin.test.assertEquals

class M3uParserTest {
    private val m3uParser = M3uParser()

    @Test
    fun nonAsciiEntriesParse() {
        val m3u = "#EXTM3U\n#EXTINF:215,Ñengo Flow - Chanson d’un jour d’hiver\n音楽/東京 🎵.flac\n"

        val m3uPlaylist = m3uParser.parse("", "Café.m3u8", m3u)

        assertEquals("Café", m3uPlaylist.name)
        assertEquals("音楽/東京 🎵.flac", m3uPlaylist.entries.single().location)
        assertEquals("Ñengo Flow", m3uPlaylist.entries.single().artist)
        assertEquals("Chanson d’un jour d’hiver", m3uPlaylist.entries.single().track)
    }

    @Test
    fun testExample1() {
        val m3uPlaylist = m3uParser.parse("", "", EXAMPLE_1)
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
        val m3uPlaylist = m3uParser.parse("", "", EXAMPLE_2)
        assertEquals(1, m3uPlaylist.entries.size)

        assertEquals("Music", m3uPlaylist.entries[0].location)
        assertEquals(null, m3uPlaylist.entries[0].duration)
        assertEquals(null, m3uPlaylist.entries[0].artist)
        assertEquals(null, m3uPlaylist.entries[0].track)
    }

    @Test
    fun testExample3() {
        val m3uPlaylist = m3uParser.parse("", "", EXAMPLE_3)
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
        val m3uPlaylist = m3uParser.parse("", "", EXAMPLE_4)
        assertEquals(7, m3uPlaylist.entries.size)
        assertEquals("Alternative/Band - Song.mp3", m3uPlaylist.entries[0].location)
    }

    @Test
    fun testExample5() {
        val m3uPlaylist = m3uParser.parse("", "", EXAMPLE_5)
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

    private companion object {
        const val EXAMPLE_1 = "#EXTM3U\n\n#EXTINF:123, Sample artist - Sample title\nC:\\Documents and Settings\\I\\My Music\\Sample.mp3\n\n#EXTINF:321,Example Artist - Example title\nC:\\Documents and Settings\\I\\My Music\\Greatest Hits\\Example.ogg"

        const val EXAMPLE_2 = "C:\\Music"

        const val EXAMPLE_3 = "#EXTM3U\n\n#EXTINF:123, Sample artist - Sample title\nSample.mp3\n\n#EXTINF:321,Example Artist - Example title\nGreatest Hits\\Example.ogg"

        const val EXAMPLE_4 = "Alternative\\Band - Song.mp3\nClassical\\Other Band - New Song.mp3\nStuff.mp3\nD:\\More Music\\Foo.mp3\n..\\Other Music\\Bar.mp3\nhttp://www.example.com:8000/Listen.mp3\nhttp://www.example.com/~user/Mine.mp3"

        const val EXAMPLE_5 = " #EXTM3U\n #EXTINF:419,Alice in Chains - Rotten Apple\n Alice in Chains_Jar of Flies_01_Rotten Apple.mp3\n #EXTINF:260,Alice in Chains - Nutshell\n Alice in Chains_Jar of Flies_02_Nutshell.mp3\n #EXTINF:255,Alice in Chains - I Stay Away\n Alice in Chains_Jar of Flies_03_I Stay Away.mp3\n #EXTINF:256,Alice in Chains - No Excuses\n Alice in Chains_Jar of Flies_04_No Excuses.mp3\n #EXTINF:157,Alice in Chains - Whale And Wasp\n Alice in Chains_Jar of Flies_05_Whale And Wasp.mp3\n #EXTINF:263,Alice in Chains - Don't Follow\n Alice in Chains_Jar of Flies_06_Don't Follow.mp3\n #EXTINF:245,Alice in Chains - Swing On This\n Alice in Chains_Jar of Flies_07_Swing On This.mp3"
    }
}
