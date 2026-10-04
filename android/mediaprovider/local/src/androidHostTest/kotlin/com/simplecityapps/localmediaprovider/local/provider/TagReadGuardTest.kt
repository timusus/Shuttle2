package com.simplecityapps.localmediaprovider.local.provider

import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** A guard over a fresh marker folder, as the process [pid], where Android says [crashedNatively] of a process that died. */
fun testTagReadGuard(
    preferences: GeneralPreferenceManager = GeneralPreferenceManager(InMemoryKeyValueStore()),
    markerDir: File = Files.createTempDirectory("tag-reads").toFile(),
    crashedNatively: (pid: Int) -> Boolean? = { null },
    pid: Int = 1,
    permits: Int = 3
) = TagReadGuard(markerDir, preferences, crashedNatively, TagReadLimiter(permits), pid)

class TagReadGuardTest {
    private val preferences = GeneralPreferenceManager(InMemoryKeyValueStore())
    private val markerDir: File = Files.createTempDirectory("tag-reads").toFile()
    private val a = TagReadFile("/music/a.flac", 100, 1_000)
    private val b = TagReadFile("/music/b.flac", 200, 2_000)

    private fun guard(
        pid: Int,
        crashedNatively: (pid: Int) -> Boolean? = { null }
    ) = testTagReadGuard(preferences, markerDir, crashedNatively, pid)

    private fun markers() = markerDir.listFiles().orEmpty().map { it.readText() }

    /** Starts reads of [files] in a process [pid] that dies while they run, leaving their markers behind. */
    private fun crashDuring(
        vararg files: TagReadFile,
        pid: Int = 1
    ) = runBlocking {
        val guard = guard(pid)
        val started = files.map { CompletableDeferred<Unit>() }
        val jobs = files.mapIndexed { index, file -> launch { guard.read(file) { started[index].complete(Unit).also { CompletableDeferred<Unit>().await() } } } }
        started.awaitAll()
        // The process is gone: its reads never finish, so their markers stay where the next one finds them
        val left = markerDir.listFiles().orEmpty().associateWith { it.readText() }
        jobs.forEach { it.cancelAndJoin() }
        left.forEach { (file, text) -> file.writeText(text) }
    }

    @Test
    fun `a read leaves a marker while it runs and none after`() = runTest {
        val guard = guard(pid = 7)
        var during = emptyList<String>()

        val tags = guard.read(a) { "tags".also { during = markers() } }

        tags shouldBe "tags"
        during shouldContainExactly listOf("7\n${a.key}")
        markers().shouldBeEmpty()
    }

    @Test
    fun `a read that throws takes its marker with it`() = runTest {
        val guard = guard(pid = 7)

        runCatching { guard.read(a) { error("corrupt") } }

        markers().shouldBeEmpty()
    }

    @Test
    fun `a marker that can't be written leaves the read to run unguarded`() = runTest {
        // A file where the marker folder should be, so writing a marker throws as a full disk would
        val blocked = File.createTempFile("tag-reads", null)
        val guard = testTagReadGuard(preferences, markerDir = blocked, pid = 7)

        guard.recover()

        guard.read(a) { "tags" } shouldBe "tags"
        guard.read(b) { "more tags" } shouldBe "more tags"
    }

    @Test
    fun `a native crash with one read in flight quarantines its file`() = runTest {
        crashDuring(a)
        val guard = guard(pid = 2, crashedNatively = { pid -> pid == 1 })

        guard.recover()
        var read = false
        val tags = guard.read(a) { "tags".also { read = true } }

        tags shouldBe null
        read shouldBe false
        guard.skippedPaths shouldBe setOf(a.path)
        preferences.tagReadQuarantine() shouldBe setOf(a.key)
        markers().shouldBeEmpty()
    }

    @Test
    fun `a native crash with several reads in flight reads each alone and quarantines the one that crashes again`() = runTest {
        crashDuring(a, b)
        val suspicious = guard(pid = 2, crashedNatively = { true })
        suspicious.recover()

        preferences.tagReadQuarantine().shouldBeEmpty()
        // Read alone: nothing else runs beside either
        val running = AtomicInteger()
        val alongside = AtomicInteger()
        listOf(a, b, TagReadFile("/music/c.mp3", 1, 1), TagReadFile("/music/d.mp3", 1, 1))
            .map { file ->
                async {
                    suspicious.read(file) {
                        val now = running.incrementAndGet()
                        if (file == a || file == b) alongside.accumulateAndGet(now - 1) { x, y -> maxOf(x, y) }
                        delay(10)
                        running.decrementAndGet()
                    }
                }
            }.awaitAll()
        alongside.get() shouldBe 0

        crashDuring(b, pid = 2)
        guard(pid = 3, crashedNatively = { true }).recover()

        preferences.tagReadQuarantine() shouldBe setOf(b.key)
    }

    @Test
    fun `a process that died some other way quarantines nothing`() = runTest {
        crashDuring(a)
        val guard = guard(pid = 2, crashedNatively = { false })

        guard.recover()

        preferences.tagReadQuarantine().shouldBeEmpty()
        preferences.tagReadStrikes().shouldBeEmpty()
        guard.read(a) { "tags" } shouldBe "tags"
        markers().shouldBeEmpty()
    }

    @Test
    fun `where Android can't say a file takes two strikes to be quarantined`() = runTest {
        crashDuring(a)
        guard(pid = 2).recover()

        preferences.tagReadStrikes() shouldBe setOf(a.key)
        preferences.tagReadQuarantine().shouldBeEmpty()

        crashDuring(a, pid = 2)
        guard(pid = 3).recover()

        preferences.tagReadQuarantine() shouldBe setOf(a.key)
        preferences.tagReadStrikes().shouldBeEmpty()
    }

    @Test
    fun `a struck file read without a crash loses its strike`() = runTest {
        crashDuring(a)
        val guard = guard(pid = 2)
        guard.recover()

        guard.read(a) { "tags" } shouldBe "tags"

        preferences.tagReadStrikes().shouldBeEmpty()
    }

    @Test
    fun `a quarantined file that changed since is read again`() = runTest {
        preferences.quarantineTagRead(a.key)
        val guard = guard(pid = 2)
        guard.recover()

        guard.read(a.copy(size = 101)) { "tags" } shouldBe "tags"
        guard.skippedPaths.shouldBeEmpty()
    }

    @Test
    fun `recovery leaves this process's own reads alone`() = runTest {
        val guard = guard(pid = 7)
        var during = emptyList<String>()

        guard.read(a) {
            guard.recover()
            during = markers()
        }

        during shouldContainExactly listOf("7\n${a.key}")
    }

    @Test
    fun `clearing the quarantine reads its files again at the next recovery`() = runTest {
        preferences.quarantineTagRead(a.key)
        val guard = guard(pid = 2)
        guard.recover()
        guard.read(a) { "tags" } shouldBe null

        preferences.clearTagReadQuarantine()
        guard.recover()

        guard.read(a) { "tags" } shouldBe "tags"
        guard.skippedPaths.shouldBeEmpty()
    }
}
