package com.simplecityapps.shuttle.shared.platform

import com.simplecityapps.mediaprovider.MediaImportStrings
import com.simplecityapps.mediaprovider.server.ServerStrings
import com.simplecityapps.shuttle.di.IoDispatcher
import com.simplecityapps.shuttle.platform.AppVersion
import com.simplecityapps.shuttle.platform.BundledText
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import platform.Foundation.NSBundle
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile

/** A text file copied into the app bundle (the changelog, the licences), or null if the bundle has none by that name. */
@ContributesBinding(AppScope::class)
class BundleText @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : BundledText {
    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    override suspend fun read(name: String): String? = withContext(ioDispatcher) {
        val path = NSBundle.mainBundle.pathForResource(name.substringBeforeLast('.'), ofType = name.substringAfterLast('.', ""))
        path?.let { NSString.stringWithContentsOfFile(it, encoding = NSUTF8StringEncoding, error = null) }
    }
}

/** The app's marketing version, `CFBundleShortVersionString` (Android's `versionName`). */
@ContributesBinding(AppScope::class)
class BundleAppVersion @Inject constructor() : AppVersion {
    override fun name(): String = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "1.0"
}

/**
 * A string from the app's string catalogue under its Android resource name, or [english] until the catalogue has it
 * (`Localizable.xcstrings`, generated from strings.xml, comes with the Swift composition).
 */
private fun localized(
    key: String,
    english: String
): String = NSBundle.mainBundle.localizedStringForKey(key, value = english, table = null)

@ContributesBinding(AppScope::class)
class BundleServerStrings @Inject constructor() : ServerStrings {
    override val addressMissing: String get() = localized("media_provider_address_missing", "Server address missing")
    override val queryingApi: String get() = localized("media_provider_querying_api", "Querying API")
    override val authenticationError: String get() = localized("media_provider_authentication_error", "Failed to authenticate")
    override val unknownName: String get() = localized("unknown", "Unknown")
}

@ContributesBinding(AppScope::class)
class BundleMediaImportStrings @Inject constructor() : MediaImportStrings {
    override val retrievingSongs: String get() = localized("media_import_retrieving_songs", "Retrieving existing songs")
    override val retrievingPlaylists: String get() = localized("media_import_retrieving_playlists", "Retrieving existing playlists")
    override val updatingDatabase: String get() = localized("media_import_updating_database", "Updating database")
    override val importError: String get() = localized("media_import_error", "An error occurred importing songs")
}
