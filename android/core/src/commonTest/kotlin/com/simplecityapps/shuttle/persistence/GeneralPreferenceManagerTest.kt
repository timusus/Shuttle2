package com.simplecityapps.shuttle.persistence

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Instant

/**
 * The keys, defaults and forms [GeneralPreferenceManager] saves under, as they were when it wrote SharedPreferences
 * directly: pinned so values saved by an older build survive the move to [KeyValueStore] (#584).
 */
class GeneralPreferenceManagerTest {
    private val store = InMemoryKeyValueStore()
    private val preferences = GeneralPreferenceManager(store)

    @Test
    fun `nothing saved reads as the defaults`() {
        preferences.previousVersionCode shouldBe -1
        preferences.showChangelogOnLaunch shouldBe true
        preferences.lastViewedChangelogVersion.shouldBeNull()
        preferences.appPurchasedDate.shouldBeNull()
        preferences.lastViewedRatingFlow.shouldBeNull()
        preferences.artistListViewMode.shouldBeNull()
        preferences.albumListViewMode.shouldBeNull()
        preferences.currentLibraryTab.shouldBeNull()
        preferences.searchFilterArtists shouldBe true
        preferences.searchFilterAlbums shouldBe true
        preferences.searchFilterSongs shouldBe true
        preferences.searchFilterGenres shouldBe true
        preferences.searchFilterPlaylists shouldBe true
        preferences.recentSearches shouldBe emptyList()
        preferences.sleepTimerPlayToEnd shouldBe false
        preferences.allLibraryTabs shouldBe LibraryTab.entries
        preferences.enabledLibraryTabs shouldBe LibraryTab.entries - LibraryTab.Folders
        preferences.songTagsVersion("Jellyfin") shouldBe 0
        preferences.songTagsRescanVersion shouldBe 0
        preferences.lastMediaImportDate.shouldBeNull()
        preferences.lastSyncStart("Jellyfin").shouldBeNull()
        preferences.lastFullSyncStart("Jellyfin").shouldBeNull()
        preferences.sourceReachability("Jellyfin").shouldBeNull()
    }

    @Test
    fun `a source's reachability is kept per source - a success clears its error`() {
        val failed = SourceReachability("Can't reach the server", Instant.fromEpochMilliseconds(1_000))
        preferences.setSourceReachability("Jellyfin", failed)

        preferences.sourceReachability("Jellyfin") shouldBe failed
        preferences.sourceReachability("Plex").shouldBeNull()

        val reached = SourceReachability(null, Instant.fromEpochMilliseconds(2_000))
        preferences.setSourceReachability("Jellyfin", reached)
        preferences.sourceReachability("Jellyfin") shouldBe reached
    }

    @Test
    fun `sync starts are kept per source`() {
        preferences.setLastSyncStart("Jellyfin", Instant.fromEpochMilliseconds(1_000))
        preferences.setLastFullSyncStart("Plex", Instant.fromEpochMilliseconds(2_000))

        preferences.lastSyncStart("Jellyfin") shouldBe Instant.fromEpochMilliseconds(1_000)
        preferences.lastSyncStart("Plex").shouldBeNull()
        preferences.lastFullSyncStart("Plex") shouldBe Instant.fromEpochMilliseconds(2_000)
        preferences.lastFullSyncStart("Jellyfin").shouldBeNull()

        preferences.setLastSyncStart("Jellyfin", null)
        preferences.lastSyncStart("Jellyfin").shouldBeNull()
    }

