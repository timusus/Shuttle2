package com.simplecityapps.shuttle.dagger

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import dagger.Module
import dagger.Provides

@Module
class PersistenceModule(private val context: Context) {

    @AppScope
    @Provides
    fun provideSharedPrefs(): SharedPreferences {
        return PreferenceManager.getDefaultSharedPreferences(context)
    }

    @AppScope
    @Provides
    fun provideGeneralPreferenceManager(preference: SharedPreferences): GeneralPreferenceManager {
        return GeneralPreferenceManager(preference)
    }
}