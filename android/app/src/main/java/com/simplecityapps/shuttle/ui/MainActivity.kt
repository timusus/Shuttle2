package com.simplecityapps.shuttle.ui

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.simplecityapps.playback.mediasession.PlayRequests
import com.simplecityapps.shuttle.di.AppCoroutineScope
import com.simplecityapps.shuttle.ui.common.view.SnowfallView
import com.simplecityapps.shuttle.ui.screens.paywall.showPaywallOnRequest
import com.simplecityapps.shuttle.ui.screens.sources.MediaSources
import com.simplecityapps.shuttle.ui.screens.sources.MusicPermission
import com.simplecityapps.shuttle.ui.screens.sources.SourcesSettings
import com.simplecityapps.shuttle.ui.shell.ShellRoute
import com.simplecityapps.shuttle.ui.theme.S2AppTheme
import com.simplecityapps.trial.Billing
import com.simplecityapps.trial.EntitlementRepository
import com.simplecityapps.trial.ServerAccessGate
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import timber.log.Timber

/**
 * The app's only screen: the Compose shell (docs/architecture/app-shell.md). An AppCompatActivity, because the
 * server sign-in dialogs, the Cast route chooser and the paywall show as dialog fragments over it.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject
    lateinit var themeManager: ThemeManager

    @Inject
    lateinit var billing: Billing

    @Inject
    lateinit var serverAccessGate: ServerAccessGate

    @Inject
    lateinit var remoteConfig: FirebaseRemoteConfig

    @Inject
    lateinit var playRequests: PlayRequests

    @Inject
    @AppCoroutineScope
    lateinit var scope: CoroutineScope

    @Inject
    lateinit var mediaSources: MediaSources

    @Inject
    lateinit var sourcesSettings: SourcesSettings

    @Inject
    lateinit var entitlementRepository: EntitlementRepository

    @Inject
    lateinit var reviewPrompt: ReviewPrompt

    private val musicPermissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            sourcesSettings.musicPermissionRequested.value = true
            if (granted) mediaSources.scanThisDevice()
        }

    // Lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // The XML theme still styles the dialog fragments shown over the shell.
        themeManager.setTheme(this)

        setContent {
            S2AppTheme {
                ShellRoute()
            }
        }

        // Over the shell, and blind to touches, so they reach it
        val snowfallView = SnowfallView(this, null)
        addContentView(snowfallView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // No onboarding (#379): ask for the music permission once, on first launch, and scan when it's granted.
        // A later grant goes through Settings > Media > Sources, or the system settings. One already held at startup
        // scans from MediaSources.scanIfNeverScanned.
        if (savedInstanceState == null && !MusicPermission.isGranted(this) && !sourcesSettings.musicPermissionRequested.value) {
            musicPermissionRequest.launch(MusicPermission.name)
        }

        // Not on recreation, or on a relaunch from recents, which redeliver the intent that opened the file
        if (savedInstanceState == null && intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0) {
            handleViewIntent(intent)
        }

        billing.queryPurchases()
        showPaywallOnRequest(serverAccessGate)
        recordPurchase()
        if (savedInstanceState == null && reviewPrompt.takeIfDue()) {
            launchReviewFlow()
        }

        scope.launch {
            withTimeout(5000) {
                remoteConfig.fetchAndActivate().await()
            }
            snowfallView.post { snowfallView.setForecast(remoteConfig.getDouble("snow_forecast")) }
        }
    }

    override fun onResume() {
        super.onResume()

        billing.queryPurchases()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        handleViewIntent(intent)
    }

    // Private

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
