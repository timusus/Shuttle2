package com.simplecityapps.playback

import com.simplecityapps.mediaprovider.model.Song

fun Song.getArtworkCacheKey(width: Int, height: Int): String {
    return "${albumGroupKey}_${name}_${width}_$height"
}