package com.simplecityapps.mediaprovider.server

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile
import platform.posix.getenv

/**
 * Kotlin/Native bundles no test resources, so fixtures are read from the module's source tree, which the simulator
 * shares with the host: the s2.kmp-library convention passes its `src/commonTest/resources` as `S2_TEST_RESOURCES`.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual fun readFixture(path: String): String {
    val resources = checkNotNull(getenv("S2_TEST_RESOURCES")?.toKString()) { "S2_TEST_RESOURCES isn't set" }
    return checkNotNull(NSString.stringWithContentsOfFile("$resources/$path", NSUTF8StringEncoding, null)) { "No fixture $path" }
}
