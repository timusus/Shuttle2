package com.simplecityapps.shuttle.entitlement

import com.simplecityapps.shuttle.di.AppCoroutineScope
import com.simplecityapps.shuttle.entitlement.Entitlement
import com.simplecityapps.shuttle.entitlement.ServerAccessGate
import com.simplecityapps.shuttle.ui.shell.player.ObserveGatedServerSkip
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The bindings that need [Entitlement] (`:android:trial`): kept out of `ui` sources (`UiModuleRules`) so
 * the screens that need entitlement state depend on a domain port instead.
 */
@BindingContainer
@ContributesTo(AppScope::class)
object EntitlementBindsModule {
    @Provides
    @SingleIn(AppScope::class)
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

    @Provides
    fun provideTryAddServer(serverAccessGate: ServerAccessGate): TryAddServer = TryAddServer(serverAccessGate::tryAddServer)

    @Provides
    fun provideTryDownloadFromServer(serverAccessGate: ServerAccessGate): TryDownloadFromServer = TryDownloadFromServer(serverAccessGate::tryDownloadFromServer)

    @Provides
    fun provideObservePaywallRequests(serverAccessGate: ServerAccessGate): ObservePaywallRequests = ObservePaywallRequests { serverAccessGate.paywallRequests }

    @Provides
    fun provideTryUseProFeature(serverAccessGate: ServerAccessGate): TryUseProFeature = TryUseProFeature { feature -> serverAccessGate.tryUse(feature) }

    @Provides
    fun provideTrialDisclosures(serverAccessGate: ServerAccessGate): TrialDisclosures = serverAccessGate
}
