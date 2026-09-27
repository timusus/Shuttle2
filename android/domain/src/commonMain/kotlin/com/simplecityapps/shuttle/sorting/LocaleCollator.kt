package com.simplecityapps.shuttle.sorting

/** Which differences [localeCollator] tells apart, as `java.text.Collator`'s strengths. */
enum class CollationStrength {
    /** Base letters and accents, ignoring case. */
    Secondary,

    /** Base letters, accents and case. */
    Tertiary
}

/**
 * Orders strings for the device's current locale: `java.text.Collator` on Android, NSString's localized
 * comparison on iOS. Reads the locale when called, so callers create it lazily.
 */
expect fun localeCollator(strength: CollationStrength): Comparator<String>
