package com.simplecityapps.shuttle.ui.promo

import android.content.Context
import com.simplecityapps.shuttle.analytics.Analytics
import com.simplecityapps.shuttle.di.ApplicationContext
import com.simplecityapps.shuttle.settings.Setting
import com.simplecityapps.shuttle.settings.SettingsStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.time.LocalDate

object PodcastsPromoSettings {
    val DaysOpened = Setting.int("promo_podcasts_days_opened", 0)
    val LastDayOpened = Setting.string("promo_podcasts_last_day", "", { it }, { it })

    /** Dismissed or tapped: the card never shows again. */
    val Resolved = Setting.boolean("promo_podcasts_resolved", false)
    val ShownLogged = Setting.boolean("promo_podcasts_shown_logged", false)
}

const val DAYS_BEFORE_CARD = 3

/** Whether the Home card is due: not yet resolved, the app not installed, and enough separate days of use. */
fun podcastsPromoDue(
    daysOpened: Int,
    resolved: Boolean,
    appInstalled: Boolean
): Boolean = !resolved && !appInstalled && daysOpened >= DAYS_BEFORE_CARD

/** The one-time Shuttle Podcasts card on Home: its engagement gate, state and analytics. */
@SingleIn(AppScope::class)
class PodcastsPromo(
    store: SettingsStore,
    @ApplicationContext private val context: Context,
    private val analytics: Analytics,
    private val today: () -> String
) {
    @Inject
    constructor(
        store: SettingsStore,
        @ApplicationContext context: Context,
        analytics: Analytics
    ) : this(store, context, analytics, { LocalDate.now().toString() })

    private val daysOpened = store.preference(PodcastsPromoSettings.DaysOpened)
    private val lastDayOpened = store.preference(PodcastsPromoSettings.LastDayOpened)
    private val resolved = store.preference(PodcastsPromoSettings.Resolved)
    private val shownLogged = store.preference(PodcastsPromoSettings.ShownLogged)

    /** Counts today as a day the app was opened, once per calendar day. */
    fun recordOpen() {
        val day = today()
        if (lastDayOpened.value == day) return
        lastDayOpened.value = day
        daysOpened.value += 1
    }

    fun isDue(): Boolean {
        if (resolved.value) return false
        val installed = context.packageManager.getLaunchIntentForPackage(ShuttlePodcastsLink.PACKAGE) != null
        return podcastsPromoDue(daysOpened.value, resolved.value, installed)
    }

    fun onShown() {
        if (shownLogged.value) return
        shownLogged.value = true
        analytics.capture("podcasts_promo_shown", mapOf("surface" to PromoSurface.Card.value))
    }

    fun onTapped(surface: PromoSurface) {
        if (surface == PromoSurface.Card) resolved.value = true
        analytics.capture("podcasts_promo_tapped", mapOf("surface" to surface.value))
        ShuttlePodcastsLink.open(context, surface)
    }

    fun onDismissed() {
        resolved.value = true
        analytics.capture("podcasts_promo_dismissed", mapOf("surface" to PromoSurface.Card.value))
    }
}
