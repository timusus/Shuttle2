package com.simplecityapps.shuttle.model

expect enum class MediaProviderType {
    Jellyfin
}

expect fun MediaProviderType.isRemote(): Boolean

expect fun MediaProviderType.supportsTagEditing(): Boolean

expect fun defaultMediaProvider(): MediaProviderType?