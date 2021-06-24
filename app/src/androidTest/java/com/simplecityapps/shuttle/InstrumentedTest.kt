package com.simplecityapps.shuttle

import android.view.View
import androidx.fragment.app.FragmentContainerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.launchActivity
import androidx.test.filters.SmallTest
import com.simplecityapps.shuttle.ui.MainActivity
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.CoreMatchers.notNullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.After
import org.junit.Test

@SmallTest
class InstrumentedTest {

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