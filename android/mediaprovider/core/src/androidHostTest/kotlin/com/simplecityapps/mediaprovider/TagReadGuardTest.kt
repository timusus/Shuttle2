package com.simplecityapps.mediaprovider

import com.simplecityapps.shuttle.model.MediaProviderType
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

/** A guard over a fresh marker folder, as the process [pid], where Android records [processExits]. */
private fun testTagReadGuard(
    preferences: GeneralPreferenceManager = GeneralPreferenceManager(InMemoryKeyValueStore()),
    markerDir: File = Files.createTempDirectory("tag-reads").toFile(),
    processExits: () -> List<ProcessExit>? = { null },
    pid: Int = 1,
    permits: Int = 3
) = TagReadGuard(markerDir, preferences, processExits, TagReadLimiter(permits), pid)

class TagReadGuardTest {
    private val preferences = GeneralPreferenceManager(InMemoryKeyValueStore())
    private val markerDir: File = Files.createTempDirectory("tag-reads").toFile()
    private val a = TagReadFile("/music/a.flac", 100, 1_000)
    private val b = TagReadFile("/music/b.flac", 200, 2_000)
    private val source = MediaProviderType.Shuttle

    private fun guard(
        pid: Int,
        processExits: () -> List<ProcessExit>? = { null }
    ) = testTagReadGuard(preferences, markerDir, processExits, pid)

    /** Android's record of [pid] ending after any marker it left (a timestamp no file's modified time can pass). */
    private fun exit(
        pid: Int,
        nativeCrash: Boolean = true
    ) = ProcessExit(pid, Long.MAX_VALUE, nativeCrash)

    private fun markers() = markerDir.listFiles().orEmpty().map { it.readText() }

    /** Starts reads of [files] in a process [pid] that dies while they run, leaving their markers behind. */
    private fun crashDuring(
        vararg files: TagReadFile,
        pid: Int = 1
    ) = runBlocking {
        // Its own folder and preferences, so its first read doesn't recover the markers earlier crashes left in markerDir
        val crashDir = Files.createTempDirectory("tag-reads-crash").toFile()
        val guard = testTagReadGuard(markerDir = crashDir, pid = pid)
        val started = files.map { CompletableDeferred<Unit>() }
        val jobs = files.mapIndexed { index, file -> launch { guard.read(file, source) { started[index].complete(Unit).also { CompletableDeferred<Unit>().await() } } } }
        started.awaitAll()
        // The process is gone: its reads never finish, so their markers stay where the next one finds them
        val left = crashDir.listFiles().orEmpty().associate { it.name to it.readText() }
        jobs.forEach { it.cancelAndJoin() }
        left.forEach { (name, text) -> File(markerDir, name).writeText(text) }
    }

    @Test
    fun `a read leaves a marker while it runs and none after`() = runTest {
        val guard = guard(pid = 7)
        var during = emptyList<String>()

        val tags = guard.read(a, source) { "tags".also { during = markers() } }

        tags shouldBe "tags"
        during shouldContainExactly listOf(a.key)
        markers().shouldBeEmpty()
    }

    @Test
    fun `a read that throws takes its marker with it`() = runTest {
        val guard = guard(pid = 7)

        runCatching { guard.read(a, source) { error("corrupt") } }

        markers().shouldBeEmpty()
    }

    @Test
    fun `a marker that can't be written leaves the read to run unguarded`() = runTest {
        // A file where the marker folder should be, so writing a marker throws as a full disk would
        val blocked = File.createTempFile("tag-reads", null)
        val guard = testTagReadGuard(preferences, markerDir = blocked, pid = 7)

        guard.recover(source)

        guard.read(a, source) { "tags" } shouldBe "tags"
        guard.read(b, source) { "more tags" } shouldBe "more tags"
    }

