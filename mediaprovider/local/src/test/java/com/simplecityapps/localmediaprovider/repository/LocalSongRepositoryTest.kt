package com.simplecityapps.localmediaprovider.repository

import com.simplecityapps.localmediaprovider.Diff.Companion.diff
import com.simplecityapps.localmediaprovider.data.room.entity.SongData
import com.simplecityapps.localmediaprovider.data.room.entity.toAlbumArtistData
import com.simplecityapps.localmediaprovider.data.room.entity.toAlbumData
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.*


class LocalSongRepositoryTest {

    private val databaseSongs: List<SongData>

    private val diskSongs: List<SongData>

    private val lastModified = Date()

    init {
        // Database
        val databaseSong1 = SongData("Song1", 0, 0, 1, 0, "song1.mp3", 1000, 2001, 0, lastModified, 0, 0, null, null)
        databaseSong1.albumName = "Sky Blue"
        databaseSong1.albumArtistName = "Devin Townsend"

        val databaseSong2 = SongData("Song2", 0, 1, 0, 1, "song2.mp3", 1000, 2001, 0, lastModified, 0, 0, null, null)
        databaseSong2.albumName = "Sky Blue"
        databaseSong2.albumArtistName = "Devin Townsend"

        val databaseSong3 = SongData("Song3", 0, 1, 0, 1, "song3.mp3", 1000, 2001, 0, lastModified, 0, 0, null, null)
        databaseSong3.albumName = "Sky Blue"
        databaseSong3.albumArtistName = "Devin Townsend"

        databaseSongs = listOf(databaseSong1, databaseSong2, databaseSong3)


        // Disk
        val diskSong1 = SongData("Song1", 0, 1, 0, 1, "song1.mp3", 1000, 2001, 0, lastModified, 0, 0, null, null)
        diskSong1.albumName = "Sky Blue"
        diskSong1.albumArtistName = "Devin Townsend"

        val diskSong2 = SongData("Song2", 0, 1, 0, 1, "song2.mp3", 1000, 2001, 0, lastModified, 0, 0, null, null)
        diskSong2.albumName = "Sky Blue"
        diskSong2.albumArtistName = "Devin Townsend"

        val diskSong3 = SongData("Song3", 0, 1, 0, 1, "song3.mp3", 1000, 2001, 0, lastModified, 0, 0, null, null)
        diskSong3.albumName = "Sky Blue"
        diskSong3.albumArtistName = "Devin Townsend"

        diskSongs = mutableListOf(diskSong1, diskSong2, diskSong3)
    }

    @Test
    fun testAlbumDiff0() {

        // 1. Comparing two sets of 3 equal songs, whose last modified date has changed

        val diskSongs = diskSongs.toMutableList()
        diskSongs.forEach { it.lastModified = Date(1234) }
        val songDiff = diff(databaseSongs, diskSongs)

        assertEquals(0, songDiff.insertions.size)
        assertEquals(0, songDiff.deletions.size)
        assertEquals(3, songDiff.updates.size) // Expect 3 updated songs
        assertEquals(0, songDiff.unchanged.size)

        val databaseAlbums = databaseSongs.toAlbumData()
        val diskAlbums = diskSongs.toAlbumData()

        val albumDiff = diff(databaseAlbums, diskAlbums)

        assertEquals(0, albumDiff.insertions.size)
        assertEquals(0, albumDiff.deletions.size)
        assertEquals(0, albumDiff.updates.size)
        assertEquals(1, albumDiff.unchanged.size) // Expect 1 unchanged album

        val databaseAlbumArtists = databaseAlbums.toAlbumArtistData()
        val diskAlbumArtists = diskAlbums.toAlbumArtistData()

        val albumArtistDiff = diff(databaseAlbumArtists, diskAlbumArtists)
        assertEquals(0, albumArtistDiff.insertions.size)
        assertEquals(0, albumArtistDiff.deletions.size)
        assertEquals(0, albumArtistDiff.updates.size)
        assertEquals(1, albumArtistDiff.unchanged.size) //. Expect 1 unchanged album artist
    }

