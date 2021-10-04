package com.simplecityapps.shuttle.compose.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.hilt.navigation.compose.hiltViewModel
import com.simplecityapps.shuttle.compose.ui.components.root.Root
import com.simplecityapps.shuttle.compose.ui.theme.Theme
import com.simplecityapps.shuttle.ui.root.RootViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            Theme {
                Root(hiltViewModel() as RootViewModel)
            }
        }
    }
}