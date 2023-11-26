package com.simplecityapps.shuttle

import android.view.View
import androidx.fragment.app.FragmentContainerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.launchActivity
import com.simplecityapps.shuttle.ui.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.CoreMatchers.notNullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.After
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class InstrumentedTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    lateinit var scenario: ActivityScenario<MainActivity>

    @Test
    fun testMainActivityLaunch() {
        scenario = launchActivity()
        scenario.onActivity {
            val viewById: View = it.findViewById(R.id.onboardingNavHostFragment)
            assertThat(viewById, notNullValue())
            assertThat(viewById, instanceOf(FragmentContainerView::class.java))
        }
    }

    @After
    fun cleanup() {
        scenario.close()
    }
}
