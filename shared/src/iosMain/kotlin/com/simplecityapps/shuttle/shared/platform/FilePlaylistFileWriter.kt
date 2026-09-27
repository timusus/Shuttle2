package com.simplecityapps.shuttle.shared.platform

import com.simplecityapps.shuttle.di.IoDispatcher
import com.simplecityapps.shuttle.ui.actions.ExportPlaylist
import com.simplecityapps.shuttle.ui.actions.PlaylistFileWriter
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import platform.Foundation.NSError
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.writeToFile

/**
 * Writes an exported playlist to a file path, UTF-8 (S3, phase-4-platform-seams.md). The iOS route passes a path
 * in the temporary directory and shares the file once it's written; nothing on iOS offers the export until the
 * playlist screen lands (phase 7).
 */
@ContributesBinding(AppScope::class)
class FilePlaylistFileWriter @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : PlaylistFileWriter {
    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    override suspend fun write(destination: String, text: String): ExportPlaylist.Result = withContext(ioDispatcher) {
        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            val written = NSString.create(string = text).writeToFile(destination, atomically = true, encoding = NSUTF8StringEncoding, error = error.ptr)
            if (written) {
                ExportPlaylist.Result.Success
            } else {
                ExportPlaylist.Result.Failure("IO error: ${error.value?.localizedDescription ?: "couldn't write $destination"}")
            }
        }
    }
}
