package com.simplecityapps.shuttle.model

enum class NumeralStyle { None, Roman, Arabic }

/** How a work's movements are numbered ([style] is never [NumeralStyle.None]) and what to call them: "movements", "parts" or "songs". */
data class WorkNumbering(val style: NumeralStyle, val unit: String) {
    /** [NumeralStyle.None] for a movement whose name carries its own label ("Var. 13", "Act 2"). */
    fun styleFor(cleanName: String): NumeralStyle = if (hasOwnLabel(cleanName)) NumeralStyle.None else style

    fun format(number: Int): String = if (style == NumeralStyle.Roman) toRoman(number) else number.toString()
}

private val leadingNumeral = Regex("""^(?:X{0,3}(?:IX|IV|V?I{0,3})|\d{1,3})(?:\.|:|\)|\s[-–—])\s+(?=\S)""")
private val ownLabel = Regex("""^(?:Var(?:iations?)?|No|Nr|Aria|Thema|Theme|Coda|Act|Akt|Scene)\b""", RegexOption.IGNORE_CASE)
private val workPrefixSeparators = listOf(": ", " - ")

/** The movement name without a leading "Work: " and a leading numeral ("II. ", "2. ", "II - ", "IV: "); the input when nothing would be left. */
fun cleanMovementName(name: String, work: String? = null): String {
    val trimmed = name.trim()
    var rest = trimmed
    val prefix = work?.trim()?.takeIf { it.isNotEmpty() }
    if (prefix != null && rest.startsWith(prefix, ignoreCase = true)) {
        val after = rest.substring(prefix.length)
        workPrefixSeparators.firstOrNull { after.startsWith(it) }?.let { rest = after.removePrefix(it).trim() }
    }
    val numeral = leadingNumeral.find(rest)
    if (numeral != null && numeral.value.isNotBlank()) rest = rest.substring(numeral.range.last + 1).trim()
    return rest.ifEmpty { trimmed }
}

/** Whether [cleanName] starts with its own label (Var., No., Nr., Aria, Thema, Coda, Act/Akt, Scene), which replaces the numeral. */
fun hasOwnLabel(cleanName: String): Boolean = ownLabel.containsMatchIn(cleanName.trim())

private val voiceWords = setOf("voice", "vocal", "vocals", "singer", "tenor", "soprano", "baritone", "mezzo", "alto", "bass", "countertenor")
private val instrumentWords = setOf("double", "guitar", "viol", "viola", "saxophone", "sax", "flute", "recorder", "clarinet", "trombone", "tuba", "horn", "oboe", "bassoon")
private val classicalGenreWords = listOf("classical", "opera", "baroque", "romantic", "orchestral", "chamber", "symphon", "choral", "concerto", "early music")
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

/** The numbering of one work's [songs]: Roman for an instrumental classical work of up to 12 movements, Arabic for song cycles, vocal works, longer works and non-classical suites. */
fun workNumbering(songs: List<Song>): WorkNumbering = when {
    songs.isNonClassical() -> WorkNumbering(NumeralStyle.Arabic, if (songs.isSongGenre()) "songs" else "parts")
    songs.isSongGenre() -> WorkNumbering(NumeralStyle.Arabic, "songs")
    songs.any { song -> song.performers.orEmpty().any { it.isVoice() } } -> WorkNumbering(NumeralStyle.Arabic, "parts")
    songs.size > 12 -> WorkNumbering(NumeralStyle.Arabic, "movements")
    else -> WorkNumbering(NumeralStyle.Roman, "movements")
}
