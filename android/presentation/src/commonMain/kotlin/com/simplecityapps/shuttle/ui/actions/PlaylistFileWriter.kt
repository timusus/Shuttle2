package com.simplecityapps.shuttle.ui.actions

/** Writes [text] to [destination], a platform location string the route obtained. */
fun interface PlaylistFileWriter {
    suspend fun write(destination: String, text: String): ExportPlaylist.Result
}
