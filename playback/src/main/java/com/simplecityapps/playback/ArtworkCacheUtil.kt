package com.simplecityapps.playback

import com.simplecityapps.mediaprovider.model.Song

fun Song.getArtworkCacheKey(width: Int, height: Int): String {
    return "${hashCode()}_${width}_$height"
}