package com.simplecityapps.shuttle.model

/**
 * What the player surfaces show for a song inside a work: the work with its movement, performers and the [ribbon] over
 * the whole work. Built only for a work of two or more movements; every other song keeps its own title and artist.
 */
data class NowPlayingWork(
    val composer: String?,
    val work: String,
    /** Nickname, key, catalogue and arrangement on one secondary line. */
    val detail: String?,
    val movement: String,
    val performers: String?,
    /** "Beethoven: Symphony No. 5" */
    val miniTitle: String,
    /** "II. Andante con moto · Kleiber" */
    val miniSubtitle: String,
    val ribbon: WorkProgress,
    /** "II" of "IV", numbered like the movements themselves. */
    val ordinal: String,
    val total: String
)

/** Null unless the song at [index] sits in a work block; [library] lets two composers sharing a surname show their full names. */
fun nowPlayingWork(queue: List<Song>, index: Int, library: Collection<ComposerName> = emptyList()): NowPlayingWork? {
    if (index !in queue.indices) return null
    val run = runAround(queue, index).takeIf { it.work != null } ?: return null
    val block = buildBlock(queue.slice(run.range), run.work.orEmpty(), run.inferred, null)
    val ribbon = workProgress(run, block, index, 0)
    val song = queue[index]
    val movement = block.movements[ribbon.movementIndex]
    val composer = song.composerDisplayName(library)
    val label = movement.label.ifEmpty { song.name.orEmpty() }
    val title = block.title
    return NowPlayingWork(
        composer = composer,
        work = title.core,
        detail = listOfNotNull(title.nickname, title.key, title.catalogue, title.arrangement).joinToString(" · ").ifEmpty { null },
        movement = label,
        performers = song.creditsLine(),
        miniTitle = listOfNotNull(composer, title.core).joinToString(": "),
        miniSubtitle = listOfNotNull(label, song.leadPerformer()).joinToString(" · "),
        ribbon = ribbon,
        ordinal = position(block.numbering, ribbon.movementIndex + 1),
        total = position(block.numbering, ribbon.movementCount)
    )
}

private fun position(numbering: WorkNumbering, number: Int) = if (numbering.style == NumeralStyle.Roman) toRoman(number) else number.toString()

/** The ribbon's progress through the work at [positionMs] into the current movement, 0..1 per segment; the current one fills. */
fun WorkProgress.segmentFill(positionMs: Long): Float {
    val length = segments[currentSegment]
    return if (length <= 0) 0f else ((currentSegmentOffsetMs + positionMs) / length.toFloat()).coerceIn(0f, 1f)
}

/** "24:10", or "1:02:05" from an hour. */
fun formatWorkRemaining(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val hours = total / 3600
    val minutes = total % 3600 / 60
    val seconds = total % 60
    return if (hours > 0) "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}" else "$minutes:${seconds.toString().padStart(2, '0')}"
}
