package com.simplecityapps.localmediaprovider.local.data.room.entity

import androidx.room.Entity
import com.simplecityapps.shuttle.model.MediaProviderType
import kotlin.time.Instant

/**
 * Where the queue started from a [com.simplecityapps.shuttle.model.PlayContext] was left (#670), one row per context:
 * its current song, keyed like [PlayEventData]'s by [mediaProvider] and [songPath], the [positionMs] in it, the song's
 * place in the queue as it played ([track] of [trackCount], from 0, in the shuffled order when [shuffled]), and whether
 * the queue [finished] (its last song played through).
 */
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
