package com.simplecityapps.shuttle

import kotlinx.coroutines.CoroutineScope

expect abstract class ViewModel() {
    val coroutineScope: CoroutineScope
}