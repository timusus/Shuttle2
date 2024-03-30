package com.simplecityapps.shuttle.ui.screens.library.genres

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.annotation.StyleRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.core.util.Preconditions
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation.findNavController
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.simplecityapps.shuttle.HiltTestActivity
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.MockKAnnotations
import io.mockk.impl.annotations.MockK
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

@HiltAndroidTest
class GenreListFragmentTest {
    @MockK(relaxed = true)
    lateinit var viewModel: GenreListViewModel

    private val hiltRule = HiltAndroidRule(this)

    private val composeTestRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rule: RuleChain = RuleChain
        .outerRule(hiltRule)
        .around(composeTestRule)

    @get:Rule
    val composeTestRule2 = createComposeRule()

    @Before
    fun goToNonOrganizerFragment() {
        MockKAnnotations.init(this)
        hiltRule.inject()
        launchFragmentInHiltContainer<GenreListFragment>()

        composeTestRule.activityRule.scenario.onActivity {
            // FIXME: I guess there's a better way to do this
            it.preferenceManager.hasSeenThankYouDialog = true
            it.preferenceManager.showChangelogOnLaunch = false

            findNavController(it, R.id.onboardingNavHostFragment)
                .navigate(R.id.mainFragment)
        }
    }

    @Test
    fun testEventFragment2() {
        onView(withText("Genres")).perform(click())
        composeTestRule.onNodeWithTag("genres-list-lazy-column")
            .assertIsDisplayed()
    }
}

/**
 * launchFragmentInContainer from the androidx.fragment:fragment-testing library
 * is NOT possible to use right now as it uses a hardcoded Activity under the hood
 * (i.e. [EmptyFragmentActivity]) which is not annotated with @AndroidEntryPoint.
 *
 * As a workaround, use this function that is equivalent. It requires you to add
 * [HiltTestActivity] in the debug folder and include it in the debug
 * AndroidManifest.xml file as can be found in this project.
 *
 * See https://developer.android.com/training/dependency-injection/hilt-testing#launchfragment
 */
inline fun <reified T : Fragment> launchFragmentInHiltContainer(
    fragmentArgs: Bundle? = null,
    @StyleRes themeResId: Int = R.style.AppTheme_Light,
    crossinline action: Fragment.() -> Unit = {},
) {
    val startActivityIntent = Intent.makeMainActivity(
        ComponentName(
            ApplicationProvider.getApplicationContext(),
            HiltTestActivity::class.java,
        )
    ).putExtra(
        "androidx.fragment.app.testing.FragmentScenario.EmptyFragmentActivity.THEME_EXTRAS_BUNDLE_KEY",
        themeResId,
    )

    ActivityScenario
        .launch<HiltTestActivity>(startActivityIntent)
        .onActivity { activity ->
            val fragment = activity.supportFragmentManager.fragmentFactory.instantiate(
                Preconditions.checkNotNull(T::class.java.classLoader),
                T::class.java.name,
            )
            fragment.arguments = fragmentArgs
            activity.supportFragmentManager.beginTransaction()
        }
}
