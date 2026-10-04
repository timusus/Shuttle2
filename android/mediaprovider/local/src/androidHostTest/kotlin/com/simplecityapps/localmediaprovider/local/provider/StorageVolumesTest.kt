package com.simplecityapps.localmediaprovider.local.provider

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.Test

class StorageVolumesTest {
    private val mounted = setOf("/storage/emulated/0/", "/storage/1234-5678/")

    @Test
    fun `songs on mounted volumes are on no unmounted root`() {
        unmountedRoots(listOf("/storage/emulated/0/Music/a.mp3", "/storage/1234-5678/b.flac"), mounted).shouldBeEmpty()
    }

    @Test
    fun `a song on a card that's out is under that card's root`() {
        unmountedRoots(listOf("/storage/ABCD-EF01/Music/Album/a.mp3", "/storage/ABCD-EF01/b.mp3"), mounted) shouldBe setOf("/storage/ABCD-EF01/")
    }

    @Test
    fun `another user's storage beside the mounted one is told apart from it`() {
        unmountedRoots(listOf("/storage/emulated/10/Music/a.mp3"), mounted) shouldBe setOf("/storage/emulated/10/")
    }

    @Test
    fun `document URIs are left to the provider's tree walk`() {
        unmountedRoots(listOf("content://com.android.externalstorage.documents/tree/primary%3AMusic/document/primary%3AMusic%2Fa.mp3"), mounted).shouldBeEmpty()
    }

    @Test
    fun `with no volume mounted every song's volume is unmounted`() {
        unmountedRoots(listOf("/storage/emulated/0/Music/a.mp3"), emptySet()) shouldBe setOf("/storage/emulated/")
    }

    @Test
    fun `a mounted volume's root is its directory, ending in a separator`() {
        mountedRoot("mounted", directory = "/mnt/media_rw/USB1", isPrimary = false, uuid = "USB1", primaryDirectory = "/storage/emulated/0") shouldBe "/mnt/media_rw/USB1/"
        mountedRoot("mounted_ro", directory = "/storage/1234-5678", isPrimary = false, uuid = "1234-5678", primaryDirectory = "/storage/emulated/0") shouldBe "/storage/1234-5678/"
    }

    @Test
    fun `before Android says a volume's directory, the primary one is shared storage and the others are under storage by uuid`() {
        mountedRoot("mounted", directory = null, isPrimary = true, uuid = null, primaryDirectory = "/storage/emulated/0") shouldBe "/storage/emulated/0/"
        mountedRoot("mounted", directory = null, isPrimary = false, uuid = "ABCD-EF01", primaryDirectory = "/storage/emulated/0") shouldBe "/storage/ABCD-EF01/"
        mountedRoot("mounted", directory = null, isPrimary = false, uuid = null, primaryDirectory = "/storage/emulated/0").shouldBeNull()
    }

    @Test
    fun `a volume that isn't mounted has no root`() {
        listOf("unmounted", "removed", "ejecting", "checking").forEach { state ->
            mountedRoot(state, directory = "/storage/ABCD-EF01", isPrimary = false, uuid = "ABCD-EF01", primaryDirectory = "/storage/emulated/0").shouldBeNull()
        }
    }

    @Test
    fun `without MediaStore's listing every file path is unreadable, but not the songs from extra folders`() {
        val roots = scannerUnreadableRoots(listOf("/storage/emulated/0/Music/a.mp3"), mounted, mediaStoreListed = false, unavailableTrees = listOf("content://tree/b"))

        roots shouldBe setOf("/", "content://tree/b/document/")
        roots.none { root -> "content://tree/a/document/a.mp3".startsWith(root) } shouldBe true
    }

    @Test
    fun `with MediaStore's listing only unmounted volumes and unavailable trees are unreadable`() {
        scannerUnreadableRoots(
            listOf("/storage/emulated/0/Music/a.mp3", "/storage/ABCD-EF01/b.mp3"),
            mounted,
            mediaStoreListed = true,
            unavailableTrees = emptyList()
        ) shouldBe setOf("/storage/ABCD-EF01/")
    }
}
