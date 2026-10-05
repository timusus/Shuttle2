package com.simplecityapps.localmediaprovider.local.provider

import com.simplecityapps.shuttle.model.AlbumArtistGroupKey
import com.simplecityapps.shuttle.model.AlbumIdentityRule
import com.simplecityapps.shuttle.model.AlbumIdentityTags
import com.simplecityapps.shuttle.model.ArtistCredits
import com.simplecityapps.shuttle.model.MediaProviderType
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.datetime.LocalDate

class FileTagsTest {
    @Test
    fun `maps the tags TagLib reads from a Matroska file tagged by ffmpeg`() {
        // What TagLib's Matroska PropertyMap holds for ffmpeg's untargeted simple tags: standard names are translated
        // (PART_NUMBER to TRACKNUMBER), others such as ALBUM and ALBUM_ARTIST pass through as they are
        val tags =
            mapOf(
                "ARTIST" to listOf("Gapless Artist"),
                "ALBUM_ARTIST" to listOf("Gapless Album Artist"),
                "ALBUM" to listOf("Gapless Album"),
                "TRACKNUMBER" to listOf("2/5"),
                "DATE" to listOf("2023")
            ).toFileTags()

        tags.title shouldBe null
        tags.artists shouldBe listOf("Gapless Artist")
        tags.albumArtist shouldBe "Gapless Album Artist"
        tags.album shouldBe "Gapless Album"
        tags.track shouldBe 2
        tags.trackTotal shouldBe 5
        tags.year shouldBe "2023"
    }

    @Test
    fun `maps a Matroska file's album-level tags`() {
        // TITLE and ARTIST targeted at the album (TargetTypeValue 50) come out of TagLib as ALBUM and ALBUMARTIST
        val tags =
            mapOf(
                "TITLE" to listOf("Track Title"),
                "ALBUM" to listOf("Album Title"),
                "ALBUMARTIST" to listOf("Album Artist"),
                "DISCNUMBER" to listOf("1")
            ).toFileTags()

        tags.title shouldBe "Track Title"
        tags.album shouldBe "Album Title"
        tags.albumArtist shouldBe "Album Artist"
        tags.disc shouldBe 1
    }

    @Test
    fun `an untagged file has no tag values`() {
        val tags = emptyMap<String, List<String>>().toFileTags()

        tags.title shouldBe null
        tags.album shouldBe null
        tags.artists shouldBe emptyList()
        tags.track shouldBe null
        tags.albumArtists shouldBe emptyList()
        tags.artistsTag shouldBe emptyList()
        tags.artistDisplay shouldBe null
        tags.compilation shouldBe null
        tags.mbTrackId shouldBe null
        tags.mbArtistIds shouldBe emptyList()
    }

    @Test
    fun `reads the multi-value artist tags compilation and MusicBrainz ids of a Vorbis comment file none split`() {
        // A FLAC tagged by Picard: ARTIST is the display credit, ARTISTS and ALBUMARTISTS hold one value per artist
        val tags =
            mapOf(
                "ARTIST" to listOf("Simon & Garfunkel feat. Someone; Else"),
                "ARTISTS" to listOf("Simon & Garfunkel", "Someone; Else"),
                "ALBUMARTIST" to listOf("Various Artists"),
                "ALBUMARTISTS" to listOf("Various Artists"),
                "COMPILATION" to listOf("1"),
                "MUSICBRAINZ_TRACKID" to listOf("5B11F4CE-A62D-471E-81FC-A69A8278C7DA"),
                "MUSICBRAINZ_ALBUMID" to listOf("a1b2c3d4-0000-4000-8000-000000000001"),
                "MUSICBRAINZ_RELEASEGROUPID" to listOf("a1b2c3d4-0000-4000-8000-000000000002"),
                "MUSICBRAINZ_ARTISTID" to listOf("a1b2c3d4-0000-4000-8000-000000000003", "a1b2c3d4-0000-4000-8000-000000000004"),
                "MUSICBRAINZ_ALBUMARTISTID" to listOf("89ad4ac3-39f7-470e-963a-56509c546377")
            ).toFileTags()

        // The split ARTIST list is what it always was
        tags.artists shouldBe listOf("Simon & Garfunkel feat. Someone", "Else")
        tags.artistDisplay shouldBe "Simon & Garfunkel feat. Someone; Else"
        tags.artistsTag shouldBe listOf("Simon & Garfunkel", "Someone; Else")
        tags.albumArtists shouldBe listOf("Various Artists")
        tags.compilation shouldBe true
        tags.mbTrackId shouldBe "5b11f4ce-a62d-471e-81fc-a69a8278c7da"
        tags.mbAlbumId shouldBe "a1b2c3d4-0000-4000-8000-000000000001"
        tags.mbReleaseGroupId shouldBe "a1b2c3d4-0000-4000-8000-000000000002"
        tags.mbArtistIds shouldBe listOf("a1b2c3d4-0000-4000-8000-000000000003", "a1b2c3d4-0000-4000-8000-000000000004")
        tags.mbAlbumArtistIds shouldBe listOf("89ad4ac3-39f7-470e-963a-56509c546377")
    }

