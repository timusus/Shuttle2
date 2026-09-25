package com.simplecityapps.shuttle.settings

import javax.inject.Inject
import javax.inject.Singleton

/** The Home analytics consent card's own state (#421): whether it's been answered, and how many days it's counted. */
@Singleton
class AnalyticsConsentSettings @Inject constructor(
    store: SettingsStore
) {
    /** Answered, by Share, No thanks or dismissing the card; once true, the card never shows again. */
    val asked = store.preference(Asked)

    /** Days the app has been opened while the card hadn't been answered yet, counted once per calendar day. */
    val daysOpened = store.preference(DaysOpened)

    /** The epoch day [daysOpened] was last incremented on, so a day is never counted twice. */
    val lastCountedEpochDay = store.preference(LastCountedEpochDay)

    companion object {
        val Asked = Setting.boolean("pref_analytics_consent_asked", false)
        val DaysOpened = Setting.int("pref_analytics_consent_days_opened", 0)
        val LastCountedEpochDay = Setting.int("pref_analytics_consent_last_counted_epoch_day", -1)
    }
}
