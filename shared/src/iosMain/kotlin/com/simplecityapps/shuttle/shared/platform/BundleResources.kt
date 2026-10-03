package com.simplecityapps.shuttle.shared.platform

import com.simplecityapps.mediaprovider.MediaImportStrings
import com.simplecityapps.mediaprovider.server.ServerStrings
import com.simplecityapps.provider.plex.PlexStrings
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
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterDecimalStyle
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
    override val authenticationError: String get() = localized("media_provider_authentication_error", "Failed to authenticate")
    override val unknownName: String get() = localized("unknown", "Unknown")
}

@ContributesBinding(AppScope::class)
class BundlePlexStrings @Inject constructor() : PlexStrings {
    override val musicLibraryMissing: String get() = localized("media_provider_plex_music_library_missing", "No Plex music library found")
}

@ContributesBinding(AppScope::class)
class BundleMediaImportStrings @Inject constructor() : MediaImportStrings {
    override fun connecting(provider: String): String = localized("media_import_connecting", "Connecting to %1\$@…").withArguments(provider)

    override val fetching: String get() = localized("media_import_fetching", "Fetching your library…")

    override fun fetchingSongs(
        count: Int,
        total: Int
    ): String = localized("media_import_fetching_songs", "Fetching %1\$@ of %2\$@ songs").withArguments(count.formatted(), total.formatted())

    // One English plural form until the catalogue carries Android's plurals (.stringsdict)
    override fun saving(count: Int): String = if (count == 1) {
        localized("media_import_saving_song", "Saving %1\$@ song…").withArguments(count.formatted())
    } else {
        localized("media_import_saving_songs", "Saving %1\$@ songs…").withArguments(count.formatted())
    }

    override val importError: String get() = localized("media_import_error", "An error occurred importing songs")

    private fun Int.formatted(): String = NSNumberFormatter().apply { numberStyle = NSNumberFormatterDecimalStyle }.stringFromNumber(NSNumber(int = this)) ?: toString()
}

/** This format with its `%1$@`, `%2$@`, ... placeholders filled with [arguments], in order. */
private fun String.withArguments(vararg arguments: String): String = arguments.foldIndexed(this) { index, text, argument -> text.replace("%${index + 1}\$@", argument) }
