package com.simplecityapps.playback

import com.simplecityapps.shuttle.model.Song

fun Song.getArtworkCacheKey(width: Int, height: Int): String {
    return "${albumGroupKey}_${name}_${width}_$height"
}
