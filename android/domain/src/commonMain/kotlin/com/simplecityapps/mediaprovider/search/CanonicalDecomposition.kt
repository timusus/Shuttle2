package com.simplecityapps.mediaprovider.search

/** [text] in Unicode normalization form D (é → e + U+0301), so [SearchText] can drop the combining marks. */
internal expect fun decomposeCanonical(text: String): String
