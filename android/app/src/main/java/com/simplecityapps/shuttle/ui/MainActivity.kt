package com.simplecityapps.shuttle.ui

import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.simplecityapps.playback.mediasession.PlayRequests
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.di.AppCoroutineScope
import com.simplecityapps.shuttle.ui.common.view.SnowfallView
import com.simplecityapps.shuttle.ui.screens.paywall.showPaywallOnRequest
import com.simplecityapps.shuttle.ui.screens.sources.MediaSources
import com.simplecityapps.shuttle.ui.screens.sources.MusicPermission
import com.simplecityapps.shuttle.ui.screens.sources.SourcesSettings
import com.simplecityapps.trial.Billing
import com.simplecityapps.trial.ServerAccessGate
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

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

    var snowfallView: SnowfallView? = null

    private val musicPermissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            sourcesSettings.musicPermissionRequested.value = true
            if (granted) mediaSources.scanThisDevice()
        }

    // Lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        themeManager.setTheme(this)

        setContentView(R.layout.activity_main)

        val navHost = supportFragmentManager.findFragmentById(R.id.onboardingNavHostFragment) as NavHostFragment
        val navController = navHost.navController

        navController.setGraph(R.navigation.launch)

        // No onboarding (#379): ask for the music permission once, on first launch, and scan when it's granted.
        // A later grant goes through Settings > Media > Sources, or the system settings.
        if (savedInstanceState == null && !sourcesSettings.musicPermissionRequested.value && !MusicPermission.isGranted(this)) {
            musicPermissionRequest.launch(MusicPermission.name)
        }

        handleSearchQuery(intent)
        // Not on recreation, or on a relaunch from recents, which redeliver the intent that opened the file
        if (savedInstanceState == null && intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0) {
            handleViewIntent(intent)
        }

        billing.queryPurchases()
        showPaywallOnRequest(serverAccessGate)

        snowfallView = findViewById(R.id.snowfallView)

        scope.launch {
            withTimeout(5000) {
                remoteConfig.fetchAndActivate().await()
            }
            snowfallView?.setForecast(remoteConfig.getDouble("snow_forecast"))
        }
    }

    override fun onResume() {
        super.onResume()

        billing.queryPurchases()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        handleSearchQuery(intent)
        handleViewIntent(intent)
    }

    // Private

    /** Plays what a voice search (e.g. Assistant's "play X on S2") asks for. */
    private fun handleSearchQuery(intent: Intent?) {
        if (intent?.action == MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH) {
            playRequests.playFromSearch(intent.getStringExtra(SearchManager.QUERY), intent.extras)
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
