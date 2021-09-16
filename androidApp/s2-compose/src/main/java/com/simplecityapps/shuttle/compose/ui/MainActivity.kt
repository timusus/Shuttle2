package com.simplecityapps.shuttle.compose.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.simplecityapps.shuttle.compose.ui.components.root.Root
import com.simplecityapps.shuttle.compose.ui.theme.Theme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            Theme {
                Root()
            }
        }
    }
}