package com.simplecityapps.provider.plex

import android.content.Context
import com.simplecityapps.mediaprovider.R
import com.simplecityapps.shuttle.di.ApplicationContext
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

/** [PlexStrings] from the app's string resources, read when the message is reported so it follows a locale change. */
@ContributesBinding(AppScope::class)
class ResourcePlexStrings @Inject constructor(
    @ApplicationContext private val context: Context
) : PlexStrings {
    override val musicLibraryMissing: String get() = context.getString(R.string.media_provider_plex_music_library_missing)
}
