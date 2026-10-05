package com.simplecityapps.shuttle.analytics

import com.simplecityapps.shuttle.entitlement.Entitlement
import com.simplecityapps.shuttle.entitlement.PaywallSource
import com.simplecityapps.shuttle.entitlement.ProSource
import com.simplecityapps.shuttle.model.MediaProviderType
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * The paywall, purchase, server sign-in and onboarding events, shared by Android and iOS (#776). Every property is an
 * enum's value, a product id or a provider type: never free text, so no error message, address or name is sent.
 */
@SingleIn(AppScope::class)
class MonetisationAnalytics
@Inject
constructor(
    private val analytics: Analytics
) {
    fun paywallShown(source: PaywallSource) = analytics.capture("paywall_shown", mapOf("source" to source.value))

    /** The paywall closed without a purchase or a trial. */
    fun paywallDismissed(source: PaywallSource) = analytics.capture("paywall_dismissed", mapOf("source" to source.value))

    fun purchaseStarted(productId: String) = analytics.capture("purchase_started", mapOf("product" to productId))

    fun purchaseCompleted(productId: String) = analytics.capture("purchase_completed", mapOf("product" to productId))

    fun purchaseFailed(
        productId: String,
        reason: PurchaseFailureReason
    ) = analytics.capture("purchase_failed", mapOf("product" to productId, "reason" to reason.value))

    /** A restore found an earlier purchase or trial of [productId]. */
    fun purchaseRestored(productId: String) = analytics.capture("purchase_restored", mapOf("product" to productId))

    fun trialStarted() = analytics.capture("trial_started")

    fun serverConnected(
        type: MediaProviderType,
        method: SignInMethod = SignInMethod.Password
    ) = analytics.capture("server_connected", mapOf("type" to type.analyticsValue, "method" to method.value))

    fun signInFailed(
        type: MediaProviderType,
        method: SignInMethod,
        reason: SignInFailureReason
    ) = analytics.capture(
        "sign_in_failed",
        mapOf("type" to type.analyticsValue, "method" to method.value, "reason" to reason.value)
    )

    /** The first-run setup finished: [path] is how (iOS only; Android has no first run). */
    fun onboardingCompleted(path: OnboardingPath) = analytics.capture("onboarding_completed", mapOf("path" to path.value))

    /**
     * The first time the entitlement is known on this install; [source] is where it came from: none, trial, pro, legacy
     * (an old purchase) or debug. Tells how many existing buyers there are, with [mediaSourcesChanged] for what they use.
     * Returns whether an enabled backend took the event; false means the caller should offer it again later.
     */
    fun entitlementResolved(entitlement: Entitlement): Boolean {
        if (!analytics.isCapturing) return false
        analytics.capture("entitlement_resolved", mapOf("source" to entitlement.analyticsSource))
        return true
    }

    /** The `media_sources` property: which kinds of source are set up, sent with every later event. */
    fun mediaSourcesChanged(types: Collection<MediaProviderType>) = analytics.register("media_sources", mediaSourcesValue(types))

    private val MediaProviderType.analyticsValue: String get() = name.lowercase()
}

/** The `media_sources` value: the configured source kinds, sorted and comma-separated; "none" when there are no sources. */
fun mediaSourcesValue(types: Collection<MediaProviderType>): String = types
    .map { if (it.remote) it.name.lowercase() else "local" }
    .distinct()
    .sorted()
    .joinToString(",")
    .ifEmpty { "none" }

private val Entitlement.analyticsSource: String
    get() = when (this) {
        is Entitlement.Unknown, is Entitlement.Free -> "none"

        is Entitlement.Trial -> "trial"

        is Entitlement.Pro -> when (source) {
            ProSource.Lifetime, ProSource.Subscription -> "pro"
            ProSource.LegacyLifetime, ProSource.LegacySubscription -> "legacy"
            ProSource.Debug -> "debug"
        }
    }

/** Why a purchase didn't complete. */
enum class PurchaseFailureReason(
    val value: String
) {
    /** The user backed out of the store's sheet. */
    Cancelled("cancelled"),

    /** Waiting on approval (Ask to Buy, a deferred payment). */
    Pending("pending"),

    /** The store refused it, couldn't be reached, or the transaction didn't verify. */
    Failed("error"),
}

/** How a server sign-in was made. */
enum class SignInMethod(
    val value: String
) {
    /** Username and password. */
    Password("password"),

    /** Jellyfin Quick Connect: a code approved in another client. */
    QuickConnect("quick_connect"),

    /** Plex: a plex.tv PIN approved on the web, then one of the account's servers. */
    Pin("pin"),
}

/** Why a server sign-in failed, as a bucket: the error's own text never leaves the device. */
enum class SignInFailureReason(
    val value: String
) {
    /** The address didn't answer: a wrong host or port, or the server is down. */
    Unreachable("unreachable"),

    /** The device has no internet connection. */
    Offline("offline"),

    /** The TLS handshake failed: an untrusted or mismatched certificate. */
    Tls("tls"),

    Timeout("timeout"),

    /** The server refused the credentials (401 or 403). */
    Auth("auth"),

    /** The server answered with a 5xx. */
    ServerError("server_error"),

    /** A Quick Connect code or sign-in PIN ran out before it was approved. */
    Expired("expired"),

    /** A Quick Connect code was denied in the other client. */
    Denied("denied"),

    Other("other"),
}

/** Sorts a sign-in failure into its [SignInFailureReason]; bound where the network's error types are visible. */
fun interface SignInFailureClassifier {
    fun classify(error: Throwable): SignInFailureReason
}

/** How the first-run setup finished. */
enum class OnboardingPath(
    val value: String
) {
    /** A Jellyfin, Emby or Plex server was connected. */
    Server("server"),

    /** The music on this device was chosen. */
    ThisDevice("local"),

    /** The user skipped or closed the setup. */
    Skipped("skipped"),
}
