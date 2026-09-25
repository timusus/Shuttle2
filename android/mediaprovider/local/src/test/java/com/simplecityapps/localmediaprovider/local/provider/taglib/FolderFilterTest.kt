package com.simplecityapps.localmediaprovider.local.provider.taglib

import io.kotest.matchers.shouldBe
import org.junit.Test

class FolderFilterTest {
    @Test
    fun `with no includes or excludes every file is accepted`() {
        FolderFilter().accepts("/storage/emulated/0/Music/a.mp3") shouldBe true
    }

    @Test
    fun `with includes only files inside one of them are accepted`() {
        val filter = FolderFilter(includes = listOf("/storage/emulated/0/Music", "/storage/1234-ABCD/Albums/"))

        filter.accepts("/storage/emulated/0/Music/Artist/a.mp3") shouldBe true
        filter.accepts("/storage/1234-ABCD/Albums/b.flac") shouldBe true
        filter.accepts("/storage/emulated/0/Download/c.mp3") shouldBe false
    }

    @Test
    fun `a folder whose name only starts with an included folder's name is not included`() {
        FolderFilter(includes = listOf("/storage/emulated/0/Music")).accepts("/storage/emulated/0/Music2/a.mp3") shouldBe false
    }

    @Test
    fun `an exclude inside an include wins`() {
        val filter = FolderFilter(includes = listOf("/storage/emulated/0/Music"), excludes = listOf("/storage/emulated/0/Music/Podcasts"))

        filter.accepts("/storage/emulated/0/Music/Album/a.mp3") shouldBe true
        filter.accepts("/storage/emulated/0/Music/Podcasts/episode.mp3") shouldBe false
    }

    @Test
    fun `excludes apply without includes`() {
        val filter = FolderFilter(excludes = listOf("/storage/emulated/0/Recordings"))

        filter.accepts("/storage/emulated/0/Music/a.mp3") shouldBe true
        filter.accepts("/storage/emulated/0/Recordings/memo.m4a") shouldBe false
    }

    @Test
    fun `matching ignores case, as shared storage paths do`() {
        FolderFilter(includes = listOf("/storage/emulated/0/music")).accepts("/storage/emulated/0/Music/a.mp3") shouldBe true
    }

    @Test
    fun `a primary storage tree maps to its folder`() {
        externalStorageTreeFolder(EXTERNAL_STORAGE, "primary:Music/Rock", PRIMARY) shouldBe "/storage/emulated/0/Music/Rock"
    }

    @Test
    fun `the primary storage root maps to the storage root`() {
        externalStorageTreeFolder(EXTERNAL_STORAGE, "primary:", PRIMARY) shouldBe "/storage/emulated/0"
    }

    @Test
    fun `a secondary volume tree maps to its mount point`() {
        externalStorageTreeFolder(EXTERNAL_STORAGE, "04B9-1208:Music", PRIMARY) shouldBe "/storage/04B9-1208/Music"
    }

    @Test
    fun `the documents root maps to the primary Documents folder`() {
        externalStorageTreeFolder(EXTERNAL_STORAGE, "home:Scores", PRIMARY) shouldBe "/storage/emulated/0/Documents/Scores"
    }

    @Test
    fun `a tree from another provider has no folder`() {
        externalStorageTreeFolder("com.android.providers.downloads.documents", "raw:/storage/emulated/0/Download", PRIMARY) shouldBe null
        externalStorageTreeFolder(null, "primary:Music", PRIMARY) shouldBe null
    }

    companion object {
        private const val EXTERNAL_STORAGE = "com.android.externalstorage.documents"
        private const val PRIMARY = "/storage/emulated/0"
    }
}
