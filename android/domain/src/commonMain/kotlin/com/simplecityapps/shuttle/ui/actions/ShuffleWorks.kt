package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.shuttle.model.AlbumItem
import com.simplecityapps.shuttle.model.PlayContext
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.model.groupWorks
import dev.zacsweers.metro.Inject
import kotlin.random.Random

/** Shuffles an album by work: a work's movements stay together and in order, so a shuffle never lands mid-symphony. */
@Inject
class ShuffleWorks(
    private val playSongs: PlaySongs,
) {
    sealed interface Result {
        data object Success : Result
        data class Failure(val message: String?) : Result
    }

    suspend operator fun invoke(songs: List<Song>, context: PlayContext = PlayContext.None, random: Random = Random): Result {
        val shuffled = groupWorks(songs)
            .shuffled(random)
            .flatMap { item ->
                when (item) {
                    is AlbumItem.Track -> listOf(item.song)
                    is AlbumItem.Work -> item.block.songs
                }
            }
        return when (val result = playSongs(shuffled, 0, context)) {
            is PlaySongs.Result.Success -> Result.Success
            is PlaySongs.Result.Failure -> Result.Failure(result.message)
        }
    }
}
