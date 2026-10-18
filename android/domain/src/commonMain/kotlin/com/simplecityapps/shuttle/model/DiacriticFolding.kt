package com.simplecityapps.shuttle.model

import com.simplecityapps.mediaprovider.search.SearchText

internal fun String.foldDiacritics(): String = buildString {
    for (c in this@foldDiacritics) {
        if (c.code < 128) append(c) else append(SearchText.fold(c) ?: c.toString())
    }
}
