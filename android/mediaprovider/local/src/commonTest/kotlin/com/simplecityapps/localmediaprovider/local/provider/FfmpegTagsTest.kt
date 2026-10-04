package com.simplecityapps.localmediaprovider.local.provider

import io.kotest.matchers.shouldBe
import kotlin.test.Test

/**
 * iOS's tags: what libavformat reads from S2Playback's `tagged*` fixtures (written by the ffmpeg CLI, each in its
 * container's spelling), the container's tags before the stream's, renamed by [ffmpegPropertyMap] and mapped by
 * [toFileTags].
 */
class FfmpegTagsTest {
    private fun fileTags(vararg tags: Pair<String, String>) = ffmpegPropertyMap(tags.toList()).toFileTags()

    @Test
    fun `maps an MP3's ID3 frames`() {
        val tags =
            fileTags(
                "compilation" to "1",
                "title" to "Tagged Chirp",
                "artist" to "Artist A; Artist B",
                "album_artist" to "The Album Artist",
                "album" to "The Album",
                "track" to "3/12",
                "disc" to "1/2",
                "encoder" to "Lavf62.12.101",
                "REPLAYGAIN_TRACK_GAIN" to "-6.50 dB",
                "genre" to "Rock/Pop",
                "date" to "1997-05-21",
                "encoder" to "Lavc62.28",
                "comment" to "Other"
            )

        tags.title shouldBe "Tagged Chirp"
        tags.artists shouldBe listOf("Artist A", "Artist B")
        tags.artistDisplay shouldBe "Artist A; Artist B"
        tags.albumArtist shouldBe "The Album Artist"
        tags.album shouldBe "The Album"
        tags.track shouldBe 3
        tags.trackTotal shouldBe 12
        tags.disc shouldBe 1
        tags.discTotal shouldBe 2
        tags.year shouldBe "1997"
        tags.genres shouldBe listOf("Rock/Pop")
        tags.replayGainTrack shouldBe -6.5
        tags.compilation shouldBe true
    }

    @Test
    fun `maps a FLAC's Vorbis comments`() {
        val tags =
            fileTags(
                "MUSICBRAINZ_TRACKID" to "11111111-2222-3333-4444-555555555555",
                "title" to "Tagged Tone",
                "artist" to "Flac Artist",
                "album_artist" to "Flac Album Artist",
                "album" to "Flac Album",
                "track" to "7",
                "disc" to "2",
                "DATE" to "2004",
                "GENRE" to "Jazz",
                "REPLAYGAIN_ALBUM_GAIN" to "+1.25 dB",
                "comment" to "Other"
            )

        tags.title shouldBe "Tagged Tone"
        tags.artists shouldBe listOf("Flac Artist")
        tags.albumArtist shouldBe "Flac Album Artist"
        tags.album shouldBe "Flac Album"
        tags.track shouldBe 7
        tags.trackTotal shouldBe null
        tags.disc shouldBe 2
        tags.year shouldBe "2004"
        tags.genres shouldBe listOf("Jazz")
        tags.replayGainAlbum shouldBe 1.25
        tags.mbTrackId shouldBe "11111111-2222-3333-4444-555555555555"
    }

    @Test
    fun `maps an Opus file's stream tags and an MP4's atoms`() {
        val opus = fileTags("encoder" to "Lavc62.28.101 libopus", "title" to "Tagged Opus", "artist" to "Opus Artist", "album" to "Opus Album", "track" to "1/9")
        opus.title shouldBe "Tagged Opus"
        opus.artists shouldBe listOf("Opus Artist")
        opus.album shouldBe "Opus Album"
        opus.track shouldBe 1
        opus.trackTotal shouldBe 9

        val mp4 =
            fileTags(
                "major_brand" to "M4A ",
                "title" to "Tagged Alac",
                "album_artist" to "Alac Album Artist",
                "date" to "2011",
                "track" to "4/10",
                "language" to "und"
            )
        mp4.title shouldBe "Tagged Alac"
        mp4.albumArtist shouldBe "Alac Album Artist"
        mp4.track shouldBe 4
        mp4.trackTotal shouldBe 10
        mp4.year shouldBe "2011"
    }

    @Test
    fun `an untagged file has no tag values`() {
        val tags = fileTags()

        tags.title shouldBe null
        tags.artists shouldBe emptyList()
    }

    @Test
    fun `matches each spelling of a name and the container's value beats the stream's`() {
        val tags =
            fileTags(
                "ALBUMARTIST" to "AA",
                "TRACKNUMBER" to "05",
                "ORIGINALDATE" to "1980-01-01",
                "MusicBrainz Album Artist Id" to "a1b2c3d4-0000-4000-8000-000000000001/a1b2c3d4-0000-4000-8000-000000000002",
                "lyrics-eng" to "la la",
                "lyrics-fra" to "lo lo",
                "title" to "From the container",
                "TITLE" to "From the stream",
                "GENRE" to "Rock, Indie; Pop",
                "compilation" to "0",
                "TIT1" to "ignored",
                "contentgroup" to "Group"
            )

        tags.albumArtist shouldBe "AA"
        tags.track shouldBe 5
        tags.year shouldBe "1980"
        tags.mbAlbumArtistIds shouldBe listOf("a1b2c3d4-0000-4000-8000-000000000001", "a1b2c3d4-0000-4000-8000-000000000002")
        tags.lyrics shouldBe "la la"
        tags.title shouldBe "From the container"
        tags.genres shouldBe listOf("Rock", "Indie", "Pop")
        tags.compilation shouldBe false
        tags.grouping shouldBe "Group"
    }

    @Test
    fun `maps an Opus file's R128 gains`() {
        val tags = fileTags("R128_TRACK_GAIN" to "-512", "r128_album_gain" to "256")

        tags.replayGainTrack shouldBe 3.0
        tags.replayGainAlbum shouldBe 6.0
    }

    @Test
    fun `a blank value gives way to the next one`() {
        fileTags("title" to " ", "TITLE" to " From the stream ").title shouldBe "From the stream"
        fileTags("track" to "", "tracknumber" to "6").track shouldBe 6
    }

    @Test
    fun `the multi-value artist tags libavformat joined are split back into their values`() {
        val tags = fileTags("ARTISTS" to "Simon & Garfunkel;Someone", "ALBUMARTISTS" to "Various Artists", "artist" to "Simon & Garfunkel feat. Someone")

        tags.artistsTag shouldBe listOf("Simon & Garfunkel", "Someone")
        tags.albumArtists shouldBe listOf("Various Artists")
        tags.artistDisplay shouldBe "Simon & Garfunkel feat. Someone"
    }

    @Test
    fun `the original release year beats a reissue's date`() {
        // reissue.mp3: TDRC=2017 (the remaster) with TDOR=2002, as ffmpeg wrote them
        fileTags("date" to "2017", "TDOR" to "2002").year shouldBe "2002"
        fileTags("TYER" to "2017", "TORY" to "2002").year shouldBe "2002"
        fileTags("date" to "2017-06-30", "originaldate" to "2002-01-28").year shouldBe "2002"
        fileTags("date" to "2017", "year" to "2002").year shouldBe "2017"
        fileTags("originaldate" to "n/a", "date" to "2017").year shouldBe "2017"
        fileTags("date" to "unknown").year shouldBe null
    }

    @Test
    fun `UTF-8 read as Latin-1 is decoded as UTF-8`() {
        fileTags("artist" to "Ã\u0091engo Flow").artists shouldBe listOf("Ñengo Flow")
    }
}
