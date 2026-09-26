package com.simplecityapps.shuttle.debug

import android.content.Context
import com.simplecityapps.shuttle.settings.DebugSettings
import com.simplecityapps.shuttle.settings.SettingsStore
import com.simplecityapps.shuttle.settings.defaultSharedPreferences
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Settings > About > Advanced > File logging: what the tree writes to the file Copy debug logs reads. */
@RunWith(RobolectricTestRunner::class)
class DebugLoggingTreeTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val debugSettings = DebugSettings(SettingsStore(context.defaultSharedPreferences().apply { edit().clear().commit() }))
    private val tree = DebugLoggingTree(context, debugSettings)

    private val logFile get() = context.getFileStreamPath(DebugLoggingTree.FILE_NAME)

    @Before
    fun setUp() {
        context.deleteFile(DebugLoggingTree.FILE_NAME)
    }

    @Test
    fun `nothing is written while file logging is off`() {
        tree.i("Playback started")

        logFile.exists() shouldBe false
    }

    @Test
    fun `each message is appended while file logging is on`() {
        debugSettings.fileLogging.value = true

        tree.i("Playback started")
        tree.w("Queue restored")

        val logs = logFile.readText()
        logs shouldContain "Playback started"
        logs shouldContain "Queue restored"
    }
}
