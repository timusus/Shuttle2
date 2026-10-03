package com.simplecityapps.shuttle.shared.telemetry

import com.simplecityapps.shuttle.entitlement.Entitlement
import com.simplecityapps.shuttle.entitlement.ProSource
import com.simplecityapps.shuttle.logging.Logger
import com.simplecityapps.shuttle.model.MediaProviderType
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Instant

class IosTelemetryStartupTest {
    @Test
    fun superPropertiesCarryThePlatformBuildProStateAndSourceTypes() {
        IosTelemetryStartup.superProperties(
            entitlement = Entitlement.Pro(ProSource.Lifetime),
            sources = listOf(MediaProviderType.Plex, MediaProviderType.Shuttle, MediaProviderType.Jellyfin),
            appVersion = "1.0",
            build = "42",
        ) shouldBe mapOf(
            "platform" to "ios",
            "app_version" to "1.0",
            "build" to "42",
            "pro_state" to "pro",
            "source_types" to listOf("jellyfin", "local", "plex"),
        )
    }

    @Test
    fun proStateBucketsEachEntitlement() {
        fun proState(entitlement: Entitlement) = IosTelemetryStartup.superProperties(entitlement, emptyList(), "1", "1")["pro_state"]

        proState(Entitlement.Unknown) shouldBe "unknown"
        proState(Entitlement.Free(trialUsed = false)) shouldBe "free"
        proState(Entitlement.Free(trialUsed = true)) shouldBe "trial_ended"
        proState(Entitlement.Trial(Instant.fromEpochSeconds(0))) shouldBe "trial"
    }

    @Test
    fun warningsAndErrorsBecomeScrubbedBreadcrumbsAndTheRestOnlyLogs() {
        val reporter = RecordingCrashReporter()
        val logged = mutableListOf<String>()
        val delegate = object : Logger {
            override fun debug(
                throwable: Throwable?,
                message: () -> String
            ) {
                logged += message()
            }

            override fun info(
                throwable: Throwable?,
                message: () -> String
            ) {
                logged += message()
            }

            override fun warn(
                throwable: Throwable?,
                message: () -> String
            ) {
                logged += message()
            }

            override fun error(
                throwable: Throwable?,
                message: () -> String
            ) {
                logged += message()
            }
        }
        val logger = BreadcrumbLogger(delegate, "Sync", reporter)

        logger.debug { "debug" }
        logger.info { "info" }
        logger.warn { "Retrying https://music.example.com/Items" }
        logger.error(IllegalStateException("token=abc123")) { "Sync failed" }

        logged shouldBe listOf("debug", "info", "Retrying https://music.example.com/Items", "Sync failed")
        reporter.breadcrumbs shouldBe listOf(
            Triple("Sync", "Retrying <url>", false),
            Triple("Sync", "Sync failed (IllegalStateException: token=<redacted>)", true),
        )
    }

    private class RecordingCrashReporter : IosCrashReporter {
        val breadcrumbs = mutableListOf<Triple<String, String, Boolean>>()

        override fun setEnabled(enabled: Boolean) = Unit

        override fun addBreadcrumb(
            category: String,
            message: String,
            isError: Boolean
        ) {
            breadcrumbs += Triple(category, message, isError)
        }
    }
}
