package com.simplecityapps.shuttle

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope

actual abstract class ViewModel : androidx.lifecycle.ViewModel() {

    actual val coroutineScope: CoroutineScope
        get() = viewModelScope
}