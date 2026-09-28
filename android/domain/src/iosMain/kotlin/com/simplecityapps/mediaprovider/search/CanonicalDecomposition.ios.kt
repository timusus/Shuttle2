package com.simplecityapps.mediaprovider.search

import platform.Foundation.NSString
import platform.Foundation.decomposedStringWithCanonicalMapping

// Kotlin/Native bridges String to NSString, so the cast always succeeds.
@Suppress("CAST_NEVER_SUCCEEDS")
internal actual fun decomposeCanonical(text: String): String = (text as NSString).decomposedStringWithCanonicalMapping
