package com.simplecityapps

fun createSmartPlaylist(
    id: com.simplecityapps.shuttle.model.SmartPlaylistId = com.simplecityapps.shuttle.model.SmartPlaylistId.RecentlyAdded,
) = com.simplecityapps.shuttle.model.SmartPlaylist(id)
