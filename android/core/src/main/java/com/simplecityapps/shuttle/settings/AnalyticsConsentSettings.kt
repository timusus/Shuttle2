package com.simplecityapps.shuttle.settings

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Analytics consent history. [asked] is a leftover from the deleted Home consent card (#421), kept so
 * InstallDefaults can migrate anyone who answered or dismissed it into an explicit opt-out (#481).
 * [noticeShown] tracks the one-time notice that replaced the card.
 */
@Singleton
class AnalyticsConsentSettings @Inject constructor(
    store: SettingsStore
) {
    /** Answered, by Share, No thanks or dismissing the deleted card. */
    val asked = store.preference(Asked)

    /** Whether the one-time "analytics is now on" notice has been shown (or never applies) for this install. */
    val noticeShown = store.preference(NoticeShown)

    companion object {
        val Asked = Setting.boolean("pref_analytics_consent_asked", false)
        val NoticeShown = Setting.boolean("pref_analytics_notice_shown", false)
    }
}
