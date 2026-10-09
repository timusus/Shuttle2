package com.simplecityapps.mediaprovider.server

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The `PlaySessionId` a Jellyfin or Emby stream is opened under, so the play's progress reports carry the same one: the
 * server throttles or ends a transcode it sees no reports for, and reports under another id look like no reports.
 *
 * A stream is opened under its play's id ([open]), or a fresh one without a play. The reporter's play has an id of its
 * own, which [begin] swaps for the latest stream opened for the item, held for the play's reports ([of]) until [end].
 */
@OptIn(ExperimentalUuidApi::class)
class PlaySessionIds {
    private val lock = Lock()
    private val opened = LinkedHashMap<String, String>()
    private val reporting = LinkedHashMap<String, String>()

    fun open(
        itemId: String,
        playId: String?
    ): String {
        val id = playId ?: Uuid.random().toString()
        lock.withLock { opened.put(itemId, id, MAX_ENTRIES) }
        return id
    }

    fun begin(
        reportId: String,
        itemId: String
    ): String = lock.withLock {
        val id = opened[itemId] ?: reportId
        reporting.put(reportId, id, MAX_ENTRIES)
        id
    }

    fun of(reportId: String): String = lock.withLock { reporting[reportId] } ?: reportId

    fun end(reportId: String) {
        lock.withLock { reporting.remove(reportId) }
    }

    private fun LinkedHashMap<String, String>.put(
        key: String,
        value: String,
        max: Int
    ) {
        remove(key)
        put(key, value)
        while (size > max) remove(keys.first())
    }

    private companion object {
        const val MAX_ENTRIES = 32
    }
}
