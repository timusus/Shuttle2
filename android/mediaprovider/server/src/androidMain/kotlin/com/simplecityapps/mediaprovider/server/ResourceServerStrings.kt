package com.simplecityapps.mediaprovider.server

import android.content.Context
import com.simplecityapps.mediaprovider.R
import com.simplecityapps.shuttle.di.ApplicationContext
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

/** [ServerStrings] from the app's string resources, read when each message is reported so they follow a locale change. */
@ContributesBinding(AppScope::class)
class ResourceServerStrings @Inject constructor(
    @ApplicationContext private val context: Context
) : ServerStrings {
    override val addressMissing: String get() = context.getString(R.string.media_provider_address_missing)

    override val authenticationError: String get() = context.getString(R.string.media_provider_authentication_error)

    override val musicLibraryMissing: String get() = context.getString(R.string.media_provider_music_library_missing)

    override val unknownName: String get() = context.getString(com.simplecityapps.core.R.string.unknown)
}
