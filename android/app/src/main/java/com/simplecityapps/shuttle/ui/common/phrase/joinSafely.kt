package com.simplecityapps.shuttle.ui.common.phrase

import com.squareup.phrase.ListPhrase

/**
 * Similar to [ListPhrase.join], but returns [defaultValue] if [items] is empty, or contains only items that are null or empty
 */
fun <T> ListPhrase.joinSafely(
    items: Iterable<T>,
    defaultValue: String? = null
): CharSequence? {
    val notNullList = items.mapNotNull { it?.toString()?.ifEmpty { null } }
    if (notNullList.isEmpty()) {
        return defaultValue
    }
    return try {
        join(notNullList)
    } catch (e: Exception) {
        defaultValue
    }
}
