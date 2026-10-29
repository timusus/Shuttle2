package com.simplecityapps.shuttle.model

actual fun readSongCopyResource(path: String): String = checkNotNull(SongCopyPair::class.java.classLoader?.getResource(path)) { "No resource $path" }.readText()
