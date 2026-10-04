package com.simplecityapps.localmediaprovider.local.provider

import io.kotest.matchers.collections.shouldBeEmpty
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
}
