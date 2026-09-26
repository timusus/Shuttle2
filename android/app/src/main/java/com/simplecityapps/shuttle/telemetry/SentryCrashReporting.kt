package com.simplecityapps.shuttle.telemetry

import android.app.Application
import com.simplecityapps.shuttle.BuildConfig
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.android.core.SentryAndroid
import io.sentry.android.core.SentryAndroidOptions
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * Sentry crash and ANR reporting. Not started until the user allows crash reporting ([TelemetryConsentGate]), and
 * closed again if they turn it off, which uninstalls its crash handler. Without a DSN (local builds, tests) it never
 * starts at all.
 */
@Singleton
class SentryCrashReporting @Inject constructor(
    private val application: Application
) : CrashReportingSdk {
    @Volatile
    private var enabled = false

    @Synchronized
    override fun setEnabled(enabled: Boolean) {
        if (BuildConfig.SENTRY_DSN.isBlank() || enabled == this.enabled) return
        this.enabled = enabled
        if (enabled) {
            try {
                SentryAndroid.init(application, ::configure)
            } catch (e: Exception) {
                Timber.e(e, "Failed to initialise Sentry")
            }
        } else {
            Sentry.close()
        }
    }

    private fun configure(options: SentryAndroidOptions) {
        options.dsn = BuildConfig.SENTRY_DSN
        options.environment = if (BuildConfig.DEBUG) "development" else "production"
        options.release = "${BuildConfig.APPLICATION_ID}@${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}"
        options.isDebug = BuildConfig.DEBUG
        options.setDiagnosticLevel(if (BuildConfig.DEBUG) SentryLevel.DEBUG else SentryLevel.ERROR)
        options.isAnrEnabled = true
        options.isEnableAutoSessionTracking = true
        // Crashes only: no tracing or profiling, and no user identity
        options.isSendDefaultPii = false
        // Belt and braces: an event raised while the SDK closes after an opt-out is dropped
        options.beforeSend = SentryOptions.BeforeSendCallback { event, _ -> event.takeIf { enabled } }
    }
}

/**
 * Leaves each logged error as a Sentry breadcrumb, so a crash report shows what went wrong just before it. A no-op
 * while Sentry isn't running.
 */
class SentryBreadcrumbTree : Timber.Tree() {
    override fun log(
        priority: Int,
        tag: String?,
        message: String,
        t: Throwable?
    ) {
        if (t == null) return
        Sentry.addBreadcrumb(
            Breadcrumb.error("tag: $tag, message: $message, throwable: ${t.message}")
        )
    }
}
