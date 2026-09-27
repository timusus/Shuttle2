package com.simplecityapps.shuttle.ui.actions

import android.content.Context
import android.net.Uri
import com.simplecityapps.shuttle.di.ApplicationContext
import com.simplecityapps.shuttle.di.IoDispatcher
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Writes m3u text to a SAF document Uri via the content resolver. */
class ContentResolverPlaylistFileWriter(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher,
) : PlaylistFileWriter {
    override suspend fun write(destination: String, text: String): ExportPlaylist.Result = withContext(ioDispatcher) {
        val uri = Uri.parse(destination)
        try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(text.toByteArray(Charsets.UTF_8))
                ExportPlaylist.Result.Success
            } ?: ExportPlaylist.Result.Failure("Could not open output stream for URI: $uri")
        } catch (e: IOException) {
            Timber.e(e, "Failed to export playlist to $uri")
            ExportPlaylist.Result.Failure("IO error: ${e.message}")
        } catch (e: SecurityException) {
            Timber.e(e, "Failed to export playlist to $uri (permission denied)")
            ExportPlaylist.Result.Failure("Permission denied: ${e.message}")
        }
    }
}

@BindingContainer
@ContributesTo(AppScope::class)
object PlaylistFileWriterModule {
    @Provides
    @SingleIn(AppScope::class)
    fun providePlaylistFileWriter(
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): PlaylistFileWriter = ContentResolverPlaylistFileWriter(context, ioDispatcher)
}
