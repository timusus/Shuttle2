package com.simplecityapps.mediaprovider

import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import io.kotest.matchers.shouldBe
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MediaImporterTest {
    private val provider = GatedProvider()
    private val importer =
        MediaImporter(
            context = RuntimeEnvironment.getApplication(),
            songRepository = emptyRepository<SongRepository>(),
            playlistRepository = emptyRepository<PlaylistRepository>(),
            preferenceManager = GeneralPreferenceManager(FakeSharedPreferences())
        ).apply { mediaProviders += provider }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `imports started together scan the providers once`() = runBlocking<Unit> {
        val returned = Channel<Unit>(Channel.UNLIMITED)
        repeat(IMPORTS) { launch(Dispatchers.Default) { importer.import().also { returned.send(Unit) } } }

        // Every import but the one holding the scan returns without waiting for it
        repeat(IMPORTS - 1) { returned.receive() }
        importer.isImporting shouldBe true
        provider.release.complete(Unit)
        returned.receive()

        provider.scans.get() shouldBe 1
        importer.isImporting shouldBe false
    }

    @Test
    fun `an import after one has finished scans again`() = runBlocking<Unit> {
        provider.release.complete(Unit)

        importer.import()
        importer.import()

        provider.scans.get() shouldBe 2
    }

    /** Counts its scans, and holds each one open until [release] completes. */
    private class GatedProvider : MediaProvider {
        override val type = MediaProviderType.Shuttle

        val scans = AtomicInteger()
        val release = CompletableDeferred<Unit>()

        override fun findSongs(existingSongs: List<Song>): Flow<FlowEvent<List<Song>, MessageProgress>> = flow {
            scans.incrementAndGet()
            release.await()
        }

        override fun findPlaylists(
            existingPlaylists: List<Playlist>,
            existingSongs: List<Song>
        ): Flow<FlowEvent<List<MediaImporter.PlaylistUpdateData>, MessageProgress>> = emptyFlow()
    }

    private companion object {
        const val IMPORTS = 8

        /** A repository whose queries all return an empty list; anything else fails the test. */
        inline fun <reified T : Any> emptyRepository(): T = Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
            check(method.returnType == Flow::class.java) { "${T::class.simpleName}.${method.name} isn't faked" }
            flowOf(emptyList<Any>())
        } as T
    }
}
