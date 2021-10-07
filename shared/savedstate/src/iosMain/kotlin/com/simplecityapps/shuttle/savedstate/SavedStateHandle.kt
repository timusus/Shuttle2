package com.simplecityapps.shuttle.savedstate

actual class SavedStateHandle {

    actual fun <T> get(key: String): T? {
        return null
    }

    actual fun contains(key: String): Boolean {
        return false
    }

    actual fun <T> set(key: String, value: T?) {

    }
}