package com.simplecityapps.shuttle.shared.downloads

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class DownloadFileNamesTest {
    @Test
    fun aFileNameReadsBackToItsPath() {
        for (path in listOf("jellyfin://item/42", "plex:///library/metadata/1?x=ü&y=/", "emby://item/a b")) {
            val name = DownloadFileNames.fileName(path, "flac")

            name.endsWith(".flac") shouldBe true
            name.substringBeforeLast('.').all { it.isLetterOrDigit() || it == '-' || it == '_' } shouldBe true
            DownloadFileNames.path(name) shouldBe path
        }
    }

    @Test
    fun theDocumentedExampleHoldsTrue() {
        DownloadFileNames.fileName("jellyfin://item/42", "flac") shouldBe "amVsbHlmaW46Ly9pdGVtLzQy.flac"
    }

    @Test
    fun aFileThatIsntOneOfOursHasNoPath() {
        DownloadFileNames.path(".DS_Store") shouldBe null
        DownloadFileNames.path("not base64!.mp3") shouldBe null
        DownloadFileNames.path("amVsbHlmaW46Ly9pdGVtLzQy") shouldBe null
    }

    @Test
    fun theExtensionComesFromTheMimeTypeThenTheServersName() {
        DownloadFileNames.extension("audio/flac", fallback = "mp3") shouldBe "flac"
        DownloadFileNames.extension("audio/mpeg; charset=binary", fallback = null) shouldBe "mp3"
        DownloadFileNames.extension("audio/x-unknown", fallback = "DSF") shouldBe "dsf"
        DownloadFileNames.extension("application/octet-stream", fallback = "../x") shouldBe "audio"
        DownloadFileNames.extension("", fallback = null) shouldBe "audio"
    }
}
