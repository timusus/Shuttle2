package com.simplecityapps.shuttle.ui.screens.sources

import android.Manifest
import android.os.Build
import io.kotest.matchers.shouldBe
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.util.ReflectionHelpers

/** The permission asked for depends on the Android version: storage up to Android 12L, audio from Android 13. */
@RunWith(RobolectricTestRunner::class)
class MusicPermissionTest {
    private val sdkInt = Build.VERSION.SDK_INT

    @After
    fun tearDown() = ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", sdkInt)

    private fun onSdk(sdk: Int) = ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", sdk)

    @Test
    fun `Android 6 to 12L ask to read external storage`() {
        onSdk(Build.VERSION_CODES.M)
        MusicPermission.name shouldBe Manifest.permission.READ_EXTERNAL_STORAGE

        onSdk(Build.VERSION_CODES.S_V2)
        MusicPermission.name shouldBe Manifest.permission.READ_EXTERNAL_STORAGE
    }

    @Test
    fun `Android 13 and later ask to read audio`() {
        onSdk(Build.VERSION_CODES.TIRAMISU)
        MusicPermission.name shouldBe Manifest.permission.READ_MEDIA_AUDIO
    }
}
