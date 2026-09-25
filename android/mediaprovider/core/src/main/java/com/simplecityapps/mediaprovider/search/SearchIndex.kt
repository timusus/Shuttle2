package com.simplecityapps.mediaprovider.search

import kotlin.math.min

/** A field a document is searched by; a match in a lower-[weight] field ranks higher. */
enum class SearchField(internal val weight: Int) {
    Name(0),
    Artist(1),
    Album(2),
    Genre(3),
}

/**
 * Something to search for: an [item] and the text of its [fields]. A field may repeat (a song's several artists);
 * blank values are skipped. [popularity], a play count, breaks ties between otherwise equal matches.
 */
class SearchDocument<T>(
    val item: T,
    val fields: List<Pair<SearchField, String?>>,
    val popularity: Int = 0,
)

/** One result: the [item] and the [query] that found it, which knows which parts of any of its text to highlight. */
data class SearchHit<out T>(val item: T, val query: SearchQuery) {
    fun highlights(text: String?): List<IntRange> = query.highlights(text)

    /** [SearchQuery.highlights] across a row's [texts], so each query token is highlighted only where it matches best. */
    fun highlights(texts: List<String?>): List<List<IntRange>> = query.highlights(texts)

    fun <R> map(transform: (T) -> R): SearchHit<R> = SearchHit(transform(item), query)
}

/**
 * An immutable in-memory search index in the style of Typesense: typo-tolerant, prefix-as-you-type, AND across query
 * tokens over every field, with bucketed ranking. Build it off the main thread with [build]; [search] is safe to call
 * from any thread.
 *
 * Tokens live in one sorted array, so a prefix is a binary-searched range and a typo-tolerant lookup walks the array
 * as an implicit trie, reusing the edit-distance rows a term shares with the one before and skipping every term under
 * a prefix that is already too far from the query.
 */
