package com.simplecityapps.playback.lyrics

import com.simplecityapps.playback.settings.PlaybackSettings
import com.simplecityapps.shuttle.di.IoDispatcher
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.KeyValueStore
import com.simplecityapps.shuttle.ui.actions.OnlineLyricsSource
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Lyrics from LRCLIB for a song with none of its own. Looks only while [enabled] (it sends the song's artist, title,
 * album and length to a third party), answers from [cache] when it can, and caches "not found". A failed request is
 * not cached: the next view tries again.
 */
class OnlineLyrics(
    override val enabled: Flow<Boolean>,
    private val cache: LyricsCache,
    private val client: LrclibClient,
) : OnlineLyricsSource {
    override suspend fun lookup(song: Song): String? {
        if (!enabled.first()) return null
        when (val cached = cache.get(song.id)) {
            is LyricsCache.Entry.Found -> return cached.text
            LyricsCache.Entry.NotFound -> return null
            null -> Unit
        }
        val title = song.name?.takeIf { it.isNotBlank() } ?: return null
        val artist = (song.artistDisplay ?: song.artists.firstOrNull() ?: song.albumArtist)?.takeIf { it.isNotBlank() } ?: return null
        val text = try {
            client.find(LyricsQuery(artist, title, song.album, song.duration / 1000))?.displayText()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return null
        }
        cache.put(song.id, text)
        return text
    }
}

/** [OnlineLyrics] wired to the app: the Playback setting, the preferences store and a Ktor client. */
@OptIn(ExperimentalTime::class)
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class AppOnlineLyrics(
    private val settings: PlaybackSettings,
    store: KeyValueStore,
    http: LrclibHttp,
    @IoDispatcher io: CoroutineDispatcher,
) : OnlineLyricsSource {
    override val enabled: Flow<Boolean> get() = settings.fetchLyricsOnline.flow

    private val delegate by lazy {
        OnlineLyrics(
            enabled = enabled,
            cache = LyricsCache(store, io) { Clock.System.now().toEpochMilliseconds() },
            client = LrclibClient(http.client),
        )
    }

    override suspend fun lookup(song: Song): String? = delegate.lookup(song)
}
