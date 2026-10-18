package com.simplecityapps.playback.queue

import com.simplecityapps.shuttle.model.PlayContext

/** [QueueItem] equality is by uid alone, so [version] is bumped on each publish to keep snapshots distinct. */
data class QueueState(
    val items: List<QueueItem>,
    val currentItem: QueueItem?,
    val currentPosition: Int?,
    val version: Long = 0,
    val contentVersion: Long = 0,
    val nonMoveContentVersion: Long = 0,
    val songDataVersion: Long = 0,
    val isRestored: Boolean = false,
    val shuffleMode: ShuffleMode = ShuffleMode.Off,
    val playContext: PlayContext = PlayContext.None
) {
    companion object {
        val Empty = QueueState(items = emptyList(), currentItem = null, currentPosition = null)
    }
}
