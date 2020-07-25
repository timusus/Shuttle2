package com.simplecityapps.shuttle.ui.common.dialog

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.simplecityapps.ktaglib.AudioFile
import com.simplecityapps.ktaglib.KTagLib
import com.simplecityapps.localmediaprovider.local.provider.taglib.FileScanner
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.mediaprovider.repository.SongRepository
import com.simplecityapps.shuttle.ui.common.mvp.BaseContract
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.FileNotFoundException
import javax.inject.Inject

interface TagEditorContract {

    data class Field(val initialValue: String?, val hasMultipleValues: Boolean, val visible: Boolean = true) {

        var currentValue: String? = initialValue

        val hasChanged get() = currentValue != initialValue

        fun getValueIfChanged(): String? {
            if (hasChanged) {
                return currentValue
            }
            return null
        }

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
        val genreField: Field
    ) {

        val all: List<Field>
            get() = listOf(titleField, artistField, albumField, albumArtistField, dateField, trackField, trackTotalField, discField, discTotalField, genreField)
    }

    enum class LoadingState {
        None, ReadingTags, WritingTags
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

class TagEditorPresenter @Inject constructor(
    private val context: Context,
    private val fileScanner: FileScanner,
    private val tagLib: KTagLib,
    private val songRepository: SongRepository
) : BasePresenter<TagEditorContract.View>(), TagEditorContract.Presenter {

    private lateinit var uneditables: List<Pair<Song, AudioFile?>>
    private lateinit var editables: List<Pair<Song, AudioFile?>>

    private val saveTagsScope = CoroutineScope(Dispatchers.Main + exceptionHandler)

    override fun load(songs: List<Song>) {
        view?.setLoading(TagEditorContract.LoadingState.ReadingTags)

        launch {
            val songAudioFilePairs = songs.map { song: Song ->
                val uri = Uri.parse(song.path)
                if (song.path.startsWith("content://")) {
                    if (DocumentsContract.isDocumentUri(context, uri)) {
                        return@map song to fileScanner.getAudioFile(context, uri)
                    }
                }
                song to (null as AudioFile?)
            }

            this@TagEditorPresenter.uneditables = songAudioFilePairs.filter { it.second == null }
            editables = songAudioFilePairs - uneditables

            if (editables.isEmpty()) {
                view?.closeWithToast("The selected song(s) tags could not be read")
                return@launch
            }

            val titles = editables.map { it.second?.title }.distinct()
            val artists = editables.map { it.second?.artist }.distinct()
            val albums = editables.map { it.second?.album }.distinct()
            val albumArtists = editables.map { it.second?.albumArtist }.distinct()
            val dates = editables.map { it.second?.date }.distinct()
            val tracks = editables.map { it.second?.track }.distinct()
            val trackTotals = editables.map { it.second?.trackTotal }.distinct()
            val discs = editables.map { it.second?.disc }.distinct()
            val discTotals = editables.map { it.second?.discTotal }.distinct()
            val genres = editables.map { it.second?.genre }.distinct()

            view?.setLoading(TagEditorContract.LoadingState.None)
            view?.setData(
                TagEditorContract.Data(
                    titleField = TagEditorContract.Field(
                        initialValue = if (titles.size > 1) null else titles.firstOrNull(),
                        hasMultipleValues = titles.size > 1,
                        visible = editables.size <= 1
                    ),
                    artistField = TagEditorContract.Field(
                        initialValue = if (artists.size > 1) null else artists.firstOrNull(),
                        hasMultipleValues = artists.size > 1
                    ),
                    albumField = TagEditorContract.Field(
                        initialValue = if (albums.size > 1) null else albums.firstOrNull(),
                        hasMultipleValues = albums.size > 1
                    ),
                    albumArtistField = TagEditorContract.Field(
                        initialValue = if (albumArtists.size > 1) null else albumArtists.firstOrNull(),
                        hasMultipleValues = albumArtists.size > 1
                    ),
                    dateField = TagEditorContract.Field(
                        initialValue = if (dates.size > 1) null else dates.firstOrNull(),
                        hasMultipleValues = dates.size > 1
                    ),
                    trackField = TagEditorContract.Field(
                        initialValue = if (tracks.size > 1) null else tracks.firstOrNull()?.toString(),
                        hasMultipleValues = tracks.size > 1,
                        visible = editables.size <= 1
                    ),
                    trackTotalField = TagEditorContract.Field(
                        initialValue = if (trackTotals.size > 1) null else trackTotals.firstOrNull()?.toString(),
                        hasMultipleValues = trackTotals.size > 1
                    ),
                    discField = TagEditorContract.Field(
                        initialValue = if (discs.size > 1) null else discs.firstOrNull()?.toString(),
                        hasMultipleValues = discs.size > 1,
                        visible = editables.size <= 1
                    ),
                    discTotalField = TagEditorContract.Field(
                        initialValue = if (discTotals.size > 1) null else discTotals.firstOrNull()?.toString(),
                        hasMultipleValues = discTotals.size > 1
                    ),
                    genreField = TagEditorContract.Field(
                        initialValue = if (genres.size > 1) null else genres.firstOrNull(),
                        hasMultipleValues = genres.size > 1
                    )
                )
            )
        }
    }

    override fun save(data: TagEditorContract.Data) {
        view?.setLoading(TagEditorContract.LoadingState.WritingTags)
        saveTagsScope.launch {
            val result = withContext(Dispatchers.IO) {
                editables.map { (song, _) ->
                    songRepository.updateSong(
                        song.copy(
                            name = data.titleField.getValueIfChanged() ?: song.name,
                            album = data.albumField.getValueIfChanged() ?: song.album,
                            albumArtist = data.albumArtistField.getValueIfChanged() ?: song.albumArtist,
                            year = data.dateField.getValueIfChanged()?.toIntOrNull() ?: song.year,
                            track = data.trackField.getValueIfChanged()?.toIntOrNull() ?: song.track,
                            disc = data.discField.getValueIfChanged()?.toIntOrNull() ?: song.disc
                        )
                    )

                    val uri = Uri.parse(song.path)
                    if (song.path.startsWith("content://")) {
                        if (DocumentsContract.isDocumentUri(context, uri)) {
                            try {
                                context.contentResolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                                    return@map song to tagLib.updateTags(
                                        pfd.detachFd(),
                                        data.titleField.getValueIfChanged(),
                                        data.artistField.getValueIfChanged(),
                                        data.albumField.getValueIfChanged(),
                                        data.albumArtistField.getValueIfChanged(),
                                        data.dateField.getValueIfChanged(),
                                        data.trackField.getValueIfChanged()?.toIntOrNull(),
                                        data.trackTotalField.getValueIfChanged()?.toIntOrNull(),
                                        data.discField.getValueIfChanged()?.toIntOrNull(),
                                        data.discTotalField.getValueIfChanged()?.toIntOrNull(),
                                        data.genreField.getValueIfChanged()
                                    )
                                }
                            } catch (e: IllegalStateException) {
                                Timber.e(e, "Failed to update tags")
                            } catch (e: FileNotFoundException) {
                                Timber.e(e, "Failed to update tags")
                            } catch (e: SecurityException) {
                                Timber.e(e, "FFailed to update tags")
                            }
                        }
                    }
                    return@map song to false
                }
            }

            val total = uneditables.size + editables.size // All of the songs the user wanted to edit
            val failureCount = result.filter { !it.second }.size // Songs that we tried to edit, but failed
            val successCount = result.size - failureCount // Songs we successfully edited
            if (successCount != total) { // If any failures occurred, or we couldn't edit all songs
                view?.closeWithToast("Failed to update ${total - successCount} of $total song${if (total > 1) "s" else ""}")
            } else {
                view?.closeWithToast("Successfully updated $successCount song${if (successCount > 1) "s" else ""}")
            }
        }
    }
}