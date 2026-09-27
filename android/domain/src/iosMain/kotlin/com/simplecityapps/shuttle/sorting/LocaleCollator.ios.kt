package com.simplecityapps.shuttle.sorting

import platform.Foundation.NSString
import platform.Foundation.localizedCaseInsensitiveCompare
import platform.Foundation.localizedCompare

// Kotlin/Native bridges String to NSString, so the casts always succeed.
@Suppress("CAST_NEVER_SUCCEEDS")
actual fun localeCollator(strength: CollationStrength): Comparator<String> = when (strength) {
    CollationStrength.Secondary -> Comparator { a, b -> (a as NSString).localizedCaseInsensitiveCompare(b).toInt() }
    CollationStrength.Tertiary -> Comparator { a, b -> (a as NSString).localizedCompare(b).toInt() }
}
