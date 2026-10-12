package com.simplecityapps.shuttle.shared.playback

import com.simplecityapps.playback.queue.QueueItem
import com.simplecityapps.shuttle.model.Song
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext

/** The play order's streams for a player that owns the order: shared per entry, pre-warmed ahead, dropped with the entry. */
@OptIn(ExperimentalCoroutinesApi::class)
class IosItemResolverTest {
    private val resolved = mutableListOf<Long>()
    private val ended = mutableListOf<Pair<Long, String>>()
    private var gate: CompletableDeferred<Unit>? = null

    private val order = (1L..5L).map { QueueItem(100 + it, song(it), isCurrent = it == 1L) }

    /** [ignoringCancel]: a provider whose resolve runs on once its server session is open, cancelled or not. */
    private fun TestScope.resolver(
        scope: CoroutineScope = TestScope(UnconfinedTestDispatcher(testScheduler)),
        ignoringCancel: Boolean = false
    ) = IosItemResolver(
        object : IosStreamResolver {
            override suspend fun resolve(
                song: Song,
                startPositionMs: Long,
                playRequested: Boolean,
                playId: String
            ): IosStream {
                resolved += song.id
                if (ignoringCancel) withContext(NonCancellable) { gate?.await() } else gate?.await()
                return IosStream("song:${song.id}")
            }

            override suspend fun endPlay(
                song: Song,
                playId: String
            ) {
                ended += song.id to playId
            }
        },
        scope
    )

    @Test
    fun updatePreWarmsTheCurrentItemAndTheTwoAfterIt() = runTest {
        val resolver = resolver()

        resolver.update(order, currentIndex = 1)

        assertEquals(listOf(2L, 3L, 4L), resolved)
    }

    @Test
    fun aPreWarmAndEveryOpenShareOneResolutionAndPlay() = runTest {
        val resolver = resolver()
        gate = CompletableDeferred()
        resolver.update(order, currentIndex = 0)
        val playId = resolver.playId(order[0])
        val urls = mutableListOf<String?>()

        resolver.resolve(101) { stream, _ -> urls += stream?.url }
        resolver.resolve(101) { stream, _ -> urls += stream?.url }
        gate!!.complete(Unit)

        assertEquals(listOf<String?>("song:1", "song:1"), urls)
        assertEquals(listOf(1L, 2L, 3L), resolved)
        assertEquals(playId, resolver.playId(order[0]))
    }

    @Test
    fun anEntryThatLeavesTheOrderIsCancelledAndItsPlayEnded() = runTest {
        val resolver = resolver()
        gate = CompletableDeferred()
        resolver.update(order, currentIndex = 0)
        val playId = resolver.playId(order[1])
        val errors = mutableListOf<String?>()
        resolver.resolve(102) { _, error -> errors += error }

        resolver.update(order - order[1], currentIndex = 0)

        assertEquals(listOf(2L to playId), ended)
        assertEquals(1, errors.size)
        resolver.resolve(102) { _, error -> errors += error }
        assertEquals("song 102 is no longer queued", errors.last())
    }

    @Test
    fun anItemNotPreWarmedResolvesWhenOpened() = runTest {
        val resolver = resolver()
        resolver.update(order, currentIndex = 0)
        var url: String? = null

        resolver.resolve(105) { stream, _ -> url = stream?.url }

        assertEquals("song:5", url)
        assertEquals(listOf(1L, 2L, 3L, 5L), resolved)
    }

    @Test
    fun anOpenIsAnsweredWhenTheScopeIsCancelled() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val resolver = resolver(scope)
        gate = CompletableDeferred()
        resolver.update(order, currentIndex = 0)
        val errors = mutableListOf<String?>()
        resolver.resolve(101) { _, error -> errors += error }

        scope.cancel()
        resolver.resolve(102) { _, error -> errors += error }

        assertEquals(2, errors.size)
    }

    @Test
    fun aResolveThatFinishesAfterItsEntryWasDroppedHasItsPlayEnded() = runTest {
        val resolver = resolver(ignoringCancel = true)
        gate = CompletableDeferred()
        resolver.update(order, currentIndex = 0)
        val playId = resolver.playId(order[0])

        resolver.update(order - order[0], currentIndex = 0)
        val endedAtDrop = ended.toList()
        gate!!.complete(Unit)

        assertEquals(listOf(1L to playId), endedAtDrop)
        assertEquals(1L to playId, ended.last())
        assertEquals(2, ended.size)
    }

    @Test
    fun aFailureIsKeptForAPreWarmAndAskedAgainOnALoad() = runTest {
        val failing = IosItemResolver(
            object : IosStreamResolver {
                override suspend fun resolve(
                    song: Song,
                    startPositionMs: Long,
                    playRequested: Boolean,
                    playId: String
                ): IosStream {
                    resolved += song.id
                    throw ServerStreamNotAllowedException(song, undecided = false)
                }
            },
            TestScope(UnconfinedTestDispatcher(testScheduler))
        )
        failing.update(order, currentIndex = 0)
        failing.update(order, currentIndex = 0)
        assertEquals(listOf(1L, 2L, 3L), resolved)

        failing.playId(order[0], playRequested = true, retryFailed = true)

        assertEquals(listOf(1L, 2L, 3L, 1L), resolved)
        assertNull(failing.failure(999))
    }
}
