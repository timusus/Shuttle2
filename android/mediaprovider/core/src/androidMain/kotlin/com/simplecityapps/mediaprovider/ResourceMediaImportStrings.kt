package com.simplecityapps.mediaprovider

import android.content.Context
import com.simplecityapps.shuttle.di.ApplicationContext
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

/** [MediaImportStrings] from this module's string resources, read when each message is reported so they follow a locale change. */
@ContributesBinding(AppScope::class)
class ResourceMediaImportStrings @Inject constructor(
    @ApplicationContext private val context: Context
) : MediaImportStrings {
    override val retrievingSongs: String get() = context.getString(R.string.media_import_retrieving_songs)
    override val retrievingPlaylists: String get() = context.getString(R.string.media_import_retrieving_playlists)
    override val updatingDatabase: String get() = context.getString(R.string.media_import_updating_database)
    override val importError: String get() = context.getString(R.string.media_import_error)
}
