package com.simplecityapps.shuttle.logging

import timber.log.Timber

internal actual fun platformLogger(tag: String): Logger = TimberLogger(tag)

/**
 * Logs through Timber under [tag], to the trees the app plants (the debug tree, the Sentry breadcrumb tree). An
 * explicit tag, since Timber's own class-name tag would name this class.
 */
class TimberLogger(
    private val tag: String
) : Logger {
    override fun debug(
        throwable: Throwable?,
        message: () -> String
    ) {
        if (Timber.treeCount > 0) Timber.tag(tag).d(throwable, message())
    }

    override fun info(
        throwable: Throwable?,
        message: () -> String
    ) {
        if (Timber.treeCount > 0) Timber.tag(tag).i(throwable, message())
    }

    override fun warn(
        throwable: Throwable?,
        message: () -> String
    ) {
        if (Timber.treeCount > 0) Timber.tag(tag).w(throwable, message())
    }

    override fun error(
        throwable: Throwable?,
        message: () -> String
    ) {
        if (Timber.treeCount > 0) Timber.tag(tag).e(throwable, message())
    }
}
