package com.simplecityapps.mediaprovider

import android.net.Uri

interface MediaPathProvider {
    @Throws(IllegalStateException::class)
    fun handles(uri: Uri): Boolean
    fun getPath(uri: Uri): Uri
    fun isRemote(uri: Uri): Boolean
}

class AggregateMediaPathProvider : MediaPathProvider {

    private var providers: MutableSet<MediaPathProvider> = mutableSetOf()

    fun addProvider(provider: MediaPathProvider) {
        providers.add(provider)
    }

    fun removeProvider(provider: MediaPathProvider) {
        providers.remove(provider)
    }

    override fun handles(uri: Uri): Boolean {
        return true
    }

    override fun getPath(uri: Uri): Uri {
        return providers.firstOrNull { it.handles(uri) }?.getPath(uri) ?: uri
    }

    override fun isRemote(uri: Uri): Boolean {
        return providers.firstOrNull { it.handles(uri) }?.isRemote(uri) == true
    }
}