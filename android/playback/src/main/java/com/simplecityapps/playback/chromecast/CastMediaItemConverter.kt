package com.simplecityapps.playback.chromecast

import android.content.Context
import android.net.Uri
import android.net.wifi.WifiManager
import androidx.media3.cast.MediaItemConverter
import androidx.media3.common.MediaItem
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.common.images.WebImage
import com.simplecityapps.playback.queue.queueEntry
import com.simplecityapps.playback.queue.queueEntryOrNull
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONObject

/**
 * Turns queue items into what a Cast receiver loads, and back. Every song streams from the phone's [HttpServer]: a
 * local song as its file, a remote-provider song as a redirect to its server's stream, resolved when the receiver
 * fetches it (see [CastService.getAudio]), so sending a queue never waits on a server.
 *
 * Each entry's content id is its stream URL with the entry's uid as the fragment, which HTTP never sends: Media3 keys
 * the items it sent by content id, so two entries for the same song must differ. The uid also rides in the custom
 * data, and an item the receiver reports back is the entry's own [MediaItem] (tagged with its queue entry) for as
 * long as it was last sent.
 */
class CastMediaItemConverter(
    /** The phone's address on the network the receiver streams from. */
    private val hostAddress: () -> String,
    /** Stands in for a missing artist, album or title. */
    private val unknown: String
) : MediaItemConverter {
    private val sent = ConcurrentHashMap<Long, MediaItem>()

    override fun toMediaQueueItem(mediaItem: MediaItem): MediaQueueItem {
        val entry = mediaItem.queueEntry
        val song = entry.song
        sent[entry.uid] = mediaItem

        val host = hostAddress()
        val metadata =
            MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK).apply {
                putString(MediaMetadata.KEY_ARTIST, song.friendlyArtistName ?: unknown)
                putString(MediaMetadata.KEY_ALBUM_TITLE, song.album ?: unknown)
                putString(MediaMetadata.KEY_TITLE, song.name ?: unknown)
                addImage(WebImage(Uri.parse(artworkUrl(host, song.id))))
            }
        val url = audioUrl(host, song.id)
        val mediaInfo =
            MediaInfo.Builder("$url#${entry.uid}")
                .setContentUrl(url)
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setContentType(song.mimeType)
                .setStreamDuration(song.duration.toLong())
                .setMetadata(metadata)
                .setCustomData(JSONObject().put(KEY_UID, entry.uid))
                .build()
        return MediaQueueItem.Builder(mediaInfo).build()
    }

    /**
     * The entry's own item, if it's among those last sent; else (an item another sender queued) one that plays its
     * stream, with no queue entry.
     */
    override fun toMediaItem(mediaQueueItem: MediaQueueItem): MediaItem {
        val mediaInfo = mediaQueueItem.media
        val uid = mediaInfo?.customData?.optLong(KEY_UID, NO_UID)?.takeIf { it != NO_UID }
        uid?.let(sent::get)?.let { return it }
        return MediaItem.Builder()
            .setMediaId(mediaInfo?.contentId ?: MediaItem.DEFAULT_MEDIA_ID)
            .setUri(mediaInfo?.contentUrl ?: mediaInfo?.contentId)
            .build()
    }

    /** Forgets every sent item but those in [items], before a new queue is sent. */
    fun retainOnly(items: List<MediaItem>) {
        val keep = items.mapNotNullTo(HashSet()) { it.queueEntryOrNull?.uid }
        sent.keys.retainAll(keep)
    }

    companion object {
        const val PORT = 5000
        private const val KEY_UID = "uid"
        private const val NO_UID = Long.MIN_VALUE

        fun audioUrl(
            host: String,
            songId: Long
        ) = "http://$host:$PORT/songs/$songId/audio"

        fun artworkUrl(
            host: String,
            songId: Long
        ) = "http://$host:$PORT/songs/$songId/artwork"

        /** The dotted form of a [WifiManager] IPv4 address, whose first octet is its lowest byte. */
        fun formatIpAddress(address: Int): String = "%d.%d.%d.%d".format(
            address and 0xFF,
            address shr 8 and 0xFF,
            address shr 16 and 0xFF,
            address shr 24 and 0xFF
        )

        /** The phone's Wi-Fi address, read when an item is sent, as it changes when the phone changes network. */
        @Suppress("DEPRECATION")
        fun wifiAddress(context: Context): () -> String {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            return { formatIpAddress(wifiManager.connectionInfo.ipAddress) }
        }
    }
}
