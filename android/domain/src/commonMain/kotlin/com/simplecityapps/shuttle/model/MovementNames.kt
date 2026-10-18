package com.simplecityapps.shuttle.model

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

/** Whether two or more songs are titled as movements numbered I, II, III... of one work ("Work: I. Name"). */
internal fun List<Song>.hasNumberedMovementTitles(): Boolean {
    if (size < 2) return false
    val work = firstNotNullOfOrNull { it.work?.trim()?.takeIf { work -> work.isNotEmpty() } }
    return withIndex().all { (index, song) ->
        movementNumberIn(song.movementName?.takeIf { it.isNotBlank() } ?: song.name.orEmpty(), work, looseNumerals = false) == index + 1
    }
}

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
