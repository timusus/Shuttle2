package com.simplecityapps.shuttle.analytics

/**
 * Product analytics events. The app sends them to PostHog, and only while the user has opted in to analytics:
 * until then, and after an opt-out, [capture] drops them.
 */
interface Analytics {
    fun capture(
        event: String,
        properties: Map<String, Any> = emptyMap()
    )

    /** A super property sent with every later event, replacing an earlier value of the same [name]. */
    fun register(
        name: String,
        value: Any
    )
}
