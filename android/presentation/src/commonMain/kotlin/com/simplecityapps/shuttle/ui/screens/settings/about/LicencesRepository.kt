package com.simplecityapps.shuttle.ui.screens.settings.about

import com.simplecityapps.shuttle.logging.Logger
import com.simplecityapps.shuttle.platform.BundledText
import dev.zacsweers.metro.Inject
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class Licence(
    val name: String,
    val version: String?,
    val licence: String?,
    val website: String?
)

/**
 * The open source libraries the build bundles, from the aboutlibraries plugin's generated metadata. Parsed here
 * rather than with aboutlibraries-core, whose JVM classes need Java 21 and so don't load in the host tests.
 */
class LicencesRepository @Inject constructor(
    private val bundledText: BundledText,
) {
    suspend fun licences(): List<Licence> {
        val metadata = try {
            bundledText.read(LICENCES)?.let { json.decodeFromString<Metadata>(it) }
        } catch (e: RuntimeException) {
            logger.error(e) { "Invalid licences metadata" }
            null
        } ?: return emptyList()
        return metadata.libraries
            .map { library ->
                Licence(
                    name = library.name,
                    version = library.artifactVersion?.ifEmpty { null },
                    licence = library.licenses.firstNotNullOfOrNull { metadata.licenses[it] }?.name?.ifEmpty { null },
                    website = library.website?.ifEmpty { null }
                )
            }
            .sortedBy { it.name }
    }

    /** The parts of the plugin's `aboutlibraries.json` the screen shows: libraries name their licences by hash. */
    @Serializable
    private class Metadata(
        val libraries: List<Library> = emptyList(),
        val licenses: Map<String, License> = emptyMap(),
    )

    @Serializable
    private class Library(
        val name: String,
        val artifactVersion: String? = null,
        val website: String? = null,
        val licenses: List<String> = emptyList(),
    )

    @Serializable
    private class License(
        val name: String,
    )

    private companion object {
        /** What the aboutlibraries plugin generates: a raw resource on Android. */
        const val LICENCES = "aboutlibraries.json"
        val json = Json { ignoreUnknownKeys = true }
        val logger = Logger.tagged("LicencesRepository")
    }
}
