package com.simplecityapps.shuttle.dagger

import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager

interface GeneralPreferenceManagerProvider {
    fun provideGeneralPreferenceManager(): GeneralPreferenceManager
}