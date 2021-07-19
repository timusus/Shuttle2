package com.simplecityapps.mediaprovider.model

class Entry(val location: String, val duration: Int?, val artist: String?, val track: String?)

data class M3uPlaylist(val path: String, val name: String, val entries: List<Entry>)