package com.simplecityapps.playback.persistence

import com.simplecityapps.playback.dsp.equalizer.Equalizer
import com.simplecityapps.playback.dsp.equalizer.EqualizerBand
import com.simplecityapps.playback.queue.RepeatMode
import com.simplecityapps.playback.queue.ShuffleMode
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlin.test.Test

/**
 * What [PlaybackPreferenceManager] saves, key for key, and the JSON it reads: the JSON strings below are what the
 * Moshi adapters it used before kotlinx.serialization (#584) wrote, captured from a run of them, so a queue and EQ
 * saved by an older build read back unchanged.
 */
class PlaybackPreferenceManagerTest {
    private val store = InMemoryKeyValueStore()
    private val manager = PlaybackPreferenceManager(store)

    @Test
    fun `each value is saved under the key and in the form it always was`() {
        manager.queueIds = "1,2,3"
        manager.shuffleQueueIds = "3,1,2"
        manager.queuePosition = 1
        manager.restoreQueuePositionFromStart = true
        manager.playbackPosition = 30_000
        manager.shuffleMode = ShuffleMode.On
        manager.repeatMode = RepeatMode.All
        manager.mediaProviderTypes = listOf(MediaProviderType.Shuttle, MediaProviderType.Jellyfin)
        manager.preset = Equalizer.Presets.bassBoost
        manager.customPresetBands = listOf(EqualizerBand(32, 1.5))
        manager.nowPlaying = sparseSnapshot

        store.values shouldBe mapOf(
            "queue_ids" to "1,2,3",
            "shuffle_queue_ids" to "3,1,2",
            "queue_position" to 1,
            "restore_queue_position_from_start" to true,
            "playback_position" to 30_000,
            "shuffle_mode" to ShuffleMode.On.ordinal,
            "repeat_mode" to RepeatMode.All.ordinal,
            "media_providers" to "0,3",
            "preset_name" to "Bass Boost",
            "custom_preset_bands" to """[{"centerFrequency":32,"gain":1.5}]""",
            "now_playing" to MOSHI_SPARSE_SNAPSHOT
        )
    }

    @Test
    fun `cleared values are saved as the markers the app always used`() {
        manager.queueIds = null
        manager.shuffleQueueIds = null
        manager.queuePosition = null
        manager.playbackPosition = null
        manager.customPresetBands = null
        manager.nowPlaying = null

        store.values shouldBe mapOf(
            "queue_ids" to "",
            "shuffle_queue_ids" to "",
            "queue_position" to -1,
            "playback_position" to -1,
            "custom_preset_bands" to "null",
            "now_playing" to ""
        )
    }

    @Test
    fun `nothing saved reads as the defaults`() {
        manager.queueIds.shouldBeNull()
        manager.shuffleQueueIds.shouldBeNull()
        manager.queuePosition.shouldBeNull()
        manager.restoreQueuePositionFromStart shouldBe false
        manager.playbackPosition.shouldBeNull()
        manager.shuffleMode shouldBe ShuffleMode.init(-1)
        manager.repeatMode shouldBe RepeatMode.init(-1)
        manager.mediaProviderTypes shouldBe listOf(MediaProviderType.Shuttle)
        manager.preset shouldBe Equalizer.Presets.custom
        manager.customPresetBands.shouldBeNull()
        manager.nowPlaying.shouldBeNull()
    }

