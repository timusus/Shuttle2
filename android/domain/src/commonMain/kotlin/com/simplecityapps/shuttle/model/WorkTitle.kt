package com.simplecityapps.shuttle.model

/** A work title split for display: [core] on the first line, the rest ("C minor", "Op. 67", "Busoni") on the second. */
data class WorkTitle(
    val core: String,
    val nickname: String? = null,
    val key: String? = null,
    val catalogue: String? = null,
    val arrangement: String? = null
)

// The single letters K, B and D are only a catalogue with their dot ("K. 466", "B. 12", "D. 850"), S with a dot or a space.
private val catalogueNames = listOf("BuxWV", "BWV", "Hob", "HWV", "TWV", "TrV", "SWV", "WAB", "WoO", "Sz", "RV", "FP", "JW", "Wq", "Kk", "KV", "Op")
private val CATALOGUE_PREFIXES = catalogueNames.joinToString("|") + "|K(?=\\.)|D(?=\\.)|B(?=\\.)|S(?=[.\\s])"

// "no." and "nos." are written "No." and "Nos." in a work title, whatever the tagger typed.
private val numberAbbreviation = Regex("""\b(no|nos)\.(?=\s*\d)""", RegexOption.IGNORE_CASE)

private fun String.capitaliseNumberAbbreviations() = replace(numberAbbreviation) { it.groupValues[1].lowercase().replaceFirstChar(Char::uppercase) + "." }

// Groups: 1 prefix, 2 Roman:number (Hob. XVI:52), 3 number with optional letter (BWV 1041a), 4 "No. 3" of "Op. 10 No. 3".
private val catalogueRef = Regex(
    """($CATALOGUE_PREFIXES)\.?\s*(?:([IVX]+:\s*\d+[a-z]?)|(\d+[a-z]?))(?![A-Za-z0-9])(?:\s*(?:No|Nr)\.?\s*(\d+))?""",
    RegexOption.IGNORE_CASE
)

internal val workNumberRef = Regex("""\b(?:No|Nr)\.\s*\d""", RegexOption.IGNORE_CASE)

private val structuralWords = setOf("part", "prelude", "book", "act", "movement", "no")

// English keys only, and only at the end of the title or before punctuation, so "in A Little Night Music" is left alone.
private val keyRef = Regex(
    """\sin\s+([A-G](?:[-\s]?(?:[Ff]lat|[Ss]harp)|[♭♯#]|b(?![A-Za-z]))?(?:\s+(?:[Mm]ajor|[Mm]inor))?)(?=\s*(?:$|[,;(\[]))"""
)

private val arrangementRef = Regex(
    """(?:[\s,]*[(\[]\s*arr(?:\.|anged)?\s+(?:by\s+)?([^)\]]+?)\s*[)\]]|[\s,]+arr\.?\s+(?:by\s+)?(\S.*?))\s*$""",
    RegexOption.IGNORE_CASE
)

private val quotedRef = Regex(""""([^"]+)"|“([^”]+)”|«\s*([^»]+?)\s*»""")

private val commaNicknameRef = Regex("""^(.*\d[^,]*),\s*([A-ZÀ-Ý][A-Za-zÀ-ÿ'’-]*)$""")

private val whitespace = Regex("""\s+""")
private val emptyBrackets = Regex("""\(\s*\)|\[\s*]""")
private val spaceBeforeComma = Regex("""\s+,""")
private val repeatedCommas = Regex(""",(\s*,)+""")

// A query may drop the dot a stored "K. 466" needs: exactly letter, optional space, number.
private val bareLetterQuery = Regex("""(KV|K|D|B)\s*(\d+[a-z]?)""", RegexOption.IGNORE_CASE)

private fun String.tidy(): String = replace(emptyBrackets, " ")
    .replace(whitespace, " ")
    .replace(spaceBeforeComma, ",")
    .replace(repeatedCommas, ",")
    .trim(' ', ',', ';', ':', '-', '–', '—')

private fun catalogueMatches(text: String): List<MatchResult> = catalogueRef.findAll(text).filter { match ->
    val start = match.range.first
    val prefix = match.groupValues[1]
    val before = text.substring(0, start).trimEnd()
    (start == 0 || !text[start - 1].isLetterOrDigit()) &&
        (prefix.length > 1 || prefix[0].isUpperCase()) &&
        (!prefix.equals("D", ignoreCase = true) || before.isEmpty() || before.endsWith(','))
}.toList()

