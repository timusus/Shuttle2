package com.simplecityapps.shuttle.ui.common.phrase

import com.squareup.phrase.ListPhrase

/**
 * Similar to [ListPhrase.join], but returns null if [items] is empty, or contains items that are null or empty
 */
fun <T> ListPhrase.joinSafely(items: Iterable<T>): CharSequence? {
    val notNullList = items.mapNotNull { it }
    if (notNullList.isEmpty()) {
        return null
    }
    return try {
        join(notNullList)
    } catch (e: Exception) {
        null
    }
}