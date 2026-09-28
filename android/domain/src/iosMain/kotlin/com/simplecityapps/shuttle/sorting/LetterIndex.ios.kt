package com.simplecityapps.shuttle.sorting

import platform.Foundation.NSString
import platform.Foundation.decomposedStringWithCanonicalMapping

// Kotlin/Native bridges String to NSString, so the cast always succeeds.
@Suppress("CAST_NEVER_SUCCEEDS")
internal actual fun baseLetter(letter: Char): Char = (letter.toString() as NSString).decomposedStringWithCanonicalMapping.first()
