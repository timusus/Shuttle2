package com.simplecityapps.shuttle.compose.ui.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.BottomAppBar
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.simplecityapps.shuttle.compose.ui.theme.MaterialColors
import com.simplecityapps.shuttle.compose.ui.theme.S2androidTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            S2androidTheme {
                S2App()
            }
        }
    }
}

@Composable
fun S2App() {
    // A surface container using the 'background' color from the theme
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colors.background
    ) {
        Scaffold(
            bottomBar = {
                BottomAppBar(
                    backgroundColor = MaterialColors.background
                ) {
                    AppBottomNavigation(selected = false) {

                    }
                }
            }
        ) {

        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    S2androidTheme {
        S2App()
    }
}