    @Test
    fun `reads an ID3v2_3 file's joined MusicBrainz ids its TXXX spellings and TCMP`() {
        // TagLib maps TCMP to COMPILATION and the UFID frame to MUSICBRAINZ_TRACKID. ID3v2.3 has no multi-value frames,
        // so Picard joins several ids with '/'; a TXXX frame TagLib doesn't know comes through as its description.
        val tags =
            mapOf(
                "ARTIST" to listOf("A", "B"),
                "COMPILATION" to listOf("0"),
                "MUSICBRAINZ_TRACKID" to listOf("a1b2c3d4-0000-4000-8000-000000000001"),
                "MUSICBRAINZ_ARTISTID" to listOf("a1b2c3d4-0000-4000-8000-000000000002/a1b2c3d4-0000-4000-8000-000000000003"),
                "MUSICBRAINZ ALBUM ID" to listOf("a1b2c3d4-0000-4000-8000-000000000004")
            ).toFileTags()

        tags.artistDisplay shouldBe "A; B"
        tags.compilation shouldBe false
        tags.mbArtistIds shouldBe listOf("a1b2c3d4-0000-4000-8000-000000000002", "a1b2c3d4-0000-4000-8000-000000000003")
        tags.mbAlbumId shouldBe "a1b2c3d4-0000-4000-8000-000000000004"
    }

    @Test
    fun `an MP4 cpil read as true is a compilation and a malformed id or flag reads as untagged`() {
        val tags =
            mapOf(
                "COMPILATION" to listOf("true"),
                "MUSICBRAINZ_ALBUMID" to listOf("not-an-id")
            ).toFileTags()
        tags.compilation shouldBe true
        tags.mbAlbumId shouldBe null

        mapOf("COMPILATION" to listOf("maybe")).toFileTags().compilation shouldBe null
    }

    @Test
    fun `UTF-8 tags decode as written`() {
        val tags =
            mapOf(
                "TITLE" to listOf("Chanson d’un jour d’hiver"),
                "ARTIST" to listOf("Ñengo Flow;坂本龍一"),
                "ALBUM" to listOf("東京 🎵")
            ).toFileTags()

        tags.title shouldBe "Chanson d’un jour d’hiver"
        tags.artists shouldBe listOf("Ñengo Flow", "坂本龍一")
        tags.album shouldBe "東京 🎵"
    }

    @Test
    fun `UTF-8 written into a Latin-1 tag is decoded as UTF-8`() {
        // ID3v1, and ID3v2 frames declared ISO-8859-1, are often written with UTF-8 bytes; TagLib decodes them a byte per
        // character, so "Ñengo Flow" arrives as "Ã\u0091engo Flow"
        val tags =
            mapOf(
                "TITLE" to listOf("Chanson d’un jour d’hiver".asLatin1()),
                "ARTIST" to listOf("Ñengo Flow".asLatin1()),
                "ALBUM" to listOf("東京 🎵".asLatin1()),
                "GENRE" to listOf("Música Latina".asLatin1())
            ).toFileTags()

        tags.title shouldBe "Chanson d’un jour d’hiver"
        tags.artists shouldBe listOf("Ñengo Flow")
        tags.album shouldBe "東京 🎵"
        tags.genres shouldBe listOf("Música Latina")
    }

