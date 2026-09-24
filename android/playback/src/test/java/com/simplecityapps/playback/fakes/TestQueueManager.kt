package com.simplecityapps.playback.fakes

import androidx.media3.test.utils.TestExoPlayerBuilder
import com.simplecityapps.playback.engine.SongUriResolver
import com.simplecityapps.playback.exoplayer.MediaResolver
import com.simplecityapps.playback.exoplayer.ResolvedMedia
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import kotlin.coroutines.EmptyCoroutineContext
import org.robolectric.RuntimeEnvironment

/**
 * A [QueueManager] over a real, unprepared ExoPlayer on the calling thread's looper, so a Robolectric test can change
 * the queue and read what it publishes without playing anything. Queue entries are built inline.
 */
fun testQueueManager(): QueueManager {
    val resolver = SongUriResolver(MediaResolver { song -> ResolvedMedia(uri = song.path, mimeType = song.mimeType, isRemote = false) })
    val player = TestExoPlayerBuilder(RuntimeEnvironment.getApplication()).build()
    return QueueManager(player, GeneralPreferenceManager(FakeSharedPreferences()), resolver, buildContext = EmptyCoroutineContext)
}
