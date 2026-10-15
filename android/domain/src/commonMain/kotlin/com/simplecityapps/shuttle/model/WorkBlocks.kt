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

/** A disc change inside a work: a divider goes before the movement at [beforeIndex]. */
data class DiscBreak(val beforeIndex: Int, val disc: Int)

/** A sub-header ("Act 2") over [movements]; [label] is null for movements before the first act (an overture). */
data class ActSection(val label: String?, val movements: IntRange)

data class WorkBlock(
    val work: String,
    val title: WorkTitle,
    val composer: String?,
    val inferred: Boolean,
    val movements: List<Movement>,
    val numbering: WorkNumbering,
    val discBreaks: List<DiscBreak>,
    val acts: List<ActSection>,
    val durationMs: Long,
    /** Null when it matches the album's credits. */
    val credits: String?,
    val collapsedByDefault: Boolean
) {
    val movementCount: Int get() = movements.size
    val songs: List<Song> get() = movements.map { it.song }
}

private class Run(val range: IntRange, val work: String?, val inferred: Boolean)

private fun String.normalised(): String = lowercase()
    .replace(Regex("""\bop\.?\s*(?=\d)"""), "op")
    .map { if (it.isLetterOrDigit()) it else ' ' }
    .joinToString("")
    .replace(Regex("""\s+"""), " ")
    .trim()

private fun Song.cleanWork(): String? = work?.trim()?.takeIf { it.isNotEmpty() }

private fun Song.cleanComposer(): String? = composer?.trim()?.takeIf { it.isNotEmpty() }

private val titleSeparators = listOf(": ", " - ")

/** The "Work" of a "Work: movement" title, or null when the title has no such split. */
private fun Song.titlePrefix(): String? {
    val title = name?.trim() ?: return null
    val at = titleSeparators.map { title.indexOf(it) }.filter { it > 0 }.minOrNull() ?: return null
    if (title.substring(at).trim(' ', ':', '-').isEmpty()) return null
    return title.substring(0, at).trim().takeIf { it.isNotEmpty() }
}

private fun runKey(song: Song): String? {
    val composer = song.cleanComposer()?.normalised().orEmpty()
    song.cleanWork()?.let { return "w:${it.normalised()}|$composer" }
    val prefix = song.titlePrefix() ?: return null
    return if (composer.isEmpty()) null else "i:${prefix.normalised()}|$composer"
}

private val formWords = Regex(
    """\b(?:Symphon(?:y|ie)|Sonata|Concerto|Suite|Quartet|Trio|Partita|Mass|Requiem|Variations|Preludes|Études|Etudes|Cantata|Overture|Serenade)\b""",
    RegexOption.IGNORE_CASE
)

/** Whether a shared title prefix on [songs] is a classical work rather than "Artist - Song" or "Hold On - Live". */
private fun looksLikeInferredWork(songs: List<Song>, prefix: String): Boolean {
    val first = songs.first()
    val composer = first.cleanComposer() ?: return false
    val albumArtist = first.albumArtist?.trim()
    if (albumArtist != null && composer.equals(albumArtist, ignoreCase = true)) return false
    val name = prefix.normalised()
    val credited = songs.flatMap { it.artists + listOfNotNull(it.albumArtist, it.album) }
    if (credited.any { it.normalised() == name }) return false
    val classicalGenre = songs.any { song -> song.genres.any { genre -> classicalGenreWords.any { genre.contains(it, ignoreCase = true) } } }
    val tagged = songs.any { !it.conductor.isNullOrBlank() || it.performers.orEmpty().isNotEmpty() }
    return classicalGenre || tagged || parseWorkTitle(prefix).catalogue != null || workNumberRef.containsMatchIn(prefix) || formWords.containsMatchIn(prefix)
}

private fun findRuns(songs: List<Song>): List<Run> {
    val runs = mutableListOf<Run>()
    var start = 0
    while (start < songs.size) {
        val key = runKey(songs[start])
        var end = start
        while (key != null && end + 1 < songs.size && runKey(songs[end + 1]) == key) end++
        val first = songs[start]
        val tagged = first.cleanWork()
        val prefix = if (tagged == null) first.titlePrefix() else null
        val grouped = end > start && (tagged != null || (prefix != null && looksLikeInferredWork(songs.slice(start..end), prefix)))
        if (grouped) {
            runs += Run(start..end, tagged ?: prefix, inferred = tagged == null)
        } else {
            for (index in start..end) runs += Run(index..index, null, false)
        }
        start = end + 1
    }
    return runs
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

private fun buildBlock(songs: List<Song>, work: String, inferred: Boolean, albumCredits: String?): WorkBlock {
    val numbering = workNumbering(songs)
    val movements = songs.mapIndexed { index, song ->
        val raw = song.movementName?.trim()?.takeIf { it.isNotEmpty() } ?: song.name.orEmpty()
        val name = cleanMovementName(raw, work)
        val number = song.movementNumber?.takeIf { it >= 1 } ?: (index + 1)
        val numeral = if ((songs.size > 1 || song.movementNumber != null) && numbering.styleFor(name) != NumeralStyle.None) numbering.format(number) else null
        Movement(song, number, name, numeral)
    }
    val discBreaks = songs.indices.drop(1).mapNotNull { index ->
        val disc = songs[index].disc
        if (disc != null && songs[index - 1].disc != null && disc != songs[index - 1].disc) DiscBreak(index, disc) else null
    }
    return WorkBlock(
        work = work,
        title = parseWorkTitle(work),
        composer = songs.firstNotNullOfOrNull { it.cleanComposer() },
        inferred = inferred,
        movements = movements,
        numbering = numbering,
        discBreaks = discBreaks,
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
        listOf(AlbumItem.Work(block.copy(collapsedByDefault = block.movementCount > 20 && blockCount > 1)))
    }
}

/** The queue indices of the work containing [index] (just [index] for a lone song). */
fun workRunAt(queue: List<Song>, index: Int): IntRange = findRuns(queue).firstOrNull { index in it.range }?.range ?: index..index

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
    val continuous: Boolean
)

/** Null when the song at [index] is not part of a work of two or more movements. [positionMs] is the playback position in that song. */
fun workProgress(queue: List<Song>, index: Int, positionMs: Long = 0): WorkProgress? {
    val run = findRuns(queue).firstOrNull { index in it.range && it.work != null } ?: return null
    val block = buildBlock(queue.slice(run.range), run.work!!, run.inferred, null)
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
        movementCount = block.movementCount,
        remainingMs = durations.drop(current).sum() - played,
        continuous = segments.size > 12
    )
}

/** What the lock screen, notification and Android Auto show: the movement as title, "Beethoven: Symphony No. 5 · Kleiber" as artist. */
data class LockScreenText(val title: String, val artist: String?)

/** Null unless the song at [index] is classical or part of a work. */
fun lockScreenText(queue: List<Song>, index: Int, library: Collection<ComposerName> = emptyList()): LockScreenText? {
    val song = queue.getOrNull(index) ?: return null
    val run = findRuns(queue).first { index in it.range }
    if (run.work == null && !song.isClassical) return null
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
