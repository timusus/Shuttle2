package com.simplecityapps.shuttle.ui.text

/**
 * A user-visible string shared code names, by its key in each platform's catalogue. The key is the entry's
 * name in lower case, and it is both the Android resource name (`R.string.<key>`) and the iOS Localizable key,
 * so neither platform keeps a second copy of the text. Add an entry when a string moves into shared code; the
 * Android resolver's exhaustive `when` and `StringKeyResourcesTest` hold it to an existing resource.
 */
enum class StringKey {
    // Song info labels
    SONG_INFO_SECTION_TAGS,
    SONG_INFO_SECTION_FILE,
    SONG_INFO_SECTION_PLAYBACK,
    SONG_INFO_TRACK_TITLE,
    SONG_INFO_ARTISTS,
    SONG_INFO_ALBUM,
    SONG_INFO_ALBUM_ARTIST,
    SONG_INFO_YEAR,
    SONG_INFO_TRACK_NUMBER,
    SONG_INFO_DISC,
    SONG_INFO_GENRES,
    SONG_INFO_LYRICS,
    SONG_INFO_PATH,
    SONG_INFO_MIME_TYPE,
    SONG_INFO_SIZE,
    SONG_INFO_DURATION,
    SONG_INFO_BIT_RATE,
    SONG_INFO_BIT_DEPTH,
    SONG_INFO_SAMPLE_RATE,
    SONG_INFO_CHANNEL_COUNT,
    SONG_INFO_PLAY_COUNT,
    SONG_INFO_REPLAY_GAIN_TRACK,
    SONG_INFO_REPLAY_GAIN_ALBUM,

    // Media action results
    MEDIA_ACTION_NO_SONGS,
    MEDIA_ACTION_PLAYBACK_FAILED,
    MEDIA_ACTION_NOT_FOUND,
    MEDIA_ACTION_UNDO,
    MEDIA_ACTION_ADD_ANYWAY,
    PLAYLIST_MENU_CREATE_PLAYLIST_SUCCESS,
    PLAYLIST_MENU_CREATE_PLAYLIST_FAILURE,
    PLAYLIST_TITLE_FAVORITES,
    PLAYLIST_TITLE_RECENTLY_ADDED,
    PLAYLIST_TITLE_MOST_PLAYED,
    PLAYLIST_TITLE_HISTORY,
    DIALOG_DELETE_MESSAGE,
    ERROR_UNKNOWN,
    ;

    /** The catalogue key: the Android resource name and the iOS Localizable key. */
    val key: String get() = name.lowercase()
}

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
    ;

    /** The catalogue key: the Android resource name and the iOS `.stringsdict` key. */
    val key: String get() = name.lowercase()
}
