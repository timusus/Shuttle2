package com.simplecityapps.shuttle.ui.screens.settings.about

import android.content.Context
import com.simplecityapps.shuttle.di.ApplicationContext
import com.simplecityapps.shuttle.di.IoDispatcher
import com.simplecityapps.shuttle.platform.BundledText
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import java.io.FileNotFoundException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Reads a bundled file from the app's assets, else from the raw resource of the same base name: the aboutlibraries
 * plugin generates `aboutlibraries.json` as `R.raw.aboutlibraries`.
 */
@ContributesBinding(AppScope::class)
class AssetBundledText @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : BundledText {
    override suspend fun read(name: String): String? = withContext(ioDispatcher) {
        asset(name) ?: rawResource(name.substringBeforeLast('.'))
    }

    private fun asset(name: String): String? = try {
        context.assets.open(name).bufferedReader().use { it.readText() }
    } catch (_: FileNotFoundException) {
        null
    }

    private fun rawResource(name: String): String? {
        // The plugin names the resource at build time, so there's no R field to reference
        @Suppress("DiscouragedApi")
        val id = context.resources.getIdentifier(name, "raw", context.packageName)
        if (id == 0) return null
        return context.resources.openRawResource(id).bufferedReader().use { it.readText() }
    }
}
