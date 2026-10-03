package com.simplecityapps.shuttle.ui.screens.settings

import android.content.res.Resources
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** The restore snackbar text: pluralised counts, the not-found suffix, and the nothing-matched message. */
@RunWith(RobolectricTestRunner::class)
class BackupImportedMessageTest {
    private val resources: Resources = RuntimeEnvironment.getApplication().resources

    private fun message(
        songsMatched: Int,
        playlistsRestored: Int,
        songsUnmatched: Int = 0,
        settingsRestored: Int = 0
    ) = backupImportedMessage(resources, SettingsUiEvent.BackupImported(songsMatched, playlistsRestored, songsUnmatched, settingsRestored))

    @Test
    fun `pluralises both counts`() {
        message(songsMatched = 3, playlistsRestored = 1) shouldBe "Restore complete: 3 songs matched, 1 playlist added or updated"
        message(songsMatched = 1, playlistsRestored = 2) shouldBe "Restore complete: 1 song matched, 2 playlists added or updated"
    }

    @Test
    fun `appends songs not found`() {
        message(songsMatched = 9, playlistsRestored = 0, songsUnmatched = 1) shouldBe "Restore complete: 9 songs matched, 0 playlists added or updated; 1 song not found"
        message(songsMatched = 9, playlistsRestored = 2, songsUnmatched = 3) shouldBe "Restore complete: 9 songs matched, 2 playlists added or updated; 3 songs not found"
    }

    @Test
    fun `says nothing matched when no songs matched and no playlists were added`() {
        message(songsMatched = 0, playlistsRestored = 0) shouldBe "Nothing in this backup matched your library"
        message(songsMatched = 0, playlistsRestored = 0, songsUnmatched = 12) shouldBe "Nothing in this backup matched your library"
    }

    @Test
    fun `says the settings were restored`() {
        message(songsMatched = 0, playlistsRestored = 0, songsUnmatched = 12, settingsRestored = 20) shouldBe
            "Settings restored; no songs or playlists in this backup matched your library"
        message(songsMatched = 3, playlistsRestored = 1, settingsRestored = 20) shouldBe
            "Restore complete: 3 songs matched, 1 playlist added or updated; settings restored"
        message(songsMatched = 9, playlistsRestored = 2, songsUnmatched = 3, settingsRestored = 1) shouldBe
            "Restore complete: 9 songs matched, 2 playlists added or updated; 3 songs not found; settings restored"
    }
}
