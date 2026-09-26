package com.simplecityapps.shuttle.ui.screens.tageditor

import android.content.IntentSender
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakePlaybackOperations
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.shuttle.ui.actions.ObserveSongs
import com.simplecityapps.testing.MainDispatcherRule
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TagEditorViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val songRepository = FakeSongRepository().apply { applyQueryPredicates = true }
    private val tagFileAccess = FakeTagFileAccess()
    private val playbackOperations = FakePlaybackOperations()

    private fun TestScope.viewModel(vararg songIds: Long) = TagEditorViewModel(
        songIds.toList(),
        ObserveSongs(songRepository),
        ReadSongTags(tagFileAccess),
        WriteSongTags(tagFileAccess, songRepository, playbackOperations),
        tagFileAccess,
    ).also { viewModel -> backgroundScope.launch { viewModel.uiState.collect {} } }

    private val TagEditorViewModel.editing get() = uiState.value.shouldBeInstanceOf<TagEditorUiState.Editing>()

    /** What the screen does once it has acted on the pending events. */
    private fun TagEditorViewModel.consumeEvents() = editing.events.forEach { onEventHandled(it.id) }

    @Test
    fun `one song shows every field with its value`() = runTest {
        songRepository.setSongs(listOf(createSong(id = 1)))
        tagFileAccess.files = mapOf(1L to createAudioFile(title = "Song", track = 3))

        val viewModel = viewModel(1)
        advanceUntilIdle()

        val editing = viewModel.editing
        editing.songCount shouldBe 1
        editing.fields.map { it.field } shouldBe TagField.entries
        editing.fields.first { it.field == TagField.Title }.initial shouldBe "Song"
        editing.fields.first { it.field == TagField.Track }.initial shouldBe "3"
        editing.hasChanges shouldBe false
    }

    @Test
    fun `several songs hide title and track and mark the fields they disagree on`() = runTest {
        songRepository.setSongs(listOf(createSong(id = 1), createSong(id = 2)))
        tagFileAccess.files = mapOf(1L to createAudioFile(album = "A"), 2L to createAudioFile(album = "B"))

        val viewModel = viewModel(1, 2)
        advanceUntilIdle()

        val fields = viewModel.editing.fields.associateBy { it.field }
        fields.keys.contains(TagField.Title) shouldBe false
        fields.keys.contains(TagField.Track) shouldBe false
        fields.getValue(TagField.Album) shouldBe TagFieldState(TagField.Album, initial = "", mixed = true)
        fields.getValue(TagField.Genres) shouldBe TagFieldState(TagField.Genres, initial = "Jazz", mixed = false)
    }

    @Test
    fun `songs whose files can't be read are listed and left out`() = runTest {
        val unreadable = createSong(id = 2, name = "Remote")
        songRepository.setSongs(listOf(createSong(id = 1), unreadable))
        tagFileAccess.files = mapOf(1L to createAudioFile())

        val viewModel = viewModel(1, 2)
        advanceUntilIdle()

        viewModel.editing.songCount shouldBe 1
        viewModel.editing.skipped shouldBe listOf(unreadable)
    }

    @Test
    fun `no readable songs is unreadable`() = runTest {
        songRepository.setSongs(listOf(createSong(id = 1)))

        val viewModel = viewModel(1)
        advanceUntilIdle()

        viewModel.uiState.value shouldBe TagEditorUiState.Unreadable
    }

    @Test
    fun `editing a field marks it changed and reset puts it back`() = runTest {
        songRepository.setSongs(listOf(createSong(id = 1)))
        tagFileAccess.files = mapOf(1L to createAudioFile(album = "Album"))
        val viewModel = viewModel(1)
        advanceUntilIdle()

        viewModel.onFieldChange(TagField.Album, "Other")
        viewModel.editing.hasChanges shouldBe true

        viewModel.onFieldReset(TagField.Album)
        viewModel.editing.hasChanges shouldBe false
    }

    @Test
    fun `save writes the changes and reports the result`() = runTest {
        songRepository.setSongs(listOf(createSong(id = 1), createSong(id = 2)))
        tagFileAccess.files = mapOf(1L to createAudioFile(), 2L to createAudioFile())
        tagFileAccess.failingSongIds = setOf(2)
        val viewModel = viewModel(1, 2)
        advanceUntilIdle()

        viewModel.onFieldChange(TagField.Album, "New")
        viewModel.onSave()
        advanceUntilIdle()

        val saved = viewModel.editing.events.single().value.shouldBeInstanceOf<TagEditorEvent.Saved>()
        saved.result.updated.map { it.id } shouldBe listOf(1L)
        saved.result.failed.map { it.id } shouldBe listOf(2L)
        tagFileAccess.writes.map { it.second } shouldBe List(2) { mapOf("ALBUM" to listOf("New")) }
        songRepository.updatedSongs.map { it.album } shouldBe listOf("New")
    }

    @Test
    fun `save without changes does nothing`() = runTest {
        songRepository.setSongs(listOf(createSong(id = 1)))
        tagFileAccess.files = mapOf(1L to createAudioFile())
        val viewModel = viewModel(1)
        advanceUntilIdle()

        viewModel.onSave()
        advanceUntilIdle()

        viewModel.editing.writing shouldBe null
        tagFileAccess.writes shouldBe emptyList()
    }

    @Test
    fun `save asks for consent first and writes once it's given`() = runTest {
        songRepository.setSongs(listOf(createSong(id = 1)))
        tagFileAccess.files = mapOf(1L to createAudioFile())
        val consent = mockk<IntentSender>()
        tagFileAccess.consent = consent
        val viewModel = viewModel(1)
        advanceUntilIdle()

        viewModel.onFieldChange(TagField.Album, "New")
        viewModel.onSave()
        advanceUntilIdle()

        viewModel.editing.events.map { it.value } shouldBe listOf(TagEditorEvent.RequestWriteConsent(consent))
        tagFileAccess.writes shouldBe emptyList()
        viewModel.consumeEvents()

        viewModel.onWriteConsent(granted = true)
        advanceUntilIdle()

        viewModel.editing.events.single().value.shouldBeInstanceOf<TagEditorEvent.Saved>().result.updated.map { it.id } shouldBe listOf(1L)
        tagFileAccess.writes.map { it.second } shouldBe listOf(mapOf("ALBUM" to listOf("New")))
    }

    @Test
    fun `declined consent goes back to editing without writing`() = runTest {
        songRepository.setSongs(listOf(createSong(id = 1)))
        tagFileAccess.files = mapOf(1L to createAudioFile())
        tagFileAccess.consent = mockk<IntentSender>()
        val viewModel = viewModel(1)
        advanceUntilIdle()

        viewModel.onFieldChange(TagField.Album, "New")
        viewModel.onSave()
        advanceUntilIdle()
        viewModel.editing.events.single().value.shouldBeInstanceOf<TagEditorEvent.RequestWriteConsent>()
        viewModel.consumeEvents()

        viewModel.onWriteConsent(granted = false)
        advanceUntilIdle()

        viewModel.editing.writing shouldBe null
        viewModel.editing.hasChanges shouldBe true
        tagFileAccess.writes shouldBe emptyList()
    }
}