    @Test
    fun testAlbumDiff1() {

        // 1. Comparing two sets of 3 identical songs

        val songDiff = diff(databaseSongs, diskSongs)

        assertEquals(0, songDiff.insertions.size)
        assertEquals(0, songDiff.deletions.size)
        assertEquals(0, songDiff.updates.size)
        assertEquals(3, songDiff.unchanged.size) // Expect 3 unchanged songs

        val databaseAlbums = databaseSongs.toAlbumData()
        val diskAlbums = diskSongs.toAlbumData()

        val albumDiff = diff(databaseAlbums, diskAlbums)

        assertEquals(0, albumDiff.insertions.size)
        assertEquals(0, albumDiff.deletions.size)
        assertEquals(0, albumDiff.updates.size)
        assertEquals(1, albumDiff.unchanged.size) // Expect 1 unchanged album

        val databaseAlbumArtists = databaseAlbums.toAlbumArtistData()
        val diskAlbumArtists = diskAlbums.toAlbumArtistData()

        val albumArtistDiff = diff(databaseAlbumArtists, diskAlbumArtists)
        assertEquals(0, albumArtistDiff.insertions.size)
        assertEquals(0, albumArtistDiff.deletions.size)
        assertEquals(0, albumArtistDiff.updates.size)
        assertEquals(1, albumArtistDiff.unchanged.size) //. Expect 1 unchanged album artist
    }

    @Test
    fun testAlbumDiff2() {

        // Comparing a set of 3 songs, with a set of 3 identical songs, plus one new song by a different artist, on a different album.

        val newDiskSong = SongData("Song4", 0, 1, 0, 1, "song4.mp3", 1000, 2001, 0, lastModified, 100, 0, null, null)
        newDiskSong.albumArtistName = "Roger Schleemskins"
        newDiskSong.albumName = "Flamboyance"
        val diskSongs = diskSongs.toMutableList()
        diskSongs.add(newDiskSong)

        val songDiff = diff(databaseSongs, diskSongs)
        assertEquals(1, songDiff.insertions.size) // Expect 1 new song to be inserted
        assertEquals(0, songDiff.deletions.size)
        assertEquals(0, songDiff.updates.size)
        assertEquals(3, songDiff.unchanged.size) // Expect 3 songs to remain unchanged

        val databaseAlbums = databaseSongs.toAlbumData()
        val diskAlbums = diskSongs.toAlbumData()

        val albumDiff = diff(databaseAlbums, diskAlbums)
        assertEquals(1, albumDiff.insertions.size) // Expect 1 new album to be inserted
        assertEquals(0, albumDiff.deletions.size)
        assertEquals(0, albumDiff.updates.size)
        assertEquals(1, albumDiff.unchanged.size) // Expect 1 album to remain unchanged

        val databaseAlbumArtists = databaseAlbums.toAlbumArtistData()
        val diskAlbumArtists = diskAlbums.toAlbumArtistData()

        val albumArtistDiff = diff(databaseAlbumArtists, diskAlbumArtists)
        assertEquals(1, albumArtistDiff.insertions.size) // Expect 1 new album artist to be inserted
        assertEquals(0, albumArtistDiff.deletions.size)
        assertEquals(0, albumArtistDiff.updates.size)
        assertEquals(1, albumArtistDiff.unchanged.size) // Expect 1 album artist to remain unchanged
    }

    @Test
    fun testAlbumDiff3() {

        // Comparing a set of 3 songs, with a set of 3 identical songs, plus one new song by the same artist, on a different album.

        val newDiskSong = SongData("Song4", 0, 1, 0, 1, "song4.mp3", 1000, 2001, 0, lastModified, 100, 0, null, null)
        newDiskSong.albumArtistName = "Devin Townsend"
        newDiskSong.albumName = "Flarge"
        val diskSongs = diskSongs.toMutableList()
        diskSongs.add(newDiskSong)

        val songDiff = diff(databaseSongs, diskSongs)
        assertEquals(1, songDiff.insertions.size) // Expect 1 new song to be inserted
        assertEquals(0, songDiff.deletions.size)
        assertEquals(0, songDiff.updates.size)
        assertEquals(3, songDiff.unchanged.size) // Expect 3 songs to remain unchanged

        val databaseAlbums = databaseSongs.toAlbumData()
        val diskAlbums = diskSongs.toAlbumData()

        val diff = diff(databaseAlbums, diskAlbums)
        assertEquals(1, diff.insertions.size) // Expect 1 new album to be inserted
        assertEquals(0, diff.deletions.size)
        assertEquals(0, diff.updates.size)
        assertEquals(1, diff.unchanged.size) // Expect 1 album to remain unchanged

        val databaseAlbumArtists = databaseAlbums.toAlbumArtistData()
        val diskAlbumArtists = diskAlbums.toAlbumArtistData()

        val albumArtistDiff = diff(databaseAlbumArtists, diskAlbumArtists)
        assertEquals(0, albumArtistDiff.insertions.size)
        assertEquals(0, albumArtistDiff.deletions.size)
        assertEquals(0, albumArtistDiff.updates.size)
        assertEquals(1, albumArtistDiff.unchanged.size) // Expect 1 album artist to be unchanged (the name has not changed)
    }

