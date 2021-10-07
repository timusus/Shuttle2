package com.simplecityapps.shuttle.savedstate

expect class SavedStateHandle {
    fun <T> get(key: String): T?
    fun contains(key: String): Boolean
    fun <T> set(key: String, value: T?)
}