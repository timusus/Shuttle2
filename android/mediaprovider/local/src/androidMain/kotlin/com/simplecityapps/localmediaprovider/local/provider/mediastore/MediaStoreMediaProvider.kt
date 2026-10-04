package com.simplecityapps.localmediaprovider.local.provider.mediastore

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.ext.SdkExtensions
import android.provider.MediaStore
import androidx.core.database.getIntOrNull
import androidx.core.database.getStringOrNull
import com.simplecityapps.localmediaprovider.local.provider.FolderImageReader
import com.simplecityapps.localmediaprovider.local.provider.TagReadGuard
import com.simplecityapps.localmediaprovider.local.provider.localArtworkVersion
import com.simplecityapps.localmediaprovider.local.provider.mountedVolumeRoots
import com.simplecityapps.localmediaprovider.local.provider.splitArtistTag
import com.simplecityapps.localmediaprovider.local.provider.unmountedRoots
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
import kotlin.math.abs
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import timber.log.Timber

class MediaStoreMediaProvider(
    private val context: Context,
    private val tagReader: MediaStoreTagReader,
    private val preferenceManager: GeneralPreferenceManager,
    // Takes the reads a crash interrupted before tags are read again, so a file that crashes TagLib is left unread
    private val tagReadGuard: TagReadGuard
) : MediaProvider {
    override val type = MediaProviderType.MediaStore

    @Volatile
    override var unreadableRoots: Set<String> = emptySet()
        private set

    // Songs

    override fun findSongs(existingSongs: List<Song>): Flow<FlowEvent<List<Song>, MessageProgress>> = flow {
        tagReadGuard.recover(type)
        val rawSongs = mutableListOf<Song>()
        val projection =
            mediaStoreSongProjection(
                hasDiscNumber = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
                hasBitsPerSample = hasBitsPerSampleColumn()
            )
        // Without a listing, failing keeps the library as it was: an empty one would remove every song
        val songCursor =
            try {
                context.contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection.toTypedArray(),
                    "${MediaStore.Audio.Media.IS_MUSIC}=1 OR ${MediaStore.Audio.Media.IS_PODCAST}=1",
                    null,
                    null
                )
            } catch (e: SecurityException) {
                Timber.e(e, "Failed to query MediaStore for songs")
                null
            }
        if (songCursor == null) {
            emit(FlowEvent.Failure(context.getString(com.simplecityapps.mediaprovider.R.string.media_import_error)))
            return@flow
        }

        songCursor.use {
            val folderImageReader = FolderImageReader(sharedStorageListsImages = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
            val discNumberColumnIndex =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    songCursor.getColumnIndex(MediaStore.Audio.Media.DISC_NUMBER)
                } else {
                    -1
                }
            // -1 when the projection left it out
            val bitsPerSampleColumnIndex = songCursor.getColumnIndex(BITS_PER_SAMPLE)
            while (currentCoroutineContext().isActive && songCursor.moveToNext()) {
                val rawTrack =
                    songCursor.getInt(songCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK))
                val discNumberColumnValue =
                    if (discNumberColumnIndex != -1) songCursor.getStringOrNull(discNumberColumnIndex) else null
                val (disc, track) = decodeDiscTrack(rawTrack, discNumberColumnValue)
                val path = songCursor.getString(songCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA))
                val lastModified = songCursor.getLong(songCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)) * 1000

                // MediaStore has no column for the ARTISTS or ALBUMARTISTS multi-value tags or the MusicBrainz ids, its
                // COMPILATION column exists only from API 30, and its ARTIST_ID and ALBUM_ID are its own row ids rather than
                // identities, so none of them is taken from the cursor: withFileTags reads them from the file. A file TagLib
                // can't read keeps only the ARTIST credit (artistDisplay), and the rest stay empty.
                val artist = songCursor.getStringOrNull(songCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST))
                val song =
                    Song(
                        id = 0,
                        name =
                            songCursor.getStringOrNull(
                                songCursor.getColumnIndexOrThrow(
                                    MediaStore.Audio.Media.TITLE
                                )
                            ),
                        artists = artist?.let(::splitArtistTag).orEmpty(),
                        albumArtist = songCursor.getStringOrNull(songCursor.getColumnIndex("album_artist")),
                        album =
                            songCursor.getStringOrNull(
                                songCursor.getColumnIndexOrThrow(
                                    MediaStore.Audio.Media.ALBUM
                                )
                            ),
                        track = track,
                        disc = disc,
                        duration = songCursor.getInt(songCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)),
                        date = songCursor.getIntOrNull(songCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR))?.let { LocalDate(it, 1, 1) },
                        genres = emptyList(),
                        path = path,
                        size = songCursor.getLong(songCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)),
                        mimeType = songCursor.getString(songCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)),
                        lastModified = Instant.fromEpochMilliseconds(lastModified),
                        lastPlayed = null,
                        lastCompleted = null,
                        playCount = 0,
                        playbackPosition = 0,
                        blacklisted = false,
                        externalId =
                            songCursor.getLong(
                                songCursor.getColumnIndexOrThrow(
                                    MediaStore.Audio.Media._ID
                                )
                            ).toString(),
                        mediaProvider = type,
                        lyrics = null,
                        grouping = null,
                        bitRate = null,
                        bitDepth =
                            mediaStoreBitDepth(
                                mimeType = songCursor.getString(songCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)),
                                bitsPerSample = if (bitsPerSampleColumnIndex != -1) songCursor.getIntOrNull(bitsPerSampleColumnIndex) else null
                            ),
                        sampleRate = null,
                        channelCount = null,
                        artworkVersion = localArtworkVersion(lastModified, folderImageReader.imagesNear(path)),
                        artistDisplay = artist
                    )
                rawSongs.add(song)
            }
        }

        // After the listing, so a volume unmounted while it ran counts too: MediaStore leaves its songs out until it's back
        unreadableRoots = unmountedRoots(existingSongs.map { song -> song.path }, mountedVolumeRoots(context))

        val songs = mutableListOf<Song>()
        // The importer records the new version once this import's result is stored, so one cancelled part way through
        // reads every file again next time. Only this source's version: another failing doesn't make it read them again
        val backfillFileTags = preferenceManager.songTagsOutdated(type)
        rawSongs
            .withFileTags(existingSongs, tagReader, readUnchanged = backfillFileTags)
            .collectIndexed { index, song ->
                songs.add(song)
                emit(
                    FlowEvent.Progress(
                        MessageProgress(
                            phase = ImportPhase.Fetching,
                            detail =
                                listOf(
                                    song.friendlyArtistName ?: song.albumArtist,
                                    song.name
                                ).joinToString(" • "),
                            progress = Progress(index + 1, rawSongs.size)
                        )
                    )
                )
            }

        // Each genre's members by song id, gathered first: matching each member against every song is quadratic in a large library
        val genresBySongId = mutableMapOf<String, MutableList<String>>()
        context.contentResolver.query(
            MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME),
            null,
            null,
            null
        )?.use { genreCursor ->
            while (currentCoroutineContext().isActive && genreCursor.moveToNext()) {
                val id =
                    genreCursor.getLong(genreCursor.getColumnIndexOrThrow(MediaStore.Audio.Genres._ID))
                val genre =
                    genreCursor.getString(genreCursor.getColumnIndexOrThrow(MediaStore.Audio.Genres.NAME))
                context.contentResolver.query(
                    MediaStore.Audio.Genres.Members.getContentUri("external", id),
                    arrayOf(MediaStore.Audio.Media._ID),
                    null,
                    null,
                    null
                )?.use { genreSongCursor ->
                    while (currentCoroutineContext().isActive && genreSongCursor.moveToNext()) {
                        val songId =
                            genreSongCursor.getLong(
                                genreSongCursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                            ).toString()
                        genresBySongId.getOrPut(songId) { mutableListOf() } += genre
                    }
                }
            }
        }
        emit(FlowEvent.Success(songs.withGenres(genresBySongId)))
    }

    data class MediaStoreSong(
        val playOrder: Long,
        val title: String?,
        val album: String?,
        val artist: String?,
        val albumArtist: String?,
        val duration: Int,
        val year: Int?,
        val track: Int,
        val mimeType: String,
        val path: String
    )

    // Playlists

    override fun findPlaylists(existingSongs: List<Song>): Flow<FlowEvent<List<MediaImporter.PlaylistUpdateData>, MessageProgress>> = flow {
        val mediaStorePlaylists = findMediaStorePlaylists().toList()
        val updates =
            mediaStorePlaylists.mapIndexed { i, mediaStorePlaylist ->
                val mediaStoreSongs = findSongsForMediaStorePlaylist(mediaStorePlaylist.id)

                // Associate Media Store songs with Shuttle's songs
                val matchingSongs =
                    mediaStoreSongs.mapNotNull { mediaStoreSong ->
                        existingSongs.firstOrNull { existingSong -> existingSong.matchesPlaylistEntry(mediaStoreSong) }
                    }

                val updateData =
                    MediaImporter.PlaylistUpdateData(
                        type,
                        mediaStorePlaylist.name,
                        matchingSongs,
                        mediaStorePlaylist.id.toString()
                    )
                emit(FlowEvent.Progress(MessageProgress(ImportPhase.Fetching, Progress(i, mediaStorePlaylists.size))))
                updateData
            }

        emit(FlowEvent.Success(updates.toList()))
    }

    private suspend fun findSongsForMediaStorePlaylist(mediaStorePlaylistId: Long): List<MediaStoreSong> {
        return withContext(Dispatchers.IO) {
            val songs = mutableListOf<MediaStoreSong>()

            val cursor =
                context.contentResolver.query(
                    MediaStore.Audio.Playlists.Members.getContentUri("external", mediaStorePlaylistId),
                    arrayOf(
                        MediaStore.Audio.Playlists.Members.TITLE,
                        MediaStore.Audio.Playlists.Members.ALBUM,
                        MediaStore.Audio.Playlists.Members.ARTIST,
                        MediaStore.Audio.Playlists.Members.DURATION,
                        MediaStore.Audio.Playlists.Members.YEAR,
                        MediaStore.Audio.Media.TRACK,
                        MediaStore.Audio.Playlists.Members.MIME_TYPE,
                        MediaStore.Audio.Playlists.Members.DATA,
                        MediaStore.Audio.Playlists.Members.PLAY_ORDER,
                        "album_artist"
                    ),
                    null,
                    null,
                    MediaStore.Audio.Playlists.Members.DEFAULT_SORT_ORDER
                )

            cursor?.use {
                while (cursor.moveToNext()) {
                    if (!isActive) {
                        return@use
                    }

                    val rawTrack =
                        cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK))
                    val track = decodeDiscTrack(rawTrack, discNumberColumnValue = null).track

                    songs.add(
                        MediaStoreSong(
                            playOrder = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.PLAY_ORDER)),
                            title = cursor.getStringOrNull(cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.TITLE)),
                            album = cursor.getStringOrNull(cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.ALBUM)),
                            artist = cursor.getStringOrNull(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)),
                            albumArtist = cursor.getStringOrNull(cursor.getColumnIndex("album_artist")),
                            duration = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.DURATION)),
                            year = cursor.getIntOrNull(cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.YEAR)),
                            track = track,
                            mimeType = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.MIME_TYPE)),
                            path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.DATA))
                        )
                    )
                }
            }

            songs
        }
    }

    data class MediaStorePlaylist(val id: Long, val name: String)

    private fun findMediaStorePlaylists(): Flow<MediaStorePlaylist> = flow {
        val cursor =
            context.contentResolver.query(
                MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                arrayOf(
                    MediaStore.Audio.Playlists._ID,
                    MediaStore.Audio.Playlists.NAME
                ),
                null,
                null,
                MediaStore.Audio.Playlists.DEFAULT_SORT_ORDER
            )

        cursor?.use {
            while (cursor.moveToNext()) {
                emit(
                    MediaStorePlaylist(
                        cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists._ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.NAME))
                    )
                )
            }
        }
    }
}

