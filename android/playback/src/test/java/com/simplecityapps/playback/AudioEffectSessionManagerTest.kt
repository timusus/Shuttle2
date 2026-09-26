package com.simplecityapps.playback

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.Before
import org.junit.Test

/**
 * The effect control session follows the active playback's audio session id, as bound by
 * [AudioEffectSessionManager.attach] on init and on every playback switch. Chromecast has no local audio session
 * (its id is 0).
 */
class AudioEffectSessionManagerTest {
    private val events = mutableListOf<String>()
    private lateinit var manager: AudioEffectSessionManager

    @Before
    fun setUp() {
        manager =
            AudioEffectSessionManager(
                openSession = { events += "open $it" },
                closeSession = { events += "close $it" }
            )
    }

    @Test
    fun `binding the local session opens it`() {
        manager.bindTo(LOCAL_SESSION)

        events shouldBe listOf("open $LOCAL_SESSION")
        manager.sessionId shouldBe LOCAL_SESSION
    }

    @Test
    fun `switching local to cast closes the local session`() {
        manager.bindTo(LOCAL_SESSION)
        events.clear()

        manager.bindTo(CAST_SESSION)

        events shouldBe listOf("close $LOCAL_SESSION")
        manager.sessionId.shouldBeNull()
    }

    @Test
    fun `switching cast back to local reopens the local session`() {
        manager.bindTo(LOCAL_SESSION)
        manager.bindTo(CAST_SESSION)
        events.clear()

        manager.bindTo(LOCAL_SESSION)

        events shouldBe listOf("open $LOCAL_SESSION")
        manager.sessionId shouldBe LOCAL_SESSION
    }

    @Test
    fun `switching local to local with a new id closes the old and opens the new`() {
        manager.bindTo(LOCAL_SESSION)
        events.clear()

        manager.bindTo(OTHER_LOCAL_SESSION)

        events shouldBe listOf("close $LOCAL_SESSION", "open $OTHER_LOCAL_SESSION")
        manager.sessionId shouldBe OTHER_LOCAL_SESSION
    }

    @Test
    fun `rebinding the same session does not reopen it`() {
        manager.bindTo(LOCAL_SESSION)
        manager.bindTo(LOCAL_SESSION)

        events shouldBe listOf("open $LOCAL_SESSION")
    }

    @Test
    fun `binding cast twice closes only once`() {
        manager.bindTo(LOCAL_SESSION)
        manager.bindTo(CAST_SESSION)
        manager.bindTo(CAST_SESSION)

        events shouldBe listOf("open $LOCAL_SESSION", "close $LOCAL_SESSION")
    }

    @Test
    fun `an invalid session id never opens a session`() {
        manager.bindTo(-1)
        manager.bindTo(CAST_SESSION)

        events shouldBe emptyList()
        manager.sessionId.shouldBeNull()
    }

    private companion object {
        const val LOCAL_SESSION = 42
        const val OTHER_LOCAL_SESSION = 43
        const val CAST_SESSION = 0
    }
}
