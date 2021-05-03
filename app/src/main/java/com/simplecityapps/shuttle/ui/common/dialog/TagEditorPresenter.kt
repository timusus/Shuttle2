package com.simplecityapps.shuttle.ui.common.dialog

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.simplecityapps.ktaglib.KTagLib
import com.simplecityapps.localmediaprovider.local.provider.TagLibProperty
import com.simplecityapps.localmediaprovider.local.provider.taglib.FileScanner
import com.simplecityapps.mediaprovider.model.AudioFile
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.mediaprovider.repository.SongRepository
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.mvp.BaseContract
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import com.squareup.phrase.Phrase
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

        val mapOfChangedValues: HashMap<String, ArrayList<String?>>
            get() {
                val map = hashMapOf<String, ArrayList<String?>>()
                if (titleField.hasChanged) {
                    map[TagLibProperty.Title.key] = ArrayList(listOf(titleField.currentValue))
                }
                if (artistField.hasChanged) {
                    map[TagLibProperty.Artist.key] = ArrayList(listOf(artistField.currentValue))
                }
                if (albumField.hasChanged) {
                    map[TagLibProperty.Album.key] = ArrayList(listOf(albumField.currentValue))
                }
                if (albumArtistField.hasChanged) {
                    map[TagLibProperty.AlbumArtist.key] = ArrayList(listOf(albumArtistField.currentValue))
                }
                if (dateField.hasChanged) {
                    map[TagLibProperty.Date.key] = ArrayList(listOf(dateField.currentValue))
                }
                if (trackField.hasChanged || trackTotalField.hasChanged) {
                    if (trackField.currentValue != null) {
                        var track = trackField.currentValue!!.padStart(2, '0')
                        if (trackTotalField.currentValue != null) {
                            track += "/${trackTotalField.currentValue!!.padStart(2, '0')}"
                        }
                        map[TagLibProperty.Track.key] = ArrayList(listOf(track))
                    }
                }
                if (discField.hasChanged || discTotalField.hasChanged) {
                    if (discField.currentValue != null) {
                        var disc = discField.currentValue!!.padStart(2, '0')
                        if (discTotalField.currentValue != null) {
                            disc += "/${discTotalField.currentValue!!.padStart(2, '0')}"
                        }
                        map[TagLibProperty.Disc.key] = ArrayList(listOf(disc))
                    }
                }
                if (genreField.hasChanged) {
                    map[TagLibProperty.Genre.key] = ArrayList(listOf(genreField.currentValue))
                }
                if (lyricsField.hasChanged) {
                    map[TagLibProperty.Lyrics.key] = ArrayList(listOf(lyricsField.currentValue))
                }
                return map
            }
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

