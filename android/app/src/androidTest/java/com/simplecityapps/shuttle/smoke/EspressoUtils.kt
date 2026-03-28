package com.simplecityapps.shuttle.smoke

import android.view.View
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import org.hamcrest.Matcher

/**
 * Waits for a view matching [viewMatcher] to become displayed, polling every 100ms.
 * Returns as soon as the view is found. Throws after [timeoutMs] if not found.
 */
fun waitForView(viewMatcher: Matcher<View>, timeoutMs: Long = 3000) {
    val end = System.currentTimeMillis() + timeoutMs
    var lastError: Throwable? = null
    while (System.currentTimeMillis() < end) {
        try {
            onView(viewMatcher).check(matches(isDisplayed()))
            return
        } catch (e: Throwable) {
            lastError = e
            Thread.sleep(100)
        }
    }
    throw lastError ?: AssertionError("Timed out waiting for view: $viewMatcher")
}
