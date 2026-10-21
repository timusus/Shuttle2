package com.simplecityapps.shuttle.ui.shell.player

import com.simplecityapps.playback.sleeptimer.SleepTimer
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import dev.zacsweers.metro.Inject

sealed interface SleepTimerCommand {
    /** Pauses after [durationMs], or at the end of the song playing then when [playToEnd]. */
    data class Start(
        val durationMs: Long,
        val playToEnd: Boolean,
    ) : SleepTimerCommand

    /** Pauses when the last movement of the work playing ends. */
    data object StartAtEndOfWork : SleepTimerCommand

    data object Stop : SleepTimerCommand
}

/** Starts the sleep timer, remembering its play-to-end choice for next time, or stops it. */
class ControlSleepTimer @Inject constructor(
    private val sleepTimer: SleepTimer,
    private val preferenceManager: GeneralPreferenceManager,
) {
    operator fun invoke(command: SleepTimerCommand) {
        when (command) {
            is SleepTimerCommand.Start -> {
                preferenceManager.sleepTimerPlayToEnd = command.playToEnd
                sleepTimer.startTimer(command.durationMs, command.playToEnd)
            }

            SleepTimerCommand.StartAtEndOfWork -> sleepTimer.startTimerAtEndOfWork()

            SleepTimerCommand.Stop -> sleepTimer.stopTimer()
        }
    }
}
