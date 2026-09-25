package com.simplecityapps.shuttle.ui.shell

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.simplecityapps.shuttle.ui.theme.S2AppTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Debug-only launcher for the Compose shell (#375), beside the production MainActivity. It shares
 * the app process, so the player follows the real queue. A FragmentActivity, because the Cast
 * button shows its route chooser as a dialog fragment.
 */
@AndroidEntryPoint
class ShellActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            S2AppTheme {
                ShellRoute()
            }
        }
    }
}
