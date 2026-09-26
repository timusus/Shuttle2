package com.simplecityapps.shuttle.entitlement

import com.simplecityapps.shuttle.di.AppCoroutineScope
import com.simplecityapps.shuttle.ui.shell.player.ObserveGatedServerSkip
import com.simplecityapps.trial.Entitlement
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The Hilt bindings that need [Entitlement] (`:android:trial`): kept out of `ui` sources (`UiModuleRules`) so
 * the screens that need entitlement state depend on a domain port instead.
 */
@Module
@InstallIn(SingletonComponent::class)
object EntitlementBindsModule {
    @Provides
    @Singleton
    fun provideObserveServerStreamingNeedsPro(
        entitlement: @JvmSuppressWildcards StateFlow<Entitlement>,
        @AppCoroutineScope appCoroutineScope: CoroutineScope
    ): ObserveServerStreamingNeedsPro {
        val needsPro = entitlement.map { it is Entitlement.Free }
            .stateIn(appCoroutineScope, SharingStarted.Eagerly, entitlement.value is Entitlement.Free)
        return ObserveServerStreamingNeedsPro { needsPro }
    }

    @Provides
    fun provideObserveGatedServerSkip(policy: EntitledServerStreamPolicy): ObserveGatedServerSkip = ObserveGatedServerSkip { policy.gatedSongs }
}
