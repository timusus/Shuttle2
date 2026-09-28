package com.simplecityapps.shuttle.ui.screens.home

import com.simplecityapps.createAlbum
import com.simplecityapps.createAlbumArtist
import com.simplecityapps.createGenre
import com.simplecityapps.createPlaylist
import com.simplecityapps.fakes.FakePlayHistoryRepository
import com.simplecityapps.fakes.FakePlaylistRepository
import com.simplecityapps.fakes.FakeSuggestionsRepository
import com.simplecityapps.mediaprovider.repository.playhistory.AlbumArtistCompletions
import com.simplecityapps.mediaprovider.repository.playhistory.AlbumCompletions
import com.simplecityapps.mediaprovider.repository.playhistory.ContextDays
import com.simplecityapps.mediaprovider.repository.playhistory.GenrePlays
import com.simplecityapps.mediaprovider.repository.playhistory.RecentContext
import com.simplecityapps.shuttle.model.AlbumArtistGroupKey
import com.simplecityapps.shuttle.model.AlbumGroupKey
import com.simplecityapps.shuttle.model.PlayContext
import com.simplecityapps.shuttle.model.SmartPlaylistId
import com.simplecityapps.shuttle.ui.actions.MediaAction
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone

class HomeSuggestionsTest {
    private val suggestions = FakeSuggestionsRepository()
    private val playHistory = FakePlayHistoryRepository()
    private val playlists = FakePlaylistRepository()
    private val resolve = ResolveHomeItems(suggestions, playlists)

    private val now = Instant.parse("2026-09-22T22:00:00Z")
    private val blue = createAlbum("blue", "joni mitchell")
    private val kidA = createAlbum("kid a", "radiohead")
    private val joni = createAlbumArtist("joni mitchell")
    private val jazz = createGenre("Jazz", songCount = 40)
    private val mix = createPlaylist(id = 7, name = "Mix")

    init {
        suggestions.albums = listOf(blue, kidA)
        suggestions.albumArtists = listOf(joni)
        suggestions.genres = listOf(jazz, createGenre("Polka", songCount = 5))
        playlists.setPlaylists(listOf(mix))
    }

    private fun context(key: AlbumGroupKey?) = PlayContext.Album(key!!)

    @Test
    fun `contexts resolve to items in order, dropping what the library no longer has`() = runTest {
        val items = resolve(
            listOf(
                context(kidA.groupKey),
                PlayContext.Album(AlbumGroupKey("gone", AlbumArtistGroupKey("nobody"))),
                PlayContext.AlbumArtist(joni.groupKey),
                PlayContext.Playlist(7),
                PlayContext.Playlist(8),
                PlayContext.SmartPlaylist(SmartPlaylistId.Favourites),
                PlayContext.UserSmartPlaylist(3),
                PlayContext.Genre("Jazz"),
                context(kidA.groupKey),
            ),
        )

        items shouldBe listOf(
            HomeItem.AlbumItem(kidA),
            HomeItem.ArtistItem(joni),
            HomeItem.PlaylistItem(mix),
            HomeItem.SmartPlaylistItem(SmartPlaylistId.Favourites),
            HomeItem.GenreItem(jazz),
        )
    }

    @Test
    fun `each item plays its own context, a genre shuffling and a smart playlist by query`() {
        HomeItem.AlbumItem(blue).playAction() shouldBe MediaAction.Play(MediaSelection.Albums(blue))
        HomeItem.GenreItem(jazz).playAction() shouldBe MediaAction.Shuffle(MediaSelection.Genres(jazz))
        HomeItem.SmartPlaylistItem(SmartPlaylistId.MostPlayed).playAction() shouldBe
            MediaAction.Play(MediaSelection.SongsMatching(SmartPlaylistId.MostPlayed.songQuery), context = PlayContext.SmartPlaylist(SmartPlaylistId.MostPlayed))
    }

    @Test
    fun `jump back in resolves the recent contexts and the albums last played through`() = runTest {
        playHistory.recentContexts = listOf(RecentContext(PlayContext.Genre("Jazz"), now), RecentContext(context(blue.groupKey), now))
        suggestions.recentlyCompleted = listOf(kidA.groupKey!!)

        JumpBackIn(playHistory, suggestions, resolve)() shouldBe
            JumpBackInCandidates(listOf(HomeItem.GenreItem(jazz), HomeItem.AlbumItem(blue)), listOf(HomeItem.AlbumItem(kidA)))
    }

    @Test
    fun `around this time asks for the local hour over sixty days`() = runTest {
        playHistory.contextsAroundHour = listOf(ContextDays(context(blue.groupKey), days = 4, weekendDays = 1, lastPlayedAt = now))

        val candidates = AroundThisTime(playHistory, resolve)(now, TimeZone.of("Australia/Melbourne"))

        candidates shouldBe listOf(AroundThisTimeCandidate(HomeItem.AlbumItem(blue), days = 4, weekendDays = 1))
        playHistory.queries shouldBe listOf("contextsAroundHour(8, 90, ${now - 60.days})")
    }

    @Test
    fun `on repeat merges albums and artists by score over twenty eight days`() = runTest {
        playHistory.albumCompletions = listOf(AlbumCompletions(blue.groupKey!!, completions = 6, score = 2.0, lastCompletedAt = now))
        playHistory.albumArtistCompletions = listOf(AlbumArtistCompletions(joni.groupKey, completions = 8, score = 5.0, lastCompletedAt = now))

        OnRepeat(playHistory, resolve)(now) shouldBe listOf(
            OnRepeatCandidate(HomeItem.ArtistItem(joni), 8, 5.0),
            OnRepeatCandidate(HomeItem.AlbumItem(blue), 6, 2.0),
        )
        playHistory.queries shouldBe listOf("albumCompletions(${now - 28.days}, 14d)")
    }

    @Test
    fun `rediscover asks for its window, and recently added for the newest albums with none`() = runTest {
        suggestions.toRediscover = listOf(blue.groupKey!!)
        suggestions.recentlyAdded = listOf(kidA.groupKey!!)

        Rediscover(suggestions, resolve)(now) shouldBe listOf(HomeItem.AlbumItem(blue))
        RecentlyAdded(suggestions, resolve)() shouldBe listOf(HomeItem.AlbumItem(kidA))
        suggestions.calls shouldBe listOf("albumsToRediscover(3, ${now - 90.days})", "recentlyAddedAlbums(30)")
    }

    @Test
    fun `genre picks resolve the played genres and the largest with twenty songs`() = runTest {
        playHistory.genrePlays = listOf(GenrePlays("Polka", plays = 3, score = 2.0), GenrePlays("Gone", plays = 1, score = 1.0))

        GenrePicks(playHistory, suggestions)(now) shouldBe
            GenrePickCandidates(played = listOf(HomeItem.GenreItem(createGenre("Polka", songCount = 5))), largest = listOf(HomeItem.GenreItem(jazz)))
    }
}
