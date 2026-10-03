@file:OptIn(ExperimentalNativeApi::class)

package com.simplecityapps.shuttle.shared.di

import com.simplecityapps.shuttle.di.AppCoroutineScope
import com.simplecityapps.shuttle.entitlement.Entitlement
import com.simplecityapps.shuttle.entitlement.ObservePaywallRequests
import com.simplecityapps.shuttle.entitlement.ObserveServerStreamingNeedsPro
import com.simplecityapps.shuttle.entitlement.ServerAccessGate
import com.simplecityapps.shuttle.entitlement.TryAddServer
import com.simplecityapps.shuttle.entitlement.TryDownloadFromServer
import com.simplecityapps.shuttle.shared.entitlement.GatedServerStreams
import com.simplecityapps.shuttle.shared.entitlement.StoreEntitlements
import com.simplecityapps.shuttle.ui.shell.player.ObserveGatedServerSkip
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Shuttle Music Pro on iOS: the entitlement StoreKit answers ([StoreEntitlements], fed by Swift's `StoreKitManager`)
 * behind the same [ServerAccessGate] Android uses. The trial is a free App Store purchase the user starts from the
 * paywall, so the gate never starts it itself: a stream or download before the trial refuses and opens the paywall.
 * StoreKit answers from its on-device cache soon after launch; until it has, a stream waits up to
 * [STORE_ANSWER_WAIT] for it rather than refusing a purchaser.
 */
@ContributesTo(AppScope::class)
@BindingContainer
class IosEntitlementModule {
    @Provides
    @SingleIn(AppScope::class)
    fun provideStoreEntitlements(
        @AppCoroutineScope appCoroutineScope: CoroutineScope
    ): StoreEntitlements = StoreEntitlements(Clock.System, appCoroutineScope, isDebug = Platform.isDebugBinary)

    @Provides
    @SingleIn(AppScope::class)
    fun provideServerAccessGate(storeEntitlements: StoreEntitlements): ServerAccessGate = ServerAccessGate(
        storeEntitlements.entitlement,
        startTrial = null,
        storeAnswerWait = STORE_ANSWER_WAIT
    )

    @Provides
    @SingleIn(AppScope::class)
    fun provideGatedServerStreams(gate: ServerAccessGate): GatedServerStreams = GatedServerStreams(gate)

    @Provides
    fun provideTryAddServer(gate: ServerAccessGate): TryAddServer = TryAddServer(gate::tryAddServer)

    @Provides
    fun provideTryDownloadFromServer(gate: ServerAccessGate): TryDownloadFromServer = TryDownloadFromServer(gate::tryDownloadFromServer)

    @Provides
    fun provideObservePaywallRequests(gate: ServerAccessGate): ObservePaywallRequests = ObservePaywallRequests { gate.paywallRequests }

    /** The sign-in discloses that streaming needs Pro to anyone without Pro or a running trial. */
    @Provides
    @SingleIn(AppScope::class)
    fun provideObserveServerStreamingNeedsPro(
        storeEntitlements: StoreEntitlements,
        @AppCoroutineScope appCoroutineScope: CoroutineScope
    ): ObserveServerStreamingNeedsPro {
        val entitlement = storeEntitlements.entitlement
        val needsPro = entitlement.map { it is Entitlement.Free }
            .stateIn(appCoroutineScope, SharingStarted.Eagerly, entitlement.value is Entitlement.Free)
        return ObserveServerStreamingNeedsPro { needsPro }
    }

    @Provides
    fun provideObserveGatedServerSkip(streams: GatedServerStreams): ObserveGatedServerSkip = ObserveGatedServerSkip { streams.gatedSongs }
}

private val STORE_ANSWER_WAIT = 5.seconds