    @Test
    fun `Latin-1 text stays as it is`() {
        val tags =
            mapOf(
                "TITLE" to listOf("Café Brûlé"),
                "ARTIST" to listOf("Björk"),
                "ALBUM" to listOf("Señor ¿Qué?")
            ).toFileTags()

        tags.title shouldBe "Café Brûlé"
        tags.artists shouldBe listOf("Björk")
        tags.album shouldBe "Señor ¿Qué?"
    }

    // Each UTF-8 byte as the Latin-1 character of that code
    private fun String.asLatin1() = encodeToByteArray().joinToString("") { (it.toInt() and 0xFF).toChar().toString() }

    @Test
    fun parseDate() {
        "2004".parseDate() shouldBe "2004"
        "2010-00-00".parseDate() shouldBe "2010"
        "2020-04-03T07:00:00Z".parseDate() shouldBe "2020"
        "99".parseDate() shouldBe null
        "199a".parseDate() shouldBe null
        "unknown".parseDate() shouldBe null
    }

    @Test
    fun `a combined album artist tag reads as its first artist`() {
        mapOf("ALBUMARTIST" to listOf("A; B")).toFileTags().albumArtist shouldBe "A"
        mapOf("ALBUMARTIST" to listOf("A | B")).toFileTags().albumArtist shouldBe "A"
        mapOf("ALBUMARTIST" to listOf("A / B")).toFileTags().albumArtist shouldBe "A"
        mapOf("ALBUM_ARTIST" to listOf("A; B")).toFileTags().albumArtist shouldBe "A"
        mapOf("ALBUMARTIST" to listOf("AC/DC")).toFileTags().albumArtist shouldBe "AC/DC"
        mapOf("ALBUMARTIST" to listOf("Earth, Wind & Fire")).toFileTags().albumArtist shouldBe "Earth, Wind & Fire"
        mapOf("ALBUMARTIST" to listOf("A; B"), "ALBUMARTISTS" to listOf("A", "B")).toFileTags().albumArtists shouldBe listOf("A", "B")
    }

    @Test
    fun `a full date becomes the first of its year`() {
        "2021-05-14".toYearDate() shouldBe LocalDate(2021, 1, 1)
        "2021".toYearDate() shouldBe LocalDate(2021, 1, 1)
        "2020-04-03T07:00:00Z".toYearDate() shouldBe LocalDate(2020, 1, 1)
        "unknown".toYearDate() shouldBe null
    }

    @Test
    fun `the original release year beats a reissue's date`() {
        mapOf("DATE" to listOf("2017"), "ORIGINALDATE" to listOf("2002-01-28")).toFileTags().year shouldBe "2002"
        mapOf("DATE" to listOf("2017"), "YEAR" to listOf("2002")).toFileTags().year shouldBe "2017"
        mapOf("DATE" to listOf("unknown"), "YEAR" to listOf("2002")).toFileTags().year shouldBe "2002"
        mapOf("ORIGINALDATE" to listOf("n/a"), "DATE" to listOf("2017")).toFileTags().year shouldBe "2017"
    }

    @Test
    fun `a track or disc number and a ReplayGain value may be padded`() {
        val tags =
            mapOf(
                "TRACKNUMBER" to listOf("3 / 12"),
                "DISCNUMBER" to listOf(" 1/2"),
                "REPLAYGAIN_ALBUM_GAIN" to listOf("+1.25 dB")
            ).toFileTags()

        tags.track shouldBe 3
        tags.trackTotal shouldBe 12
        tags.disc shouldBe 1
        tags.discTotal shouldBe 2
        tags.replayGainAlbum shouldBe 1.25
    }

    @Test
    fun `a genre is split on semicolons - commas and multi-value entries but keeps slashes`() {
        mapOf("GENRE" to listOf("R&B/Soul", "Rock; Pop", "Folk, World")).toFileTags().genres shouldBe
            listOf("R&B/Soul", "Rock", "Pop", "Folk", "World")
    }

    @Test
    fun `an Opus R128 gain reads as ReplayGain when the ReplayGain tags are absent`() {
        val tags = mapOf("R128_TRACK_GAIN" to listOf("-512"), "R128_ALBUM_GAIN" to listOf("256")).toFileTags()

        tags.replayGainTrack shouldBe 3.0
        tags.replayGainAlbum shouldBe 6.0
    }

