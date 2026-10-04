package com.simplecityapps.mediaprovider

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class SyncPolicyTest {
    private val now = Instant.parse("2026-10-04T09:00:00Z")

    private fun plan(
        trigger: SyncTrigger = SyncTrigger.Foreground,
        incremental: Boolean = true,
        lastSync: Duration? = 1.hours,
        lastFullSync: Duration? = 1.days,
        songTagsOutdated: Boolean = false
    ) = SyncPolicy.plan(
        trigger = trigger,
        incremental = incremental,
        lastSyncStart = lastSync?.let { now - it },
        lastFullSyncStart = lastFullSync?.let { now - it },
        songTagsOutdated = songTagsOutdated,
        now = now
    )

    @Test
    fun `a server synced a while ago asks for what changed since - less the overlap`() {
        plan() shouldBe SyncPlan.Incremental(since = now - 1.hours - 10.minutes)
        plan(trigger = SyncTrigger.Periodic) shouldBe SyncPlan.Incremental(since = now - 1.hours - 10.minutes)
    }

    @Test
    fun `a source synced in the last 15 minutes is left alone on return to the app`() {
        plan(lastSync = 14.minutes).shouldBeNull()
        plan(lastSync = 15.minutes) shouldBe SyncPlan.Incremental(since = now - 15.minutes - 10.minutes)
    }

    @Test
    fun `the daily sync isn't throttled`() {
        plan(lastSync = 14.minutes, trigger = SyncTrigger.Periodic) shouldBe SyncPlan.Incremental(since = now - 14.minutes - 10.minutes)
        plan(lastSync = 14.minutes, incremental = false, trigger = SyncTrigger.Periodic) shouldBe SyncPlan.Full
    }

    @Test
    fun `a last sync in the future - from a clock set back - is synced in full rather than throttled`() {
        plan(lastSync = (-2).hours) shouldBe SyncPlan.Full
        plan(lastSync = (-2).hours, trigger = SyncTrigger.Periodic) shouldBe SyncPlan.Full
        plan(lastFullSync = (-2).hours) shouldBe SyncPlan.Full
    }

    @Test
    fun `a last sync a hair in the future is read as now`() {
        plan(lastSync = (-1).minutes).shouldBeNull()
        plan(lastSync = (-1).minutes, trigger = SyncTrigger.Periodic) shouldBe SyncPlan.Incremental(since = now - 10.minutes)
    }

    @Test
    fun `a server never synced - or not in full for a week - is synced in full`() {
        plan(lastSync = null, lastFullSync = null) shouldBe SyncPlan.Full
        plan(lastFullSync = null) shouldBe SyncPlan.Full
        plan(lastFullSync = 7.days) shouldBe SyncPlan.Full
        plan(lastFullSync = 7.days - 1.minutes) shouldBe SyncPlan.Incremental(since = now - 1.hours - 10.minutes)
    }

    @Test
    fun `a server whose songs lack tags this build reads is synced in full`() {
        plan(songTagsOutdated = true) shouldBe SyncPlan.Full
    }

    @Test
    fun `this device's songs are left alone on return to the app and read in full by the daily sync`() {
        plan(incremental = false).shouldBeNull()
        plan(incremental = false, trigger = SyncTrigger.Periodic) shouldBe SyncPlan.Full
        plan(incremental = false, trigger = SyncTrigger.Periodic, lastSync = null, lastFullSync = null) shouldBe SyncPlan.Full
    }
}
