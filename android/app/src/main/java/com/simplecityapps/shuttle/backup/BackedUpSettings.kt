package com.simplecityapps.shuttle.backup

import com.simplecityapps.mediaprovider.settings.LibrarySettings
import com.simplecityapps.playback.equalizer.KeyValueEqualizerPresetStore
import com.simplecityapps.playback.settings.PlaybackSettings
import com.simplecityapps.shuttle.persistence.KeyValueStore
import com.simplecityapps.shuttle.persistence.LibraryTab
import com.simplecityapps.shuttle.settings.AppearanceSettings
import com.simplecityapps.shuttle.settings.ArtworkSettings
import com.simplecityapps.shuttle.settings.DownloadSettings
import com.simplecityapps.shuttle.settings.EqualizerSettings
import com.simplecityapps.shuttle.settings.PlayerSettings
import com.simplecityapps.shuttle.settings.Setting
import com.simplecityapps.shuttle.settings.Setting.Storage
import com.simplecityapps.shuttle.settings.StreamingSettings
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull

/**
 * The preferences a library backup carries: an explicit allowlist, so a preference added later is not exported until
 * someone decides it should be.
 *
 * Left out on purpose: server credentials and tokens (their own secure store), billing and trial state, the scanner's
 * folder rules and SAF grants (tied to this device's storage), first-run, changelog and consent flags, the client id,
 * debug logging, the queue, playback position and Now Playing snapshot, recent searches and the current tab.
 */
internal object BackedUpSettings {
    /** Settings > Appearance, Playback & sound, Library, Sources' streaming, and the equalizer's switch and preamp. */
    val settings: List<Setting<*>> = listOf(
        AppearanceSettings.Theme,
        AppearanceSettings.AccentColour,
        AppearanceSettings.PureBlack,
        AppearanceSettings.DynamicColour,
        AppearanceSettings.ColourFromArtwork,
        AppearanceSettings.ShowHomeOnLaunch,
        AppearanceSettings.WidgetBackgroundOpacity,
        PlayerSettings.ShowRemainingTime,
        ArtworkSettings.WifiOnly,
        ArtworkSettings.LocalOnly,
        ArtworkSettings.MediaSessionArtwork,
        PlaybackSettings.RetainShuffleOnNewQueue,
        PlaybackSettings.UsbDacDirectOutput,
        PlaybackSettings.ReplayGain,
        PlaybackSettings.PreAmpGain,
        PlaybackSettings.CrossfadeDuration,
        EqualizerSettings.Enabled,
        EqualizerSettings.PreampGain,
        LibrarySettings.RescanFrequency,
        LibrarySettings.ReportPlaybackToServer,
        LibrarySettings.MinTrackLength,
        StreamingSettings.UnmeteredQuality,
        StreamingSettings.MeteredQuality,
        DownloadSettings.WifiOnly
    )

    /**
     * Preferences with no [Setting]: the equalizer's preset and Custom band gains, the library's view modes, tabs and
     * search filters, and the sleep timer's default.
     */
    private val otherKeys: List<Pair<String, Storage>> = listOf(
        KeyValueEqualizerPresetStore.PresetKey to Storage.String,
        KeyValueEqualizerPresetStore.CustomPresetBandsKey to Storage.String,
        "pref_artist_view_mode" to Storage.String,
        "pref_album_view_mode" to Storage.String,
        "pref_library_tabs_all" to Storage.String,
        "pref_library_tabs_enabled" to Storage.String,
        "search_filter_artists" to Storage.Boolean,
        "search_filter_albums" to Storage.Boolean,
        "search_filter_songs" to Storage.Boolean,
        "search_filter_genres" to Storage.Boolean,
        "search_filter_playlists" to Storage.Boolean,
        "sleep_timer_play_to_end" to Storage.Boolean
    )

    private val allowedKeys: Map<String, Storage> = settings.associate { it.key to it.storage } + otherKeys.toMap()

    /** What a string preference holds once checked, or null to leave it at its default: one this app version can't read would break it. */
    private val validators: Map<String, (String) -> String?> = mapOf(
        "pref_library_tabs_all" to ::knownLibraryTabs,
        "pref_library_tabs_enabled" to ::knownLibraryTabs
    )

    /** The comma-separated [LibraryTab] names this app knows, in order and once each; null when none are left. */
    private fun knownLibraryTabs(value: String): String? = value.split(",")
        .filter { name -> LibraryTab.entries.any { it.name == name } }
        .distinct()
        .takeIf { it.isNotEmpty() }
        ?.joinToString(",")

    /** What [store] holds under each allowlisted key; a key never written is left out. */
    fun export(store: KeyValueStore): Map<String, JsonPrimitive> = buildMap {
        allowedKeys.forEach { (key, storage) ->
            if (!store.contains(key)) return@forEach
            val value = runCatching {
                when (storage) {
                    Storage.Boolean -> JsonPrimitive(store.getBoolean(key, false))
                    Storage.Int -> JsonPrimitive(store.getInt(key, 0))
                    Storage.Float -> JsonPrimitive(store.getFloat(key, 0f))
                    Storage.String -> store.getString(key, null)?.let(::JsonPrimitive)
                }
            }.getOrNull()
            if (value != null) put(key, value)
        }
    }

    /**
     * Replaces every allowlisted key with [values]: a key the backup doesn't hold, or holds a value this app can't read
     * (an unknown library tab), goes back to its default, and keys outside the allowlist, or holding the wrong type, are
     * ignored. Returns the number of keys written.
     */
    fun restore(
        store: KeyValueStore,
        values: Map<String, JsonPrimitive>
    ): Int {
        var written = 0
        store.edit {
            allowedKeys.forEach { (key, storage) ->
                val validate = validators[key]
                val value = values[key]?.let { value -> if (validate == null) value else value.validatedWith(validate) }
                if (value == null) {
                    remove(key)
                    return@forEach
                }
                val applied = when (storage) {
                    Storage.Boolean -> value.booleanOrNull?.also { putBoolean(key, it) }
                    Storage.Int -> value.intOrNull?.also { putInt(key, it) }
                    Storage.Float -> value.floatOrNull?.also { putFloat(key, it) }
                    Storage.String -> value.takeIf { it.isString }?.content?.also { putString(key, it) }
                }
                if (applied != null) written++
            }
        }
        return written
    }

    private fun JsonPrimitive.validatedWith(validate: (String) -> String?): JsonPrimitive? = if (isString) validate(content)?.let(::JsonPrimitive) else this
}
