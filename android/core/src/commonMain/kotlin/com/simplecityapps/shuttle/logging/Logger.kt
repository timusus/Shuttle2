package com.simplecityapps.shuttle.logging

import kotlin.concurrent.Volatile

/**
 * A log for shared code, tagged with where it logs from: Timber on Android (whatever trees the app planted, the
 * Sentry breadcrumb tree included), os_log on iOS. Messages are lambdas, only built when something is listening.
 *
 * ```
 * private val logger = Logger.tagged("LocalSongRepository")
 * logger.error(e) { "Failed to update song" }
 * ```
 */
interface Logger {
    fun debug(
        throwable: Throwable? = null,
        message: () -> String
    )

    fun info(
        throwable: Throwable? = null,
        message: () -> String
    )

    fun warn(
        throwable: Throwable? = null,
        message: () -> String
    )

    fun error(
        throwable: Throwable? = null,
        message: () -> String
    )

    companion object {
        @Volatile
        private var factory: (tag: String) -> Logger = ::platformLogger

        /** Replaces the platform's logger, for every logger [tagged] hands out, before or after this call. */
        fun install(factory: (tag: String) -> Logger) {
            this.factory = factory
        }

        fun tagged(tag: String): Logger = InstalledLogger(tag)
    }

    /** Follows [install]: loggers are usually created before a test (or the iOS app) installs its own. */
    private class InstalledLogger(
        private val tag: String
    ) : Logger {
        private var resolved: Pair<(String) -> Logger, Logger>? = null

        private val delegate: Logger
            get() {
                val current = factory
                resolved?.let { (resolvedFactory, logger) -> if (resolvedFactory === current) return logger }
                return current(tag).also { resolved = current to it }
            }

        override fun debug(
            throwable: Throwable?,
            message: () -> String
        ) = delegate.debug(throwable, message)

        override fun info(
            throwable: Throwable?,
            message: () -> String
        ) = delegate.info(throwable, message)

        override fun warn(
            throwable: Throwable?,
            message: () -> String
        ) = delegate.warn(throwable, message)

        override fun error(
            throwable: Throwable?,
            message: () -> String
        ) = delegate.error(throwable, message)
    }
}

/** The platform's log, used until something else is [Logger.install]ed. */
internal expect fun platformLogger(tag: String): Logger
