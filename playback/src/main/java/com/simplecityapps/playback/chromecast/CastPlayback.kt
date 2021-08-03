package com.simplecityapps.playback.chromecast

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.net.wifi.WifiManager
import com.google.android.gms.cast.*
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.images.WebImage
import com.simplecityapps.core.R
import com.simplecityapps.mediaprovider.MediaInfoProvider
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.playback.Playback
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.queue.QueueManager
import org.json.JSONException
import timber.log.Timber

@SuppressLint("WifiManagerPotentialLeak")
class CastPlayback(
    private val context: Context,
    private val castSession: CastSession,
    private val mediaInfoProvider: MediaInfoProvider
) : Playback {

    override var callback: Playback.Callback? = null

    override var isReleased: Boolean = true

    private var currentPosition = 0

    // remoteMediaClient.isPlaying() returns true momentarily after it is paused, so we use this to track whether it really is playing, based on calls to play(), pause(), stop() and load()
    private var isMeantToBePlaying = false

    private var playerState: Int? = MediaStatus.PLAYER_STATE_UNKNOWN

    private val remoteMediaClientCallback = CastMediaClientCallback()

    init {
        castSession.remoteMediaClient?.registerCallback(remoteMediaClientCallback)
    }

    override suspend fun load(current: Song, next: Song?, seekPosition: Int, completion: (Result<Any?>) -> Unit) {
        Timber.v("load(current: ${current.name}|${current.mimeType})")
        isReleased = false

        callback?.onPlaybackStateChanged(PlaybackState.Loading)

        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK)
        metadata.putString(MediaMetadata.KEY_ARTIST, current.friendlyArtistName ?: context.getString(R.string.unknown))
        metadata.putString(MediaMetadata.KEY_ALBUM_TITLE, current.album ?: context.getString(R.string.unknown))
        metadata.putString(MediaMetadata.KEY_TITLE, current.name ?: context.getString(R.string.unknown))
        metadata.addImage(WebImage(Uri.parse("http://$ipAddress:5000/songs/${current.id}/artwork")))

        var path = "http://$ipAddress:5000/songs/${current.id}/audio"

        val mediaInfo = mediaInfoProvider.getMediaInfo(current)
        if (mediaInfo.isRemote) {
            path = mediaInfo.path.toString()
        }
        val castMediaInfo = MediaInfo.Builder(path)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(mediaInfo.mimeType)
            .setStreamDuration(current.duration.toLong())
            .setMetadata(metadata)
            .build()

        castSession.remoteMediaClient?.load(
            castMediaInfo, MediaLoadOptions.Builder()
                .setPlayPosition(seekPosition.toLong())
                .setAutoplay(false)
                .build()
        )
            ?.setResultCallback { result ->
                if (result.status.isSuccess) {
                    completion(Result.success(null))
                } else {
                    completion(Result.failure(Error("Remote Media Client failed to load media. ${result.status.statusMessage}")))
                }
            } ?: completion(Result.failure(Error("Remote Media Client failed to load media. (RemoteMediaClient null)")))
    }

    override suspend fun loadNext(song: Song?) {
        // Nothing to do
    }

    override fun play() {
        isMeantToBePlaying = true

        if (castSession.remoteMediaClient?.hasMediaSession() == true && castSession.remoteMediaClient?.isPlaying == false) {
            currentPosition = castSession.remoteMediaClient?.approximateStreamPosition?.toInt() ?: 0
            castSession.remoteMediaClient?.play() ?: run {
                Timber.e("Failed to play - remote media client null")
            }
        } else {
            Timber.e("play() failed. HasMediaSession ${castSession.remoteMediaClient?.hasMediaSession()}")
        }
    }

    override fun pause() {
        isMeantToBePlaying = false
        try {
            if (castSession.remoteMediaClient?.hasMediaSession() == true) {
                currentPosition = castSession.remoteMediaClient?.approximateStreamPosition?.toInt() ?: 0
                castSession.remoteMediaClient?.pause() ?: run {
                    Timber.e("Failed to pause - remote media client null")
                }
            } else {
                Timber.e("pause() failed. No remote media session")
            }
        } catch (e: JSONException) {
            Timber.e(e, "Exception pausing cast playback")
        }
    }

    override fun release() {
        isMeantToBePlaying = false

        if (castSession.remoteMediaClient?.hasMediaSession() == true) {
            currentPosition = castSession.remoteMediaClient?.approximateStreamPosition?.toInt() ?: 0
            castSession.remoteMediaClient?.stop() ?: run {
                Timber.e("Failed to stop - remote media client null")
            }
        }

        castSession.remoteMediaClient?.unregisterCallback(remoteMediaClientCallback)

        isReleased = true
    }

    override fun playBackState(): PlaybackState {
        return if (castSession.remoteMediaClient?.isPlaying == true || isMeantToBePlaying) {
            PlaybackState.Playing
        } else {
            PlaybackState.Paused
        }
    }

    override fun seek(position: Int) {
        currentPosition = position
        try {
            if (castSession.remoteMediaClient?.hasMediaSession() == true) {
                castSession.remoteMediaClient?.seek(
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

    override fun getProgress(): Int? {
        if (castSession.remoteMediaClient?.approximateStreamPosition == 0L) {
            return if (currentPosition <= getDuration() ?: 0) currentPosition else 0
        }
        return castSession.remoteMediaClient?.approximateStreamPosition?.toInt()
    }

    override fun getDuration(): Int? {
        return castSession.remoteMediaClient?.streamDuration?.toInt()
    }

    override fun setVolume(volume: Float) {
        // Nothing to do
    }

    override fun getResumeWhenSwitched(oldPlayback: Playback): Boolean {
        return true
    }

    override fun setRepeatMode(repeatMode: QueueManager.RepeatMode) {

    }

    override fun setPlaybackSpeed(multiplier: Float) {
        castSession.remoteMediaClient?.setPlaybackRate(multiplier.toDouble())
    }

    override fun getPlaybackSpeed(): Float {
        return castSession.remoteMediaClient?.mediaStatus?.playbackRate?.toFloat() ?: 1f
    }

    override fun updateLastKnownStreamPosition() {
        super.updateLastKnownStreamPosition()

        getProgress()?.let { position ->
            currentPosition = position
        }
    }

    override fun respondsToAudioFocus(): Boolean {
        return false
    }

    private fun updatePlaybackState() {
        val playerState = castSession.remoteMediaClient?.playerState
        // Convert the remote playback states to media playback states.
        if (playerState != this.playerState) when (playerState) {
            MediaStatus.PLAYER_STATE_IDLE -> {
                val idleReason = castSession.remoteMediaClient?.idleReason ?: MediaStatus.IDLE_REASON_NONE
                Timber.v("onRemoteMediaPlayerStatusUpdated... IDLE, reason: $idleReason")
                if (idleReason == MediaStatus.IDLE_REASON_FINISHED) {
                    currentPosition = 0
                    callback?.onTrackEnded(false)
                }
            }
            MediaStatus.PLAYER_STATE_PLAYING -> {
                Timber.v("onRemoteMediaPlayerStatusUpdated ${playerState.playerStateToString()}")
                callback?.onPlaybackStateChanged(PlaybackState.Playing)
            }
            MediaStatus.PLAYER_STATE_PAUSED -> {
                Timber.v("onRemoteMediaPlayerStatusUpdated ${playerState.playerStateToString()}")
                callback?.onPlaybackStateChanged(PlaybackState.Paused)
            }
            MediaStatus.PLAYER_STATE_LOADING -> {
                Timber.v("onRemoteMediaPlayerStatusUpdated ${playerState.playerStateToString()}")
                callback?.onPlaybackStateChanged(PlaybackState.Loading)
            }
            MediaStatus.PLAYER_STATE_BUFFERING -> Timber.v("onRemoteMediaPlayerStatusUpdated ${playerState.playerStateToString()}")
            MediaStatus.PLAYER_STATE_UNKNOWN -> Timber.v("onRemoteMediaPlayerStatusUpdated ${playerState.playerStateToString()}")
            else -> Timber.v("onRemoteMediaPlayerStatusUpdated State default $playerState")
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
            Timber.v("RemoteMediaClient.onMetadataUpdated ${castSession.remoteMediaClient?.mediaInfo}")
        }

        override fun onStatusUpdated() {
            Timber.v("RemoteMediaClient.onStatusUpdated: ${castSession.remoteMediaClient?.playerState?.playerStateToString()}")
            updatePlaybackState()
        }
    }
}

fun Int.playerStateToString(): String {
    return when (this) {
        MediaStatus.PLAYER_STATE_IDLE -> "IDLE"
        MediaStatus.PLAYER_STATE_PLAYING -> "PLAYING"
        MediaStatus.PLAYER_STATE_PAUSED -> "PAUSED"
        MediaStatus.PLAYER_STATE_LOADING -> "LOADING"
        MediaStatus.PLAYER_STATE_BUFFERING -> "BUFFERING"
        MediaStatus.PLAYER_STATE_UNKNOWN -> "UNKNOWN"
        else -> "Unknown"
    }
}