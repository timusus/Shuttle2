package com.simplecityapps.shuttle.model

data class ComposerName(val name: String, val sort: String?)

fun Song.composerName(): ComposerName? = composer?.trim()?.takeIf { it.isNotEmpty() }?.let { ComposerName(it, composerSort?.trim()?.takeIf(String::isNotEmpty)) }

private val lifespan = Regex("""\s*\([^)]*\d{4}[^)]*\)""")

private val generationSuffix = Regex("""(?:,?\s+(?:II|III|IV|Jr\.?|Sr\.?|the\s+(?:Younger|Elder)))+\s*$""", RegexOption.IGNORE_CASE)
private val surnameParticles = setOf("van", "von", "de", "da", "di", "del", "della", "du", "la", "le", "der", "den", "ten", "ter", "dos", "das")

// "McCartney" in the forenames slot is a second surname ("Lennon, McCartney"), not a forename.
private val surnamePrefixed = Regex("""\b(?:Mc|Mac)[A-Z]""")

/** A composer name taken apart; [forenames] is null for a surname alone. */
private class NameParts(val surname: String, val forenames: String?, val suffix: String, val plain: String) {
    // "Forename Surname", reordering a "Surname, Forename" name.
    fun display(): String = if (forenames != null && plain.contains(',')) listOf(forenames, surname, suffix).filter { it.isNotEmpty() }.joinToString(" ") else plain
}

// Only "Surname, Forenames" counts as a comma name: not "Lennon, McCartney" or "Lennon & McCartney, Paul".
private fun looksLikeSurnameComma(text: String): Boolean {
    if (text.count { it == ',' } != 1) return false
    val (surname, forenames) = text.split(',').map { it.trim() }
    if (surname.isEmpty() || forenames.isEmpty() || forenames.contains('&') || forenames.split(' ').any { it.equals("and", ignoreCase = true) }) return false
    if (surnamePrefixed.containsMatchIn(forenames)) return false
    val words = surname.split(' ')
    return words.size == 1 || words.first().lowercase() in surnameParticles
}

private fun nameParts(name: String): NameParts {
    val bare = name.replace(lifespan, "").trim()
    val suffixMatch = generationSuffix.find(bare)
    val core = if (suffixMatch != null) bare.substring(0, suffixMatch.range.first).trim() else bare
    val suffix = suffixMatch?.value?.trim(' ', ',').orEmpty()
    if (looksLikeSurnameComma(core)) {
        val (surname, forenames) = core.split(',').map { it.trim() }
        return NameParts(surname, forenames, suffix, bare)
    }
    val words = core.split(Regex("""\s+"""))
    return NameParts(words.last(), words.dropLast(1).joinToString(" ").ifEmpty { null }, suffix, bare)
}

private fun String.normalisedName(): String = lowercase().filter { it.isLetterOrDigit() || it == ' ' }.replace(Regex("""\s+"""), " ").trim()

// Taggers write one composer as "Mahler", "Gustav Mahler" and "Mahler, Gustav (1860 - 1911)"; the surname is what they share.
internal fun composerSurnameKey(name: String): String = nameParts(name).surname.normalisedName()

/** Whether two composer tags name one person: same surname, and the same full name unless one of them is the surname alone. */
internal fun sameComposer(a: String?, b: String?): Boolean {
    if (a.isNullOrBlank() || b.isNullOrBlank()) return a.isNullOrBlank() && b.isNullOrBlank()
    val first = nameParts(a)
    val second = nameParts(b)
    if (first.surname.normalisedName() != second.surname.normalisedName()) return false
    if (first.forenames == null || second.forenames == null) return true
    fun NameParts.full() = listOfNotNull(forenames, surname).joinToString(" ").normalisedName()
    return first.full() == second.full()
}

private fun ComposerName.surname(): String? {
    val comma = sort?.takeIf { it.contains(',') } ?: name.replace(lifespan, "").trim().takeIf(::looksLikeSurnameComma)
    return comma?.substringBefore(',')?.trim()?.takeIf { it.isNotEmpty() }
}

// The fullest "Forename Surname" the tags give, preferring the name over the sort tag.
private fun ComposerName.fullName(): String {
    val own = nameParts(name)
    if (own.forenames != null) return own.display()
    val fromSort = sort?.takeIf { it.contains(',') }?.let(::nameParts)?.takeIf { it.forenames != null }
    return (fromSort ?: own).display()
}

// A surname for the library check: from the sort tag when it has one, else read off the name like composerSurnameKey.
private fun ComposerName.surnameForCollision(): String = (surname() ?: nameParts(name).surname).normalisedName()

/**
 * The composer's surname from the sort name, or from a "Surname, Forename" name with no sort tag ("Beethoven, Ludwig van"
 * gives "Beethoven"); the full name otherwise, or "Forename Surname" when another composer in [library] shares the
 * surname (the Bach family, the Strausses).
 */
fun composerDisplayName(composer: ComposerName, library: Collection<ComposerName> = emptyList()): String {
    val surname = composer.surname() ?: return composer.name
    val full = composer.fullName()
    val shared = library.any { other ->
        val sameSort = other.sort != null && other.sort.equals(composer.sort, ignoreCase = true)
        val same = other.name.equals(composer.name, ignoreCase = true) || sameSort || sameComposer(full, other.fullName())
        !same && other.surnameForCollision() == surname.normalisedName()
    }
    return if (shared) full else surname
}

fun Song.composerDisplayName(library: Collection<ComposerName> = emptyList()): String? = composerName()?.let { composerDisplayName(it, library) }

private val ensembleWords = setOf("orchestra", "choir", "chorus", "ensemble", "quartet", "trio", "quintet", "sextet", "philharmonic", "consort", "band", "orchester", "symphony")

private fun Performer.cleanRole() = role?.trim()?.takeIf { it.isNotEmpty() }

private fun Performer.isConductor() = cleanRole()?.equals("conductor", ignoreCase = true) == true

internal fun Performer.isEnsemble(): Boolean {
    val role = cleanRole() ?: return true
    return role.lowercase().split(Regex("""[^a-z]+""")).any { it in ensembleWords }
}

private fun Song.soloists() = performers.orEmpty().filter { !it.isConductor() && !it.isEnsemble() && it.name.isNotBlank() }

private fun Song.ensembles() = performers.orEmpty().filter { !it.isConductor() && it.isEnsemble() && it.name.isNotBlank() }

private fun Song.conductorName() = conductor?.trim()?.takeIf { it.isNotEmpty() } ?: performers.orEmpty().firstOrNull { it.isConductor() }?.name?.trim()?.takeIf { it.isNotEmpty() }

/** The soloist, else the conductor, else the ensemble; null when the tags name none. */
fun Song.leadPerformer(): String? = soloists().firstOrNull()?.name?.trim() ?: conductorName() ?: ensembles().firstOrNull()?.name?.trim()

/** "Soloist, instrument · Ensemble · Conductor"; null when the tags name none. */
fun Song.creditsLine(): String? {
    val parts = soloists().map { "${it.name.trim()}, ${it.cleanRole()}" } + ensembles().map { it.name.trim() } + listOfNotNull(conductorName())
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/** The credits most of [songs] share, shown once in the album header. */
fun albumCreditsLine(songs: List<Song>): String? = songs.mapNotNull { it.creditsLine() }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key

/** The credits line for one work's [songs], or null when it matches [albumCredits] (it is already in the header). */
fun workCreditsLine(songs: List<Song>, albumCredits: String?): String? = albumCreditsLine(songs)?.takeIf { it != albumCredits }
