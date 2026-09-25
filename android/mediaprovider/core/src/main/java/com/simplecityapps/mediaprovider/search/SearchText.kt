package com.simplecityapps.mediaprovider.search

import java.text.Normalizer

/**
 * Normalises text for search: lowercase, diacritics folded (Björk → bjork), apostrophes and dots dropped inside a
 * word (Don't → dont, R.E.M. → rem), everything else that isn't a letter or digit splits tokens. A word that
 * punctuation splits into parts (AC/DC, Jay-Z) also yields the parts joined (acdc, jayz), at the first part's position.
 */
internal object SearchText {
    /** What each char folds to: null splits tokens, "" is dropped without splitting. Filled lazily; races are benign. */
    private val folded = arrayOfNulls<String>(Char.MAX_VALUE.code + 1)
    private const val SEPARATOR = "\u0000"

    private val special = mapOf(
        'ß' to "ss",
        'æ' to "ae",
        'Æ' to "ae",
        'œ' to "oe",
        'Œ' to "oe",
        'ø' to "o",
        'Ø' to "o",
        'đ' to "d",
        'Đ' to "d",
        'ð' to "d",
        'Ð' to "d",
        'ł' to "l",
        'Ł' to "l",
        'ı' to "i",
        'þ' to "th",
        'Þ' to "th",
    )

    /** The normalised form of [c]: null if it splits tokens, "" if it's dropped. */
    fun fold(c: Char): String? {
        val cached = folded[c.code] ?: computeFold(c).also { folded[c.code] = it }
        return if (cached === SEPARATOR) null else cached
    }

    private fun computeFold(c: Char): String = when {
        c in 'a'..'z' || c in '0'..'9' -> c.toString()
        c in 'A'..'Z' -> c.lowercaseChar().toString()
        c == '\'' || c == '.' || c == '`' || c == '’' || c == '‘' || c == 'ʼ' -> ""
        c.code < 128 -> SEPARATOR
        c.isMark() -> ""
        c in special -> special.getValue(c)
        c.isLetterOrDigit() -> Normalizer.normalize(c.toString(), Normalizer.Form.NFD).filterNot { it.isMark() }.lowercase().ifEmpty { SEPARATOR }
        else -> SEPARATOR
    }

    fun Char.isMark(): Boolean = when (Character.getType(this).toByte()) {
        Character.NON_SPACING_MARK, Character.COMBINING_SPACING_MARK, Character.ENCLOSING_MARK -> true
        else -> false
    }

    /**
     * Calls [onToken] for each token of [text] in order: its normalised form, the index in [text] each of its chars
     * came from (only when [withSources]), its word position, and whether it's the joined form of a split word.
     */
    inline fun tokens(
        text: String,
        withSources: Boolean = false,
        onToken: (token: String, sources: IntArray?, position: Int, joined: Boolean) -> Unit,
    ) {
        val part = StringBuilder()
        val partSources = if (withSources) IntArrayBuilder() else null
        val word = StringBuilder()
        val wordSources = if (withSources) IntArrayBuilder() else null
        var wordParts = 0
        var wordPosition = 0
        var position = 0
        var i = 0
        while (i <= text.length) {
            val c = if (i < text.length) text[i] else ' '
            val fold = if (c.isWhitespace()) null else fold(c)
            val endsWord = c.isWhitespace()
            if (fold != null) {
                for (f in fold) {
                    part.append(f)
                    partSources?.add(i)
                }
            } else {
                if (part.isNotEmpty()) {
                    if (wordParts == 0) wordPosition = position
                    onToken(part.toString(), partSources?.toArray(), position, false)
                    word.append(part)
                    partSources?.let { wordSources!!.addAll(it) }
                    wordParts++
                    position++
                    part.setLength(0)
                    partSources?.clear()
                }
                if (endsWord) {
                    if (wordParts > 1) onToken(word.toString(), wordSources?.toArray(), wordPosition, true)
                    word.setLength(0)
                    wordSources?.clear()
                    wordParts = 0
                }
            }
            i++
        }
    }

    /** The normalised tokens of [text], split parts only (queries don't add joined forms). */
    fun queryTokens(text: String): List<String> = buildList { tokens(text) { token, _, _, joined -> if (!joined) add(token) } }

    /** [text]'s split tokens joined by spaces: an alphabetical sort key that ignores case and diacritics. */
    fun sortKey(text: String): String = queryTokens(text).joinToString(" ")
}

internal class IntArrayBuilder {
    private var values = IntArray(16)
    var size = 0
        private set

    fun add(value: Int) {
        if (size == values.size) values = values.copyOf(size * 2)
        values[size++] = value
    }

    fun addAll(other: IntArrayBuilder) {
        for (i in 0 until other.size) add(other.values[i])
    }

    fun clear() {
        size = 0
    }

    fun toArray(): IntArray = values.copyOf(size)
}
