package com.simplecityapps.mediaprovider

import android.content.Context
import com.simplecityapps.shuttle.di.ApplicationContext
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import java.text.NumberFormat

/** [MediaImportStrings] from this module's string resources, read when each message is reported so they follow a locale change. */
@ContributesBinding(AppScope::class)
class ResourceMediaImportStrings @Inject constructor(
    @ApplicationContext private val context: Context
) : MediaImportStrings {
    override fun connecting(provider: String): String = context.getString(R.string.media_import_connecting, provider)

    override val fetching: String get() = context.getString(R.string.media_import_fetching)

    override fun fetchingSongs(
        count: Int,
        total: Int
    ): String = context.getString(R.string.media_import_fetching_songs, count.formatted(), total.formatted())

    override fun saving(count: Int): String = context.resources.getQuantityString(R.plurals.media_import_saving_songs, count, count.formatted())

    override val importError: String get() = context.getString(R.string.media_import_error)

    private fun Int.formatted(): String = NumberFormat.getIntegerInstance().format(this)
}
