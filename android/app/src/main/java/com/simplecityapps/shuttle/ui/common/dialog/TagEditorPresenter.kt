package com.simplecityapps.shuttle.ui.common.dialog

import android.content.Context
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.common.mvp.BaseContract
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import com.simplecityapps.shuttle.ui.screens.tageditor.EditableSong
import com.simplecityapps.shuttle.ui.screens.tageditor.ReadSongTags
import com.simplecityapps.shuttle.ui.screens.tageditor.TagField
import com.simplecityapps.shuttle.ui.screens.tageditor.WriteSongTags
import com.simplecityapps.shuttle.ui.screens.tageditor.tagFields
import com.squareup.phrase.Phrase
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

interface TagEditorContract {
    data class Field(val initialValue: String?, val hasMultipleValues: Boolean, val visible: Boolean = true) {
        var currentValue: String? = initialValue

        val hasChanged get() = currentValue != initialValue

        fun reset() {
            currentValue = initialValue
        }
    }

    data class Data(
        val titleField: Field,
        val artistField: Field,
        val albumField: Field,
        val albumArtistField: Field,
        val dateField: Field,
        val trackField: Field,
        val trackTotalField: Field,
        val discField: Field,
        val discTotalField: Field,
        val genreField: Field,
        val lyricsField: Field
    ) {
        val all: List<Field>
            get() = listOf(titleField, artistField, albumField, albumArtistField, dateField, trackField, trackTotalField, discField, discTotalField, genreField, lyricsField)

        /** The fields the user changed, as the shared tag writer takes them. A mixed field left empty isn't written. */
        val edits: Map<TagField, String>
            get() = listOf(
                TagField.Title to titleField,
                TagField.Artists to artistField,
                TagField.Album to albumField,
                TagField.AlbumArtist to albumArtistField,
                TagField.Year to dateField,
                TagField.Track to trackField,
                TagField.TrackTotal to trackTotalField,
                TagField.Disc to discField,
                TagField.DiscTotal to discTotalField,
                TagField.Genres to genreField,
                TagField.Lyrics to lyricsField,
            ).filter { (_, field) -> field.hasChanged && !(field.hasMultipleValues && field.currentValue.isNullOrEmpty()) }
                .associate { (tag, field) -> tag to field.currentValue.orEmpty() }
    }

    sealed class LoadingState {
        object None : LoadingState()

        class ReadingTags(val progress: Int, val total: Int) : LoadingState()

        class WritingTags(val progress: Int, val total: Int) : LoadingState()
    }

    interface View {
        fun setData(data: Data)

        fun setLoading(loadingState: LoadingState)

        fun closeWithToast(message: String)
    }

    interface Presenter : BaseContract.Presenter<View> {
        fun load(songs: List<Song>)

        fun save(data: Data)
    }
}

/** The legacy tag editor dialog's presenter, on the same read and write path as the shell's tag editor. */
class TagEditorPresenter
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val readSongTags: ReadSongTags,
    private val writeSongTags: WriteSongTags,
) : BasePresenter<TagEditorContract.View>(),
    TagEditorContract.Presenter {
    private var editables: List<EditableSong> = emptyList()

    // Outlives the dialog, so a save finishes after it closes.
    private val saveTagsScope = CoroutineScope(Dispatchers.Main + exceptionHandler)

    override fun load(songs: List<Song>) {
        view?.setLoading(TagEditorContract.LoadingState.ReadingTags(0, songs.size))

        launch {
            val tags = readSongTags(songs) { read, total -> view?.setLoading(TagEditorContract.LoadingState.ReadingTags(read, total)) }
            editables = tags.editable

            if (editables.isEmpty()) {
                view?.closeWithToast(context.getString(R.string.edit_tags_read_failed))
                return@launch
            }

            val fields = tagFields(editables.map { it.file }).associateBy { it.field }
            fun field(tag: TagField) = fields[tag]?.let { TagEditorContract.Field(initialValue = if (it.mixed) null else it.initial, hasMultipleValues = it.mixed) }
                ?: TagEditorContract.Field(initialValue = null, hasMultipleValues = false, visible = false)

            view?.setLoading(TagEditorContract.LoadingState.None)
            view?.setData(
                TagEditorContract.Data(
                    titleField = field(TagField.Title),
                    artistField = field(TagField.Artists),
                    albumField = field(TagField.Album),
                    albumArtistField = field(TagField.AlbumArtist),
                    dateField = field(TagField.Year),
                    trackField = field(TagField.Track),
                    trackTotalField = field(TagField.TrackTotal),
                    discField = field(TagField.Disc),
                    discTotalField = field(TagField.DiscTotal),
                    genreField = field(TagField.Genres),
                    lyricsField = field(TagField.Lyrics),
                ),
            )
        }
    }

    override fun save(data: TagEditorContract.Data) {
        view?.setLoading(TagEditorContract.LoadingState.WritingTags(0, editables.size))
        saveTagsScope.launch {
            val result = writeSongTags(editables, data.edits) { written, total -> view?.setLoading(TagEditorContract.LoadingState.WritingTags(written, total)) }
            if (result.failed.isEmpty()) {
                view?.closeWithToast(
                    Phrase.fromPlural(context, R.plurals.edit_tags_success, result.updated.size)
                        .put("count", result.updated.size)
                        .format()
                        .toString(),
                )
            } else {
                // Songs that couldn't be read were never attempted, so they don't count as failures.
                val total = result.updated.size + result.failed.size
                view?.closeWithToast(
                    Phrase.fromPlural(context, R.plurals.edit_tags_failure, total)
                        .putOptional("count", result.failed.size)
                        .putOptional("total", total)
                        .format()
                        .toString(),
                )
            }
        }
    }
}
