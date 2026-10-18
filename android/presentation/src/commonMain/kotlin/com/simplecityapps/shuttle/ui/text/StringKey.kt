package com.simplecityapps.shuttle.ui.text

/**
 * A user-visible string shared code names, by its key in each platform's catalogue. The key is the entry's
 * name in lower case, and it is both the Android resource name (`R.string.<key>`) and the iOS Localizable key,
 * so neither platform keeps a second copy of the text. Each feature owns an enum of its entries, so adding one
 * rebuilds only that feature's readers. Add an entry when a string moves into shared code; the Android
 * resolver's exhaustive `when` and `StringKeyResourcesTest` hold it to an existing resource.
 */
sealed interface StringKey {
    val name: String

    /** The catalogue key: the Android resource name and the iOS Localizable key. */
    val key: String get() = name.lowercase()
}

/** Every [StringKey] entry of every feature enum. */
val allStringKeys: List<StringKey> =
    SongInfoString.entries +
        MediaActionString.entries +
        SettingsString.entries +
        TagEditorString.entries +
        EqualizerPresetString.entries +
        HomeString.entries

/** A plural string shared code names; keyed like [StringKey] (`R.plurals.<key>`, the iOS `.stringsdict` key). */
enum class PluralKey {
    QUEUE_SONGS_ADDED,
    PLAYLIST_SONGS_ADDED,
    MEDIA_ACTION_ALREADY_IN_PLAYLIST,
    MEDIA_ACTION_REMOVED_FROM_PLAYLIST,
    MEDIA_ACTION_EXCLUDED,
    MEDIA_ACTION_CONFIRM_DELETE,
    MEDIA_ACTION_DELETED,
    MEDIA_ACTION_DELETE_FAILED,
    MEDIA_ACTION_DOWNLOAD_QUEUED,
    MEDIA_ACTION_DOWNLOAD_FAILED,
    MEDIA_ACTION_DOWNLOAD_REMOVED,
    WORK_MOVEMENTS,
    WORK_PARTS,
    WORK_SONGS,
    ;

    /** The catalogue key: the Android resource name and the iOS `.stringsdict` key. */
    val key: String get() = name.lowercase()
}
