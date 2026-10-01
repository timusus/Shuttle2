package com.simplecityapps.localmediaprovider.local.data.room.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Upsert
import com.simplecityapps.localmediaprovider.local.data.room.entity.ResumePointData

@Dao
interface ResumePointDao {
    @Upsert
    suspend fun upsert(point: ResumePointData)

    /** The context's resume point, with its song's name and duration while the song is still in the library (#706). */
    @Query(
        """
        SELECT resume_points.*, songs.name AS songName, songs.duration AS songDuration FROM resume_points
        LEFT JOIN songs ON songs.path = resume_points.songPath AND songs.mediaProvider = resume_points.mediaProvider
        WHERE resume_points.contextType = :contextType AND resume_points.contextId = :contextId
        """
    )
    suspend fun get(
        contextType: String,
        contextId: String
    ): ResumePointWithSong?

    @Query("DELETE FROM resume_points")
    suspend fun clear()
}

/** A resume point and its song as the library has it now: null [songName] and [songDuration] once it's gone. */
data class ResumePointWithSong(
    @Embedded val point: ResumePointData,
    val songName: String?,
    val songDuration: Int?
)
