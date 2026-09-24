package com.simplecityapps.playback.queue

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import com.simplecityapps.playback.dsp.replaygain.ReplayGain
import com.simplecityapps.playback.dsp.replaygain.replayGain
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
)

/** A new entry for this song, with a fresh uid. */
fun Song.toQueueEntry(): QueueEntry = QueueEntry(toQueueItem(isCurrent = false).uid, this)

fun QueueEntry.toQueueItem(isCurrent: Boolean): QueueItem = QueueItem(uid, song, isCurrent)

/**
 * The [MediaItem] the player queues for this entry. Its URI is the song's own path: a remote song's `jellyfin://`,
 * `emby://` or `plex://` URI is resolved to a stream URL only when the player opens it
 * (see [com.simplecityapps.playback.engine.SongUriResolver]).
 */
fun QueueEntry.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(song.id.toString())
    .setUri(song.uri())
    .setMimeType(MimeTypes.normalizeMimeType(song.mimeType))
    .setTag(this)
    .build()

/** The entry [toMediaItem] tagged this item with. Every item in the playlist was built by it. */
val MediaItem.queueEntry: QueueEntry
    get() = checkNotNull(localConfiguration?.tag as? QueueEntry) { "MediaItem $mediaId has no queue entry" }

/** A path is either an absolute file path or a URI. */
fun Song.uri(): Uri = if (path.startsWith("/")) Uri.fromFile(java.io.File(path)) else Uri.parse(path)
