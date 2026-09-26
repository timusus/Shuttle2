package com.simplecityapps.playback.spec

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.Player
import androidx.media3.common.util.HandlerWrapper
import androidx.media3.test.utils.FakeClock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.robolectric.Shadows.shadowOf

/**
 * Runs a player on a [FakeClock] that doesn't advance by itself, from the Robolectric main looper: [idle] lets the
 * clock hand out the messages due now and waits for each to be handled, and [runUntil] moves the clock on a step at a
 * time until a condition holds.
 *
 * Neither gives up after an amount of wall time, however slowly a loaded machine runs the player's threads (#555):
 * [idle] waits for the thread handling each message, failing only if one never finishes one, and [runUntil] fails
 * after an amount of the clock's time. What loads media still runs on real threads, so how much of it is ready at a
 * given clock time isn't fixed.
 *
 * The failure messages say what [player] was doing when it hung or a condition never held.
 */
class ClockDriver(
    private val clock: FakeClock,
    private val player: Player
) {
    /**
     * Runs the main looper's due tasks and what the clock hands the player's other threads (such as its playback
     * thread taking or giving up audio focus), until each thread has handled them and the main looper has had the
     * events they raised, and so on until none has anything left to do now, without letting the clock's time pass.
     */
    fun idle() {
        val mainLooper = shadowOf(Looper.getMainLooper())
        for (round in 0 until MAX_IDLE_ROUNDS) {
            mainLooper.idle()
            val looper = synchronized(clock) { handlingLooper() }
            when {
                looper == null -> if (mainLooper.isIdle) return
                looper != Looper.getMainLooper() -> awaitHandled(looper)
            }
        }
        throw AssertionError("The player never settled: its threads were still handing each other work after $MAX_IDLE_ROUNDS rounds, ${state()}")
    }

    /**
     * Plays the player on, a step of its clock at a time, each followed by [idle], until [condition] holds. Between
     * steps the main looper also runs a task it has scheduled up to a second ahead, as Media3's `runMainLooperUntil`
     * does. Fails once the clock has moved on [limitMs] without [condition] holding.
     */
    fun runUntil(
        limitMs: Long = DEFAULT_LIMIT_MS,
        condition: () -> Boolean
    ) {
        val mainLooper = shadowOf(Looper.getMainLooper())
        idle()
        var elapsedMs = 0L
        while (!condition()) {
            if (elapsedMs >= limitMs) throw AssertionError("The condition didn't hold after $limitMs ms of the player's clock, ${state()}")
            clock.advanceTime(STEP_MS)
            elapsedMs += STEP_MS
            idle()
            val nextTask = mainLooper.nextScheduledTaskTime
            if (!nextTask.isZero && nextTask.toMillis() <= SystemClock.elapsedRealtime() + MAIN_LOOPER_LOOKAHEAD_MS) {
                mainLooper.runOneTask()
                idle()
            }
        }
    }

    /**
     * The looper of the message the clock has handed out and is waiting to see handled, or of the next one due now if
     * its thread is blocked in another; null once every message due now has been handled. The clock hands them out one
     * at a time, in time order, to the main and other threads alike.
     */
    private fun handlingLooper(): Looper? {
        (clockActiveLooper.get(clock) as Looper?)?.let { return it }
        val now = clockTime.get(clock) as Long
        val due = (clockMessages.get(clock) as List<*>).filter { (messageTime.get(it) as Long) <= now }
        return due.minByOrNull { messageTime.get(it) as Long }?.let { (messageHandler.get(it) as HandlerWrapper).looper }
    }

    /** Waits for [looper]'s thread to get through what it has been sent so far: the clock's message among it. */
    private fun awaitHandled(looper: Looper) {
        val handled = CountDownLatch(1)
        // A looper that has quit takes nothing more; the clock drops what it had for it.
        val posted = runCatching { Handler(looper).post(handled::countDown) }.getOrDefault(false)
        if (posted && !handled.await(HANG_SECONDS, TimeUnit.SECONDS)) {
            val thread = looper.thread
            throw AssertionError(
                "${thread.name} hung: it hasn't finished handling a message in $HANG_SECONDS s, ${state()}\n" +
                    thread.stackTrace.joinToString("\n") { "\tat $it" }
            )
        }
    }

    private fun state(): String {
        val playbackState =
            when (player.playbackState) {
                Player.STATE_IDLE -> "idle"
                Player.STATE_BUFFERING -> "buffering"
                Player.STATE_READY -> "ready"
                else -> "ended"
            }
        return "at ${clock.elapsedRealtime()} ms on the player's clock, with the player $playbackState " +
            "(playWhenReady ${player.playWhenReady}) ${player.currentPosition} ms into item ${player.currentMediaItemIndex}"
    }

    companion object {
        /** How far [runUntil] moves the player's clock on at a time: the player's working interval while it plays. */
        const val STEP_MS = 10L

        /**
         * How much of the player's clock [runUntil] runs through by default: far longer than any test plays, so that a
         * load a loaded machine holds up can't use it up, while one that never holds still fails in seconds.
         */
        const val DEFAULT_LIMIT_MS = 3_600_000L

        /** How far ahead [runUntil] runs a task the main looper has scheduled, moving its time on to it. */
        private const val MAIN_LOOPER_LOOKAHEAD_MS = 1_000L

        /** How many handovers between threads [idle] allows at one clock time before it takes them for a livelock. */
        private const val MAX_IDLE_ROUNDS = 100_000

        /**
         * How long a thread may take over one message before [idle] reports it hung. It only bounds a thread that never
         * finishes, which would otherwise hang the test run: a message takes milliseconds, however loaded the machine.
         */
        private const val HANG_SECONDS = 60L

        /**
         * FakeClock's state, which it doesn't expose: its time, the looper of the message it has out (if any), and the
         * messages yet to go, each with its time and handler. Guarded by the clock.
         */
        private val clockTime = FakeClock::class.java.getDeclaredField("timeSinceBootMs").apply { isAccessible = true }
        private val clockActiveLooper = FakeClock::class.java.getDeclaredField("activeMessageLooper").apply { isAccessible = true }
        private val clockMessages = FakeClock::class.java.getDeclaredField("handlerMessages").apply { isAccessible = true }
        private val handlerMessage = Class.forName("androidx.media3.test.utils.FakeClock\$HandlerMessage")
        private val messageTime = handlerMessage.getDeclaredField("timeMs").apply { isAccessible = true }
        private val messageHandler = handlerMessage.getDeclaredField("handler").apply { isAccessible = true }
    }
}
