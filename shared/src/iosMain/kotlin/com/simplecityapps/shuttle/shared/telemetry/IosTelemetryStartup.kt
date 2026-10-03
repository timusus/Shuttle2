package com.simplecityapps.shuttle.shared.telemetry

import com.simplecityapps.shuttle.di.AppCoroutineScope
import com.simplecityapps.shuttle.entitlement.Entitlement
import com.simplecityapps.shuttle.logging.Logger
import com.simplecityapps.shuttle.logging.OsLogLogger
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.shared.entitlement.StoreEntitlements
import com.simplecityapps.shuttle.telemetry.TelemetryConsentGate
import com.simplecityapps.shuttle.telemetry.TelemetryScrubber
import com.simplecityapps.shuttle.ui.screens.sources.MediaSources
import com.simplecityapps.shuttle.ui.screens.sources.isLocal
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import platform.Foundation.NSBundle

/**
 * Starts iOS telemetry, once, at launch (`AppGraph.initialize()`): logged warnings and errors become Sentry
 * breadcrumbs, the consent gate applies the stored choices and follows them, and PostHog's super properties follow
 * the entitlement and the enabled sources.
 */
@SingleIn(AppScope::class)
class IosTelemetryStartup @Inject constructor(
    private val telemetry: IosTelemetry,
    private val consentGate: TelemetryConsentGate,
    private val entitlements: StoreEntitlements,
    private val mediaSources: MediaSources,
    @AppCoroutineScope private val scope: CoroutineScope,
) {
    private var started = false

    fun start() {
        if (started) return
        started = true
        Logger.install { tag -> BreadcrumbLogger(OsLogLogger(tag), tag, telemetry.crashReporter) }
        consentGate.start()
        scope.launch {
            combine(entitlements.entitlement, mediaSources.enabledTypes, ::superProperties)
                .distinctUntilChanged()
                .collect(telemetry.analytics::register)
        }
    }

    companion object {
        /** What every event carries: never an identity, only the app, its build and coarse state. */
        fun superProperties(
            entitlement: Entitlement,
            sources: List<MediaProviderType>,
            appVersion: String = bundleString("CFBundleShortVersionString"),
            build: String = bundleString("CFBundleVersion"),
        ): Map<String, Any> = mapOf(
            "platform" to "ios",
            "app_version" to appVersion,
            "build" to build,
            "pro_state" to entitlement.proState,
            "source_types" to sources.map { if (it.isLocal) "local" else it.name.lowercase() }.distinct().sorted(),
        )

        private val Entitlement.proState: String
            get() = when (this) {
                Entitlement.Unknown -> "unknown"
                is Entitlement.Free -> if (trialUsed) "trial_ended" else "free"
                is Entitlement.Trial -> "trial"
                is Entitlement.Pro -> "pro"
            }

        private fun bundleString(key: String): String = NSBundle.mainBundle.objectForInfoDictionaryKey(key) as? String ?: "unknown"
    }
}

/** Logs to [delegate], and leaves each warning and error, scrubbed, as a Sentry breadcrumb. */
internal class BreadcrumbLogger(
    private val delegate: Logger,
    private val tag: String,
    private val crashReporter: IosCrashReporter,
) : Logger by delegate {
    override fun warn(
        throwable: Throwable?,
        message: () -> String
    ) {
        delegate.warn(throwable, message)
        breadcrumb(throwable, message, isError = false)
    }

    override fun error(
        throwable: Throwable?,
        message: () -> String
    ) {
        delegate.error(throwable, message)
        breadcrumb(throwable, message, isError = true)
    }

    private fun breadcrumb(
        throwable: Throwable?,
        message: () -> String,
        isError: Boolean
    ) {
        val text = throwable?.let { "${message()} (${it::class.simpleName}: ${it.message})" } ?: message()
        crashReporter.addBreadcrumb(tag, TelemetryScrubber.scrub(text), isError)
    }
}