    @Test
    fun `a native crash with one read in flight quarantines its file`() = runTest {
        crashDuring(a)
        val guard = guard(pid = 2, processExits = { listOf(exit(pid = 1)) })

        guard.recover(source)
        var read = false
        val tags = guard.read(a, source) { "tags".also { read = true } }

        tags shouldBe null
        read shouldBe false
        guard.skippedPaths(source) shouldBe setOf(a.path)
        preferences.tagReadQuarantine() shouldBe setOf(a.key)
        markers().shouldBeEmpty()
    }

    @Test
    fun `the first read recovers a crash's markers itself, without an import's recover`() = runTest {
        crashDuring(a)
        val guard = guard(pid = 2, processExits = { listOf(exit(pid = 1)) })

        guard.read(a, source) { "tags" } shouldBe null

        preferences.tagReadQuarantine() shouldBe setOf(a.key)
        markers().shouldBeEmpty()
        // And an import's recover after it finds nothing left to take
        guard.recover(source)
        preferences.tagReadQuarantine() shouldBe setOf(a.key)
    }

    @Test
    fun `a native crash with several reads in flight reads each alone and quarantines the one that crashes again`() = runTest {
        crashDuring(a, b)
        val suspicious = guard(pid = 2, processExits = { listOf(exit(pid = 1)) })
        suspicious.recover(source)

        preferences.tagReadQuarantine().shouldBeEmpty()
        // Read alone: nothing else runs beside either
        val running = AtomicInteger()
        val alongside = AtomicInteger()
        listOf(a, b, TagReadFile("/music/c.mp3", 1, 1), TagReadFile("/music/d.mp3", 1, 1))
            .map { file ->
                async {
                    suspicious.read(file, source) {
                        val now = running.incrementAndGet()
                        if (file == a || file == b) alongside.accumulateAndGet(now - 1) { x, y -> maxOf(x, y) }
                        delay(10)
                        running.decrementAndGet()
                    }
                }
            }.awaitAll()
        alongside.get() shouldBe 0

        crashDuring(b, pid = 2)
        guard(pid = 3, processExits = { listOf(exit(pid = 2)) }).recover(source)

        preferences.tagReadQuarantine() shouldBe setOf(b.key)
    }

    @Test
    fun `a process that died some other way quarantines nothing`() = runTest {
        crashDuring(a)
        val guard = guard(pid = 2, processExits = { listOf(exit(pid = 1, nativeCrash = false)) })

        guard.recover(source)

        preferences.tagReadQuarantine().shouldBeEmpty()
        preferences.tagReadStrikes().shouldBeEmpty()
        guard.read(a, source) { "tags" } shouldBe "tags"
        markers().shouldBeEmpty()
    }

    @Test
    fun `where Android can't say a file takes two strikes to be quarantined`() = runTest {
        crashDuring(a)
        guard(pid = 2).recover(source)

        preferences.tagReadStrikes() shouldBe setOf(a.key)
        preferences.tagReadQuarantine().shouldBeEmpty()

        crashDuring(a, pid = 2)
        guard(pid = 3).recover(source)

        preferences.tagReadQuarantine() shouldBe setOf(a.key)
        preferences.tagReadStrikes().shouldBeEmpty()
    }

    @Test
    fun `where Android has no record of the process a file takes a strike`() = runTest {
        crashDuring(a)
        guard(pid = 2, processExits = { listOf(exit(pid = 5)) }).recover(source)

        preferences.tagReadStrikes() shouldBe setOf(a.key)
        preferences.tagReadQuarantine().shouldBeEmpty()
    }

    @Test
    fun `recovery asks Android how processes ended once`() = runTest {
        crashDuring(a)
        crashDuring(b, pid = 2)
        var asked = 0

        guard(pid = 3, processExits = {
            asked++
            listOf(exit(pid = 1), exit(pid = 2))
        }).recover(source)

        asked shouldBe 1
        preferences.tagReadQuarantine() shouldBe setOf(a.key, b.key)
    }

