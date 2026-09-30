package com.simplecityapps.localmediaprovider.local.data.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.simplecityapps.localmediaprovider.local.data.room.entity.ResumePointData

@Dao
interface ResumePointDao {
    @Upsert
    suspend fun upsert(point: ResumePointData)

    @Query("SELECT * FROM resume_points WHERE contextType = :contextType AND contextId = :contextId")
    suspend fun get(
        contextType: String,
        contextId: String
    ): ResumePointData?

    @Query("DELETE FROM resume_points")
    suspend fun clear()
}
