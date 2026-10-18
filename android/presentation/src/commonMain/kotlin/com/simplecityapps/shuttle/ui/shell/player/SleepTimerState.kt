package com.simplecityapps.shuttle.ui.shell.player

import com.simplecityapps.playback.sleeptimer.SleepTimer
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

/** The sleep-timer fields of [PlayerUiState]. */
data class SleepTimerFields(
    val active: Boolean,
    val playToEnd: Boolean,
    val atEndOfWork: Boolean,
)

/**
 * One player's view of the sleep timer: its fields, its remaining time, and starting and stopping it. The timer has no
 * flow of its own, so this notices a change made here, or the timer going off, by polling while one runs. Holds state
 * (the last play-to-end choice), so each player gets its own.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SleepTimerState @Inject constructor(
    private val sleepTimer: SleepTimer,
    private val controlSleepTimer: ControlSleepTimer,
    observeSleepTimerAtEndOfWork: ObserveSleepTimerAtEndOfWork,
    readSleepTimerPlayToEnd: ReadSleepTimerPlayToEnd,
) {
    /** Bumped when the timer is started or stopped here. */
    private val changes = MutableStateFlow(0)
    private val playToEnd = MutableStateFlow(readSleepTimerPlayToEnd())
    private val atEndOfWork: Flow<Boolean> = observeSleepTimerAtEndOfWork()

    // Ticks only while a timer runs.
    private val active: Flow<Boolean> =
        changes
            .flatMapLatest {
                if (sleepTimer.timeRemaining() == null) {
                    flowOf(false)
                } else {
                    flow {
                        while (sleepTimer.timeRemaining() != null) {
                            emit(true)
                            delay(TICK_MS)
                        }
                        emit(false)
                    }
                }
            }.distinctUntilChanged()

    val fields: Flow<SleepTimerFields> = combine(active, playToEnd, atEndOfWork, ::SleepTimerFields)

    fun remaining(): Flow<Long?> = flow {
        while (true) {
            emit(sleepTimer.timeRemaining())
            delay(TICK_MS)
        }
    }.distinctUntilChanged()

    fun start(
        durationMs: Long,
        playToEnd: Boolean,
    ) {
        controlSleepTimer(SleepTimerCommand.Start(durationMs, playToEnd))
        this.playToEnd.value = playToEnd
        changes.value++
    }

    fun startAtEndOfWork() {
        controlSleepTimer(SleepTimerCommand.StartAtEndOfWork)
        changes.value++
    }

    fun stop() {
        controlSleepTimer(SleepTimerCommand.Stop)
        changes.value++
    }

    private companion object {
        const val TICK_MS = 1_000L
    }
}