private fun MatchResult.display(): String {
    val prefix = groupValues[1]
    val dotted = value.getOrNull(prefix.length) == '.'
    val canonical = catalogueNames.firstOrNull { it.equals(prefix, ignoreCase = true) }?.takeIf { it.length > 1 } ?: prefix
    val shown = if (canonical == "Op") "Op." else canonical + if (dotted) "." else ""
    val number = groupValues[2].ifEmpty { groupValues[3] }.replace(whitespace, "")
    val sub = groupValues[4].takeIf { it.isNotEmpty() }?.let { " No. $it" }.orEmpty()
    return "$shown $number$sub"
}

/** Splits [title] into its parts; on any doubt (unrelated catalogue numbers, two keys, two nicknames) returns it unchanged as the core. Of comma-separated catalogue numbers the last is shown. */
fun parseWorkTitle(title: String): WorkTitle {
    val original = title.trim()
    val unchanged = WorkTitle(core = original)
    var rest = original

    val arrangementMatch = arrangementRef.find(rest)
    val arrangement = arrangementMatch?.let { it.groupValues[1].ifEmpty { it.groupValues[2] }.trim() }
    if (arrangementMatch != null) rest = rest.removeRange(arrangementMatch.range).tidy()

    val quotes = quotedRef.findAll(rest).toList()
    if (quotes.size > 1) return unchanged
    var nickname = quotes.singleOrNull()?.let { match -> match.groupValues.drop(1).first { it.isNotEmpty() }.trim() }
    if (quotes.isNotEmpty()) rest = rest.removeRange(quotes.single().range).tidy()

    val catalogues = catalogueMatches(rest)
    // "Op. 89, D. 911" is one work with two catalogues; any other separator ("BWV 1041 / RV 522") is doubt.
    val commaSeparated = catalogues.zipWithNext().all { (a, b) -> rest.substring(a.range.last + 1, b.range.first).trim() == "," }
    if (catalogues.size > 1 && !commaSeparated) return unchanged
    val catalogue = catalogues.lastOrNull()?.display()
    for (match in catalogues.asReversed()) rest = rest.removeRange(match.range)
    if (catalogues.isNotEmpty()) rest = rest.tidy()

    var key: String? = null
    val keys = keyRef.findAll(rest).toList()
    if (keys.size > 1) return unchanged
    keys.singleOrNull()?.let { match ->
        val without = rest.removeRange(match.range).tidy()
        // Only "No. 5" makes the work identifiable without its key; a catalogue number alone ("Mass in B minor, BWV 232") does not.
        if (workNumberRef.containsMatchIn(without)) {
            key = match.groupValues[1].trim()
            rest = without
        }
    }

    if (nickname == null) {
        commaNicknameRef.matchEntire(rest)?.let { match ->
            val tail = match.groupValues[2].trim()
            if (tail.lowercase() !in structuralWords) {
                nickname = tail
                rest = match.groupValues[1].trim()
            }
        }
    }

    if (rest.isEmpty()) return unchanged
    return WorkTitle(core = rest.capitaliseNumberAbbreviations(), nickname = nickname, key = key, catalogue = catalogue, arrangement = arrangement)
}

private fun MatchResult.searchKey(): String {
    val prefix = groupValues[1].lowercase().let { if (it == "kv") "k" else it }
    val number = groupValues[2].ifEmpty { groupValues[3] }.lowercase().replace(whitespace, "")
    return prefix + number
}

/**
 * One comparable form for a catalogue reference ("op67", "Op. 67" -> "op67"; "K 466", "KV466" -> "k466"); null when
 * [text] is not exactly one. Unlike [catalogueSearchKeys] it accepts "k466", "d 850" and "b178" without the dot.
 */
fun catalogueSearchKey(text: String): String? {
    val trimmed = text.trim()
    catalogueRef.matchEntire(trimmed)?.let { return it.searchKey() }
    val bare = bareLetterQuery.matchEntire(trimmed) ?: return null
    val letter = bare.groupValues[1].lowercase().let { if (it == "kv") "k" else it }
    return letter + bare.groupValues[2].lowercase()
}

/** The catalogue of a [catalogueSearchKey] without its number ("op67" -> "op", "hobxvi:52" -> "hob"). */
fun catalogueKeyPrefix(key: String): String {
    val letters = key.takeWhile { it.isLetter() }
    // The Roman numeral of "Hob. XVI:52" is part of the number.
    return if (':' in key) letters.trimEnd('i', 'v', 'x') else letters
}

/** The [catalogueSearchKey] of every catalogue reference inside [text], for the indexed side of a search. */
fun catalogueSearchKeys(text: String): List<String> = catalogueMatches(text).map { it.searchKey() }
