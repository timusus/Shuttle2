package com.simplecityapps.shuttle.model

enum class NumeralStyle { None, Roman, Arabic }

/** How a work's movements are numbered ([style] is never [NumeralStyle.None]) and what to call them: "movements", "parts" or "songs". */
data class WorkNumbering(val style: NumeralStyle, val unit: String) {
    /** [NumeralStyle.None] for a movement whose name carries its own label ("Var. 13", "Act 2"). */
    fun styleFor(cleanName: String): NumeralStyle = if (hasOwnLabel(cleanName)) NumeralStyle.None else style

    fun format(number: Int): String = if (style == NumeralStyle.Roman) toRoman(number) else number.toString()
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

private val voiceWords = setOf("voice", "vocal", "vocals", "singer", "tenor", "soprano", "baritone", "mezzo", "alto", "bass", "countertenor")
private val instrumentWords = setOf("double", "guitar", "viol", "viola", "saxophone", "sax", "flute", "recorder", "clarinet", "trombone", "tuba", "horn", "oboe", "bassoon")

private fun String.words() = lowercase().split(Regex("""[^a-z]+""")).filter { it.isNotEmpty() }

private fun Performer.isVoice(): Boolean {
    val words = role?.words().orEmpty()
    return words.any { it in voiceWords } && words.none { it in instrumentWords }
}

private fun Song.composerIsAlbumArtist(): Boolean {
    val composer = composer?.trim()?.takeIf { it.isNotEmpty() } ?: return true
    val albumArtist = albumArtist?.trim()?.takeIf { it.isNotEmpty() } ?: return true
    return sameComposer(composer, albumArtist) || albumArtist.startsWith("$composer;") || albumArtist.startsWith("$composer,")
}

// A conductor tag or a classical genre keeps an album classical, and so does a composer other than the album artist
// (Beethoven played by Hilary Hahn) unless a popular genre says otherwise. Performer credits alone do not, so a band
// whose leader wrote the suite stays a plain album.
fun List<Song>.isNonClassical(): Boolean = none { !it.conductor.isNullOrBlank() || hasClassicalGenre(it.genres) } &&
    all { it.composerIsAlbumArtist() || it.hasPopularGenre() }

private val accompanimentWords = setOf("piano", "fortepiano", "guitar", "lute", "harp")

private fun Performer.isAccompanist(): Boolean = role?.words().orEmpty().let { words -> words.any { it in accompanimentWords } && words.none { it in setOf("organ", "continuo") } }

// A singer with only an accompanist and no conductor, ensemble, organ or continuo is a song cycle (Winterreise: baritone and piano).
// A performer with no role is unknown and ignored.
private fun List<Song>.isSongCycle(): Boolean {
    val performers = flatMap { it.performers.orEmpty() }.filter { !it.role.isNullOrBlank() }
    val others = performers.filter { !it.isVoice() }
    return none { !it.conductor.isNullOrBlank() } && performers.any { it.isVoice() } && others.isNotEmpty() && others.all { !it.isEnsemble() && it.isAccompanist() }
}

/**
 * The numbering of one work's [songs]: Roman for an instrumental classical work of up to 12 movements, Arabic for song
 * cycles, vocal works, longer works and non-classical suites. The length is the tagged movement total, so a slice of a
 * queue is numbered like the whole work.
 */
fun workNumbering(songs: List<Song>): WorkNumbering {
    val length = songs.firstNotNullOfOrNull { it.movementTotal?.takeIf { total -> total >= 1 } } ?: songs.size
    return when {
        songs.isNonClassical() -> WorkNumbering(NumeralStyle.Arabic, if (songs.isSongGenre()) "songs" else "parts")
        songs.isSongGenre() || songs.isSongCycle() -> WorkNumbering(NumeralStyle.Arabic, "songs")
        songs.any { song -> song.performers.orEmpty().any { it.isVoice() } } -> WorkNumbering(NumeralStyle.Arabic, "parts")
        length > 12 -> WorkNumbering(NumeralStyle.Arabic, "movements")
        else -> WorkNumbering(NumeralStyle.Roman, "movements")
    }
}
