package com.simplecityapps.shuttle.ui.screens.settings.about

import android.content.Context
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.util.withContext
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Licence(
    val name: String,
    val version: String?,
    val licence: String?,
    val website: String?
)

/** The open source libraries the build bundles, from the aboutlibraries plugin's generated metadata. */
class LicencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun licences(): List<Licence> = withContext(Dispatchers.IO) {
        Libs.Builder().withContext(context).build().libraries
            .map { library ->
                Licence(
                    name = library.name,
                    version = library.artifactVersion?.ifEmpty { null },
                    licence = library.licenses.firstOrNull()?.name?.ifEmpty { null },
                    website = library.website?.ifEmpty { null }
                )
            }
            .sortedBy { it.name }
    }
}
