package com.simplecityapps.shuttle.model

/** A work title split for display: [core] on the first line, the rest ("C minor", "Op. 67", "Busoni") on the second. */
data class WorkTitle(
    val core: String,
    val nickname: String? = null,
    val key: String? = null,
    val catalogue: String? = null,
    val arrangement: String? = null
)

private const val CATALOGUE_PREFIXES = "BuxWV|BWV|Hob|HWV|TWV|TrV|SWV|WAB|WoO|Sz|RV|FP|JW|Wq|Kk|KV|Op|K|D|S|B"

// Groups: 1 prefix, 2 Roman:number (Hob. XVI:52), 3 number with optional letter (BWV 1041a), 4 "No. 3" of "Op. 10 No. 3".
private val catalogueRef = Regex(
    """($CATALOGUE_PREFIXES)\.?\s*(?:([IVX]+:\s*\d+[a-z]?)|(\d+[a-z]?))(?![A-Za-z0-9])(?:\s*(?:No|Nr)\.?\s*(\d+))?""",
    RegexOption.IGNORE_CASE
)

// English keys only, and only at the end of the title or before punctuation, so "in A Little Night Music" is left alone.
private val keyRef = Regex(
    """\sin\s+([A-G](?:[-\s]?(?:[Ff]lat|[Ss]harp)|[♭♯#]|b(?![A-Za-z]))?(?:\s+(?:[Mm]ajor|[Mm]inor))?)(?=\s*(?:$|[,;(\[]))"""
)

private val arrangementRef = Regex(
    """(?:[\s,]*[(\[]\s*arr(?:\.|anged)?\s+(?:by\s+)?([^)\]]+?)\s*[)\]]|[\s,]+arr\.?\s+(?:by\s+)?(\S.*?))\s*$""",
    RegexOption.IGNORE_CASE
)

private val quotedRef = Regex(""""([^"]+)"|“([^”]+)”|«\s*([^»]+?)\s*»""")

private val commaNicknameRef = Regex("""^(.*\d[^,]*),\s*([A-ZÀ-Ý][^,]*)$""")

private fun String.tidy(): String = replace(Regex("""\(\s*\)|\[\s*]"""), " ")
    .replace(Regex("""\s+"""), " ")
    .replace(Regex("""\s+,"""), ",")
    .replace(Regex(""",(\s*,)+"""), ",")
    .trim(' ', ',', ';', ':', '-', '–', '—')

private fun catalogueMatches(text: String): List<MatchResult> = catalogueRef.findAll(text).filter { match ->
    val start = match.range.first
    start == 0 || !text[start - 1].isLetterOrDigit()
}.toList()

private fun MatchResult.display(): String {
    val prefix = groupValues[1]
    val dotted = value.getOrNull(prefix.length) == '.'
    val shown = if (prefix.equals("op", ignoreCase = true)) "Op." else prefix + if (dotted) "." else ""
    val number = groupValues[2].ifEmpty { groupValues[3] }.replace(Regex("""\s+"""), "")
    val sub = groupValues[4].takeIf { it.isNotEmpty() }?.let { " No. $it" }.orEmpty()
    return "$shown $number$sub"
}

/** Splits [title] into its parts; on any doubt (two catalogue numbers, two keys, two nicknames) returns it unchanged as the core. */
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
    if (catalogues.size > 1) return unchanged
    val catalogue = catalogues.singleOrNull()?.display()
    if (catalogues.isNotEmpty()) rest = rest.removeRange(catalogues.single().range).tidy()

    var key: String? = null
    val keys = keyRef.findAll(rest).toList()
    if (keys.size > 1) return unchanged
    keys.singleOrNull()?.let { match ->
        val without = rest.removeRange(match.range).tidy()
        // A work without a number ("Mass in B minor") keeps its key in the title, unless a catalogue number carries the second line.
        if (without.any { it.isDigit() } || catalogue != null) {
            key = match.groupValues[1].trim()
            rest = without
        }
    }

    if (nickname == null) {
        commaNicknameRef.matchEntire(rest)?.let { match ->
            val tail = match.groupValues[2].trim()
            if (tail.split(' ').size <= 3) {
                nickname = tail
                rest = match.groupValues[1].trim()
            }
        }
    }

    if (rest.isEmpty()) return unchanged
    return WorkTitle(core = rest, nickname = nickname, key = key, catalogue = catalogue, arrangement = arrangement)
}

private fun MatchResult.searchKey(): String {
    val prefix = groupValues[1].lowercase().let { if (it == "kv") "k" else it }
    val number = groupValues[2].ifEmpty { groupValues[3] }.lowercase().replace(Regex("""\s+"""), "")
    return prefix + number
}

/** One comparable form for a catalogue reference ("op67", "Op. 67" -> "op67"; "K 466", "KV466" -> "k466"); null when [text] is not exactly one. */
fun catalogueSearchKey(text: String): String? = catalogueRef.matchEntire(text.trim())?.searchKey()

/** The [catalogueSearchKey] of every catalogue reference inside [text], for the indexed side of a search. */
fun catalogueSearchKeys(text: String): List<String> = catalogueMatches(text).map { it.searchKey() }
