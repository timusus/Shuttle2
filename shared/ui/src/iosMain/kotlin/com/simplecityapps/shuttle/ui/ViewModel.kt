package com.simplecityapps.shuttle.ui

import kotlinx.coroutines.CoroutineScope

actual abstract class ViewModel {

    actual val coroutineScope: CoroutineScope
        get() = TODO("Not yet implemented")
}