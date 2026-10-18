package com.simplecityapps.shuttle.model

/** One row of an album: a lone track, or a work of two or more consecutive movements. */
sealed interface AlbumItem {
    data class Track(val song: Song) : AlbumItem

    data class Work(val block: WorkBlock) : AlbumItem
}

/** [numeral] is null when the name carries its own label or a lone track has no number; [name] never repeats the numeral or the work. */
data class Movement(val song: Song, val number: Int, val name: String, val numeral: String?) {
    /** "II. Andante con moto", or just the name when there is no numeral. */
    val label: String get() = if (numeral != null) "$numeral. $name" else name
}

/** A sub-header ("Act 2") over [movements]; [label] is null for movements before the first act (an overture). */
data class ActSection(val label: String?, val movements: IntRange)

data class WorkBlock(
    val work: String,
    val title: WorkTitle,
    val composer: String?,
    val inferred: Boolean,
    val movements: List<Movement>,
    val numbering: WorkNumbering,
    val acts: List<ActSection>,
    val durationMs: Long,
    /** Null when it matches the album's credits. */
    val credits: String?,
    val collapsedByDefault: Boolean
) {
    /** Movements listed in this block, not tracks: the album header describes its rows, so a tagged total of the whole work doesn't apply. */
    val movementCount: Int get() = movements.map { it.number }.distinct().size

    /** Movements in the whole work, for "III of V" with a partial queue: the tagged total wins, never below the highest number present. */
    val movementTotal: Int
        get() {
            val tagged = movements.firstNotNullOfOrNull { it.song.movementTotal?.takeIf { total -> total >= 1 } }
            return maxOf(tagged ?: movements.map { it.number }.distinct().size, movements.maxOf { it.number })
        }
    val songs: List<Song> get() = movements.map { it.song }
}

private val actRef = Regex("""^(Act|Akt)\s+(\d+|[IVX]+)\b""", RegexOption.IGNORE_CASE)

private fun actSections(movements: List<Movement>): List<ActSection> {
    val acts = movements.map { actRef.find(it.name) }
    if (acts.all { it == null }) return emptyList()
    val sections = mutableListOf<ActSection>()
    var label: String? = null
    var start = 0
    acts.forEachIndexed { index, match ->
        if (match == null) return@forEachIndexed
        val next = "${match.groupValues[1].lowercase().replaceFirstChar(Char::uppercase)} ${match.groupValues[2].uppercase()}"
        if (next != label) {
            if (index > start) sections += ActSection(label, start until index)
            label = next
            start = index
        }
    }
    sections += ActSection(label, start until movements.size)
    return sections
}

internal fun buildBlock(songs: List<Song>, work: String, inferred: Boolean, albumCredits: String?): WorkBlock {
    val numbering = workNumbering(songs)
    var previous: Int? = null
    val raws = songs.map { song -> song.movementName?.trim()?.takeIf { it.isNotEmpty() } ?: song.workParts()?.second ?: song.name.orEmpty() }
    val loose = looseNumeralsFormRun(raws, work)
    val movements = songs.mapIndexed { index, song ->
        val raw = raws[index]
        val name = cleanMovementName(raw, work, loose)
        val tagged = song.movementNumber?.takeIf { it >= 1 }
        val fromTitle = if (tagged == null) movementNumberIn(raw, work, loose) else null
        val number = tagged ?: fromTitle ?: (index + 1)
        // A movement split over several tracks ("V. ...", "V. ...") is numbered on its first track only; a tagged number is never repeated by mistake.
        val repeated = fromTitle != null && fromTitle == previous
        previous = fromTitle
        val numeral = if (!repeated && (songs.size > 1 || song.movementNumber != null) && numbering.styleFor(name) != NumeralStyle.None) numbering.format(number) else null
        Movement(song, number, name, numeral)
    }
    return WorkBlock(
        work = work,
        title = parseWorkTitle(work),
        composer = songs.firstNotNullOfOrNull { it.cleanComposer() },
        inferred = inferred,
        movements = movements,
        numbering = numbering,
        acts = actSections(movements),
        durationMs = songs.sumOf { it.duration.toLong() },
        credits = workCreditsLine(songs, albumCredits),
        collapsedByDefault = false
    )
}

/**
 * Album rows in order: consecutive songs of one work and composer (a work tag, else a shared "Work: movement" title
 * prefix) form a [AlbumItem.Work] of two or more; every other song is a [AlbumItem.Track].
 */
fun groupWorks(songs: List<Song>): List<AlbumItem> {
    val runs = findRuns(songs)
    val blockCount = runs.count { it.work != null }
    val albumCredits = albumCreditsLine(songs)
    return runs.flatMap { run ->
        if (run.work == null) return@flatMap listOf(AlbumItem.Track(songs[run.range.first]))
        val block = buildBlock(songs.slice(run.range), run.work, run.inferred, albumCredits)
        listOf(AlbumItem.Work(block.copy(collapsedByDefault = block.movements.size > 20 && blockCount > 1)))
    }
}
