package com.simplecityapps

// Apart from the shared model factories in :android:presentation-testing: it names an Android string resource
fun createSmartPlaylist(
    nameResId: Int = com.simplecityapps.mediaprovider.R.string.playlist_title_recently_added,
    songQuery: com.simplecityapps.shuttle.query.SongQuery = com.simplecityapps.shuttle.query.SongQuery.RecentlyAdded(),
) = com.simplecityapps.shuttle.model.SmartPlaylist(
    nameResId = nameResId,
    songQuery = songQuery,
)
