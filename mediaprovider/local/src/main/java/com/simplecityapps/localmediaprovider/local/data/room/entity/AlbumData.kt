package com.simplecityapps.localmediaprovider.local.data.room.entity

import androidx.room.DatabaseView

@DatabaseView(
    "SELECT songs.album as name, songs.albumArtist as albumArtist, count(distinct songs.id) as songCount, sum(distinct songs.duration) as duration, min(distinct songs.year) as year, min(distinct songs.playCount) as playCount " +
            "FROM songs " +
            "WHERE songs.blacklisted == 0 " +
            "GROUP BY LOWER(songs.albumArtist), LOWER(songs.album) " +
            "ORDER BY name"
)
class AlbumData