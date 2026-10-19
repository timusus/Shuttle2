package com.simplecityapps.shuttle.di

import dev.zacsweers.metro.Qualifier

/** A cryptographically secure `Random`, for keys and salts; the unqualified `Random` is predictable. */
@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class SecureRandom
