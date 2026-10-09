package com.simplecityapps.playback.queue

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import com.simplecityapps.playback.dsp.replaygain.ReplayGain
import com.simplecityapps.playback.dsp.replaygain.replayGain
import com.simplecityapps.playback.engine.SongUriResolver.Companion.isDirect
import com.simplecityapps.shuttle.model.Song

/**
 * One entry in the player's playlist: the tag of its [MediaItem]. [uid] tells two entries for the same song apart,
 * and is the [QueueItem.uid] the queue publishes. [replayGain] is what [com.simplecityapps.playback.dsp.replaygain.ReplayGainAudioProcessor]
 * applies when the entry's stream starts.
 */
data class QueueEntry(
    val uid: Long,
    val song: Song,
    val replayGain: ReplayGain = song.replayGain
) {
    /** The play id its stream URL and its playback reports carry (see [com.simplecityapps.playback.Play]). */
    val playId: String get() = uid.toString()
}

/** A new entry for this song, with a fresh uid. */
fun Song.toQueueEntry(): QueueEntry = QueueEntry(toQueueItem(isCurrent = false).uid, this)

fun QueueEntry.toQueueItem(isCurrent: Boolean): QueueItem = QueueItem(uid, song, isCurrent)

/**
 * The [MediaItem] the player queues for this entry. Its URI is [uri]: a remote song's is resolved to a stream URL only
 * when the player opens it (see [com.simplecityapps.playback.engine.SongUriResolver]). Its cache key is the song's
 * path, which its download is stored under.
 *
 * Its metadata is what the media session shows (the notification, the lock screen, Android Auto). It carries no
 * artwork URI: artwork is loaded by song (see [com.simplecityapps.playback.mediasession.ArtworkBitmapLoader]).
 */
fun QueueEntry.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(song.id.toString())
    .setUri(uri())
    .setCustomCacheKey(song.path)
    .setMimeType(MimeTypes.normalizeMimeType(song.mimeType))
    .setMediaMetadata(song.toMediaMetadata())
    .setTag(this)
    .build()

/** What a media session shows for this song. */
internal fun Song.toMediaMetadata(): MediaMetadata = MediaMetadata.Builder()
    .setTitle(name)
    .setArtist(friendlyArtistName ?: albumArtist)
    .setAlbumTitle(album)
    .setAlbumArtist(albumArtist)
    .setTrackNumber(track)
    .setDiscNumber(disc)
    .setDurationMs(duration.toLong().takeIf { it > 0 })
    .setIsBrowsable(false)
    .setIsPlayable(true)
    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
    .build()

/** The entry [toMediaItem] tagged this item with. Every item in the playlist was built by it. */
val MediaItem.queueEntry: QueueEntry
    get() = checkNotNull(queueEntryOrNull) { "MediaItem $mediaId has no queue entry" }

/**
 * The entry this item is tagged with, if any. A Cast receiver's playlist can hold items with none: one it hasn't
 * reported in full yet, or one another sender queued.
 */
val MediaItem.queueEntryOrNull: QueueEntry?
    get() = localConfiguration?.tag as? QueueEntry

/** A path is either an absolute file path or a URI. */
fun Song.uri(): Uri = if (path.startsWith("/")) Uri.fromFile(java.io.File(path)) else Uri.parse(path)

/**
 * The URI the player opens for this entry: the song's own, and for a remote song's `jellyfin://`, `emby://`, `plex://`
 * or `subsonic://` URI one per entry, with its [QueueEntry.playId] as the fragment, so each copy resolves its own stream.
 */
fun QueueEntry.uri(): Uri {
    val uri = song.uri()
    return if (uri.isDirect()) uri else uri.buildUpon().fragment(playId).build()
}