    @Test
    fun testAlbumDiff4() {

        // Comparing a set of 3 songs, with a set of 0 songs.

        val diskSongs = diskSongs.toMutableList()
        diskSongs.clear()

        val songDiff = diff(databaseSongs, diskSongs)
        assertEquals(0, songDiff.insertions.size)
        assertEquals(3, songDiff.deletions.size) // Expect 3 songs to be deleted
        assertEquals(0, songDiff.updates.size)
        assertEquals(0, songDiff.unchanged.size)

        val databaseAlbums = databaseSongs.toAlbumData()
        val diskAlbums = diskSongs.toAlbumData()

        val diff = diff(databaseAlbums, diskAlbums)
        assertEquals(0, diff.insertions.size)
        assertEquals(1, diff.deletions.size) // Expect 1 album to be deleted
        assertEquals(0, diff.updates.size)
        assertEquals(0, diff.unchanged.size)

        val databaseAlbumArtists = databaseAlbums.toAlbumArtistData()
        val diskAlbumArtists = diskAlbums.toAlbumArtistData()

        val albumArtistDiff = diff(databaseAlbumArtists, diskAlbumArtists)
        assertEquals(0, albumArtistDiff.insertions.size)
        assertEquals(1, albumArtistDiff.deletions.size) // Expect 1 album-artist to be deleted
        assertEquals(0, albumArtistDiff.updates.size)
        assertEquals(0, albumArtistDiff.unchanged.size)
    }

    @Test
    fun testAlbumDiff5() {

        // Comparing a set of 3 songs, with a set of 2 matching songs

        val diskSongs = diskSongs.toMutableList()
        diskSongs.removeAt(diskSongs.size - 1)

        val songDiff = diff(databaseSongs, diskSongs)
        assertEquals(0, songDiff.insertions.size)
        assertEquals(1, songDiff.deletions.size) // Expect 1 song to be deleted
        assertEquals(0, songDiff.updates.size)
        assertEquals(2, songDiff.unchanged.size) // Expect 2 songs to remain unchanged

        val databaseAlbums = databaseSongs.toAlbumData()
        val diskAlbums = diskSongs.toAlbumData()

        val diff = diff(databaseAlbums, diskAlbums)
        assertEquals(0, diff.insertions.size)
        assertEquals(0, diff.deletions.size)
        assertEquals(0, diff.updates.size)
        assertEquals(1, diff.unchanged.size) // Expect 1 album to remain unchanged

        val databaseAlbumArtists = databaseAlbums.toAlbumArtistData()
        val diskAlbumArtists = diskAlbums.toAlbumArtistData()

        val albumArtistDiff = diff(databaseAlbumArtists, diskAlbumArtists)
        assertEquals(0, albumArtistDiff.insertions.size)
        assertEquals(0, albumArtistDiff.deletions.size)
        assertEquals(0, albumArtistDiff.updates.size)
        assertEquals(1, albumArtistDiff.unchanged.size) // Expect 1 album artist to remain unchanged
    }

