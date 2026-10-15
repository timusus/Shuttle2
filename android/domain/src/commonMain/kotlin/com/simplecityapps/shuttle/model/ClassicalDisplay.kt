package com.simplecityapps.shuttle.model

/**
 * Whether the song's tags say it is a movement of a work. Genre and composer alone never count (a pop song can credit a
 * composer), and titles are never split on ":".
 */
val Song.isClassical: Boolean
    get() = !work.isNullOrBlank() || !movementName.isNullOrBlank() || showMovement == true

/** "IV. Allegro", or just the name without a number. Without a name, the title minus a leading "{work}: " / "{work} - ". */
fun Song.movementLabel(): String {
    val name = movementName?.trim().orEmpty()
    val number = movementNumber?.let(::toRoman)
    return when {
        name.isNotEmpty() -> if (number != null) "$number. $name" else name

        else -> {
            val title = stripWorkPrefix(this.name.orEmpty().trim(), work?.trim())
            if (number != null && title.isNotEmpty()) "$number. $title" else title
        }
    }
}

/** "{composer}: {work}" with " (arr. {arranger})" appended unless the work already says so; null without a work. */
fun Song.workLine(): String? {
    val work = work?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val composer = composerShortName()
    val arranger = arranger?.trim()?.takeIf { it.isNotEmpty() && !work.contains("arr.", ignoreCase = true) }
    val base = if (composer != null) "$composer: $work" else work
    return if (arranger != null) "$base (arr. $arranger)" else base
}

/** The composer's surname from the sort name ("Bach, Johann Sebastian"), else the composer as tagged. */
fun Song.composerShortName(): String? {
    val sort = composerSort?.substringBefore(',')?.trim()?.takeIf { it.isNotEmpty() }
    return sort ?: composer?.trim()?.takeIf { it.isNotEmpty() }
}

/** The conductor, then the performers as "Name (instrument)"; [fallback] (the artist text) when the tags name none. */
fun Song.performersLine(fallback: String? = null): String? {
    val parts = listOfNotNull(conductor?.trim()?.takeIf { it.isNotEmpty() }) +
        performers.orEmpty().mapNotNull { performer ->
            val name = performer.name.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val role = performer.role?.trim()?.takeIf { it.isNotEmpty() }
            if (role != null) "$name ($role)" else name
        }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: fallback?.takeIf { it.isNotBlank() }
}

/** What the player surfaces show for a classical song in place of its title and artist. */
data class ClassicalText(val movement: String, val work: String?, val performers: String?)

/** Null unless the song is classical; [artist] is the performers line's fallback. */
fun Song.classicalText(artist: String?): ClassicalText? = if (isClassical) ClassicalText(movementLabel(), workLine(), performersLine(artist)) else null

/** A run of consecutive tracks: [work] is set for a run of two or more sharing a work, null for a lone track. */
data class WorkGroup(val work: String?, val songs: List<Song>) {
    /** The first track that credits one, for the header's composer. */
    val composer: String? get() = songs.firstNotNullOfOrNull { it.composerShortName() }
    val arranger: String? get() = songs.firstNotNullOfOrNull { it.arranger?.trim()?.takeIf { arranger -> arranger.isNotEmpty() } }

    /** "Bach · arr. Busoni" beneath the header's work; the arranger is dropped when the work already says "arr."; null with neither. */
    val credits: String?
        get() {
            val arranger = arranger?.takeIf { work?.contains("arr.", ignoreCase = true) != true }
            return listOfNotNull(composer, arranger?.let { "arr. $it" }).takeIf { it.isNotEmpty() }?.joinToString(" · ")
        }
}

/** Splits [this] (in album order) into runs of consecutive tracks sharing a work; interleaved works don't merge. */
fun List<Song>.groupByWorkRuns(): List<WorkGroup> {
    val groups = mutableListOf<WorkGroup>()
    var run = mutableListOf<Song>()
    var runWork: String? = null

    fun flush() {
        if (run.isEmpty()) return
        if (run.size >= 2 && runWork != null) {
            groups += WorkGroup(runWork, run)
        } else {
            run.forEach { groups += WorkGroup(null, listOf(it)) }
        }
        run = mutableListOf()
    }

    for (song in this) {
        val work = song.work?.trim()?.takeIf { it.isNotEmpty() }
        if (work == null || work != runWork) flush()
        runWork = work
        run += song
    }
    flush()
    return groups
}

private fun stripWorkPrefix(title: String, work: String?): String {
    if (work.isNullOrEmpty() || !title.startsWith(work)) return title
    val rest = title.substring(work.length)
    val stripped = listOf(": ", " - ").firstOrNull { rest.startsWith(it) }?.let { rest.removePrefix(it) }
    return stripped?.trim()?.takeIf { it.isNotEmpty() } ?: title
}

private val romanNumerals = listOf(
    1000 to "M", 900 to "CM", 500 to "D", 400 to "CD", 100 to "C", 90 to "XC",
    50 to "L", 40 to "XL", 10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I"
)

/** Roman numerals for 1..3999; anything else stays as digits. */
internal fun toRoman(number: Int): String {
    if (number !in 1..3999) return number.toString()
    var remaining = number
    return buildString {
        for ((value, symbol) in romanNumerals) {
            while (remaining >= value) {
                append(symbol)
                remaining -= value
            }
        }
    }
}
