package com.simplecityapps.shuttle.ui

import kotlinx.coroutines.CoroutineScope

expect abstract class ViewModel() {
    val coroutineScope: CoroutineScope
}