    @Test
    fun testAlbumDiff6() {

        // Comparing a set of 3 songs, with 3 other songs whose album name has changed

        val diskSongs = diskSongs.toMutableList()
        diskSongs.forEach { it.albumName = "Sky Red" }

        val songDiff = diff(databaseSongs, diskSongs)
        assertEquals(0, songDiff.insertions.size)
        assertEquals(0, songDiff.deletions.size)
        assertEquals(0, songDiff.updates.size)
        assertEquals(3, songDiff.unchanged.size) // Expect 3 songs to be unchanged (their path hasn't changed)

        val databaseAlbums = databaseSongs.toAlbumData()
        val diskAlbums = diskSongs.toAlbumData()

        val diff = diff(databaseAlbums, diskAlbums)
        assertEquals(0, diff.insertions.size)
        assertEquals(0, diff.deletions.size)
        assertEquals(1, diff.updates.size) // Expect 1 album to be updated
        assertEquals(0, diff.unchanged.size)

        val databaseAlbumArtists = databaseAlbums.toAlbumArtistData()
        val diskAlbumArtists = diskAlbums.toAlbumArtistData()

        val albumArtistDiff = diff(databaseAlbumArtists, diskAlbumArtists)
        assertEquals(0, albumArtistDiff.insertions.size)
        assertEquals(0, albumArtistDiff.deletions.size)
        assertEquals(0, albumArtistDiff.updates.size)
        assertEquals(1, albumArtistDiff.unchanged.size) // Expect 1 album artist to remain unchanged
    }

    @Test
    fun testAlbumDiff7() {

        // Comparing a set of 3 songs, with 3 other songs whose album artist name has changed

        val diskSongs = diskSongs.toMutableList()
        diskSongs.forEach { it.albumArtistName = "Schleem Herder" }

        val songDiff = diff(databaseSongs, diskSongs)
        assertEquals(0, songDiff.insertions.size)
        assertEquals(0, songDiff.deletions.size)
        assertEquals(0, songDiff.updates.size)
        assertEquals(3, songDiff.unchanged.size) // Expect 3 songs to be unchanged (their path hasn't changed)

        val databaseAlbums = databaseSongs.toAlbumData()
        val diskAlbums = diskSongs.toAlbumData()

        val diff = diff(databaseAlbums, diskAlbums)
        assertEquals(0, diff.insertions.size)
        assertEquals(0, diff.deletions.size)
        assertEquals(1, diff.updates.size) // Expect 1 album to be updated
        assertEquals(0, diff.unchanged.size)

        val databaseAlbumArtists = databaseAlbums.toAlbumArtistData()
        val diskAlbumArtists = diskAlbums.toAlbumArtistData()

        val albumArtistDiff = diff(databaseAlbumArtists, diskAlbumArtists)
        assertEquals(0, albumArtistDiff.insertions.size)
        assertEquals(0, albumArtistDiff.deletions.size)
        assertEquals(1, albumArtistDiff.updates.size) // Expect 1 album artist to be updated
        assertEquals(0, albumArtistDiff.unchanged.size)
    }

    @Test
    fun testAlbumDiff8() {

        // Comparing a set of 3 songs, with 3 other songs whose album artist and album name has changed

        val diskSongs = diskSongs.toMutableList()
        diskSongs.forEach { it.albumArtistName = "Schleem Herder" }
        diskSongs.forEach { it.albumName = "Chemex" }

        val songDiff = diff(databaseSongs, diskSongs)
        assertEquals(0, songDiff.insertions.size)
        assertEquals(0, songDiff.deletions.size)
        assertEquals(0, songDiff.updates.size)
        assertEquals(3, songDiff.unchanged.size) // Expect 3 songs to be unchanged (their path hasn't changed)

        val databaseAlbums = databaseSongs.toAlbumData()
        val diskAlbums = diskSongs.toAlbumData()

        val diff = diff(databaseAlbums, diskAlbums)
        assertEquals(0, diff.insertions.size)
        assertEquals(0, diff.deletions.size)
        assertEquals(1, diff.updates.size) // Expect 1 album to be updated
        assertEquals(0, diff.unchanged.size)

        val databaseAlbumArtists = databaseAlbums.toAlbumArtistData()
        val diskAlbumArtists = diskAlbums.toAlbumArtistData()

        val albumArtistDiff = diff(databaseAlbumArtists, diskAlbumArtists)
        assertEquals(0, albumArtistDiff.insertions.size)
        assertEquals(0, albumArtistDiff.deletions.size)
        assertEquals(1, albumArtistDiff.updates.size) // Expect 1 album artist to be updated
        assertEquals(0, albumArtistDiff.unchanged.size)
    }
}