package com.simplecityapps.localmediaprovider.local.data.room.entity

import androidx.room.DatabaseView

@DatabaseView(
    "SELECT songs.albumArtist as name, count(distinct songs.album) as albumCount, count(distinct songs.id) as songCount, min(distinct songs.playCount) as playCount " +
            "FROM songs " +
            "WHERE songs.blacklisted == 0 " +
            "GROUP BY LOWER(songs.albumArtist) " +
            "ORDER BY name"
)
class AlbumArtistData