    @Test
    fun `each value is saved under the key and in the form it always was`() {
        preferences.previousVersionCode = 26092701
        preferences.showChangelogOnLaunch = false
        preferences.lastViewedChangelogVersion = "2026.09.27"
        preferences.appPurchasedDate = Instant.fromEpochMilliseconds(1_700_000_000_001)
        preferences.lastViewedRatingFlow = Instant.fromEpochMilliseconds(1_700_000_000_002)
        preferences.artistListViewMode = "Grid"
        preferences.albumListViewMode = "List"
        preferences.currentLibraryTab = LibraryTab.Albums
        preferences.searchFilterArtists = false
        preferences.searchFilterAlbums = false
        preferences.searchFilterSongs = false
        preferences.searchFilterGenres = false
        preferences.searchFilterPlaylists = false
        preferences.recentSearches = listOf("newest", "oldest")
        preferences.sleepTimerPlayToEnd = true
        preferences.allLibraryTabs = listOf(LibraryTab.Songs, LibraryTab.Albums)
        preferences.enabledLibraryTabs = listOf(LibraryTab.Songs)
        preferences.setSongTagsVersion("Jellyfin", 1)
        preferences.songTagsRescanVersion = 1
        preferences.lastMediaImportDate = Instant.fromEpochMilliseconds(1_700_000_000_003)

        store.values shouldBe mapOf(
            "previous_version_code" to 26092701,
            "changelog_show_on_launch" to false,
            "last_viewed_changelog_version" to "2026.09.27",
            "app_purchased_date" to 1_700_000_000_001L,
            "last_viewed_rating_flow" to 1_700_000_000_002L,
            "pref_artist_view_mode" to "Grid",
            "pref_album_view_mode" to "List",
            "library_tab_current" to "Albums",
            "search_filter_artists" to false,
            "search_filter_albums" to false,
            "search_filter_songs" to false,
            "search_filter_genres" to false,
            "search_filter_playlists" to false,
            "search_recent" to "newest\noldest",
            "sleep_timer_play_to_end" to true,
            "pref_library_tabs_all" to "Songs,Albums",
            "pref_library_tabs_enabled" to "Songs",
            "song_tags_version_Jellyfin" to 1,
            "song_tags_rescan_version" to 1,
            "pref_media_last_rescan_date" to 1_700_000_000_003L
        )
    }

    @Test
    fun `dates saved as epoch millis by an older build read back`() {
        val saved = GeneralPreferenceManager(
            InMemoryKeyValueStore(
                mapOf(
                    "app_purchased_date" to 1_700_000_000_001L,
                    "last_viewed_rating_flow" to 1_700_000_000_002L,
                    "pref_media_last_rescan_date" to 1_700_000_000_003L
                )
            )
        )

        saved.appPurchasedDate shouldBe Instant.fromEpochMilliseconds(1_700_000_000_001)
        saved.lastViewedRatingFlow shouldBe Instant.fromEpochMilliseconds(1_700_000_000_002)
        saved.lastMediaImportDate shouldBe Instant.fromEpochMilliseconds(1_700_000_000_003)
    }

    @Test
    fun `clearing a date or string removes its key`() {
        preferences.lastMediaImportDate = Instant.fromEpochMilliseconds(1)
        preferences.lastViewedChangelogVersion = "1"
        preferences.currentLibraryTab = LibraryTab.Songs

        preferences.lastMediaImportDate = null
        preferences.lastViewedChangelogVersion = null
        preferences.currentLibraryTab = null

        store.values shouldBe emptyMap()
    }

    @Test
    fun `held deletes are saved per source as sorted base-36 gaps, and an unreadable value holds none`() {
        preferences.setHeldDeletes("Shuttle", setOf(40L, 3L, 1_000_000L))
        store.getString("held_deletes_Shuttle", null) shouldBe "3,11,lfko"
        preferences.heldDeletes("Shuttle") shouldBe setOf(3L, 40L, 1_000_000L)
        preferences.heldDeletes("Jellyfin") shouldBe emptySet()

        preferences.setHeldDeletes("Shuttle", emptySet())
        store.contains("held_deletes_Shuttle") shouldBe false

        store.edit { putString("held_deletes_Shuttle", "3,!,4") }
        preferences.heldDeletes("Shuttle") shouldBe emptySet()
    }

    @Test
    fun `saved tabs gain the tabs added since and unknown enabled tabs are dropped`() {
        val saved = GeneralPreferenceManager(
            InMemoryKeyValueStore(
                mapOf(
                    "pref_library_tabs_all" to "Songs,Albums",
                    "pref_library_tabs_enabled" to "Songs,Removed"
                )
            )
        )

        saved.allLibraryTabs shouldBe listOf(LibraryTab.Songs, LibraryTab.Albums) + (LibraryTab.entries - LibraryTab.Songs - LibraryTab.Albums)
        saved.enabledLibraryTabs shouldBe listOf(LibraryTab.Songs)
    }

    @Test
    fun `unknown or empty saved tab names are skipped rather than failing`() {
        val saved = GeneralPreferenceManager(InMemoryKeyValueStore(mapOf("pref_library_tabs_all" to "Removed,,Albums,Albums")))
        val empty = GeneralPreferenceManager(InMemoryKeyValueStore(mapOf("pref_library_tabs_all" to "")))

        saved.allLibraryTabs shouldBe listOf(LibraryTab.Albums) + (LibraryTab.entries - LibraryTab.Albums)
        empty.allLibraryTabs shouldBe LibraryTab.entries
    }
}