class TagEditorPresenter @Inject constructor(
    private val context: Context,
    private val fileScanner: FileScanner,
    private val songRepository: SongRepository
) : BasePresenter<TagEditorContract.View>(), TagEditorContract.Presenter {

    private lateinit var uneditables: List<Pair<Song, AudioFile?>>
    private lateinit var editables: List<Pair<Song, AudioFile?>>

    private val saveTagsScope = CoroutineScope(Dispatchers.Main + exceptionHandler)

    override fun load(songs: List<Song>) {
        view?.setLoading(TagEditorContract.LoadingState.ReadingTags(0, songs.size))

        launch {
            val songAudioFilePairs = songs.mapIndexed { index: Int, song: Song ->
                val uri = Uri.parse(song.path)
                if (song.path.startsWith("content://")) {
                    if (DocumentsContract.isDocumentUri(context, uri)) {
                        view?.setLoading(TagEditorContract.LoadingState.ReadingTags(index, songs.size))
                        return@mapIndexed song to fileScanner.getAudioFile(context, uri)
                    }
                }
                view?.setLoading(TagEditorContract.LoadingState.ReadingTags(index, songs.size))
                song to (null as AudioFile?)
            }

            this@TagEditorPresenter.uneditables = songAudioFilePairs.filter { it.second == null }
            editables = songAudioFilePairs - uneditables

            if (editables.isEmpty()) {
                view?.closeWithToast(context.getString(R.string.edit_tags_read_failed))
                return@launch
            }

            val titles = editables.map { it.second?.title }.distinct()
            val artists = editables.map { it.second?.artists }.distinct()
            val albums = editables.map { it.second?.album }.distinct()
            val albumArtists = editables.map { it.second?.albumArtist }.distinct()
            val dates = editables.map { it.second?.year }.distinct()
            val tracks = editables.map { it.second?.track }.distinct()
            val trackTotals = editables.map { it.second?.trackTotal }.distinct()
            val discs = editables.map { it.second?.disc }.distinct()
            val discTotals = editables.map { it.second?.discTotal }.distinct()
            val genres = editables.map { it.second?.genres }.distinct()
            val lyrics = editables.map { it.second?.lyrics }.distinct()

            view?.setLoading(TagEditorContract.LoadingState.None)
            view?.setData(
                TagEditorContract.Data(
                    titleField = TagEditorContract.Field(
                        initialValue = if (titles.size > 1) null else titles.firstOrNull(),
                        hasMultipleValues = titles.size > 1,
                        visible = editables.size <= 1
                    ),
                    artistField = TagEditorContract.Field(
                        initialValue = if (artists.size > 1) null else artists.flatMap { artists -> artists.orEmpty() }.distinct().joinToString(", "),
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
                        hasMultipleValues = discs.size > 1
                    ),
                    discTotalField = TagEditorContract.Field(
                        initialValue = if (discTotals.size > 1) null else discTotals.firstOrNull()?.toString(),
                        hasMultipleValues = discTotals.size > 1
                    ),
                    genreField = TagEditorContract.Field(
                        initialValue = if (genres.size > 1) null else genres.flatMap { genres -> genres.orEmpty() }.distinct().joinToString(", "),
                        hasMultipleValues = genres.size > 1
                    ),
                    lyricsField = TagEditorContract.Field(
                        initialValue = if (lyrics.size > 1) null else lyrics.firstOrNull(),
                        hasMultipleValues = lyrics.size > 1
                    ),
                )
            )
        }
    }

    override fun save(data: TagEditorContract.Data) {
        view?.setLoading(TagEditorContract.LoadingState.WritingTags(0, editables.size))
        saveTagsScope.launch {
            val result = withContext(Dispatchers.IO) {
                editables.mapIndexed { index, (song, _) ->
                    songRepository.update(
                        song.copy(
                            name = if (data.titleField.hasChanged) data.titleField.currentValue else song.name,
                            album = if (data.albumField.hasChanged) data.albumField.currentValue else song.album,
                            albumArtist = if (data.albumArtistField.hasChanged) data.albumArtistField.currentValue else song.albumArtist,
                            artists = if (data.artistField.hasChanged) data.artistField.currentValue?.split(",").orEmpty().map { it.trim() } else song.artists,
                            year = if (data.dateField.hasChanged) data.dateField.currentValue?.toIntOrNull() else song.year,
                            genres = if (data.genreField.hasChanged) data.genreField.currentValue?.split(",").orEmpty().map { it.trim() } else song.genres,
                            track = if (data.trackField.hasChanged) data.trackField.currentValue?.toIntOrNull() else song.track,
                            disc = if (data.discField.hasChanged) data.discField.currentValue?.toIntOrNull() else song.disc,
                            lyrics = if (data.lyricsField.hasChanged) data.lyricsField.currentValue else song.lyrics
                        )
                    )

                    val uri = Uri.parse(song.path)
                    if (song.path.startsWith("content://")) {
                        if (DocumentsContract.isDocumentUri(context, uri)) {
                            val metadata = data.mapOfChangedValues
                            if (metadata.isNotEmpty()) {
                                try {
                                    context.contentResolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                                        withContext(Dispatchers.Main) {
                                            view?.setLoading(TagEditorContract.LoadingState.WritingTags(index, editables.size))
                                        }
                                        return@mapIndexed song to KTagLib.writeMetadata(pfd.detachFd(), metadata)
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
                    }

                    withContext(Dispatchers.Main) {
                        view?.setLoading(TagEditorContract.LoadingState.WritingTags(index, editables.size))
                    }

                    return@mapIndexed song to false
                }
            }

            val total = uneditables.size + editables.size // All of the songs the user wanted to edit
            val failureCount = result.filter { !it.second }.size // Songs that we tried to edit, but failed
            val successCount = result.size - failureCount // Songs we successfully edited
            if (successCount != total) { // If any failures occurred, or we couldn't edit all songs
                view?.closeWithToast(
                    Phrase.fromPlural(context, R.plurals.edit_tags_success, total)
                        .put("count", total - successCount)
                        .format()
                        .toString()
                )
            } else {
                view?.closeWithToast(
                    Phrase.fromPlural(context, R.plurals.edit_tags_failure, total)
                        .putOptional("count", successCount)
                        .putOptional("total", total)
                        .format()
                        .toString()
                )
            }
        }
    }
}