package com.simplecityapps.shuttle.model

data class ComposerName(val name: String, val sort: String?)

fun Song.composerName(): ComposerName? = composer?.trim()?.takeIf { it.isNotEmpty() }?.let { ComposerName(it, composerSort?.trim()?.takeIf(String::isNotEmpty)) }

private val lifespan = Regex("""\s*\([^)]*\d{4}[^)]*\)""")

// Taggers write one composer as "Mahler", "Gustav Mahler" and "Mahler, Gustav (1860 - 1911)"; the surname is what they share.
internal fun composerSurnameKey(name: String): String {
    val bare = name.replace(lifespan, "").trim()
    return (if (bare.contains(',')) bare.substringBefore(',') else bare.substringAfterLast(' ')).trim().lowercase()
}

private fun ComposerName.surname(): String? = (sort ?: name.replace(lifespan, "").trim()).takeIf { it.contains(',') }?.substringBefore(',')?.trim()?.takeIf { it.isNotEmpty() }

/**
 * The composer's surname from the sort name, or from a "Surname, Forename" name with no sort tag ("Beethoven, Ludwig van"
 * gives "Beethoven"); the full name otherwise, or when another composer in [library] shares the surname (the Bach family, the Strausses).
 */
fun composerDisplayName(composer: ComposerName, library: Collection<ComposerName> = emptyList()): String {
    val surname = composer.surname() ?: return composer.name
    val shared = library.any { other ->
        val sameComposer = other.name.equals(composer.name, ignoreCase = true) || (other.sort != null && other.sort.equals(composer.sort, ignoreCase = true))
        !sameComposer && other.surname().equals(surname, ignoreCase = true)
    }
    return if (shared) composer.name else surname
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
