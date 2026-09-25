package com.simplecityapps.trial

/**
 * Play product IDs that grant S2 Pro.
 *
 * The `s2_pro*` products are the ones on sale. The legacy products are no longer offered, but anyone who owns
 * one keeps Pro for as long as the purchase is active (grandfathering), so they stay in the entitlement set.
 */
object ProductIds {
    /** One-time purchase of S2 Pro. */
    const val PRO_LIFETIME = "s2_pro_lifetime"

    /** The S2 Pro subscription. Its only base plan is [PRO_BASE_PLAN_ANNUAL]; there is no monthly plan. */
    const val PRO_SUBSCRIPTION = "s2_pro"
    const val PRO_BASE_PLAN_ANNUAL = "annual"

    const val LEGACY_LIFETIME = "s2_iap_full_version"
    const val LEGACY_LIFETIME_LOW = "s2_iap_full_version_low"
    const val LEGACY_SUBSCRIPTION_MONTHLY = "s2_subscription_full_version_monthly"
    const val LEGACY_SUBSCRIPTION_YEARLY = "s2_subscription_full_version_yearly"
    const val LEGACY_SUBSCRIPTION_YEARLY_LOW = "s2_subscription_full_version_yearly_low"

    val oneTimeProducts = listOf(PRO_LIFETIME, LEGACY_LIFETIME, LEGACY_LIFETIME_LOW)

    val subscriptions = listOf(PRO_SUBSCRIPTION, LEGACY_SUBSCRIPTION_MONTHLY, LEGACY_SUBSCRIPTION_YEARLY, LEGACY_SUBSCRIPTION_YEARLY_LOW)

    /** The legacy products the old paywall offers until the `s2_pro*` products exist in Play Console. */
    val legacyOffered = listOf(LEGACY_SUBSCRIPTION_YEARLY_LOW, LEGACY_LIFETIME_LOW)

    /** Where Pro comes from when [productId] is owned, or null if it doesn't grant Pro. */
    fun proSource(productId: String): ProSource? = when (productId) {
        PRO_LIFETIME -> ProSource.Lifetime
        PRO_SUBSCRIPTION -> ProSource.Subscription
        LEGACY_LIFETIME, LEGACY_LIFETIME_LOW -> ProSource.LegacyLifetime
        LEGACY_SUBSCRIPTION_MONTHLY, LEGACY_SUBSCRIPTION_YEARLY, LEGACY_SUBSCRIPTION_YEARLY_LOW -> ProSource.LegacySubscription
        else -> null
    }
}
