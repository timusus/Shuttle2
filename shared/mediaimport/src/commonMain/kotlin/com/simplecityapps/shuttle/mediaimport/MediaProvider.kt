package com.simplecityapps.shuttle.mediaimport

import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Progress
import com.simplecityapps.shuttle.model.SongData
import kotlinx.coroutines.flow.Flow

interface MediaProvider {

    val type: MediaProviderType

    sealed class SongRetrievalState {
        data class QueryingApi(val progress: Progress) : SongRetrievalState()
        data class ReadingSongData(val progress: Progress, val songData: SongData) : SongRetrievalState()
        data class Complete(val songData: List<SongData>) : SongRetrievalState()
        object Failed : SongRetrievalState()
    }

    fun findSongs(): Flow<SongRetrievalState>
}