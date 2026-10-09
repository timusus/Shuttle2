package com.simplecityapps.shuttle.ui

import android.app.Application
import android.content.pm.ShortcutManager
import com.simplecityapps.shuttle.ui.shell.ShellRequest
import com.simplecityapps.shuttle.ui.shell.ShellTab
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ShortcutHelperTest {
    private val application: Application = RuntimeEnvironment.getApplication()
    private val shortcuts get() = application.getSystemService(ShortcutManager::class.java).dynamicShortcuts

    @Test
    fun `creates play pause, shuffle all, recently played and search shortcuts`() {
        ShortcutHelper().createPlaybackShortcut(application, isPlaying = false)

        shortcuts.map { it.id } shouldContainExactlyInAnyOrder listOf("toggle_playback", "shuffle_all", "recently_played", "search")
    }

    @Test
    fun `recently played and search shortcuts carry actions the shell understands`() {
        ShortcutHelper().createPlaybackShortcut(application, isPlaying = false)

        val actions = shortcuts.associate { it.id to it.intent?.action }
        ShellRequest.fromShortcutAction(actions["recently_played"])?.tab shouldBe ShellTab.Home
        ShellRequest.fromShortcutAction(actions["search"]) shouldBe ShellRequest(ShellTab.Search)
    }

    @Test
    fun `updating the playback shortcut keeps the others`() {
        val helper = ShortcutHelper()
        helper.createPlaybackShortcut(application, isPlaying = false)

        helper.updatePlaybackShortcut(application, isPlaying = true)

        shortcuts.size shouldBe 4
    }
}
