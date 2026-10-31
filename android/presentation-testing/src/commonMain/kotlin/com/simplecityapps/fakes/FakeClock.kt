package com.simplecityapps.fakes

import kotlin.time.Clock
import kotlin.time.Instant

/** A test double for [Clock] whose [time] can be set or advanced deterministically. */
class FakeClock(var time: Instant) : Clock {
    override fun now(): Instant = time
}
