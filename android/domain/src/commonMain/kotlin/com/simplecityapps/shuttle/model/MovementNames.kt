package com.simplecityapps.shuttle.model

enum class NumeralStyle { None, Roman, Arabic }

/** How a work's movements are numbered ([style] is never [NumeralStyle.None]) and what to call them: "movements", "parts" or "songs". */
data class WorkNumbering(val style: NumeralStyle, val unit: String) {
    /** [NumeralStyle.None] for a movement whose name carries its own label ("Var. 13", "Act 2"). */
    fun styleFor(cleanName: String): NumeralStyle = if (hasOwnLabel(cleanName)) NumeralStyle.None else style

    fun format(number: Int): String = if (style == NumeralStyle.Roman) toRoman(number) else number.toString()
}

private const val NUMERAL = """(?:X{0,3}(?:IX|IV|V?I{0,3})|\d{1,3})"""
private val leadingNumeral = Regex("""^($NUMERAL)(?:\.|:|\)|\s[-–—])\s+(?=\S)""")

// A "Work: " placed before a numbered movement, for when the work is not known ("Winterreise, D. 911: 1. Gute Nacht").
private val prefixBeforeNumeral = Regex("""^[^:]+?\s*:\s+(?=$NUMERAL\.\s)""")
private val ownLabel = Regex(
    """^(?:Var(?:iations?)?\b|(?:No|Nr)\.\s*\d|Aria\b|Coda\b|Scene\b|(?:Act|Akt)\s+(?:[IVX]+|\d+)\b|(?:Thema|Theme)(?:\s*$|\s*[:,]))""",
    RegexOption.IGNORE_CASE
)
private val workPrefixSeparators = listOf(": ", " : ", " - ")

private fun numeralValue(token: String): Int? = token.toIntOrNull() ?: token.takeIf { it.isNotEmpty() }?.let { roman ->
    val values = mapOf('I' to 1, 'V' to 5, 'X' to 10)
    roman.mapIndexed { i, c ->
        val v = values.getValue(c)
        if (i + 1 < roman.length && v < values.getValue(roman[i + 1])) -v else v
    }.sum()
}

/** The name split into its clean form and the leading numeral token ("" when it has none). */
private fun splitMovementName(name: String, work: String?): Pair<String, String> {
    val trimmed = name.trim()
    var rest = trimmed
    val prefix = work?.trim()?.takeIf { it.isNotEmpty() }
    var stripped = false
    if (prefix != null && rest.startsWith(prefix, ignoreCase = true)) {
        val after = rest.substring(prefix.length)
        workPrefixSeparators.firstOrNull { after.startsWith(it) }?.let {
            rest = after.removePrefix(it).trim()
            stripped = true
        }
    }
    if (!stripped) prefixBeforeNumeral.find(rest)?.let { rest = rest.substring(it.range.last + 1).trim() }
    val numeral = leadingNumeral.find(rest)
    var token = ""
    if (numeral != null && numeral.value.isNotBlank()) {
        token = numeral.groupValues[1]
        rest = rest.substring(numeral.range.last + 1).trim()
    }
    return rest.ifEmpty { trimmed } to token
}

/** The movement name without a leading "Work: " and a leading numeral ("II. ", "2. ", "II - ", "IV: "); the input when nothing would be left. */
fun cleanMovementName(name: String, work: String? = null): String = splitMovementName(name, work).first

/** The movement number a name leads with ("II. Andante" gives 2, after any "Work: " prefix); null when it has none. */
fun movementNumberIn(name: String, work: String? = null): Int? = numeralValue(splitMovementName(name, work).second)

/** Whether [cleanName] starts with its own label (Var., No., Nr., Aria, Thema, Coda, Act/Akt, Scene), which replaces the numeral. */
fun hasOwnLabel(cleanName: String): Boolean = ownLabel.containsMatchIn(cleanName.trim())

private val voiceWords = setOf("voice", "vocal", "vocals", "singer", "tenor", "soprano", "baritone", "mezzo", "alto", "bass", "countertenor")
private val instrumentWords = setOf("double", "guitar", "viol", "viola", "saxophone", "sax", "flute", "recorder", "clarinet", "trombone", "tuba", "horn", "oboe", "bassoon")
internal val classicalGenreWords = listOf("classical", "opera", "baroque", "romantic", "orchestral", "chamber", "symphon", "choral", "concerto", "early music")
private val songGenre = Regex("""\bsongs?\b|lieder|\blied\b""", RegexOption.IGNORE_CASE)

private fun String.words() = lowercase().split(Regex("""[^a-z]+""")).filter { it.isNotEmpty() }

private fun Performer.isVoice(): Boolean {
    val words = role?.words().orEmpty()
    return words.any { it in voiceWords } && words.none { it in instrumentWords }
}

private fun List<Song>.isSongGenre() = any { song -> song.genres.any { songGenre.containsMatchIn(it) } }

private fun List<Song>.isNonClassical(): Boolean {
    val first = firstOrNull() ?: return false
    val composer = first.composer?.trim()?.takeIf { it.isNotEmpty() }
    val albumArtist = first.albumArtist?.trim()
    if (composer != null && albumArtist != null && composer.equals(albumArtist, ignoreCase = true)) return true
    val credited = any { !it.conductor.isNullOrBlank() || it.performers.orEmpty().isNotEmpty() }
    val classicalGenre = any { song -> song.genres.any { genre -> classicalGenreWords.any { genre.contains(it, ignoreCase = true) } } }
    return !credited && !classicalGenre
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
        songs.isSongGenre() -> WorkNumbering(NumeralStyle.Arabic, "songs")
        songs.any { song -> song.performers.orEmpty().any { it.isVoice() } } -> WorkNumbering(NumeralStyle.Arabic, "parts")
        length > 12 -> WorkNumbering(NumeralStyle.Arabic, "movements")
        else -> WorkNumbering(NumeralStyle.Roman, "movements")
    }
}