/**
 * MediaStore.Audio.Media.BITS_PER_SAMPLE, spelled out so [mediaStoreSongProjection] can name it without a version check
 * of its own: the caller decides whether the column exists ([hasBitsPerSampleColumn]).
 */
private const val BITS_PER_SAMPLE = "bits_per_sample"

/**
 * Whether MediaStore has [BITS_PER_SAMPLE]. The column is new in API 36 and was backported in SDK extension 15 to API
 * 33 to 35 (api-versions.xml: since 36, sdks "33:15"). Querying a column the provider lacks throws, failing the whole
 * scan, so anything older leaves it out.
 */
private fun hasBitsPerSampleColumn(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
    hasBitsPerSampleColumn(Build.VERSION.SDK_INT, SdkExtensions.getExtensionVersion(Build.VERSION_CODES.TIRAMISU))

internal fun hasBitsPerSampleColumn(
    sdkInt: Int,
    tiramisuExtensionVersion: Int
): Boolean = sdkInt >= Build.VERSION_CODES.BAKLAVA || (sdkInt >= Build.VERSION_CODES.TIRAMISU && tiramisuExtensionVersion >= 15)

/** The columns the song scan reads; [hasDiscNumber] (API 30) and [hasBitsPerSample] say which optional ones exist. */
@SuppressLint("InlinedApi")
internal fun mediaStoreSongProjection(
    hasDiscNumber: Boolean,
    hasBitsPerSample: Boolean
): List<String> = buildList {
    add(MediaStore.Audio.Media._ID)
    add(MediaStore.Audio.Media.DATA)
    add(MediaStore.Audio.Media.TITLE)
    add(MediaStore.Audio.Media.ARTIST_ID)
    add(MediaStore.Audio.Media.ARTIST)
    add(MediaStore.Audio.Media.ALBUM_ID)
    add(MediaStore.Audio.Media.ALBUM)
    add(MediaStore.Audio.Media.DURATION)
    add(MediaStore.Audio.Media.SIZE)
    add(MediaStore.Audio.Media.YEAR)
    add(MediaStore.Audio.Media.TRACK)
    add(MediaStore.Audio.Media.DATE_MODIFIED)
    add(MediaStore.Audio.Media.IS_PODCAST)
    add(MediaStore.Audio.Media.BOOKMARK)
    add(MediaStore.Audio.Media.MIME_TYPE)
    add("album_artist")
    if (hasDiscNumber) add(MediaStore.Audio.Media.DISC_NUMBER)
    if (hasBitsPerSample) add(BITS_PER_SAMPLE)
}

private val losslessMimeSubtypes =
    setOf("flac", "x-flac", "wav", "x-wav", "wave", "vnd.wave", "aiff", "x-aiff", "ape", "x-ape", "wavpack", "x-wavpack")

/**
 * The bit depth MediaStore reports (where it has the column), kept only for a lossless format. The MIME type is all it has to go
 * on, so an ambiguous one like audio/mp4 (AAC or ALAC) is treated as lossy, and a lossy file's 16 or 32 is dropped.
 */
internal fun mediaStoreBitDepth(
    mimeType: String?,
    bitsPerSample: Int?
): Int? = bitsPerSample?.takeIf { it > 0 && mimeType?.lowercase()?.substringAfter('/') in losslessMimeSubtypes }

/** Each song with the MediaStore genres [genresBySongId] holds for its id ([Song.externalId]) added after its own. */
internal fun List<Song>.withGenres(genresBySongId: Map<String, List<String>>): List<Song> = map { song ->
    genresBySongId[song.externalId]?.let { genres -> song.copy(genres = song.genres + genres) } ?: song
}

// We assume two songs are equal, if they have the same title, album, artist & duration. We can't be too specific, as the
// MediaStore scanner may have interpreted some fields differently to Shuttle's built in scanner. MediaStore's artist is the
// raw tag, so it is split like Shuttle's own (#880) and any shared artist counts: one side may list fewer of them.
internal fun Song.matchesPlaylistEntry(mediaStoreSong: MediaStoreMediaProvider.MediaStoreSong): Boolean {
    val mediaStoreArtists = mediaStoreSong.artist?.let(::splitArtistTag).orEmpty()
    return name.equals(mediaStoreSong.title, ignoreCase = true) &&
        album.equals(mediaStoreSong.album, ignoreCase = true) &&
        (artists.any { artist -> mediaStoreArtists.any { it.equals(artist, ignoreCase = true) } } || albumArtist.equals(mediaStoreSong.albumArtist, ignoreCase = true)) &&
        abs(duration - mediaStoreSong.duration) <= 1000 // song duration is within 1 second
}
