package com.simplecityapps.mediaprovider

import kotlinx.coroutines.flow.StateFlow

interface SongImportStateProvider {
    val songImportState: StateFlow<SongImportState>
}
