package com.simplecityapps.playback.chromecast

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.net.wifi.WifiManager
import com.google.android.gms.cast.*
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.images.WebImage
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.playback.Playback
import org.json.JSONException
import timber.log.Timber

@SuppressLint("WifiManagerPotentialLeak")
class CastPlayback(
    private val context: Context,
    private val castSession: CastSession
) : Playback {

    override var callback: Playback.Callback? = null

    override var isReleased: Boolean = true

    private var currentPosition = 0

    // remoteMediaClient.isPlaying() returns true momentarily after it is paused, so we use this to track whether it really is playing, based on calls to play(), pause(), stop() and load()
    private var isMeantToBePlaying = false

    private var playerState: Int? = MediaStatus.PLAYER_STATE_UNKNOWN;

    private val remoteMediaClientCallback = CastMediaClientCallback()

    override fun load(current: Song, next: Song?, completion: (Result<Any?>) -> Unit) {
        isReleased = false
        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK)
        metadata.putString(MediaMetadata.KEY_ALBUM_ARTIST, current.albumArtistName)
        metadata.putString(MediaMetadata.KEY_ALBUM_TITLE, current.albumName)
        metadata.putString(MediaMetadata.KEY_TITLE, current.name)
        metadata.addImage(WebImage(Uri.parse("http://$ipAddress:5000/songs/${current.id}/artwork")))

        val mediaInfo = MediaInfo.Builder("http://$ipAddress:5000/songs/${current.id}/audio")
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType("audio/*")
            .setMetadata(metadata)
            .build()

        castSession.remoteMediaClient.registerCallback(remoteMediaClientCallback)

        castSession.remoteMediaClient.load(
            mediaInfo, MediaLoadOptions.Builder()
                .setAutoplay(false)
                .build()
        )

        completion(Result.success(null))
    }

    override fun loadNext(song: Song?) {
        // Nothing to do
    }

    override fun play() {
        isMeantToBePlaying = true

        if (castSession.remoteMediaClient.hasMediaSession() && !castSession.remoteMediaClient.isPlaying) {
            currentPosition = castSession.remoteMediaClient.approximateStreamPosition.toInt()
            castSession.remoteMediaClient.play()
        } else {
            Timber.e("play() failed. HasMediaSession ${castSession.remoteMediaClient.hasMediaSession()}")
        }
    }

    override fun pause() {
        isMeantToBePlaying = false
        try {
            if (castSession.remoteMediaClient.hasMediaSession()) {
                currentPosition = castSession.remoteMediaClient.approximateStreamPosition.toInt()
                castSession.remoteMediaClient.pause()
            } else {
                Timber.e("puase() failed. No remote media session")
            }
        } catch (e: JSONException) {
            Timber.e(e, "Exception pausing cast playback")
        }
    }

    override fun release() {
        isMeantToBePlaying = false

        if (castSession.remoteMediaClient.hasMediaSession()) {
            currentPosition = castSession.remoteMediaClient.approximateStreamPosition.toInt()
            castSession.remoteMediaClient.stop()
        }

        castSession.remoteMediaClient.unregisterCallback(remoteMediaClientCallback)

        isReleased = true
    }

    override fun isPlaying(): Boolean {
        return castSession.remoteMediaClient.isPlaying || isMeantToBePlaying
    }

    override fun seek(position: Int) {
        currentPosition = position
        try {
            if (castSession.remoteMediaClient.hasMediaSession()) {
                castSession.remoteMediaClient.seek(
                    MediaSeekOptions.Builder()
                        .setPosition(position.toLong())
                        .build()
                )
            } else {
                Timber.e("Seek failed, no remote media session")
            }
        } catch (e: JSONException) {
            Timber.e(e, "Seek failed")
        }
    }

    override fun getPosition(): Int? {
        if (castSession.remoteMediaClient.approximateStreamPosition == 0L) {
            return if (currentPosition <= getDuration() ?: 0) currentPosition else 0
        }
        return castSession.remoteMediaClient.approximateStreamPosition.toInt()
    }

    override fun getDuration(): Int? {
        return castSession.remoteMediaClient.streamDuration.toInt()
    }

    override fun setVolume(volume: Float) {
        // Nothing to do
    }

    private fun updatePlaybackState() {
        val playerState = castSession.remoteMediaClient.playerState
        if (playerState != this.playerState) {
            // Convert the remote playback states to media playback states.
            when (playerState) {
                MediaStatus.PLAYER_STATE_IDLE -> {
                    val idleReason = castSession.remoteMediaClient.idleReason
                    Timber.v("onRemoteMediaPlayerStatusUpdated... IDLE, reason: $idleReason")
                    if (idleReason == MediaStatus.IDLE_REASON_FINISHED) {
                        currentPosition = 0
                        callback?.onPlaybackComplete(false)
                    }
                }
                MediaStatus.PLAYER_STATE_PLAYING -> {
                    Timber.v("onRemoteMediaPlayerStatusUpdated.. PLAYING")
                    callback?.onPlayStateChanged(true)
                }
                MediaStatus.PLAYER_STATE_PAUSED -> {
                    Timber.v("onRemoteMediaPlayerStatusUpdated.. PAUSED")
                    callback?.onPlayStateChanged(false)
                }
                else -> {
                    Timber.v("State default : $playerState")
                }
            }
        }
        this.playerState = playerState
    }

    private val ipAddress: String by lazy {
        val i = (context.getSystemService(Context.WIFI_SERVICE) as WifiManager).connectionInfo.ipAddress
        val arrayOfObject = arrayOfNulls<Any>(4)
        arrayOfObject[0] = i and 0xFF
        arrayOfObject[1] = 0xFF and (i shr 8)
        arrayOfObject[2] = 0xFF and (i shr 16)
        arrayOfObject[3] = 0xFF and (i shr 24)
        String.format("%d.%d.%d.%d", *arrayOfObject)
    }


    private inner class CastMediaClientCallback : RemoteMediaClient.Callback() {

        override fun onMetadataUpdated() {
            Timber.v("RemoteMediaClient.onMetadataUpdated")
        }

        override fun onStatusUpdated() {
            Timber.v("RemoteMediaClient.onStatusUpdated")
            updatePlaybackState()
        }
    }
}

