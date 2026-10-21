package com.simplecityapps.shuttle.model

/**
 * Whether the song carries movement tags. A work tag alone never counts (Picard writes one for pop songs), and titles are never split on ":".
 * SHOWMOVEMENT=0 vetoes the tags so the title shows; grouping reads the raw tags, so such a song still joins a work around it.
 */
val Song.isClassical: Boolean
    get() = showMovement != false && (hasMovementTags || showMovement == true)

private val Song.hasMovementTags: Boolean get() = !movementName.isNullOrBlank() || (movementNumber ?: 0) >= 1

/** [isClassical] unless the song's genre is a popular one, which a stray movement tag on a pop track doesn't override. */
val Song.showsMovementLabel: Boolean
    get() = isClassical && !hasNonClassicalGenre(genres)

private fun Song.hasClassicalSignal(): Boolean = hasMovementTags || showMovement == true || !conductor.isNullOrBlank() || hasClassicalGenre(genres) ||
    listOfNotNull(work, name, album).any { catalogueSearchKeys(it).isNotEmpty() }

/**
 * The one test every classical surface applies before treating [this] (one work's songs, or an album) as classical:
 * some song has movement tags, a conductor, a classical genre or a catalogue key, and none has a popular genre; or the
 * titles number the movements from I ("Atom Heart Mother: I. ..."), which a plain pop album's titles never do. A
 * composer and work tag alone are not enough.
 */
fun List<Song>.isClassicalBlock(): Boolean = (any { it.hasClassicalSignal() } && none { hasNonClassicalGenre(it.genres) }) || hasNumberedMovementTitles()

/** Whether a lone song, tagged with a work, gets the movement-and-work wording of the player surfaces. */
internal fun Song.isClassicalWork(): Boolean = showsMovementLabel || (!work.isNullOrBlank() && listOf(this).isClassicalBlock())

/** "IV. Allegro", or just the name without a number. Without a name, the title minus a leading "{work}: " / "{work} - ". */
fun Song.movementLabel(): String {
    val name = movementName?.trim().orEmpty()
    val number = movementNumber?.takeIf { it >= 1 }?.let(::toRoman)
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
data class ClassicalText(val movement: String, val work: String?, val performers: String?, val subtitle: String) {
    /** Movement first so a one-line ellipsis cuts the work, never the movement. */
    val miniTitle: String get() = listOfNotNull(movement, work).joinToString(" – ")
}

/** Null unless the song is classical; [artist] is the performers line's fallback, and with no work the subtitle is "[artist] • [album]". */
fun Song.classicalText(artist: String?, album: String?): ClassicalText? {
    if (!isClassical) return null
    val work = workLine()
    return ClassicalText(
        movement = movementLabel(),
        work = work,
        performers = performersLine(artist),
        subtitle = work ?: listOfNotNull(artist, album).joinToString(" • "),
    )
}

private fun stripWorkPrefix(title: String, work: String?): String {
    if (work.isNullOrEmpty() || !title.startsWith(work)) return title
    val rest = title.substring(work.length)
    val stripped = listOf(": ", " - ").firstOrNull { rest.startsWith(it) }?.let { rest.removePrefix(it) }
    return stripped?.trim()?.takeIf { it.isNotEmpty() } ?: title
}
