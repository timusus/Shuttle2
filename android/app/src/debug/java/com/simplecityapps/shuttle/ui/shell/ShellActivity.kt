package com.simplecityapps.shuttle.ui.shell

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simplecityapps.shuttle.ui.theme.S2AppTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Debug-only launcher for the Compose shell spike (#375), beside the production MainActivity.
 * It shares the app process, so the player follows the real queue.
 */
@AndroidEntryPoint
class ShellActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            S2AppTheme {
                val viewModel: ShellViewModel = hiltViewModel()
                val queue by viewModel.queue.collectAsStateWithLifecycle()
                AppShell(queue = queue)
            }
        }
    }
}
