package com.simplecityapps.shuttle.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.play.core.review.ReviewManagerFactory
import com.simplecityapps.playback.mediasession.PlayRequests
import com.simplecityapps.shuttle.di.appGraph
import com.simplecityapps.shuttle.entitlement.ObservePaywallRequests
import com.simplecityapps.shuttle.entitlement.TrialDisclosures
import com.simplecityapps.shuttle.ui.screens.paywall.PaywallHost
import com.simplecityapps.shuttle.ui.screens.paywall.TrialDisclosureHost
import com.simplecityapps.shuttle.ui.screens.sources.MediaSources
import com.simplecityapps.shuttle.ui.screens.sources.MusicPermission
import com.simplecityapps.shuttle.ui.screens.sources.SourcesSettings
import com.simplecityapps.shuttle.ui.shell.ShellRequest
import com.simplecityapps.shuttle.ui.shell.ShellRoute
import com.simplecityapps.shuttle.ui.theme.S2AppTheme
import com.simplecityapps.trial.Billing
import com.simplecityapps.trial.EntitlementRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * The app's only screen: the Compose shell (docs/architecture/app-shell.md). An AppCompatActivity, because the
 * server sign-in dialogs and the Cast route chooser show as dialog fragments over it.
 */
class MainActivity : AppCompatActivity() {
    @ContributesTo(AppScope::class)
    interface Injector {
        fun inject(activity: MainActivity)
    }

    // Buffered, so a request that opens the app is delivered once the shell is composed.
    private val shellRequests = Channel<ShellRequest>(Channel.UNLIMITED)
    private val shellRequestFlow = shellRequests.receiveAsFlow()

    @Inject
    lateinit var viewModelFactory: MetroViewModelFactory

    @Inject
    lateinit var themeManager: ThemeManager

    @Inject
    lateinit var billing: Billing

    @Inject
    lateinit var observePaywallRequests: ObservePaywallRequests

    @Inject
    lateinit var trialDisclosures: TrialDisclosures

    @Inject
    lateinit var playRequests: PlayRequests

    @Inject
    lateinit var mediaSources: MediaSources

    @Inject
    lateinit var sourcesSettings: SourcesSettings

    @Inject
    lateinit var entitlementRepository: EntitlementRepository

    @Inject
    lateinit var reviewPrompt: ReviewPrompt

    private val musicPermissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            // Absent when only write access was missing: the music permission was already held
            val granted = results[MusicPermission.name] ?: return@registerForActivityResult
            sourcesSettings.musicPermissionRequested.value = true
            if (granted) mediaSources.scanThisDevice()
        }

    // Lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        appGraph<Injector>().inject(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // The XML theme still styles the dialog fragments shown over the shell.
        themeManager.setTheme(this)

        setContent {
            CompositionLocalProvider(LocalMetroViewModelFactory provides viewModelFactory) {
                S2AppTheme {
                    // Test tags as resource ids, so UiAutomator (the Baseline Profile journeys) and Maestro find them
                    Box(Modifier.semantics { testTagsAsResourceId = true }) {
                        ShellRoute(shellRequests = shellRequestFlow)
                        PaywallHost(observePaywallRequests)
                        TrialDisclosureHost(trialDisclosures)
                    }
                }
            }
        }

        // No onboarding (#379): ask for the music permission once, on first launch, and scan when it's granted.
        // A later grant goes through Settings > Media > Sources, or the system settings. One already held at startup
        // scans from MediaSources.scanIfNeverScanned.
        // Up to Android 9, someone who granted it before deleting needed write access is asked for that alone; it's in the
        // same storage group, so the system grants it without a prompt.
        val missingPermissions = MusicPermission.missing(this)
        if (savedInstanceState == null && missingPermissions.isNotEmpty()) {
            if (MusicPermission.name !in missingPermissions || !sourcesSettings.musicPermissionRequested.value) {
                musicPermissionRequest.launch(missingPermissions.toTypedArray())
            }
        }

        // Not on recreation, or on a relaunch from recents, which redeliver the intent that opened the file
        if (savedInstanceState == null && intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0) {
            handleViewIntent(intent)
            handleShortcutIntent(intent)
        }

        billing.queryPurchases()
        recordPurchase()
        if (savedInstanceState == null && reviewPrompt.takeIfDue()) {
            launchReviewFlow()
        }
    }

    override fun onResume() {
        super.onResume()

        billing.queryPurchases()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        handleViewIntent(intent)
        handleShortcutIntent(intent)
    }

    // Private

    private fun handleShortcutIntent(intent: Intent?) {
        ShellRequest.fromShortcutAction(intent?.action)?.let { shellRequests.trySend(it) }
    }

    private fun recordPurchase() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                entitlementRepository.entitlement.collect(reviewPrompt::onEntitlement)
            }
        }
    }

    /** Asks Play for its in-app review sheet; Play decides whether it actually shows. */
    private fun launchReviewFlow() {
        val reviewManager = ReviewManagerFactory.create(this)
        reviewManager.requestReviewFlow().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                if (!isFinishing) reviewManager.launchReviewFlow(this, task.result)
            } else {
                Timber.e(task.exception ?: Exception("Unknown"), "Failed to launch review flow")
            }
        }
    }

    /**
     * Plays an audio file another app opened with us. The read grant that comes with a content:// URI
     * belongs to this app and lasts while this activity's task does, so playback can open it.
     */
    private fun handleViewIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        playRequests.playFromUri(uri, intent.type)
    }
}
