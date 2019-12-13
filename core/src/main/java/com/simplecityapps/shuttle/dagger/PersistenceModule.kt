package com.simplecityapps.shuttle.dagger

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import com.simplecityapps.shuttle.GeneralPreferenceManager
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class PersistenceModule(private val context: Context) {

    @Singleton
    @Provides
    fun provideSharedPrefs(): SharedPreferences {
        return PreferenceManager.getDefaultSharedPreferences(context)
    }

    @Singleton
    @Provides
    fun provideGeneralPreferenceManager(preference: SharedPreferences): GeneralPreferenceManager {
        return GeneralPreferenceManager(preference)
    }
}