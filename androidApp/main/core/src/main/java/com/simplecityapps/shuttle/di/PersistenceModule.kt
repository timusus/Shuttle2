package com.simplecityapps.shuttle.di

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import timber.log.Timber
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class PersistenceModule {

    @Singleton
    @Provides
    fun provideSharedPrefs(@ApplicationContext context: Context): SharedPreferences {
        return PreferenceManager.getDefaultSharedPreferences(context)
    }

    @Singleton
    @Provides
    fun provideGeneralPreferenceManager(preference: SharedPreferences): GeneralPreferenceManager {
        return GeneralPreferenceManager(preference)
    }

    @SuppressLint("ApplySharedPref")
    @Singleton
    @Provides
    fun provideSecurePreferenceManager(@ApplicationContext context: Context): SecurePreferenceManager {
        var encryptedSharedPreferences = createEncryptedPreferences(context)
        if (encryptedSharedPreferences == null) {
            context.getSharedPreferences("encrypted_preferences", Context.MODE_PRIVATE).edit().clear().commit()
            encryptedSharedPreferences = createEncryptedPreferences(context)
        }
        return SecurePreferenceManager(encryptedSharedPreferences ?: PreferenceManager.getDefaultSharedPreferences(context))
    }

    @Synchronized
    private fun createEncryptedPreferences(context: Context): SharedPreferences? {
        val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return try {
            EncryptedSharedPreferences.create(
                context,
                "encrypted_preferences",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Timber.e("Failed to create encrypted preferences")
            null
        }
    }
}
