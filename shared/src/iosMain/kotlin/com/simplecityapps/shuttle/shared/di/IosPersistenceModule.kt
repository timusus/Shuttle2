@file:OptIn(ExperimentalNativeApi::class)

package com.simplecityapps.shuttle.shared.di

import com.simplecityapps.localmediaprovider.local.data.room.DatabaseProvider
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import com.simplecityapps.localmediaprovider.local.data.room.databaseBuilder
import com.simplecityapps.localmediaprovider.local.data.room.inMemoryDatabaseBuilder
import com.simplecityapps.shuttle.persistence.KeyValueStore
import com.simplecityapps.shuttle.persistence.KeychainSecureStore
import com.simplecityapps.shuttle.persistence.SecureStore
import com.simplecityapps.shuttle.persistence.UserDefaultsKeyValueStore
import com.simplecityapps.shuttle.shared.IosStorage
import com.simplecityapps.shuttle.shared.settings.IosSettingDefaults
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform

/** Where iOS keeps things: preferences in `UserDefaults`, secrets in the Keychain, the library in Room. */
@ContributesTo(AppScope::class)
@BindingContainer
class IosPersistenceModule {
    @Provides
    @SingleIn(AppScope::class)
    fun provideKeyValueStore(storage: IosStorage): KeyValueStore {
        IosSettingDefaults.register()
        return UserDefaultsKeyValueStore(storage.isolatedName)
    }

    @Provides
    @SingleIn(AppScope::class)
    fun provideSecureStore(): SecureStore = KeychainSecureStore()

    /**
     * A missing migration fails a debug binary loudly and gives a release one a fresh database, as on Android. An
     * isolated graph's library is in memory, so it starts empty and no two graphs share one.
     */
    @Provides
    @SingleIn(AppScope::class)
    fun provideMediaDatabase(storage: IosStorage): MediaDatabase {
        val builder = if (storage.isolatedName == null) databaseBuilder() else inMemoryDatabaseBuilder()
        return DatabaseProvider(builder, isDebug = Platform.isDebugBinary).database
    }
}
