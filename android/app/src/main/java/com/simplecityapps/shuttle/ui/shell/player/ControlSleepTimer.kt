package com.simplecityapps.shuttle.ui.shell.player

import com.simplecityapps.playback.sleeptimer.SleepTimer
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import javax.inject.Inject

sealed interface SleepTimerCommand {
    /** Pauses after [durationMs], or at the end of the song playing then when [playToEnd]. */
    data class Start(
        val durationMs: Long,
        val playToEnd: Boolean,
    ) : SleepTimerCommand

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

            SleepTimerCommand.Stop -> sleepTimer.stopTimer()
        }
    }
}

/** The sleep timer's time left in milliseconds: null when off, 0 while it waits for the song to end. */
class ReadSleepTimeRemaining @Inject constructor(
    private val sleepTimer: SleepTimer,
) {
    operator fun invoke(): Long? = sleepTimer.timeRemaining()
}

/** The play-to-end choice the sleep timer was last started with. */
class ReadSleepTimerPlayToEnd @Inject constructor(
    private val preferenceManager: GeneralPreferenceManager,
) {
    operator fun invoke(): Boolean = preferenceManager.sleepTimerPlayToEnd
}
