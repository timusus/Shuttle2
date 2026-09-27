package com.simplecityapps.shuttle.ui.screens.settings.about

import com.simplecityapps.shuttle.logging.Logger
import com.simplecityapps.shuttle.platform.BundledText
import com.simplecityapps.shuttle.ui.screens.changelog.Changeset
import dev.zacsweers.metro.Inject
import kotlinx.serialization.json.Json

/** The release notes bundled with the app, newest first. */
class ChangelogRepository @Inject constructor(
    private val bundledText: BundledText,
) {
    suspend fun changelog(): List<Changeset> = try {
        bundledText.read(CHANGELOG)?.let { json.decodeFromString<List<Changeset>>(it) }
    } catch (e: RuntimeException) {
        logger.error(e) { "Invalid changelog" }
        null
    }.orEmpty()

    private companion object {
        const val CHANGELOG = "changelog.json"
        val json = Json {
            isLenient = true
            ignoreUnknownKeys = true
        }
        val logger = Logger.tagged("ChangelogRepository")
    }
}
