package com.simplecityapps.shuttle.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.simplecityapps.shuttle.compose.ui.features.main.MainContent
import com.simplecityapps.shuttle.compose.ui.theme.AppTheme
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.ui.screens.library.genres.toAccent
import com.simplecityapps.shuttle.ui.screens.library.genres.toTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

class MainViewModel @Inject constructor(
    preferenceManager: GeneralPreferenceManager,
) : ViewModel() {
    val theme = preferenceManager.theme(viewModelScope)
    val accent = preferenceManager.accent(viewModelScope)
    val extraDark = preferenceManager.extraDark(viewModelScope)
}

@AndroidEntryPoint
class ComposeActivity : AppCompatActivity() {

    @Inject
    lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {

            val theme by viewModel.theme.collectAsStateWithLifecycle()
            val accent by viewModel.accent.collectAsStateWithLifecycle()
            val extraDark by viewModel.extraDark.collectAsStateWithLifecycle()

            AppTheme(
                theme = theme.toTheme(),
                accent = accent.toAccent(),
                extraDark = extraDark
            ) {
                MainContent()
            }
        }
    }
}



