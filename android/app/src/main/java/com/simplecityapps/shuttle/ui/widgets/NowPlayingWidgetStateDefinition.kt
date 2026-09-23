package com.simplecityapps.shuttle.ui.widgets

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.glance.state.GlanceStateDefinition
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * One state for every now playing widget: they all show the same track, so there's a single DataStore file
 * rather than one per widget. DataStore allows only one instance per file, hence the mutex.
 */
object NowPlayingWidgetStateDefinition : GlanceStateDefinition<NowPlayingWidgetState> {
    private const val FILE_NAME = "now_playing_widget_state"

    private val mutex = Mutex()

    @Volatile
    private var dataStore: DataStore<NowPlayingWidgetState>? = null

    override suspend fun getDataStore(
        context: Context,
        fileKey: String
    ): DataStore<NowPlayingWidgetState> = dataStore ?: mutex.withLock {
        dataStore ?: DataStoreFactory
            .create(
                serializer = NowPlayingWidgetStateSerializer,
                corruptionHandler =
                    ReplaceFileCorruptionHandler { e ->
                        // The next playback change rewrites the state, so show the idle widget until then.
                        Timber.w(e, "Discarding unreadable now playing widget state")
                        NowPlayingWidgetState.Idle
                    }
            ) { stateFile(context) }
            .also { dataStore = it }
    }

    /**
     * Glance deletes this file when a single widget is removed. The state is shared, so each widget gets a
     * per-widget path that's never written, and removing one widget leaves the others' state alone.
     */
    override fun getLocation(
        context: Context,
        fileKey: String
    ): File = File(context.applicationContext.filesDir, "datastore/${FILE_NAME}_$fileKey")

    private fun stateFile(context: Context): File = File(context.applicationContext.filesDir, "datastore/$FILE_NAME")
}

internal object NowPlayingWidgetStateSerializer : Serializer<NowPlayingWidgetState> {
    private const val VERSION = 2

    /** Version 1 had no background opacity; it reads as fully opaque. */
    private const val VERSION_WITHOUT_OPACITY = 1

    override val defaultValue: NowPlayingWidgetState = NowPlayingWidgetState.Idle

    override suspend fun readFrom(input: InputStream): NowPlayingWidgetState {
        try {
            val data = DataInputStream(input)
            val version = data.readInt()
            if (version != VERSION && version != VERSION_WITHOUT_OPACITY) return defaultValue
            return NowPlayingWidgetState(
                hasTrack = data.readBoolean(),
                title = data.readUTF(),
                artist = data.readUTF(),
                album = data.readUTF(),
                isPlaying = data.readBoolean(),
                shuffleOn = data.readBoolean(),
                repeatMode = WidgetRepeatMode.entries.getOrElse(data.readInt()) { WidgetRepeatMode.Off },
                artworkPath = data.readUTF().takeIf { it.isNotEmpty() },
                backgroundOpacity = if (version >= VERSION) data.readInt() else 100
            )
        } catch (e: IOException) {
            throw CorruptionException("Unreadable now playing widget state", e)
        }
    }

    override suspend fun writeTo(
        t: NowPlayingWidgetState,
        output: OutputStream
    ) {
        DataOutputStream(output).apply {
            writeInt(VERSION)
            writeBoolean(t.hasTrack)
            writeUTF(t.title)
            writeUTF(t.artist)
            writeUTF(t.album)
            writeBoolean(t.isPlaying)
            writeBoolean(t.shuffleOn)
            writeInt(t.repeatMode.ordinal)
            writeUTF(t.artworkPath.orEmpty())
            writeInt(t.backgroundOpacity)
            flush()
        }
    }
}
