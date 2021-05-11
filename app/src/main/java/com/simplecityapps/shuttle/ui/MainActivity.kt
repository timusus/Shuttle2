package com.simplecityapps.shuttle.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.MediaStore
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.NavHostFragment
import com.simplecityapps.playback.PlaybackService
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity :
    AppCompatActivity() {

    @Inject
    lateinit var preferenceManager: GeneralPreferenceManager

    @Inject
    lateinit var themeManager: ThemeManager

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
            graph.startDestination = R.id.onboardingFragment
        } else {
            graph.startDestination = R.id.mainFragment
        }

        navController.graph = graph

        handleSearchQuery(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        handleSearchQuery(intent)
    }

    // Private

    private fun hasStoragePermission(): Boolean {
        return (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
    }

    private fun handleSearchQuery(intent: Intent?) {
        if (intent?.action == MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH) {
            ContextCompat.startForegroundService(this, (Intent(this, PlaybackService::class.java).apply {
                action = PlaybackService.ACTION_SEARCH
                intent.extras?.let { extras ->
                    putExtras(extras)
                }
            }))
        }
    }
}