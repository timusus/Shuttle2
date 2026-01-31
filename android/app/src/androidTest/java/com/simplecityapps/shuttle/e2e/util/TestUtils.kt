package com.simplecityapps.shuttle.e2e.util

import android.view.View
import androidx.annotation.IdRes
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf

/**
 * Test utilities for E2E tests providing robust, deterministic interactions
 */
object TestUtils {

    /**
     * Wait for a view to be displayed with configurable timeout and retry
     * This makes tests more robust against timing issues
     */
    fun waitForView(
        @IdRes viewId: Int,
        timeoutMs: Long = 5000,
        checkIntervalMs: Long = 100
    ): ViewInteraction {
        val startTime = System.currentTimeMillis()
        var lastException: Throwable? = null

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            try {
                return onView(withId(viewId)).check(matches(isDisplayed()))
            } catch (e: Throwable) {
                lastException = e
                Thread.sleep(checkIntervalMs)
            }
        }

        throw AssertionError(
            "View with id $viewId not displayed after ${timeoutMs}ms",
            lastException
        )
    }

    /**
     * Wait for a view matching a custom matcher
     */
    fun waitForView(
        matcher: Matcher<View>,
        timeoutMs: Long = 5000,
        checkIntervalMs: Long = 100
    ): ViewInteraction {
        val startTime = System.currentTimeMillis()
        var lastException: Throwable? = null

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            try {
                return onView(matcher).check(matches(isDisplayed()))
            } catch (e: Throwable) {
                lastException = e
                Thread.sleep(checkIntervalMs)
            }
        }

        throw AssertionError(
            "View matching $matcher not displayed after ${timeoutMs}ms",
            lastException
        )
    }

    /**
     * Safely click a view with retry logic
     */
    fun safeClick(@IdRes viewId: Int, retries: Int = 3) {
        var attempts = 0
        var lastException: Throwable? = null

        while (attempts < retries) {
            try {
                waitForView(viewId, timeoutMs = 3000)
                onView(withId(viewId)).perform(ViewActions.click())
                return
            } catch (e: Throwable) {
                lastException = e
                attempts++
                if (attempts < retries) {
                    Thread.sleep(500)
                }
            }
        }

        throw AssertionError(
            "Failed to click view $viewId after $retries attempts",
            lastException
        )
    }

    /**
     * Safely click a view matching a custom matcher
     */
    fun safeClick(matcher: Matcher<View>, retries: Int = 3) {
        var attempts = 0
        var lastException: Throwable? = null

        while (attempts < retries) {
            try {
                waitForView(matcher, timeoutMs = 3000)
                onView(matcher).perform(ViewActions.click())
                return
            } catch (e: Throwable) {
                lastException = e
                attempts++
                if (attempts < retries) {
                    Thread.sleep(500)
                }
            }
        }

        throw AssertionError(
            "Failed to click view matching $matcher after $retries attempts",
            lastException
        )
    }

    /**
     * Check if a view is displayed (returns boolean instead of throwing)
     */
    fun isViewDisplayed(@IdRes viewId: Int): Boolean {
        return try {
            onView(withId(viewId)).check(matches(isDisplayed()))
            true
        } catch (e: Throwable) {
            false
        }
    }

    /**
     * Check if a view exists in the hierarchy (even if not visible)
     */
    fun viewExists(@IdRes viewId: Int): Boolean {
        return try {
            onView(withId(viewId)).check(
                matches(
                    withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE)
                        .or(withEffectiveVisibility(ViewMatchers.Visibility.INVISIBLE))
                        .or(withEffectiveVisibility(ViewMatchers.Visibility.GONE))
                )
            )
            true
        } catch (e: Throwable) {
            false
        }
    }

    /**
     * Wait for a view to disappear
     */
    fun waitForViewToDisappear(
        @IdRes viewId: Int,
        timeoutMs: Long = 5000,
        checkIntervalMs: Long = 100
    ) {
        val startTime = System.currentTimeMillis()

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (!isViewDisplayed(viewId)) {
                return
            }
            Thread.sleep(checkIntervalMs)
        }

        throw AssertionError("View with id $viewId still displayed after ${timeoutMs}ms")
    }

    /**
     * Perform action with retry logic
     */
    fun <T> withRetry(
        maxAttempts: Int = 3,
        delayMs: Long = 500,
        block: () -> T
    ): T {
        var attempts = 0
        var lastException: Throwable? = null

        while (attempts < maxAttempts) {
            try {
                return block()
            } catch (e: Throwable) {
                lastException = e
                attempts++
                if (attempts < maxAttempts) {
                    Thread.sleep(delayMs)
                }
            }
        }

        throw AssertionError(
            "Operation failed after $maxAttempts attempts",
            lastException
        )
    }

    /**
     * Combine multiple matchers with AND logic
     */
    fun allOf(vararg matchers: Matcher<View>): Matcher<View> {
        return allOf(*matchers)
    }
}

/**
 * Extension function for more idiomatic Kotlin usage
 */
fun Int.waitForDisplay(timeoutMs: Long = 5000): ViewInteraction {
    return TestUtils.waitForView(this, timeoutMs)
}

/**
 * Extension function for safe clicking
 */
fun Int.safeClick(retries: Int = 3) {
    TestUtils.safeClick(this, retries)
}

/**
 * Extension function to check if view is displayed
 */
fun Int.isDisplayed(): Boolean {
    return TestUtils.isViewDisplayed(this)
}