class SearchIndex<T> private constructor(
    private val items: List<T>,
    private val terms: Array<String>,
    /** Term `t`'s postings are `postings[postingStart[t] until postingStart[t + 1]]`. */
    private val postingStart: IntArray,
    /** Each posting packs a document, the field entry within it, that field's weight and the token's position. */
    private val postings: IntArray,
    private val alphabeticalRank: IntArray,
    private val byAlphabeticalRank: IntArray,
    private val popularity: IntArray,
    private val maxTermLength: Int,
) {
    val size: Int
        get() = items.size

    /** Recent tokens' per-document best matches: typing extends the last token, so the earlier ones repeat. */
    private val tokenCache = object : LinkedHashMap<String, IntArray>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, IntArray>?): Boolean = size > TOKEN_CACHE_SIZE
    }

    fun search(query: String, accept: (T) -> Boolean = { true }): List<SearchHit<T>> = search(SearchQuery.parse(query), accept)

    /**
     * The documents that [accept] lets through and that match every token of [query], best first. When none match
     * every token, tokens are dropped from the end, then from the start, until something does. A leading "the" is
     * optional, so "the tin orchards" still finds "Tin Orchards".
     *
     * Ranking is bucketed: tokens matched, then fewer typos, then whole-token over prefix matches, then field weight,
     * then tokens found next to each other in order, then popularity, then alphabetical.
     */
    fun search(query: SearchQuery, accept: (T) -> Boolean = { true }): List<SearchHit<T>> {
        if (query.isEmpty || items.isEmpty()) return emptyList()
        val matches = query.tokens.map(::bestMatches)
        val optional = if (query.tokens.size > 1 && query.tokens.first() == "the") 0 else -1
        val accepted = BooleanArray(items.size)
        val checked = BooleanArray(items.size)
        for (tokens in fallbacks(query.tokens.size)) {
            val keys = rank(tokens, matches, optional) { doc ->
                if (!checked[doc]) {
                    checked[doc] = true
                    accepted[doc] = accept(items[doc])
                }
                accepted[doc]
            }
            if (keys.isNotEmpty()) {
                return object : AbstractList<SearchHit<T>>() {
                    override val size: Int = keys.size

                    override fun get(index: Int): SearchHit<T> = SearchHit(items[byAlphabeticalRank[(keys[index] and RANK_MASK).toInt()]], query)
                }
            }
        }
        return emptyList()
    }

    /** The token subsets to try: all of them, then dropping from the end, then from the start. */
    private fun fallbacks(count: Int): List<IntRange> = buildList {
        add(0 until count)
        for (end in count - 1 downTo 1) add(0 until end)
        for (start in 1 until count) add(start until count)
    }

    /** Ranking keys, sorted, for the documents matching every token in [tokens] (bar the [optional] one). */
    private fun rank(tokens: IntRange, matches: List<IntArray>, optional: Int, accept: (Int) -> Boolean): LongArray {
        val required = tokens.filter { it != optional }
        if (required.isEmpty()) return LongArray(0)
        var keys = LongArray(64)
        var count = 0
        for (doc in items.indices) {
            if (required.any { matches[it][doc] == NO_MATCH } || !accept(doc)) continue
            var matched = 0
            var typos = 0
            var partial = 0
            var weight = 0
            var proximity = 0
            var previous = NO_MATCH
            for (token in tokens) {
                val match = matches[token][doc]
                if (match == NO_MATCH) continue
                matched++
                typos += match ushr TYPOS_SHIFT
                partial += (match ushr PARTIAL_SHIFT) and 1
                weight += (match ushr WEIGHT_SHIFT) and 0b11
                if (previous != NO_MATCH) {
                    val adjacent = entry(match) == entry(previous) && position(match) == position(previous) + 1
                    if (!adjacent) proximity++
                }
                previous = match
            }
            if (count == keys.size) keys = keys.copyOf(count * 2)
            keys[count++] = ((MAX_TOKENS_MATCHED - matched).toLong() shl 59) or
                (min(typos, 31).toLong() shl 54) or
                (min(partial, 15).toLong() shl 50) or
                (min(weight, 63).toLong() shl 44) or
                (min(proximity, 15).toLong() shl 40) or
                ((MAX_POPULARITY - min(popularity[doc], MAX_POPULARITY)).toLong() shl 21) or
                alphabeticalRank[doc].toLong()
        }
        return keys.copyOf(count).apply { sort() }
    }

    /**
     * For each document, its best match for [token] (fewest typos, then whole over prefix, then lightest field),
     * packed so a smaller value is a better match; [NO_MATCH] where it doesn't match.
     */
    private fun bestMatches(token: String): IntArray {
        synchronized(tokenCache) { tokenCache[token] }?.let { return it }
        val best = IntArray(items.size) { NO_MATCH }
        forEachMatchingTerm(token, typoBudget(token.length)) { term, typos, whole ->
            val quality = (typos shl TYPOS_SHIFT) or ((if (whole) 0 else 1) shl PARTIAL_SHIFT)
            for (p in postingStart[term] until postingStart[term + 1]) {
                val posting = postings[p]
                val doc = posting ushr DOC_SHIFT
                val match = quality or (posting and POSTING_MATCH_MASK)
                if (match < best[doc]) best[doc] = match
            }
        }
        synchronized(tokenCache) { tokenCache[token] = best }
        return best
    }

    /** Calls [onMatch] for each term that [token] matches as a prefix within [maxTypos]. */
    private inline fun forEachMatchingTerm(token: String, maxTypos: Int, onMatch: (term: Int, typos: Int, whole: Boolean) -> Unit) {
        if (maxTypos == 0) {
            var i = lowerBound(token)
            while (i < terms.size && terms[i].startsWith(token)) {
                onMatch(i, 0, terms[i].length == token.length)
                i++
            }
            return
        }
        val q = token.length
        // rows[i][j]: the edit distance between the path's first i chars and the token's first j chars.
        val rows = Array(maxTermLength + 1) { IntArray(q + 1) }
        val rowMin = IntArray(maxTermLength + 1)
        // bestPrefix[i]: the fewest typos matching the whole token against any of the path's first i chars.
        val bestPrefix = IntArray(maxTermLength + 1)
        for (j in 0..q) rows[0][j] = j
        bestPrefix[0] = q
        var path = ""
        var computed = 0
        var i = 0
        while (i < terms.size) {
            val term = terms[i]
            var depth = commonPrefix(term, path, computed)
            path = term
            var pruned = false
            while (depth < term.length) {
                depth++
                val row = rows[depth]
                val prev = rows[depth - 1]
                val c = term[depth - 1]
                row[0] = depth
                var smallest = depth
                for (j in 1..q) {
                    val cost = if (token[j - 1] == c) 0 else 1
                    var v = min(min(prev[j] + 1, row[j - 1] + 1), prev[j - 1] + cost)
                    if (depth > 1 && j > 1 && c == token[j - 2] && term[depth - 2] == token[j - 1]) v = min(v, rows[depth - 2][j - 2] + 1)
                    row[j] = v
                    if (v < smallest) smallest = v
                }
                rowMin[depth] = smallest
                bestPrefix[depth] = min(bestPrefix[depth - 1], row[q])
                if (smallest > maxTypos && rowMin[depth - 1] > maxTypos) {
                    pruned = true
                    break
                }
            }
            computed = depth
            if (pruned) {
                // No term under this prefix can do better than the prefix already has: take or skip them all.
                val end = prefixEnd(term, depth, i)
                val typos = bestPrefix[depth]
                if (typos <= maxTypos) for (t in i until end) onMatch(t, typos, false)
                i = end
            } else {
                val typos = bestPrefix[depth]
                if (typos <= maxTypos) onMatch(i, typos, rows[depth][q] == typos)
                i++
            }
        }
    }

    private fun commonPrefix(a: String, b: String, limit: Int): Int {
        val max = min(limit, min(a.length, b.length))
        var i = 0
        while (i < max && a[i] == b[i]) i++
        return i
    }

    /** The first index in [terms] not less than [key]. */
    private fun lowerBound(key: String): Int {
        var lo = 0
        var hi = terms.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (terms[mid] < key) lo = mid + 1 else hi = mid
        }
        return lo
    }

    /** The first index after [from] whose term doesn't start with [term]'s first [length] chars. */
    private fun prefixEnd(term: String, length: Int, from: Int): Int {
        var lo = from + 1
        var hi = terms.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (terms[mid].regionMatches(0, term, 0, length)) lo = mid + 1 else hi = mid
        }
        return lo
    }

    companion object {
        private const val TOKEN_CACHE_SIZE = 8
        private const val NO_MATCH = Int.MAX_VALUE

        // A posting: document (20 bits) | field entry (4) | field weight (2) | position (5).
        private const val MAX_DOCUMENTS = 1 shl 20
        private const val MAX_FIELDS = 16
        private const val MAX_POSITION = 31
        private const val DOC_SHIFT = 11
        private const val ENTRY_SHIFT = 7
        private const val WEIGHT_SHIFT = 5
        private const val POSTING_MATCH_MASK = (1 shl DOC_SHIFT) - 1

        // A match: typos (2 bits) | partial (1) | then the posting's low bits, so smaller is better.
        private const val PARTIAL_SHIFT = 11
        private const val TYPOS_SHIFT = 12

        // A ranking key, most significant first: tokens missed, typos, partial matches, field weight, proximity,
        // unpopularity, then alphabetical rank.
        private const val MAX_TOKENS_MATCHED = 15
        private const val MAX_POPULARITY = (1 shl 19) - 1
        private const val RANK_MASK = (1L shl 21) - 1

        private fun entry(match: Int) = (match ushr ENTRY_SHIFT) and 0b1111

        private fun position(match: Int) = match and MAX_POSITION

        fun <T> empty(): SearchIndex<T> = build(emptyList())

        /** Indexes [documents]; in their order, which also breaks ties that ranking leaves. */
        fun <T> build(documents: List<SearchDocument<T>>): SearchIndex<T> {
            require(documents.size < MAX_DOCUMENTS) { "Too many documents to index: ${documents.size}" }
            val termIds = HashMap<String, Int>()
            val termList = ArrayList<String>()
            var entries = LongArray(documents.size * 8 + 16)
            var count = 0
            val sortKeys = arrayOfNulls<String>(documents.size)
            val popularity = IntArray(documents.size)
            documents.forEachIndexed { doc, document ->
                popularity[doc] = document.popularity
                var entry = 0
                for ((field, text) in document.fields) {
                    if (text.isNullOrBlank()) continue
                    if (entry == MAX_FIELDS) break
                    if (field == SearchField.Name && sortKeys[doc] == null) sortKeys[doc] = SearchText.sortKey(text)
                    val posting = (doc shl DOC_SHIFT) or (entry shl ENTRY_SHIFT) or (field.weight shl WEIGHT_SHIFT)
                    SearchText.tokens(text) { token, _, position, _ ->
                        val id = termIds.getOrPut(token) { termList.add(token).let { termList.size - 1 } }
                        if (count == entries.size) entries = entries.copyOf(count * 2)
                        entries[count++] = (id.toLong() shl 32) or (posting or min(position, MAX_POSITION)).toLong()
                    }
                    entry++
                }
            }

            val terms = termList.toTypedArray().apply { sort() }
            val sortedId = IntArray(terms.size)
            terms.forEachIndexed { sorted, term -> sortedId[termIds.getValue(term)] = sorted }
            for (e in 0 until count) {
                val entry = entries[e]
                entries[e] = (sortedId[(entry ushr 32).toInt()].toLong() shl 32) or (entry and 0xFFFFFFFFL)
            }
            entries.sort(0, count)
            val postingStart = IntArray(terms.size + 1)
            val postings = IntArray(count)
            for (e in 0 until count) {
                postingStart[(entries[e] ushr 32).toInt() + 1]++
                postings[e] = entries[e].toInt()
            }
            for (t in 1..terms.size) postingStart[t] += postingStart[t - 1]

            val byAlphabeticalRank = documents.indices.sortedWith(compareBy<Int> { sortKeys[it] == null }.thenBy { sortKeys[it] }).toIntArray()
            val alphabeticalRank = IntArray(documents.size)
            byAlphabeticalRank.forEachIndexed { rank, doc -> alphabeticalRank[doc] = rank }

            return SearchIndex(
                items = documents.map { it.item },
                terms = terms,
                postingStart = postingStart,
                postings = postings,
                alphabeticalRank = alphabeticalRank,
                byAlphabeticalRank = byAlphabeticalRank,
                popularity = popularity,
                maxTermLength = terms.maxOfOrNull { it.length } ?: 0,
            )
        }
    }
}
