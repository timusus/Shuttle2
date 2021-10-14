package com.simplecityapps.shuttle.model

actual enum class MediaProviderType

actual fun MediaProviderType.isRemote(): Boolean {
    return false
}

actual fun MediaProviderType.supportsTagEditing(): Boolean {
    return false
}