    @Test
    fun `a ReplayGain tag beats an R128 gain`() {
        mapOf("REPLAYGAIN_TRACK_GAIN" to listOf("-7.5 dB"), "R128_TRACK_GAIN" to listOf("-512")).toFileTags().replayGainTrack shouldBe -7.5
        mapOf("R128_TRACK_GAIN" to listOf("loud")).toFileTags().replayGainTrack shouldBe null
    }

    @Test
    fun `splits the ARTIST tag on semicolons pipes and spaced slashes only`() {
        fun artists(value: String) = mapOf("ARTIST" to listOf(value)).toFileTags().artists

        artists("A; B") shouldBe listOf("A", "B")
        artists("A;B") shouldBe listOf("A", "B")
        artists("A | B|C") shouldBe listOf("A", "B", "C")
        artists("A / B") shouldBe listOf("A", "B")
        // Repeats stay, so the list stays paired with MUSICBRAINZ_ARTISTID
        artists(" A ;; a ; ") shouldBe listOf("A", "a")
        artists("AC/DC") shouldBe listOf("AC/DC")
        artists("Bob Marley & the Wailers") shouldBe listOf("Bob Marley & the Wailers")
        artists("Earth, Wind & Fire") shouldBe listOf("Earth, Wind & Fire")
    }

    /** The credits' artist pages for a file tagged album "Duets" by album artist B (MusicBrainz id [B_ID]). */
    private fun creditKeys(artist: String, mbArtistIds: List<String>): List<AlbumArtistGroupKey> {
        val fileTags =
            mapOf(
                "ALBUM" to listOf("Duets"),
                "ALBUMARTIST" to listOf("B"),
                "ARTIST" to listOf(artist),
                "MUSICBRAINZ_ARTISTID" to mbArtistIds,
                "MUSICBRAINZ_ALBUMARTISTID" to listOf(B_ID)
            ).toFileTags()
        val tags =
            AlbumIdentityTags(
                songId = 1,
                album = fileTags.album,
                albumArtist = fileTags.albumArtist,
                albumArtists = fileTags.albumArtists,
                artists = fileTags.artists,
                compilation = fileTags.compilation,
                mbAlbumId = fileTags.mbAlbumId,
                serverAlbumId = null,
                mediaProvider = MediaProviderType.Shuttle,
                path = "/music/Duets/1.flac",
                artistsTag = fileTags.artistsTag,
                mbArtistIds = fileTags.mbArtistIds,
                mbAlbumArtistIds = fileTags.mbAlbumArtistIds
            )
        return ArtistCredits.credits(tags, AlbumIdentityRule.resolve(listOf(tags)).getValue(1)).map { it.groupKey }
    }

    private fun key(name: String) = AlbumArtistGroupKey(AlbumIdentityRule.artistKey(name))

    @Test
    fun `each artist split from one ARTIST value keeps its own MusicBrainz id`() {
        // "Bee" carries the album artist's id, so it's B's own credit, not an appearance under another spelling
        creditKeys("A; Bee", listOf(A_ID, B_ID)) shouldBe listOf(key("A"), key("B"))
        creditKeys("Bee | A", listOf(B_ID, A_ID)) shouldBe listOf(key("B"), key("A"))
    }

    @Test
    fun `a repeated artist in one ARTIST value is credited once and never takes another's id`() {
        creditKeys("A; B; a", listOf(A_ID, B_ID)) shouldBe listOf(key("A"), key("B"))
        // The ids are read without repeats, so three names and two ids don't pair: "Bee" isn't given B's id by position
        creditKeys("A; Bee; a", listOf(A_ID, B_ID, A_ID)) shouldBe listOf(key("A"), key("Bee"))
        creditKeys("A; a; Bee", listOf(A_ID, B_ID)) shouldBe listOf(key("A"), key("Bee"))
    }

    private companion object {
        const val A_ID = "a1b2c3d4-0000-4000-8000-00000000000a"
        const val B_ID = "a1b2c3d4-0000-4000-8000-00000000000b"
    }
}
