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
import dagger.android.AndroidInjector
import dagger.android.DispatchingAndroidInjector
import dagger.android.HasAndroidInjector
import javax.inject.Inject


class MainActivity :
    AppCompatActivity(),
    HasAndroidInjector {

    @Inject lateinit var dispatchingAndroidInjector: DispatchingAndroidInjector<Any>

    @Inject lateinit var generalPreferenceManager: GeneralPreferenceManager


    // Lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        val navHost = supportFragmentManager.findFragmentById(R.id.onboardingNavHostFragment) as NavHostFragment
        val navController = navHost.navController

        val navInflater = navController.navInflater
        val graph = navInflater.inflate(R.navigation.launch)

        if (!generalPreferenceManager.hasOnboarded || !hasStoragePermission()) {
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

    // HasAndroidInjector Implementation

    override fun androidInjector(): AndroidInjector<Any> {
        return dispatchingAndroidInjector
    }
}