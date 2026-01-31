package com.simplecityapps.shuttle.e2e.util

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObjectNotFoundException
import androidx.test.uiautomator.UiSelector

/**
 * Helper class to grant runtime permissions during tests
 * This ensures tests can run without manual intervention
 */
object PermissionGranter {

    private val device: UiDevice by lazy {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    /**
     * Grant storage permission required for the app to function
     */
    fun grantStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_AUDIO
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }

            try {
                InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
                    "pm grant ${context.packageName} $permission"
                )
                // Wait a bit for the permission to be applied
                Thread.sleep(500)
            } catch (e: Exception) {
                // Permission might already be granted or shell command failed
                // Try UI automation as fallback
                tryGrantPermissionViaUI()
            }
        }
    }

    /**
     * Attempt to grant permission via UI automation (fallback method)
     */
    private fun tryGrantPermissionViaUI() {
        try {
            // Look for common permission dialog buttons
            val allowButton = device.findObject(
                UiSelector().textMatches("(?i)allow|permit|ok")
            )
            if (allowButton.exists()) {
                allowButton.click()
                Thread.sleep(500)
            }
        } catch (e: UiObjectNotFoundException) {
            // Permission dialog not found, might already be granted
        }
    }

    /**
     * Handle any permission dialogs that might appear
     * Call this after launching the app if you expect permission prompts
     */
    fun handlePermissionDialogsIfPresent() {
        repeat(3) { // Try up to 3 times for multiple permission prompts
            try {
                val allowButton = device.findObject(
                    UiSelector().textMatches("(?i)allow|permit|while using|only this time")
                )
                if (allowButton.exists()) {
                    allowButton.click()
                    Thread.sleep(500)
                } else {
                    return // No more dialogs
                }
            } catch (e: UiObjectNotFoundException) {
                return // No dialog found
            }
        }
    }

    /**
     * Dismiss any system dialogs that might interfere with tests
     */
    fun dismissSystemDialogs() {
        try {
            device.pressBack()
            device.pressBack()
        } catch (e: Exception) {
            // Ignore errors
        }
    }
}
