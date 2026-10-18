package com.simplecityapps.localmediaprovider.local.data.room.entity

import androidx.room.Entity
import com.simplecityapps.shuttle.model.MediaProviderType
import kotlin.time.Instant

/** Where a play context's queue was left, keyed by song path rather than row id so a reimport keeps it. */
@Entity(tableName = "resume_points", primaryKeys = ["contextType", "contextId"])
data class ResumePointData(
    val contextType: String,
    val contextId: String,
    val mediaProvider: MediaProviderType,
    val songPath: String,
    val positionMs: Long,
    val track: Int,
    val trackCount: Int,
    val shuffled: Boolean,
    val finished: Boolean,
    val updatedAt: Instant
)
