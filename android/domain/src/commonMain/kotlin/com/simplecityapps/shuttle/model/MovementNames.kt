package com.simplecityapps.shuttle.model

enum class NumeralStyle { None, Roman, Arabic }

/** How a work's movements are numbered ([style] is never [NumeralStyle.None]) and what to call them: "movements", "parts" or "songs". */
data class WorkNumbering(val style: NumeralStyle, val unit: String) {
    /** [NumeralStyle.None] for a movement whose name carries its own label ("Var. 13", "Act 2"). */
    fun styleFor(cleanName: String): NumeralStyle = if (hasOwnLabel(cleanName)) NumeralStyle.None else style

    fun format(number: Int): String = if (style == NumeralStyle.Roman) toRoman(number) else number.toString()
}

private const val NUMERAL = """(?:X{0,3}(?:IX|IV|V?I{0,3})|\d{1,3})"""
private val leadingNumeral = Regex("""^($NUMERAL)(?:\.|:|\))\s+(?=\S)""")

// "II - Adagio" is ambiguous ("X - Files Theme"), so a block accepts it only as a run from I.
private val looseNumeral = Regex("""^($NUMERAL)\s[-–—]\s+(?=\S)""")

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

private val structuralPrefix = Regex("""^(?:Act|Akt|Part|Teil|Disc|CD|Book|Scene)\b""", RegexOption.IGNORE_CASE)

// "Act 2: 5. Chorus" labels a section; only a prefix that names the work ("Winterreise, D. 911: 1. ...") is dropped.
private fun namesWork(candidate: String, work: String?): Boolean {
    val text = candidate.trim()
    if (structuralPrefix.containsMatchIn(text)) return false
    if (work != null && (text.equals(work, ignoreCase = true) || text.equals(parseWorkTitle(work).core, ignoreCase = true))) return true
    return catalogueSearchKeys(text).isNotEmpty() || formWords.containsMatchIn(text)
}

/** The name split into its clean form and the leading numeral token ("" when it has none). */
private fun splitMovementName(name: String, work: String?, looseNumerals: Boolean = true): Pair<String, String> {
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
    if (!stripped) {
        prefixBeforeNumeral.find(rest)?.takeIf { namesWork(rest.substringBefore(':'), prefix) }?.let { rest = rest.substring(it.range.last + 1).trim() }
    }
    val numeral = leadingNumeral.find(rest) ?: if (looseNumerals) looseNumeral.find(rest) else null
    var token = ""
    if (numeral != null && numeral.value.isNotBlank()) {
        token = numeral.groupValues[1]
        rest = rest.substring(numeral.range.last + 1).trim()
    }
    return rest.ifEmpty { trimmed } to token
}

/** The movement name without a leading "Work: " and a leading numeral ("II. ", "2. ", "II - ", "IV: "); the input when nothing would be left. */
fun cleanMovementName(name: String, work: String? = null, looseNumerals: Boolean = true): String = splitMovementName(name, work, looseNumerals).first

/** The movement number a name leads with ("II. Andante" gives 2, after any "Work: " prefix); null when it has none. */
fun movementNumberIn(name: String, work: String? = null, looseNumerals: Boolean = true): Int? = numeralValue(splitMovementName(name, work, looseNumerals).second)

/** Whether the "II - Adagio" style numerals among a block's [names] count from I upwards, so none is a title like "X - Files Theme". */
internal fun looseNumeralsFormRun(names: List<String>, work: String?): Boolean {
    val values = names.mapNotNull { name ->
        val token = splitMovementName(name, work, looseNumerals = true).second
        if (token.isNotEmpty() && splitMovementName(name, work, looseNumerals = false).second.isEmpty()) numeralValue(token) else null
    }
    return values.withIndex().all { (index, value) -> value == index + 1 }
}

/** Whether [cleanName] starts with its own label (Var., No., Nr., Aria, Thema, Coda, Act/Akt, Scene), which replaces the numeral. */
fun hasOwnLabel(cleanName: String): Boolean = ownLabel.containsMatchIn(cleanName.trim())

private val voiceWords = setOf("voice", "vocal", "vocals", "singer", "tenor", "soprano", "baritone", "mezzo", "alto", "bass", "countertenor")
private val instrumentWords = setOf("double", "guitar", "viol", "viola", "saxophone", "sax", "flute", "recorder", "clarinet", "trombone", "tuba", "horn", "oboe", "bassoon")
private val classicalGenres = setOf("classical", "opera", "baroque", "chamber", "choral", "lieder", "art song", "early music", "orchestral")
private val songGenre = Regex("""\bsongs?\b|lieder|\blied\b""", RegexOption.IGNORE_CASE)

private fun String.words() = lowercase().split(Regex("""[^a-z]+""")).filter { it.isNotEmpty() }

private fun Performer.isVoice(): Boolean {
    val words = role?.words().orEmpty()
    return words.any { it in voiceWords } && words.none { it in instrumentWords }
}

private fun List<Song>.isSongGenre() = any { song -> song.genres.any { songGenre.containsMatchIn(it) } }

/** Whether [genres] name a classical genre; "Orchestral Pop" and "Romantic" do not, "Chamber Music" and "Baroque; Vocal" do. */
internal fun hasClassicalGenre(genres: List<String>): Boolean = genres.any { genre ->
    genre.split(';', '/', ',').any { token ->
        token.trim().lowercase().split(Regex("""\s+""")).filter { it != "music" }.joinToString(" ") in classicalGenres
    }
}

private val popularGenres = setOf("rock", "pop", "jazz", "metal", "folk", "electronic", "hip hop", "rap", "r&b", "soul", "country", "blues", "punk", "indie", "soundtrack", "score")

private fun Song.hasPopularGenre() = genres.any { genre -> genre.split(';', '/', ',').any { it.trim().lowercase() in popularGenres } }

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
