package com.simplecityapps.localmediaprovider.local.data.room.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.simplecityapps.localmediaprovider.local.data.room.entity.ResumePointData

@Dao
interface ResumePointDao {
    @Upsert
    suspend fun upsert(point: ResumePointData)

    /**
     * The resume points of the contexts with any of [contextIds], whatever their type (a caller matches the type), each with
     * its song's name and duration while the song is still in the library (#706).
     */
    @Query(
        """
        SELECT resume_points.*, songs.name AS songName, songs.duration AS songDuration FROM resume_points
        LEFT JOIN songs ON songs.path = resume_points.songPath AND songs.mediaProvider = resume_points.mediaProvider
        WHERE resume_points.contextId IN (:contextIds)
        """
    )
    suspend fun getByContextIds(contextIds: List<String>): List<ResumePointWithSong>

    @Query("DELETE FROM resume_points")
    suspend fun clear()

    /** The ids of the contexts of [contextType] with a resume point. */
    @Query("SELECT contextId FROM resume_points WHERE contextType = :contextType")
    suspend fun contextIds(contextType: String): List<String>

    /**
     * Moves the resume point of [from] to [to]. When [to] has one already (two old keys naming one album now), the one
     * with the later updatedAt stays (the target on a tie): one resume point remains, the newest.
     */
    @Transaction
    suspend fun move(
        contextType: String,
        from: String,
        to: String
    ) {
        deleteOlderThan(contextType, from, to)
        moveIfFree(contextType, from, to)
        delete(contextType, from)
    }

    @Query(
        """
        DELETE FROM resume_points WHERE contextType = :contextType AND contextId = :to
        AND updatedAt < (SELECT updatedAt FROM resume_points WHERE contextType = :contextType AND contextId = :from)
        """
    )
    suspend fun deleteOlderThan(
        contextType: String,
        from: String,
        to: String
    )

    @Query("UPDATE OR IGNORE resume_points SET contextId = :to WHERE contextType = :contextType AND contextId = :from")
    suspend fun moveIfFree(
        contextType: String,
        from: String,
        to: String
    )

    @Query("DELETE FROM resume_points WHERE contextType = :contextType AND contextId = :contextId")
    suspend fun delete(
        contextType: String,
        contextId: String
    )
}

/** A resume point and its song as the library has it now: null [songName] and [songDuration] once it's gone. */
data class ResumePointWithSong(
    @Embedded val point: ResumePointData,
    val songName: String?,
    val songDuration: Int?
)
