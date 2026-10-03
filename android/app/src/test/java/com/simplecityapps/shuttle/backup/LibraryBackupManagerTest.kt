package com.simplecityapps.shuttle.backup

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.Test

class LibraryBackupManagerTest {
    @Test
    fun `a stream within the cap is read whole`() {
        LibraryBackupManager.CappedInputStream(ByteArray(100) { 1 }.inputStream(), maxBytes = 100).readBytes().size shouldBe 100
    }

    @Test
    fun `a stream over the cap fails`() {
        shouldThrow<LibraryBackupManager.BackupTooLargeException> {
            LibraryBackupManager.CappedInputStream(ByteArray(101).inputStream(), maxBytes = 100).readBytes()
        }
    }

    @Test
    fun `single byte reads count against the cap too`() {
        val stream = LibraryBackupManager.CappedInputStream(ByteArray(3).inputStream(), maxBytes = 2)
        stream.read()
        stream.read()
        shouldThrow<LibraryBackupManager.BackupTooLargeException> { stream.read() }
    }
}
