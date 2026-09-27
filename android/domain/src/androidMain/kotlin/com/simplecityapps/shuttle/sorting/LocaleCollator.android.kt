package com.simplecityapps.shuttle.sorting

import java.text.Collator

actual fun localeCollator(strength: CollationStrength): Comparator<String> {
    val collator = Collator.getInstance().apply {
        this.strength = when (strength) {
            CollationStrength.Secondary -> Collator.SECONDARY
            CollationStrength.Tertiary -> Collator.TERTIARY
        }
    }
    return Comparator { a, b -> collator.compare(a, b) }
}
