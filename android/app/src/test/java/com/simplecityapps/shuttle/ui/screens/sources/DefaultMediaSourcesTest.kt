package com.simplecityapps.shuttle.ui.screens.sources

import android.content.Context
import com.simplecityapps.createSong
import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.mediaprovider.MediaProvider
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.playback.queue.QueueItem
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.provider.jellyfin.JellyfinMediaProvider
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.squareup.moshi.Moshi
import io.kotest.matchers.shouldBe
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Removing a source (turning This device off, or signing out of a server) takes its songs with it. */
@RunWith(RobolectricTestRunner::class)
class DefaultMediaSourcesTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val preferences = PlaybackPreferenceManager(
        context.getSharedPreferences("media-sources-test", Context.MODE_PRIVATE).apply { edit().clear().commit() },
        Moshi.Builder().build()
    )
    private val jellyfin = mockk<JellyfinMediaProvider>(relaxed = true)
    private val importerProviders = mutableSetOf<MediaProvider>()
    private val mediaImporter = mockk<MediaImporter>(relaxed = true) { every { mediaProviders } returns importerProviders }
    private val songRepository = mockk<SongRepository>(relaxed = true)
    private val playlistRepository = mockk<PlaylistRepository>(relaxed = true)
    private val queueOperations = mockk<QueueOperations>(relaxed = true)
    private val playbackOperations = mockk<PlaybackOperations>(relaxed = true)
    private val scope = TestScope(StandardTestDispatcher())

    private val mediaSources = DefaultMediaSources(
        preferences = preferences,
        generalPreferences = GeneralPreferenceManager(context.getSharedPreferences("media-sources-general", Context.MODE_PRIVATE)),
        mediaImporter = mediaImporter,
        taglibMediaProvider = mockk(relaxed = true),
        mediaStoreMediaProvider = mockk(relaxed = true),
        embyMediaProvider = mockk(relaxed = true),
        jellyfinMediaProvider = jellyfin,
        plexMediaProvider = mockk(relaxed = true),
        songRepository = songRepository,
        playlistRepository = playlistRepository,
        queueOperations = queueOperations,
        playbackOperations = playbackOperations,
        appCoroutineScope = scope,
    )

    private val localItem = QueueItem(uid = 1, song = createSong(id = 1, mediaProvider = MediaProviderType.Shuttle), isCurrent = true)
    private val serverItem = QueueItem(uid = 2, song = createSong(id = 2, mediaProvider = MediaProviderType.Jellyfin), isCurrent = false)

    @Test
    fun `removing a server forgets it, drops its songs and playlists, and takes its songs out of the queue`() {
        mediaSources.enable(MediaProviderType.Jellyfin)
        every { queueOperations.getQueue() } returns listOf(localItem, serverItem)
        every { queueOperations.getCurrentItem() } returns serverItem

        mediaSources.disable(MediaProviderType.Jellyfin)
        scope.testScheduler.advanceUntilIdle()

        mediaSources.enabledTypes.value shouldBe listOf(MediaProviderType.Shuttle)
        preferences.mediaProviderTypes shouldBe listOf(MediaProviderType.Shuttle)
        importerProviders shouldBe emptySet()
        verify { playbackOperations.pause() }
        verify { queueOperations.remove(listOf(serverItem)) }
        coVerify { songRepository.removeAll(MediaProviderType.Jellyfin) }
        coVerify { playlistRepository.deleteAll(MediaProviderType.Jellyfin) }
    }

    @Test
    fun `removing a server whose song isn't playing leaves playback alone`() {
        every { queueOperations.getQueue() } returns listOf(localItem)
        every { queueOperations.getCurrentItem() } returns localItem

        mediaSources.disable(MediaProviderType.Jellyfin)

        verify(exactly = 0) { playbackOperations.pause() }
        verify { queueOperations.remove(emptyList()) }
    }
}
