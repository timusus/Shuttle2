package com.simplecityapps.shuttle.model

/**
 * The ribbon over the current work: [segments] are durations (one per movement, or per act when the movement names carry
 * Act prefixes), [segmentStarts] the queue index each begins at. [continuous] above 12 segments.
 */
data class WorkProgress(
    val segments: List<Long>,
    val segmentStarts: List<Int>,
    val currentSegment: Int,
    val movementIndex: Int,
    val movementCount: Int,
    val remainingMs: Long,
    val continuous: Boolean,
    /** Time in the current segment before the current movement starts (non-zero only for an act of several movements). */
    val currentSegmentOffsetMs: Long = 0
)

/** Null when the song at [index] is not part of a work of two or more movements. [positionMs] is the playback position in that song. */
fun workProgress(queue: List<Song>, index: Int, positionMs: Long = 0): WorkProgress? {
    if (index !in queue.indices) return null
    val run = runAround(queue, index).takeIf { it.work != null } ?: return null
    return workProgress(run, buildBlock(queue.slice(run.range), run.work!!, run.inferred, null), index, positionMs)
}

internal fun workProgress(run: Run, block: WorkBlock, index: Int, positionMs: Long): WorkProgress {
    val current = index - run.range.first
    val ranges = block.acts.map { it.movements }.ifEmpty { block.movements.indices.map { it..it } }
    val durations = block.movements.map { it.song.duration.toLong() }
    val segments = ranges.map { range -> range.sumOf { durations[it] } }
    val played = positionMs.coerceIn(0, durations[current])
    return WorkProgress(
        segments = segments,
        segmentStarts = ranges.map { run.range.first + it.first },
        currentSegment = ranges.indexOfFirst { current in it },
        movementIndex = current,
        movementCount = block.movements.size,
        remainingMs = durations.drop(current).sum() - played,
        continuous = segments.size > 12,
        currentSegmentOffsetMs = ranges.first { current in it }.let { range -> range.first.until(current).sumOf { durations[it] } }
    )
}

/** What the lock screen, notification and Android Auto show: the movement as title, "Beethoven: Symphony No. 5 · Kleiber" as artist. */
data class LockScreenText(val title: String, val artist: String?)

/** Null unless the song at [index] is classical or part of a work. */
fun lockScreenText(queue: List<Song>, index: Int, library: Collection<ComposerName> = emptyList()): LockScreenText? {
    val song = queue.getOrNull(index) ?: return null
    val run = runAround(queue, index)
    if (run.work == null && !song.isClassicalWork()) return null
    val songs = queue.slice(run.range)
    val work = run.work ?: song.cleanWork()
    val block = buildBlock(songs, work.orEmpty(), run.inferred, null)
    val movement = block.movements[index - run.range.first]
    val workPart = when {
        work == null -> song.composerDisplayName(library)
        else -> listOfNotNull(song.composerDisplayName(library), block.title.core).joinToString(": ")
    }
    val artist = listOfNotNull(workPart, song.leadPerformer()).joinToString(" · ").ifEmpty { null }
    return LockScreenText(movement.label.ifEmpty { song.name.orEmpty() }, artist)
}
