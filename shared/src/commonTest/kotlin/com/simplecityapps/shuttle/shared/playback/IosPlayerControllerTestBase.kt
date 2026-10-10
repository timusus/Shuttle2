package com.simplecityapps.shuttle.shared.playback

import com.simplecityapps.shuttle.entitlement.ServerAccess
import com.simplecityapps.shuttle.model.Song
import kotlin.random.Random
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

/**
 * The controller over a fake engine: Android's playback semantics (`PlaybackFacade`, `QueueFacade`, `ItemLoader`), and
 * that the engine is always handed the queue's current item and the one after it. The `IosPlayerController*Test`
 * classes share this fixture.
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class IosPlayerControllerTestBase {
    protected val engine = FakeIosAudioPlayer()

    /** Songs the resolver can't find a stream for. */
    protected val unresolvable = mutableSetOf<Long>()

    protected val a = song(1)
    protected val b = song(2)
    protected val c = song(3)
    protected val d = song(4)
    protected val e = song(5)

    /** The ids of the songs resolved, in order. */
    protected val resolved = mutableListOf<Long>()

    /** The play each resolution was for, by song id, in order. */
    protected val plays = mutableListOf<Pair<Long, String>>()

    /** The plays the controller ended, in order. */
    protected val endedPlays = mutableListOf<String>()

    /** Songs on a server, whose streams open at a position (`StartTimeTicks`). */
    protected val server = mutableSetOf<Long>()

    /** Whether a [server] song may stream, given whether the user asked to play it; the real gate in the gate tests. */
    protected var serverAccess: suspend (Song, Boolean) -> ServerAccess = { _, _ -> ServerAccess.Allowed }

    protected fun url(song: Song) = "song:${song.id}"

    /** While set, every stream resolution waits for it: a slow server answer. */
    protected var resolveGate: CompletableDeferred<Unit>? = null

    /**
     * Runs [block] over a controller on an unconfined test dispatcher; or, if not [unconfined], on a queued one, which
     * doesn't hold back the coroutines resumed while the controller works on main, as main doesn't.
     */
    protected fun test(
        unconfined: Boolean = true,
        block: suspend TestScope.(IosPlayerController) -> Unit
    ): TestResult = runTest {
        val dispatcher = if (unconfined) UnconfinedTestDispatcher(testScheduler) else StandardTestDispatcher(testScheduler)
        val controller = IosPlayerController(
            player = engine,
            resolver = object : IosStreamResolver {
                override suspend fun resolve(
                    song: Song,
                    startPositionMs: Long,
                    playRequested: Boolean,
                    playId: String
                ): IosStream {
                    resolved += song.id
                    plays += song.id to playId
                    resolveGate?.await()
                    if (song.id in server) {
                        when (serverAccess(song, playRequested)) {
                            ServerAccess.Allowed -> Unit
                            ServerAccess.Refused -> throw ServerStreamNotAllowedException(song, undecided = false)
                            ServerAccess.Undecided -> throw ServerStreamNotAllowedException(song, undecided = true)
                        }
                    }
                    return when {
                        song.id in unresolvable -> error("No stream for ${song.name}")
                        song.id in server && startPositionMs > 0 -> IosStream("${url(song)}?from=$startPositionMs", opensAtPosition = true)
                        else -> IosStream(url(song), opensAtPosition = song.id in server)
                    }
                }

                override suspend fun endPlay(
                    song: Song,
                    playId: String
                ) {
                    endedPlays += playId
                }
            },
            scope = CoroutineScope(dispatcher),
            random = Random(1)
        )
        block(controller)
    }

    protected fun <T> TestScope.collect(flow: Flow<T>): List<T> {
        val values = mutableListOf<T>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.toList(values) }
        return values
    }

    /** Sets [songs] as the queue at [position], loads it and, if [play], plays it. */
    protected suspend fun IosPlayerController.start(
        songs: List<Song>,
        position: Int = 0,
        play: Boolean = true
    ) {
        queueOperations.setQueue(songs, null, position)
        load { }
        engine.settle()
        if (play) {
            play()
            engine.settle()
        }
        engine.clearCalls()
    }

    protected val IosPlayerController.currentSong
        get() = queueOperations.getCurrentItem()?.song

    /** Starts [IosPlayerController.pauseAtEndOfItem] waiting, as the sleep timer's "end of song" does. */
    protected fun TestScope.pauseAtEnd(controller: IosPlayerController) = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { controller.pauseAtEndOfItem() }
}
