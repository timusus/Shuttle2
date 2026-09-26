package com.simplecityapps.shuttle.ui.screens.settings.about

import android.content.Context
import com.simplecityapps.shuttle.ui.screens.changelog.Changeset
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/** The release notes bundled with the app, newest first. */
class ChangelogRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val moshi: Moshi
) {
    suspend fun changelog(): List<Changeset> = withContext(Dispatchers.IO) {
        try {
            val type = Types.newParameterizedType(MutableList::class.java, Changeset::class.java)
            moshi.adapter<List<Changeset>>(type).lenient().fromJson(context.assets.open("changelog.json").bufferedReader().use { it.readText() })
        } catch (e: RuntimeException) {
            Timber.e(e, "Invalid changelog")
            null
        }.orEmpty()
    }
}
