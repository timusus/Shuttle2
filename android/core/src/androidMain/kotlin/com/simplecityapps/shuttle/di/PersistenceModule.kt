package com.simplecityapps.shuttle.di

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.simplecityapps.shuttle.logging.Logger
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.persistence.KeyValueStore
import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import com.simplecityapps.shuttle.persistence.SecureStore
import com.simplecityapps.shuttle.persistence.SharedPreferencesKeyValueStore
import com.simplecityapps.shuttle.persistence.SharedPreferencesSecureStore
import com.simplecityapps.shuttle.settings.defaultSharedPreferences
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

@ContributesTo(AppScope::class)
@BindingContainer
class PersistenceModule {
    @SingleIn(AppScope::class)
    @Provides
    fun provideKeyValueStore(
        @ApplicationContext context: Context
    ): KeyValueStore = SharedPreferencesKeyValueStore(context.defaultSharedPreferences())

    @SingleIn(AppScope::class)
    @Provides
    fun provideGeneralPreferenceManager(store: KeyValueStore): GeneralPreferenceManager = GeneralPreferenceManager(store)

    @SingleIn(AppScope::class)
    @Provides
    fun provideSecurePreferenceManager(store: SecureStore): SecurePreferenceManager = SecurePreferenceManager(store)

    @SuppressLint("ApplySharedPref")
    @SingleIn(AppScope::class)
    @Provides
    fun provideSecureStore(
        @ApplicationContext context: Context
    ): SecureStore {
        var encryptedSharedPreferences = createEncryptedPreferences(context)
        if (encryptedSharedPreferences == null) {
            context.getSharedPreferences("encrypted_preferences", Context.MODE_PRIVATE).edit().clear().commit()
            encryptedSharedPreferences = createEncryptedPreferences(context)
        }
        return SharedPreferencesSecureStore(encryptedSharedPreferences ?: context.defaultSharedPreferences())
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
            logger.error { "Failed to create encrypted preferences" }
            null
        }
    }
}

private val logger = Logger.tagged("PersistenceModule")
