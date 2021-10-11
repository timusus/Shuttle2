package com.simplecityapps.shuttle.ui

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope

actual abstract class ViewModel : androidx.lifecycle.ViewModel() {
    actual val coroutineScope: CoroutineScope
        get() = viewModelScope
}