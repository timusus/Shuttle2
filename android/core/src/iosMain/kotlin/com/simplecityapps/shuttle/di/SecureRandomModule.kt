package com.simplecityapps.shuttle.di

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import kotlin.random.Random
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault

@ContributesTo(AppScope::class)
@BindingContainer
class SecureRandomModule {
    @Provides
    @SecureRandom
    fun provideSecureRandom(): Random = SecRandom
}

/** Kotlin/Native's default `Random` isn't cryptographically secure; the system's is. */
@OptIn(ExperimentalForeignApi::class)
private object SecRandom : Random() {
    override fun nextBits(bitCount: Int): Int {
        val bytes = ByteArray(Int.SIZE_BYTES)
        val status = bytes.usePinned { SecRandomCopyBytes(kSecRandomDefault, bytes.size.toULong(), it.addressOf(0)) }
        check(status == 0) { "SecRandomCopyBytes failed: $status" }
        val bits = bytes.fold(0) { acc, byte -> (acc shl 8) or (byte.toInt() and 0xFF) }
        return bits ushr (Int.SIZE_BITS - bitCount)
    }
}