    @Test
    fun `EQ bands Moshi wrote read back and are written back byte for byte`() {
        store.putRaw("custom_preset_bands", MOSHI_BANDS)

        val bands = manager.customPresetBands!!
        bands.map { band -> band.centerFrequency } shouldBe listOf(32, 63, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
        bands.map { band -> band.gain } shouldBe listOf(-6.0, 2.5, -4.0, -3.0, -2.0, 0.0, 0.0, 0.0, 0.0, 0.0)

        manager.customPresetBands = bands
        store.getString("custom_preset_bands", null) shouldBe MOSHI_BANDS
    }

    @Test
    fun `a preset's bands are written as the two fields of a band as Moshi wrote them`() {
        val bands = Equalizer.Presets.bassReducer.bands.toMutableList<EqualizerBand>()
        bands[1] = EqualizerBand(63, 2.5)

        manager.customPresetBands = bands

        store.getString("custom_preset_bands", null) shouldBe MOSHI_BANDS
    }

    @Test
    fun `the null bands Moshi wrote read as none`() {
        store.putRaw("custom_preset_bands", "null")

        manager.customPresetBands.shouldBeNull()
    }

    @Test
    fun `a snapshot Moshi wrote reads back and is written back byte for byte`() {
        store.putRaw("now_playing", MOSHI_FULL_SNAPSHOT)
        store.putRaw("playback_position", 12_000)

        manager.nowPlaying shouldBe fullSnapshot.copy(positionMs = 12_000)

        manager.nowPlaying = fullSnapshot
        store.getString("now_playing", null) shouldBe MOSHI_FULL_SNAPSHOT
    }

    @Test
    fun `a snapshot Moshi wrote without its null fields reads them as null and is written back byte for byte`() {
        store.putRaw("now_playing", MOSHI_SPARSE_SNAPSHOT)

        manager.nowPlaying shouldBe sparseSnapshot

        manager.nowPlaying = sparseSnapshot
        store.getString("now_playing", null) shouldBe MOSHI_SPARSE_SNAPSHOT
    }

    @Test
    fun `fields a snapshot doesn't know are ignored`() {
        store.putRaw("now_playing", MOSHI_SPARSE_SNAPSHOT.dropLast(1) + ""","positionMs":5000,"rating":{"stars":4},"tags":["a"]}""")

        manager.nowPlaying shouldBe sparseSnapshot
    }

    @Test
    fun `a snapshot missing a field it needs reads as none`() {
        store.putRaw("now_playing", """{"songId":7,"artists":[],"durationMs":0,"mimeType":"audio/mpeg","mediaProvider":"Shuttle"}""")

        manager.nowPlaying.shouldBeNull()
    }

    @Test
    fun `a snapshot that isn't JSON reads as none`() {
        store.putRaw("now_playing", "{not json")

        manager.nowPlaying.shouldBeNull()
    }

    @Test
    fun `a snapshot saved with the queue restoring from the start resumes from the start`() {
        store.putRaw("now_playing", MOSHI_FULL_SNAPSHOT)
        store.putRaw("playback_position", 12_000)
        store.putRaw("restore_queue_position_from_start", true)

        manager.nowPlaying?.positionMs shouldBe 0
    }

    private fun InMemoryKeyValueStore.putRaw(
        key: String,
        value: Any
    ) = edit {
        when (value) {
            is String -> putString(key, value)
            is Int -> putInt(key, value)
            is Boolean -> putBoolean(key, value)
            else -> error("Unsupported $value")
        }
    }

    private companion object {
        // Captured from Moshi 1.15's codegen adapters (List<EqualizerBand>, NowPlayingSnapshot) at 2c94580a2
        const val MOSHI_BANDS =
            """[{"centerFrequency":32,"gain":-6.0},{"centerFrequency":63,"gain":2.5},{"centerFrequency":125,"gain":-4.0},""" +
                """{"centerFrequency":250,"gain":-3.0},{"centerFrequency":500,"gain":-2.0},{"centerFrequency":1000,"gain":0.0},""" +
                """{"centerFrequency":2000,"gain":0.0},{"centerFrequency":4000,"gain":0.0},{"centerFrequency":8000,"gain":0.0},""" +
                """{"centerFrequency":16000,"gain":0.0}]"""

        const val MOSHI_FULL_SNAPSHOT =
            """{"songId":42,"title":"Tïtle \"q\"","artists":["A","B"],"albumArtist":"AA","album":"Alb","durationMs":180000,""" +
                """"path":"/music/a.flac","mimeType":"audio/flac","mediaProvider":"Jellyfin","externalId":"ext-1","artworkVersion":"v2"}"""

        const val MOSHI_SPARSE_SNAPSHOT =
            """{"songId":7,"artists":[],"durationMs":0,"path":"content://x/1","mimeType":"audio/mpeg","mediaProvider":"Shuttle"}"""

        val fullSnapshot = NowPlayingSnapshot(
            songId = 42L,
            title = "Tïtle \"q\"",
            artists = listOf("A", "B"),
            albumArtist = "AA",
            album = "Alb",
            durationMs = 180000,
            path = "/music/a.flac",
            mimeType = "audio/flac",
            mediaProvider = MediaProviderType.Jellyfin,
            externalId = "ext-1",
            artworkVersion = "v2",
            positionMs = 999
        )

        val sparseSnapshot = NowPlayingSnapshot(
            songId = 7L,
            title = null,
            artists = emptyList(),
            albumArtist = null,
            album = null,
            durationMs = 0,
            path = "content://x/1",
            mimeType = "audio/mpeg",
            mediaProvider = MediaProviderType.Shuttle,
            externalId = null,
            artworkVersion = null
        )
    }
}
