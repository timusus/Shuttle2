package com.simplecityapps.shuttle.model

actual enum class MediaProviderType {
    Jellyfin
}

actual fun MediaProviderType.isRemote(): Boolean {
    return false
}

actual fun MediaProviderType.supportsTagEditing(): Boolean {
    return false
}

actual fun defaultMediaProvider(): MediaProviderType? {
    return null
}