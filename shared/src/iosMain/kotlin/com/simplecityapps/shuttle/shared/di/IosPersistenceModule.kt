@file:OptIn(ExperimentalNativeApi::class)

package com.simplecityapps.shuttle.shared.di

import com.simplecityapps.localmediaprovider.local.data.room.DatabaseProvider
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import com.simplecityapps.localmediaprovider.local.data.room.databaseBuilder
import com.simplecityapps.shuttle.persistence.KeyValueStore
import com.simplecityapps.shuttle.persistence.KeychainSecureStore
import com.simplecityapps.shuttle.persistence.SecureStore
import com.simplecityapps.shuttle.persistence.UserDefaultsKeyValueStore
import com.simplecityapps.shuttle.shared.IosPreferencesSuite
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
    fun provideKeyValueStore(preferencesSuite: IosPreferencesSuite): KeyValueStore = UserDefaultsKeyValueStore(preferencesSuite.name)

    @Provides
    @SingleIn(AppScope::class)
    fun provideSecureStore(): SecureStore = KeychainSecureStore()

    /** A missing migration fails a debug binary loudly and gives a release one a fresh database, as on Android. */
    @Provides
    @SingleIn(AppScope::class)
    fun provideMediaDatabase(): MediaDatabase = DatabaseProvider(databaseBuilder(), isDebug = Platform.isDebugBinary).database
}
