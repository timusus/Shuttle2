package com.simplecityapps.shuttle.shared.local

import com.simplecityapps.localmediaprovider.local.provider.ffmpegPropertyMap
import com.simplecityapps.localmediaprovider.local.provider.toFileTags
import com.simplecityapps.mediaprovider.FlowEvent
import com.simplecityapps.mediaprovider.ImportPhase
import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.mediaprovider.MediaImporter.Companion.songTagsOutdated
import com.simplecityapps.mediaprovider.MediaProvider
import com.simplecityapps.mediaprovider.MessageProgress
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import dev.zacsweers.metro.Inject
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.datetime.LocalDate

/**
 * This device's music on iOS (#590), as the S2 scanner ([MediaProviderType.Shuttle]): every audio file [IosLocalFiles]
 * finds in Documents and the picked folders, read with FFmpeg. A file whose size and modification time are unchanged
 * keeps the song read last time, so a rescan only reads what's new or changed, unless the songs predate this build's
 * tags ([songTagsOutdated]), when every file is read again; one that's gone leaves the library.
 *
 * Only a folder that was read in full loses songs (see [IosLocalListing]): one out of reach (its bookmark won't resolve
 * until it's picked again), unreadable, or listing nothing where it had songs keeps them, and so does a file iCloud has
 * offloaded, so a moment without access doesn't cost them their history. An offloaded file with no song yet is
 * downloaded, and imported once it's here.
 */
@OptIn(ExperimentalAtomicApi::class)
@Inject
class IosLocalMediaProvider(
    private val localFiles: IosLocalFiles,
    private val preferenceManager: GeneralPreferenceManager
) : MediaProvider {
    override val type = MediaProviderType.Shuttle

    /** The listing the last [findSongs] imported, until its songs are stored. */
    private val pending = AtomicReference<IosLocalListing?>(null)

    override fun findSongs(existingSongs: List<Song>): Flow<FlowEvent<List<Song>, MessageProgress>> = flow {
        val listing = localFiles.audioFiles()
        pending.store(null)
        val reread = preferenceManager.songTagsOutdated(type)
        val existingByPath = existingSongs.associateBy { it.path }
        val offloaded = listing.offloaded.toSet()
        val listedFolders = (listing.files.map { it.path } + listing.offloaded).mapNotNullTo(HashSet(), ::folderIdOf)
        val keptFolders = listing.unread.toSet() + listing.folders.filterNot(listedFolders::contains)

        val songs = ArrayList<Song>(existingSongs.size + listing.files.size)
        val filePaths = listing.files.mapTo(HashSet()) { it.path }
        // A folder that failed partway also lists the files it got to, which make their songs below
        songs += existingSongs.filter { song -> song.path !in filePaths && (song.path in offloaded || folderIdOf(song.path) in keptFolders) }
        listing.offloaded.filterNot(existingByPath::containsKey).forEach(localFiles::download)
        val files = listing.files
        files.forEachIndexed { index, file ->
            val existing = existingByPath[file.path]?.takeIf { !reread && it.size == file.size && it.lastModified?.toEpochMilliseconds() == file.lastModifiedMs }
            val song = existing ?: localFiles.readTags(file.path)?.toSong(file) ?: return@forEachIndexed
            songs += song
            if (existing == null) {
                val detail = listOfNotNull(song.friendlyArtistName ?: song.albumArtist, song.name).joinToString(" • ")
                emit(FlowEvent.Progress(MessageProgress(ImportPhase.Fetching, Progress(index, files.size), detail)))
            }
        }
        pending.store(listing)
        emit(FlowEvent.Success(songs))
    }.flowOn(Dispatchers.IO)

    override suspend fun songsStored() {
        pending.exchange(null)?.let(localFiles::imported)
    }

    /** Playlist files aren't read on iOS. */
    override fun findPlaylists(existingSongs: List<Song>, knownVersions: Map<String, String>): Flow<FlowEvent<MediaImporter.PlaylistListing, MessageProgress>> = flow {
        emit(FlowEvent.Success(MediaImporter.PlaylistListing(emptyList())))
    }
}

/** The folder id in song [path] (`s2local://<folder id>/...`), or null for a path that isn't a local song's. */
private fun folderIdOf(path: String): String? = path.removePrefix("${IosLocalFiles.SCHEME}://").takeIf { it != path }?.substringBefore('/', "")?.ifEmpty { null }

/**
 * As Android's `AudioFile.toSong`: the tags are mapped by the rules Android's TagLib reader uses, an untitled file is named
 * after itself, and its artwork follows its modification time.
 */
internal fun IosLocalTags.toSong(file: IosLocalFileRef): Song {
    val fileName = file.path.substringAfterLast('/')
    val fileTags = ffmpegPropertyMap(tags.map { it.key to it.value }).toFileTags()
    return Song(
        id = 0,
        name = fileTags.title ?: fileName.substringBeforeLast('.'),
        albumArtist = fileTags.albumArtist,
        artists = fileTags.artists,
        album = fileTags.album,
        track = fileTags.track,
        disc = fileTags.disc,
        duration = durationMs?.toInt() ?: 0,
        date = fileTags.year?.toIntOrNull()?.let { LocalDate(it, 1, 1) },
        genres = fileTags.genres,
        path = file.path,
        size = file.size,
        mimeType = mimeTypeOf(fileName),
        lastModified = Instant.fromEpochMilliseconds(file.lastModifiedMs),
        lastPlayed = null,
        lastCompleted = null,
        playCount = 0,
        playbackPosition = 0,
        blacklisted = false,
        mediaProvider = MediaProviderType.Shuttle,
        replayGainTrack = fileTags.replayGainTrack,
        replayGainAlbum = fileTags.replayGainAlbum,
        lyrics = fileTags.lyrics,
        grouping = fileTags.grouping,
        bitRate = bitRate,
        bitDepth = bitDepth,
        sampleRate = sampleRate,
        channelCount = channelCount,
        audioCodec = codec,
        artworkVersion = file.lastModifiedMs.toString(),
        albumArtists = fileTags.albumArtists,
        artistsTag = fileTags.artistsTag,
        artistDisplay = fileTags.artistDisplay,
        compilation = fileTags.compilation,
        mbTrackId = fileTags.mbTrackId,
        mbAlbumId = fileTags.mbAlbumId,
        mbReleaseGroupId = fileTags.mbReleaseGroupId,
        mbArtistIds = fileTags.mbArtistIds,
        mbAlbumArtistIds = fileTags.mbAlbumArtistIds
    )
}

private fun mimeTypeOf(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase()) {
    "mp3" -> "audio/mpeg"
    "m4a", "m4b", "mp4", "aac" -> "audio/mp4"
    "flac" -> "audio/flac"
    "ogg", "oga" -> "audio/ogg"
    "opus" -> "audio/opus"
    "wav" -> "audio/wav"
    "aif", "aiff", "aifc" -> "audio/aiff"
    "mka" -> "audio/x-matroska"
    else -> "audio/*"
}
