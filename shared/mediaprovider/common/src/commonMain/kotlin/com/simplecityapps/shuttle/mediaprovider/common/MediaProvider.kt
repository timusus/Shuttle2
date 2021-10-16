package com.simplecityapps.shuttle.mediaprovider.common

import com.simplecityapps.shuttle.model.*
import kotlinx.coroutines.flow.Flow

interface MediaProvider {

    val type: MediaProviderType

    fun findSongs(): Flow<SongRetrievalState>

    fun findPlaylists(existingSongs: List<Song>): Flow<PlaylistRetrievalState>

    sealed class SongRetrievalState {
        data class QueryingApi(val progress: Progress) : SongRetrievalState()
        data class ReadingSongData(val progress: Progress, val songData: SongData) : SongRetrievalState()
        data class QueryingDatabase(val progress: Progress?) : SongRetrievalState()
        data class Complete(val songData: List<SongData>) : SongRetrievalState()
        object Failed : SongRetrievalState() {
            override fun toString(): String {
                return "Failed"
            }
        }
    }

    sealed class PlaylistRetrievalState {
        data class QueryingApi(val progress: Progress) : PlaylistRetrievalState()
        data class QueryingDatabase(val progress: Progress?) : PlaylistRetrievalState()
        data class Complete(val playlistData: List<PlaylistData>) : PlaylistRetrievalState()
        object Failed : PlaylistRetrievalState() {
            override fun toString(): String {
                return "Failed"
            }
        }
    }
}