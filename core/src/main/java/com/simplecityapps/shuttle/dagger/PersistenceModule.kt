package com.simplecityapps.shuttle.dagger

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import dagger.Module
import dagger.Provides

@Module
class PersistenceModule {

    @AppScope
    @Provides
    fun provideSharedPrefs(context: Context): SharedPreferences {
        return PreferenceManager.getDefaultSharedPreferences(context)
    }

    @AppScope
    @Provides
    fun provideGeneralPreferenceManager(preference: SharedPreferences): GeneralPreferenceManager {
        return GeneralPreferenceManager(preference)
    }

    @AppScope
    @Provides
    fun provideSecurePreferenceManager(context: Context): SecurePreferenceManager {
        val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return SecurePreferenceManager(
            EncryptedSharedPreferences.create(
                context,
                "encrypted_preferences",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        )
    }
}