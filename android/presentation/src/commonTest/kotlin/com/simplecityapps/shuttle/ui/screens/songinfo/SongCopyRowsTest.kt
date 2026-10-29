package com.simplecityapps.shuttle.ui.screens.songinfo

import com.simplecityapps.createSong
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.PreferredCopy
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.model.SongCopyAvailability
import com.simplecityapps.shuttle.model.SongCopyPreferences
import com.simplecityapps.shuttle.model.copyAlbumKey
import com.simplecityapps.shuttle.model.copyKey
import com.simplecityapps.shuttle.ui.actions.CopyAvailabilitySource
import com.simplecityapps.shuttle.ui.actions.CopyContext
import com.simplecityapps.shuttle.ui.actions.CopyPreferencesStore
import com.simplecityapps.shuttle.ui.actions.ObserveCopyContext
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

internal fun fixedCopyContext(context: CopyContext = CopyContext()) = ObserveCopyContext(
    object : CopyPreferencesStore {
        override val mergeCopies: Flow<Boolean> = MutableStateFlow(context.mergeCopies)
        override val preferences: Flow<SongCopyPreferences> = MutableStateFlow(context.preferences)

        override suspend fun update(change: (SongCopyPreferences) -> SongCopyPreferences) = Unit
    },
    object : CopyAvailabilitySource {
        override fun observe(): Flow<SongCopyAvailability> = MutableStateFlow(context.availability)
    },
)

class SongCopyRowsTest {
    private val local = createSong(id = 1, path = "/music/1.flac").copy(mimeType = "audio/flac", bitRate = 900, bitDepth = 16, sampleRate = 44100)
    private val emby = createSong(id = 2, path = "emby://2", mediaProvider = MediaProviderType.Emby).copy(mimeType = "audio/mpeg", bitRate = 320, externalId = "e2")
    private val plex = createSong(id = 3, path = "plex://3", mediaProvider = MediaProviderType.Plex).copy(mimeType = "audio/mpeg", bitRate = 256, externalId = "p3")

    private fun merged(vararg copies: Song): Song = copies.first().copy(copies = copies.toList())

    private fun states(context: CopyContext, song: Song = merged(local, emby, plex)) = song.copyRows(context)!!.rows.map { it.state }

    @Test
    fun `a song with one copy or with merging off has no rows`() {
        local.copyRows(CopyContext()) shouldBe null
        merged(local).copyRows(CopyContext()) shouldBe null
        merged(local, emby).copyRows(CopyContext(mergeCopies = false)) shouldBe null
    }

    @Test
    fun `rows describe each copy and the first plays`() {
        val rows = merged(local, emby, plex).copyRows(CopyContext())!!

        rows.rows.first().let {
            it.provider shouldBe MediaProviderType.Shuttle
            it.format shouldBe "FLAC"
            it.bitRate shouldBe "900 kb/s"
            it.bitDepth shouldBe "16-bit"
            it.sampleRate shouldBe "44.1 kHz"
        }
        rows.rows.map { it.state } shouldBe listOf(SongCopyState.Playing, SongCopyState.Available, SongCopyState.Available)
        rows.sourceCount shouldBe 3
        rows.chosenBy shouldBe CopyChoice.Automatic(PreferredCopy.Local)
    }

    @Test
    fun `the playing copy reads Playing even when primary and the footnote says so`() {
        val context = CopyContext(preferences = SongCopyPreferences(primaryCopies = setOf(local.copyKey)))

        merged(local, emby).copyRows(context)!!.let {
            it.rows.map { row -> row.state } shouldBe listOf(SongCopyState.Playing, SongCopyState.Available)
            it.rows.map { row -> row.primary } shouldBe listOf(true, false)
            it.chosenBy shouldBe CopyChoice.Primary
        }
    }

    @Test
    fun `a primary copy that is not playing reads Primary`() {
        val context = CopyContext(preferences = SongCopyPreferences(primaryCopies = setOf(emby.copyKey)))

        states(context, merged(local, emby)) shouldBe listOf(SongCopyState.Playing, SongCopyState.Primary)
    }

    @Test
    fun `a primary copy that cannot play gives way to Unreachable`() {
        val context = CopyContext(
            preferences = SongCopyPreferences(primaryCopies = setOf(emby.copyKey)),
            availability = SongCopyAvailability(unreachableProviders = setOf(MediaProviderType.Emby)),
        )

        states(context, merged(local, emby)) shouldBe listOf(SongCopyState.Playing, SongCopyState.Unreachable)
    }

    @Test
    fun `an album primary source is the choice`() {
        val context = CopyContext(preferences = SongCopyPreferences(primaryAlbums = setOf(local.copyAlbumKey), preferred = PreferredCopy.BestQuality))

        merged(local, emby).copyRows(context)!!.chosenBy shouldBe CopyChoice.AlbumPrimary
    }

    @Test
    fun `best quality is the choice when nothing is primary`() {
        val context = CopyContext(preferences = SongCopyPreferences(preferred = PreferredCopy.BestQuality))

        merged(local, emby).copyRows(context)!!.chosenBy shouldBe CopyChoice.Automatic(PreferredCopy.BestQuality)
    }

    @Test
    fun `a downloaded copy reads Downloaded`() {
        val context = CopyContext(availability = SongCopyAvailability(downloadedPaths = setOf(emby.path)))

        states(context, merged(local, emby)) shouldBe listOf(SongCopyState.Playing, SongCopyState.Downloaded)
    }

    @Test
    fun `an unreachable source reads Unreachable`() {
        val context = CopyContext(availability = SongCopyAvailability(unreachableProviders = setOf(MediaProviderType.Emby)))

        states(context, merged(local, emby)) shouldBe listOf(SongCopyState.Playing, SongCopyState.Unreachable)
    }

    @Test
    fun `a missing copy reads Missing and does not play`() {
        val context = CopyContext(availability = SongCopyAvailability(missingSongIds = setOf(1L)))

        merged(emby, local).copyRows(context)!!.rows.map { it.state to it.playable } shouldBe listOf(SongCopyState.Playing to true, SongCopyState.Missing to false)
    }

    @Test
    fun `offline a copy not on the device reads Offline`() {
        val context = CopyContext(availability = SongCopyAvailability(offline = true, downloadedPaths = setOf(plex.path)))

        states(context) shouldBe listOf(SongCopyState.Playing, SongCopyState.Offline, SongCopyState.Downloaded)
    }
}
