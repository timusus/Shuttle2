package com.simplecityapps.playback.fakes

import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.io.File

/**
 * Runs [block] with [Uri.parse] and [Uri.fromFile] stubbed to return fakes carrying the same
 * string and scheme, for tests whose subject never opens the URI and only cares that a
 * [androidx.media3.common.MediaItem] exists. AGP's unit-test stub jar throws on both outside
 * Robolectric.
 */
fun <T> withFakeUriStatics(block: () -> T): T {
    setUpFakeUriStatics()
    try {
        return block()
    } finally {
        tearDownFakeUriStatics()
    }
}

/** [withFakeUriStatics] as a `@Before`/`@After` pair, for tests whose URI use is inside `@Test` bodies. */
fun setUpFakeUriStatics() {
    mockkStatic(Uri::class)
    every { Uri.parse(any()) } answers { fakeUri(firstArg()) }
    every { Uri.fromFile(any()) } answers { fakeUri("file://${firstArg<File>().path}") }
}

fun tearDownFakeUriStatics() = unmockkStatic(Uri::class)

private fun fakeUri(value: String): Uri {
    val uri = mockk<Uri>(relaxed = true)
    every { uri.toString() } returns value
    every { uri.scheme } returns value.substringBefore("://", missingDelimiterValue = "").ifEmpty { null }
    return uri
}
