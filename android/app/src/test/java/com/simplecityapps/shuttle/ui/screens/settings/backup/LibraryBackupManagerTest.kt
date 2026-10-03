package com.simplecityapps.shuttle.ui.screens.settings.backup

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.Test

class LibraryBackupManagerTest {
    @Test
    fun `a file within the cap is read whole`() {
        LibraryBackupManager.readCapped(ByteArray(100) { 1 }.inputStream(), maxBytes = 100)?.size shouldBe 100
    }

    @Test
    fun `a file over the cap is refused`() {
        LibraryBackupManager.readCapped(ByteArray(101).inputStream(), maxBytes = 100).shouldBeNull()
    }
}
