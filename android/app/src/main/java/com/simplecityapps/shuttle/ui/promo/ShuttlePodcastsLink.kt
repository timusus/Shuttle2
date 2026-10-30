package com.simplecityapps.shuttle.ui.promo

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

/** Where a Shuttle Podcasts link was tapped; the value is the `utm_campaign` and the analytics `surface`. */
enum class PromoSurface(val value: String) {
    Card("card"),
    Settings("settings")
}

object ShuttlePodcastsLink {
    const val PACKAGE = "com.simplecityapps.shuttle.podcasts"

    // Append "&listing=<custom-listing-id>" here to send promo traffic to a custom store listing.
    private const val LISTING_PARAMS = ""

    private fun query(surface: PromoSurface) = "id=$PACKAGE&referrer=utm_source%3Dshuttle_music%26utm_medium%3Dcrosspromo%26utm_campaign%3D${surface.value}$LISTING_PARAMS"

    fun marketUrl(surface: PromoSurface) = "market://details?${query(surface)}"

    fun webUrl(surface: PromoSurface) = "https://play.google.com/store/apps/details?${query(surface)}"

    /** Opens the Play Store app, or the web page where there is none. */
    fun open(
        context: Context,
        surface: PromoSurface
    ) {
        try {
            context.startActivity(view(marketUrl(surface)))
        } catch (_: ActivityNotFoundException) {
            context.startActivity(view(webUrl(surface)))
        }
    }

    private fun view(url: String) = Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
