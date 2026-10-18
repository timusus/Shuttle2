package com.simplecityapps.shuttle.model

internal class Run(val range: IntRange, val work: String?, val inferred: Boolean)

private val opNumber = Regex("""\bop\.?\s*(?=\d)""")
private val whitespaceRun = Regex("""\s+""")

internal fun String.normalised(): String = lowercase()
    .replace(opNumber, "op")
    .map { if (it.isLetterOrDigit()) it else ' ' }
    .joinToString("")
    .replace(whitespaceRun, " ")
    .trim()

// Picard writes the movement-level work: "Symphony no. 5 in C minor, op. 67: I. Allegro con brio".
private val workTail = Regex("""^(.*?\S)\s*:\s+((?:[IVX]{1,6}|\d{1,3})\.\s+\S.*)$""")

/** The parent work, with the movement tail ("I. Allegro con brio") split off when the tag carries one. */
internal fun Song.workParts(): Pair<String, String?>? {
    val tag = work?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val match = workTail.find(tag) ?: return tag to null
    return match.groupValues[1] to match.groupValues[2]
}

internal fun Song.cleanWork(): String? = workParts()?.first

internal fun Song.cleanComposer(): String? = composer?.trim()?.takeIf { it.isNotEmpty() }

/** The work this song belongs to, from its work tag or else the "Work: movement" prefix of its title. */
internal fun Song.workName(): String? = cleanWork() ?: titlePrefix()

private val titleSeparators = listOf(": ", " - ")

/** The "Work" of a "Work: movement" title, or null when the title has no such split. */
private fun Song.titlePrefix(): String? {
    val title = name?.trim() ?: return null
    val at = titleSeparators.map { title.indexOf(it) }.filter { it > 0 }.minOrNull() ?: return null
    if (title.substring(at).trim(' ', ':', '-').isEmpty()) return null
    return title.substring(0, at).trim().takeIf { it.isNotEmpty() }
}

// Consecutive songs share a run when kind and work match and the composer tags name one person.
private class RunKey(val work: String, val composer: String?) {
    fun matches(other: RunKey) = work == other.work && sameComposer(composer, other.composer)
}

private fun runKey(song: Song): RunKey? {
    val composer = song.cleanComposer()
    song.cleanWork()?.let { return RunKey("w:${it.normalised()}", composer) }
    val prefix = song.titlePrefix() ?: return null
    return if (composer == null && !isStrongWorkPrefix(listOf(song), prefix)) null else RunKey("i:${prefix.normalised()}", composer)
}

/** The work key [runAround] bounds a run by: neighbours with an equal key may share [song]'s work; null when [song] can't be in one. */
fun workRunKey(song: Song): String? = runKey(song)?.work

/**
 * Without a composer tag a prefix must name a work by itself and show a classical signal: a form word with a catalogue
 * number ("Cello Suite No. 1, BWV 1007"), or a classical genre with a catalogue number or "No. 5" and a form word. It is no artist or album.
 */
private fun isStrongWorkPrefix(songs: List<Song>, prefix: String): Boolean {
    val name = prefix.normalised()
    val credited = songs.flatMap { it.artists + listOfNotNull(it.albumArtist, it.album) }
    if (credited.any { it.normalised() == name }) return false
    val catalogue = catalogueSearchKeys(prefix).isNotEmpty()
    val form = formWords.containsMatchIn(prefix)
    if (catalogue && form) return true
    return songs.any { hasClassicalGenre(it.genres) } && (catalogue || (workNumberRef.containsMatchIn(prefix) && form))
}

/** Whether a shared title prefix on [songs] is a classical work rather than "Artist - Song" or "Hold On - Live". */
private fun looksLikeInferredWork(songs: List<Song>, prefix: String): Boolean {
    val first = songs.first()
    val composer = first.cleanComposer() ?: return isStrongWorkPrefix(songs, prefix)
    val albumArtist = first.albumArtist?.trim()
    val classicalGenre = songs.any { hasClassicalGenre(it.genres) }
    val conducted = songs.any { !it.conductor.isNullOrBlank() }
    if (albumArtist != null && composer.equals(albumArtist, ignoreCase = true) && !classicalGenre && !conducted) return false
    val name = prefix.normalised()
    val credited = songs.flatMap { it.artists + listOfNotNull(it.albumArtist, it.album) }
    if (credited.any { it.normalised() == name }) return false
    val catalogue = parseWorkTitle(prefix).catalogue != null
    val numbered = workNumberRef.containsMatchIn(prefix)
    // "Mass Effect" in "Mass Effect 3 OST" names the game, unless the prefix carries a catalogue or work number.
    if (!catalogue && !numbered && songs.any { it.album?.contains(prefix, ignoreCase = true) == true }) return false
    if (!catalogue && songs.any { song -> song.genres.any { genre -> genre.split(';', '/', ',').any { it.trim().lowercase() in screenGenres } } }) return false
    val credit = conducted || songs.any { !it.performers.isNullOrEmpty() }
    return catalogue || numbered || (formWords.containsMatchIn(prefix) && (credit || classicalGenre))
}

private val screenGenres = setOf("soundtrack", "score", "ost", "game", "film")

internal fun findRuns(songs: List<Song>): List<Run> {
    val runs = mutableListOf<Run>()
    var start = 0
    while (start < songs.size) {
        val key = runKey(songs[start])
        var end = start
        while (key != null && end + 1 < songs.size && runKey(songs[end + 1])?.let(key::matches) == true) end++
        val first = songs[start]
        val tagged = first.cleanWork()
        val prefix = if (tagged == null) first.titlePrefix() else null
        val block = songs.slice(start..end)
        val grouped = end > start && block.isClassicalBlock() && (tagged != null || (prefix != null && looksLikeInferredWork(block, prefix)))
        if (grouped) {
            runs += Run(start..end, tagged ?: prefix, inferred = tagged == null)
        } else {
            for (index in start..end) runs += Run(index..index, null, false)
        }
        start = end + 1
    }
    return runs
}

/**
 * The run containing [index], found from its neighbours alone: a run never spans two works, so the songs sharing the
 * current song's work key bound a window that starts and ends on a real run boundary, and [findRuns] over that window
 * gives the same run as over the whole queue. A group of non-classical songs (Picard-tagged pop versions of one song)
 * is not a work for the player.
 */
internal fun runAround(queue: List<Song>, index: Int): Run {
    val alone = Run(index..index, null, false)
    val work = runKey(queue[index])?.work ?: return alone
    var from = index
    while (from > 0 && runKey(queue[from - 1])?.work == work) from--
    var to = index
    while (to + 1 < queue.size && runKey(queue[to + 1])?.work == work) to++
    val run = findRuns(queue.subList(from, to + 1)).first { index - from in it.range }
    val range = (run.range.first + from)..(run.range.last + from)
    if (run.work != null && queue.subList(range.first, range.last + 1).isNonClassical()) return alone
    return Run(range, run.work, run.inferred)
}

/** The queue indices of the work containing [index] (just [index] for a lone song). */
fun workRunAt(queue: List<Song>, index: Int): IntRange = findRuns(queue).firstOrNull { index in it.range }?.range ?: index..index

/** The queue indices of the work of two or more movements containing [index], as the player shows it; null for a lone song. */
fun workRunOf(queue: List<Song>, index: Int): IntRange? {
    if (index !in queue.indices) return null
    return runAround(queue, index).takeIf { it.work != null }?.range
}
