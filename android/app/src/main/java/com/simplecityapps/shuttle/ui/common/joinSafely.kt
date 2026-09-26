package com.simplecityapps.shuttle.ui.common

/**
 * Joins [items] with [separator], skipping null or empty items, and returns [defaultValue] if
 * none are left.
 */
fun <T> joinSafely(
    separator: String,
    items: Iterable<T>,
    defaultValue: String? = null
): String? {
    val notNullList = items.mapNotNull { it?.toString()?.ifEmpty { null } }
    if (notNullList.isEmpty()) {
        return defaultValue
    }
    return notNullList.joinToString(separator)
}
