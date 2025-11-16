package com.simplecityapps.shuttle.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.NavHostFragment
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.simplecityapps.playback.PlaybackService
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.di.AppCoroutineScope
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.ui.common.view.SnowfallView
import com.simplecityapps.trial.BillingManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject
    lateinit var preferenceManager: GeneralPreferenceManager

    @Inject
    lateinit var themeManager: ThemeManager

    @Inject
    lateinit var billingManager: BillingManager

    @Inject
    lateinit var remoteConfig: FirebaseRemoteConfig

    @Inject
    @AppCoroutineScope
    lateinit var scope: CoroutineScope

    lateinit var snowfallView: SnowfallView

    // Lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        themeManager.setTheme(this)

        setContentView(R.layout.activity_main)

        val navHost = supportFragmentManager.findFragmentById(R.id.onboardingNavHostFragment) as NavHostFragment
        val navController = navHost.navController

        val navInflater = navController.navInflater
        val graph = navInflater.inflate(R.navigation.launch)

        if (!preferenceManager.hasOnboarded || !hasStoragePermission()) {
            graph.setStartDestination(R.id.onboardingFragment)
        } else {
            graph.setStartDestination(R.id.mainFragment)
        }

        navController.graph = graph

        handleSearchQuery(intent)

        billingManager.queryPurchases()

        snowfallView = findViewById(R.id.snowfallView)

        scope.launch {
            withTimeout(5000) {
                remoteConfig.fetchAndActivate().await()
            }
            snowfallView.setForecast(remoteConfig.getDouble("snow_forecast"))
        }
    }

    override fun onResume() {
        super.onResume()

        billingManager.queryPurchases()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        handleSearchQuery(intent)
    }

    // Private

    private fun hasStoragePermission(): Boolean = (ContextCompat.checkSelfPermission(this, getStoragePermission()) == PackageManager.PERMISSION_GRANTED)

    private fun getStoragePermission(): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    private fun handleSearchQuery(intent: Intent?) {
        if (intent?.action == MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH) {
            ContextCompat.startForegroundService(
                this,
                Intent(this, PlaybackService::class.java).apply {
                    action = PlaybackService.ACTION_SEARCH
                    intent.extras?.let { extras ->
                        putExtras(extras)
                    }
                }
            )
        }
    }
}
