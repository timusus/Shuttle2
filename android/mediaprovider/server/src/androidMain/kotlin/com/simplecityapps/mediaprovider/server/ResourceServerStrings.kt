package com.simplecityapps.mediaprovider.server

import android.content.Context
import com.simplecityapps.mediaprovider.R

/** [ServerStrings] from the app's string resources. */
class ResourceServerStrings(private val context: Context) : ServerStrings {
    override val addressMissing: String get() = context.getString(R.string.media_provider_address_missing)

    override val queryingApi: String get() = context.getString(R.string.media_provider_querying_api)

    override val authenticationError: String get() = context.getString(R.string.media_provider_authentication_error)
}
