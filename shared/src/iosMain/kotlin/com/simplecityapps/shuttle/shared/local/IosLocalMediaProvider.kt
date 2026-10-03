package com.simplecityapps.shuttle.shared.local

import com.simplecityapps.mediaprovider.FlowEvent
import com.simplecityapps.mediaprovider.ImportPhase
import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.mediaprovider.MediaProvider
import com.simplecityapps.mediaprovider.MessageProgress
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import dev.zacsweers.metro.Inject
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
 * keeps the song read last time, so a rescan only reads what's new or changed; one that's gone leaves the library.
 *
 * A picked folder that's out of reach (its bookmark won't resolve until it's picked again) keeps its songs, so a
 * moment without access doesn't cost them their history.
 */
@Inject
class IosLocalMediaProvider(
    private val localFiles: IosLocalFiles
) : MediaProvider {
    override val type = MediaProviderType.Shuttle

    override fun findSongs(existingSongs: List<Song>): Flow<FlowEvent<List<Song>, MessageProgress>> = flow {
        val unreachable = localFiles.folders().filterNot { it.hasAccess }.map { folder -> "${IosLocalFiles.SCHEME}://${folder.id}/" }
        val kept = existingSongs.filter { song -> unreachable.any(song.path::startsWith) }
        val existingByPath = existingSongs.associateBy { it.path }

        val files = localFiles.audioFiles()
        val songs = ArrayList<Song>(kept.size + files.size)
        songs += kept
        files.forEachIndexed { index, file ->
            val existing = existingByPath[file.path]?.takeIf { it.size == file.size && it.lastModified?.toEpochMilliseconds() == file.lastModifiedMs }
            val song = existing ?: localFiles.readTags(file.path)?.toSong(file) ?: return@forEachIndexed
            songs += song
            if (existing == null) {
                val detail = listOfNotNull(song.friendlyArtistName ?: song.albumArtist, song.name).joinToString(" • ")
                emit(FlowEvent.Progress(MessageProgress(ImportPhase.Fetching, Progress(index, files.size), detail)))
            }
        }
        emit(FlowEvent.Success(songs))
    }.flowOn(Dispatchers.IO)

    /** Playlist files aren't read on iOS. */
    override fun findPlaylists(existingSongs: List<Song>): Flow<FlowEvent<List<MediaImporter.PlaylistUpdateData>, MessageProgress>> = flow {
        emit(FlowEvent.Success(emptyList()))
    }
}

/** As Android's `AudioFile.toSong`: an untitled file is named after itself, and its artwork follows its modification time. */
internal fun IosLocalTags.toSong(file: IosLocalFileRef): Song {
    val fileName = file.path.substringAfterLast('/')
    return Song(
        id = 0,
        name = title ?: fileName.substringBeforeLast('.'),
        albumArtist = albumArtist,
        artists = artists,
        album = album,
        track = track,
        disc = disc,
        duration = durationMs?.toInt() ?: 0,
        date = year?.let { LocalDate(it, 1, 1) },
        genres = genres,
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
        replayGainTrack = replayGainTrack,
        replayGainAlbum = replayGainAlbum,
        lyrics = lyrics,
        grouping = grouping,
        bitRate = bitRate,
        bitDepth = bitDepth,
        sampleRate = sampleRate,
        channelCount = channelCount,
        audioCodec = codec,
        artworkVersion = file.lastModifiedMs.toString(),
        albumArtists = albumArtists,
        artistsTag = artistsTag,
        artistDisplay = artistDisplay,
        compilation = compilation,
        mbTrackId = mbTrackId,
        mbAlbumId = mbAlbumId,
        mbReleaseGroupId = mbReleaseGroupId,
        mbArtistIds = mbArtistIds,
        mbAlbumArtistIds = mbAlbumArtistIds
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
