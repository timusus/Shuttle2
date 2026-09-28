package com.simplecityapps.mediaprovider.search

import java.text.Normalizer

internal actual fun decomposeCanonical(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
