package com.simplecityapps.shuttle.ui.actions

import android.content.Context
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.simplecityapps.shuttle.di.ApplicationContext
import com.simplecityapps.shuttle.di.IoDispatcher
import com.simplecityapps.shuttle.model.Song
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Deletes through the Storage Access Framework, the only way S2 is granted write access to a song's file. */
class SafSongFileDeleter @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : SongFileDeleter {
    override suspend fun delete(song: Song): Boolean = withContext(ioDispatcher) {
        try {
            DocumentFile.fromSingleUri(context, song.path.toUri())?.delete() == true
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete ${song.path}")
            false
        }
    }
}

@BindingContainer
@ContributesTo(AppScope::class)
abstract class SongFileDeleterModule {
    @Binds
    abstract fun bindSongFileDeleter(deleter: SafSongFileDeleter): SongFileDeleter
}
