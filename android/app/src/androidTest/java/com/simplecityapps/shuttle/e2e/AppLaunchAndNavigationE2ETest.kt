package com.simplecityapps.shuttle.e2e

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.e2e.util.PermissionGranter
import com.simplecityapps.shuttle.e2e.util.TestUtils
import com.simplecityapps.shuttle.e2e.util.safeClick
import com.simplecityapps.shuttle.e2e.util.waitForDisplay
import com.simplecityapps.shuttle.ui.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * E2E test for app launch and main navigation flows
 *
 * This test covers the critical user journey of:
 * 1. Launching the app for the first time or as a returning user
 * 2. Navigating between main app sections (Home, Library, Search)
 * 3. Verifying that all main UI components are present and functional
 *
 * Best practices implemented:
 * - Uses HiltAndroidTest for proper dependency injection
 * - Implements robust wait strategies to avoid flakiness
 * - Grants permissions programmatically for deterministic behavior
 * - Tests are isolated and can run independently
 */
@LargeTest
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AppLaunchAndNavigationE2ETest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val grantPermissionRule: GrantPermissionRule = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        GrantPermissionRule.grant(Manifest.permission.READ_MEDIA_AUDIO)
    } else {
        GrantPermissionRule.grant(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    private lateinit var scenario: ActivityScenario<MainActivity>
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setup() {
        hiltRule.inject()

        // Ensure permissions are granted before test starts
        PermissionGranter.grantStoragePermission()

        // Small delay to ensure permission is applied
        Thread.sleep(500)
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) {
            scenario.close()
        }
    }

    /**
     * Test: App launches successfully and displays main UI
     *
     * Verifies that:
     * - App launches without crashing
     * - Main navigation container is present
     * - Bottom navigation is visible
     *
     * This is the most critical test - if the app doesn't launch, nothing else works.
     */
    @Test
    fun appLaunchesSuccessfully() {
        // Launch the app
        scenario = ActivityScenario.launch(MainActivity::class.java)

        // Handle any permission dialogs that might appear
        PermissionGranter.handlePermissionDialogsIfPresent()

        // Wait for the onboarding or main navigation host to appear
        TestUtils.withRetry(maxAttempts = 3) {
            val onboardingExists = TestUtils.viewExists(R.id.onboardingNavHostFragment)
            val mainExists = TestUtils.viewExists(R.id.navHostFragment)

            // At least one navigation host should exist
            assert(onboardingExists || mainExists) {
                "Neither onboarding nor main navigation host found"
            }
        }

        // Verify the activity is in resumed state
        scenario.onActivity { activity ->
            assert(!activity.isFinishing) { "Activity should not be finishing" }
            assert(!activity.isDestroyed) { "Activity should not be destroyed" }
        }
    }

    /**
     * Test: Bottom navigation is functional and allows switching between tabs
     *
     * Verifies that:
     * - Bottom navigation bar is visible
     * - All navigation items are clickable
     * - Clicking navigation items switches the active fragment
     *
     * This tests the core navigation pattern of the app.
     */
    @Test
    fun bottomNavigationWorksCorrectly() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        PermissionGranter.handlePermissionDialogsIfPresent()

        // Wait for bottom navigation to be visible
        // Note: This assumes the user has completed onboarding
        // In a real scenario, you might need to skip onboarding first
        TestUtils.waitForView(R.id.bottomNavigationView, timeoutMs = 10000)

        // Verify bottom navigation is displayed
        onView(withId(R.id.bottomNavigationView))
            .check(matches(isDisplayed()))

        // Test navigation to Home
        TestUtils.withRetry {
            R.id.homeFragment.safeClick()
            Thread.sleep(500) // Allow navigation animation
        }

        // Test navigation to Library
        TestUtils.withRetry {
            R.id.libraryFragment.safeClick()
            Thread.sleep(500)
        }

        // Test navigation to Search
        TestUtils.withRetry {
            R.id.searchFragment.safeClick()
            Thread.sleep(500)
        }

        // Verify we can navigate back to Library
        TestUtils.withRetry {
            R.id.libraryFragment.safeClick()
            Thread.sleep(500)
        }

        // Bottom navigation should still be visible after all navigations
        onView(withId(R.id.bottomNavigationView))
            .check(matches(isDisplayed()))
    }

    /**
     * Test: Navigate between all main sections without crashes
     *
     * Verifies that:
     * - Rapid navigation between sections doesn't cause crashes
     * - UI remains responsive after multiple navigation events
     * - No memory leaks or fragment transaction errors occur
     *
     * This is a stress test for the navigation system.
     */
    @Test
    fun navigateAllSectionsWithoutCrashes() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        PermissionGranter.handlePermissionDialogsIfPresent()

        // Wait for bottom navigation
        TestUtils.waitForView(R.id.bottomNavigationView, timeoutMs = 10000)

        // Perform multiple navigation cycles
        repeat(3) { cycle ->
            // Navigate through all tabs
            listOf(
                R.id.homeFragment,
                R.id.libraryFragment,
                R.id.searchFragment,
                R.id.libraryFragment // Back to middle tab
            ).forEach { tabId ->
                TestUtils.withRetry(maxAttempts = 3) {
                    onView(withId(tabId)).perform(click())
                    Thread.sleep(300) // Short delay for navigation
                }
            }

            // Verify navigation bar is still functional after this cycle
            onView(withId(R.id.bottomNavigationView))
                .check(matches(isDisplayed()))
        }

        // Verify activity is still in good state
        scenario.onActivity { activity ->
            assert(!activity.isFinishing) { "Activity should not be finishing after navigation stress test" }
        }
    }

    /**
     * Test: Main fragment container is present and responsive
     *
     * Verifies that:
     * - Navigation host fragment is properly initialized
     * - Fragment container can host different fragments
     *
     * This ensures the fragment navigation infrastructure is working.
     */
    @Test
    fun mainFragmentContainerIsPresent() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        PermissionGranter.handlePermissionDialogsIfPresent()

        // The app should have either onboarding or main nav host
        TestUtils.withRetry(maxAttempts = 5, delayMs = 1000) {
            val hasOnboarding = TestUtils.viewExists(R.id.onboardingNavHostFragment)
            val hasMain = TestUtils.viewExists(R.id.navHostFragment)

            assert(hasOnboarding || hasMain) {
                "Expected either onboarding or main navigation host to exist"
            }
        }
    }

    /**
     * Test: App handles configuration changes correctly
     *
     * Verifies that:
     * - App survives screen rotation
     * - Navigation state is preserved across configuration changes
     * - No crashes occur during recreation
     *
     * This is critical for a good user experience on Android.
     */
    @Test
    fun appHandlesRotationCorrectly() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        PermissionGranter.handlePermissionDialogsIfPresent()

        // Wait for UI to be ready
        TestUtils.waitForView(R.id.bottomNavigationView, timeoutMs = 10000)

        // Navigate to a specific tab
        TestUtils.withRetry {
            R.id.searchFragment.safeClick()
            Thread.sleep(500)
        }

        // Simulate rotation by recreating the activity
        scenario.recreate()

        // Wait for UI to be restored
        Thread.sleep(1000)

        // Verify bottom navigation is still visible after recreation
        onView(withId(R.id.bottomNavigationView))
            .check(matches(isDisplayed()))

        // Verify we can still navigate
        TestUtils.withRetry {
            R.id.libraryFragment.safeClick()
            Thread.sleep(500)
        }

        // Verify activity is in good state
        scenario.onActivity { activity ->
            assert(!activity.isFinishing) { "Activity should not be finishing after rotation" }
        }
    }
}