    @Test
    fun `recovery with no markers doesn't ask Android how processes ended`() = runTest {
        var asked = 0

        guard(pid = 3, processExits = {
            asked++
            emptyList()
        }).recover(source)

        asked shouldBe 0
    }

    @Test
    fun `a process's end is its first record from when its marker was written`() {
        val exits = listOf(ProcessExit(1, 3_000, nativeCrash = false), ProcessExit(1, 2_000, nativeCrash = true), ProcessExit(2, 2_500, nativeCrash = false))

        crashedNatively(exits, pid = 1, since = 1_500) shouldBe true
        crashedNatively(exits, pid = 2, since = 1_500) shouldBe false
    }

    @Test
    fun `an earlier process with the same pid isn't the one that wrote the marker`() {
        val exits = listOf(ProcessExit(1, 1_000, nativeCrash = true))

        crashedNatively(exits, pid = 1, since = 1_500) shouldBe null
    }

    @Test
    fun `Android without exit records can't say how a process ended`() {
        crashedNatively(null, pid = 1, since = 0) shouldBe null
        crashedNatively(emptyList(), pid = 1, since = 0) shouldBe null
    }

    @Test
    fun `a struck file read without a crash loses its strike`() = runTest {
        crashDuring(a)
        val guard = guard(pid = 2)
        guard.recover(source)

        guard.read(a, source) { "tags" } shouldBe "tags"

        preferences.tagReadStrikes().shouldBeEmpty()
    }

    @Test
    fun `a quarantined file that changed since is read again`() = runTest {
        preferences.quarantineTagRead(a.key)
        val guard = guard(pid = 2)
        guard.recover(source)

        guard.read(a.copy(size = 101), source) { "tags" } shouldBe "tags"
        guard.skippedPaths(source).shouldBeEmpty()
    }

    @Test
    fun `recovery leaves this process's own reads alone`() = runTest {
        val guard = guard(pid = 7)
        var during = emptyList<String>()

        guard.read(a, source) {
            guard.recover(source)
            during = markers()
        }

        during shouldContainExactly listOf(a.key)
    }

    @Test
    fun `recovery leaves a marker this process is still writing`() = runTest {
        val guard = guard(pid = 7)
        var during = emptyList<String>()

        guard.read(a, source) {
            // Caught between creating the marker and writing its key, as the other provider's recovery could see it
            markerDir.listFiles().orEmpty().single().writeText("")
            guard.recover(MediaProviderType.MediaStore)
            during = markerDir.listFiles().orEmpty().map { it.name }
        }

        during.size shouldBe 1
        markers().shouldBeEmpty()
    }

    @Test
    fun `an unreadable marker of another process is left while fresh and removed once old`() = runTest {
        val marker = File(markerDir, "slot-9-0").apply { writeText("") }
        val guard = guard(pid = 7)

        guard.recover(source)
        marker.exists() shouldBe true

        // Modified in 1970, older than any freshness window
        marker.setLastModified(1_000L)
        guard.recover(source)
        marker.exists() shouldBe false
        preferences.tagReadStrikes().shouldBeEmpty()
    }

    @Test
    fun `one source's recovery keeps another's skipped files`() = runTest {
        preferences.quarantineTagRead(a.key)
        val guard = guard(pid = 2)
        guard.recover(source)
        guard.read(a, source) { "tags" } shouldBe null

        guard.recover(MediaProviderType.MediaStore)

        guard.skippedPaths(source) shouldBe setOf(a.path)
        guard.skippedPaths(MediaProviderType.MediaStore).shouldBeEmpty()
    }

    @Test
    fun `clearing the quarantine reads its files again at the next recovery`() = runTest {
        preferences.quarantineTagRead(a.key)
        val guard = guard(pid = 2)
        guard.recover(source)
        guard.read(a, source) { "tags" } shouldBe null

        preferences.clearTagReadQuarantine()
        guard.recover(source)

        guard.read(a, source) { "tags" } shouldBe "tags"
        guard.skippedPaths(source).shouldBeEmpty()
    }
}
