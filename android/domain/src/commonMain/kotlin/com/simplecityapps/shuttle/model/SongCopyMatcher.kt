package com.simplecityapps.shuttle.model

import kotlin.math.abs

/**
 * The tags of one copy of a song that [SongCopyMatcher] reads: the plain tags every source has. No MusicBrainz or
 * server ids, since most libraries carry none and a rule that leant on them would only work for the few that do.
 */
data class SongCopyTags(
    val title: String?,
    val album: String?,
    val albumArtist: String?,
    val artist: String?,
    val disc: Int?,
    val track: Int?,
    val durationMs: Int?,
    val year: Int?
)

val Song.copyTags: SongCopyTags
    get() = SongCopyTags(
        title = name,
        album = album,
        albumArtist = albumArtistDisplay ?: albumArtist,
        artist = artistDisplay ?: artists.joinToString(", ").ifEmpty { null },
        disc = disc,
        track = track,
        durationMs = duration.takeIf { it > 0 },
        year = date?.year
    )

/** A deterministic step applied to titles, albums and artists before any comparison. */
enum class SongCopyNormalisation {
    FoldDiacritics,

    /** "A (feat. B)", "A ft. B": the credit moves between title and artist from one source to the next. */
    DropFeaturing,

    /** "(2011 Remaster)", "[Deluxe Edition]", "- Album Version": the same audio, labelled per release. */
    DropEditionQualifiers,

    /** Every bracketed or " - " qualifier of a title; only safe alongside [SongCopyMatchParams.markerCheck]. */
    DropAllTitleQualifiers,

    /** "&" and "+" read as "and". */
    Ampersand,

    /** A leading "The"/"A"/"An" of an album or artist, as [AlbumIdentityRule.albumKey] drops it. */
    Articles
}

/** How a field takes part in a match: it must agree when both copies have it, adds its weight to the score, or is unused. */
enum class SongCopyFieldUse { Gate, Score, Ignore }

/** The fields compared within a block (album, disc and track already agree there). */
enum class SongCopyField { Title, Duration, AlbumArtist, Artist, Year }

/** Fellegi–Sunter log2 weights: log2(m/u) when the field agrees, log2((1-m)/(1-u)) when it disagrees. */
data class SongCopyFieldWeight(
    val agree: Double,
    val disagree: Double
)

/**
 * The defaults are the rule chosen on the song-match corpus (support/song-match/README.md): block, then equal titles,
 * lengths within 2 s and the same version markers. Dropping every title qualifier, or gating on artist, year or album
 * artist, or scoring every field, traded more recall than it saved in wrong merges.
 */
data class SongCopyMatchParams(
    val normalisation: Set<SongCopyNormalisation> = SongCopyNormalisation.entries.toSet() - SongCopyNormalisation.DropAllTitleQualifiers,
    /** A copy with no disc number is on disc 1: most single-disc rips leave it out. */
    val missingDiscIsOne: Boolean = true,
    /**
     * Normalised album names a server or tagger writes for no album ("Unknown Album", "<unknown>"): read as untagged,
     * since they put unrelated files of one track number in one block.
     */
    val placeholderAlbums: Set<String> = PLACEHOLDER_ALBUMS,
    val durationToleranceMs: Int = 2_000,
    /** Live, remix, demo, explicit and the like must be the same set in both titles. */
    val markerCheck: Boolean = true,
    /** Normalised titles this many edits apart still agree; 0 asks for equal titles. */
    val titleEditDistance: Int = 0,
    val uses: Map<SongCopyField, SongCopyFieldUse> = mapOf(
        SongCopyField.Title to SongCopyFieldUse.Gate,
        SongCopyField.Duration to SongCopyFieldUse.Gate
    ),
    val weights: Map<SongCopyField, SongCopyFieldWeight> = emptyMap(),
    /** The least total weight of the [SongCopyFieldUse.Score] fields for a match. */
    val minScore: Double = 0.0
) {
    fun use(field: SongCopyField): SongCopyFieldUse = uses[field] ?: SongCopyFieldUse.Ignore

    companion object {
        val PLACEHOLDER_ALBUMS = setOf("unknown album", "unknown", "no album")
    }
}

/** A song's tags normalised once, so a library is prepared in one pass and each comparison is cheap. */
class PreparedSongCopy internal constructor(
    val tags: SongCopyTags,
    /** Album, disc and track; null when the album or track is untagged, which never matches anything. */
    val blockingKey: String?,
    internal val title: String?,
    internal val markers: Set<String>,
    internal val albumArtist: String?,
    internal val artist: String?
) {
    /** [blockingKey] as a stable 64-bit FNV-1a hash, for an indexed column. */
    val blockingHash: Long? get() = blockingKey?.let(SongCopyMatcher::fnv1a64)
}

/** Why two copies are or aren't one song: the first gate that failed, or the score against the threshold. */
data class SongCopyMatch(
    val isCopy: Boolean,
    val failedGate: SongCopyField? = null,
    val score: Double = 0.0,
    val blocked: Boolean = true
)

/**
 * Whether two songs from different sources are copies of one song of one album. Copies share a block (normalised album,
 * disc and track), then must pass every gate field and reach the score threshold. Merging two different songs hides
 * one, while missing a merge only shows a duplicate, so every rule here errs towards not merging.
 */
class SongCopyMatcher(private val params: SongCopyMatchParams = SongCopyMatchParams()) {
    fun prepare(tags: SongCopyTags): PreparedSongCopy {
        val steps = params.normalisation
        return PreparedSongCopy(
            tags = tags,
            blockingKey = blockingKey(tags.album, tags.disc, tags.track),
            title = tags.title?.let { normaliseTitle(it, steps) }?.takeIf { it.isNotEmpty() },
            markers = tags.title?.let(::versionMarkers).orEmpty(),
            albumArtist = tags.albumArtist?.let { normaliseArtist(it, steps) }?.takeIf { it.isNotEmpty() },
            artist = tags.artist?.let { normaliseArtist(it, steps) }?.takeIf { it.isNotEmpty() }
        )
    }

    private fun blockingKey(
        album: String?,
        disc: Int?,
        track: Int?
    ): String? {
        val normalised = album?.let { normaliseAlbum(it, params.normalisation) }?.takeIf { it.isNotEmpty() && it !in params.placeholderAlbums }
        val resolvedDisc = disc ?: if (params.missingDiscIsOne) 1 else null
        return if (normalised != null && track != null) "$normalised\u0000${resolvedDisc ?: "?"}\u0000$track" else null
    }

    /** [PreparedSongCopy.blockingHash] from the three tags it reads, for storing with a song whenever its tags are written. */
    fun blockingHash(
        album: String?,
        disc: Int?,
        track: Int?
    ): Long? = blockingKey(album, disc, track)?.let(::fnv1a64)

    fun match(
        a: SongCopyTags,
        b: SongCopyTags
    ): SongCopyMatch = match(prepare(a), prepare(b))

    fun match(
        a: PreparedSongCopy,
        b: PreparedSongCopy
    ): SongCopyMatch {
        if (a.blockingKey == null || a.blockingKey != b.blockingKey) return SongCopyMatch(isCopy = false, blocked = false)
        // An untitled copy can't be told from its album's other untitled tracks
        if (a.title == null || b.title == null) return SongCopyMatch(isCopy = false, failedGate = SongCopyField.Title)
        if (params.markerCheck && a.markers != b.markers) return SongCopyMatch(isCopy = false, failedGate = SongCopyField.Title)
        var score = 0.0
        for (field in SongCopyField.entries) {
            val use = params.use(field)
            if (use == SongCopyFieldUse.Ignore) continue
            val agrees = agreement(field, a, b) ?: continue
            if (use == SongCopyFieldUse.Gate && !agrees) return SongCopyMatch(isCopy = false, failedGate = field)
            if (use == SongCopyFieldUse.Score) {
                val weight = params.weights[field] ?: continue
                score += if (agrees) weight.agree else weight.disagree
            }
        }
        return SongCopyMatch(isCopy = score >= params.minScore, score = score)
    }

    /** Whether [field] agrees between the copies; null when either lacks it, which is no evidence either way. */
    fun agreement(
        field: SongCopyField,
        a: PreparedSongCopy,
        b: PreparedSongCopy
    ): Boolean? = when (field) {
        SongCopyField.Title -> both(a.title, b.title) { x, y -> x == y || (params.titleEditDistance > 0 && withinEdits(x, y, params.titleEditDistance)) }
        SongCopyField.Duration -> both(a.tags.durationMs, b.tags.durationMs) { x, y -> abs(x - y) <= params.durationToleranceMs }
        SongCopyField.AlbumArtist -> both(a.albumArtist, b.albumArtist) { x, y -> x == y }
        SongCopyField.Artist -> both(a.artist, b.artist) { x, y -> x == y }
        SongCopyField.Year -> both(a.tags.year, b.tags.year) { x, y -> x == y }
    }

    companion object {
        private val FEATURING_BRACKET = Regex("""\s*[(\[]\s*(?:feat\.?|ft\.?|featuring|with)\s[^)\]]*[)\]]""")
        private val FEATURING_TAIL = Regex("""\s+(?:feat\.?|ft\.?|featuring)\s.*$""")
        private val QUALIFIER_BRACKET = Regex("""\s*[(\[][^)\]]*[)\]]""")
        private val QUALIFIER_DASH = Regex("""\s+[-–—]\s+.*$""")
        private val EDITION_WORDS = Regex("""\b(?:re-?master(?:ed)?|deluxe|expanded|anniversary|edition|reissue|bonus tracks?|album version|lp version|original version)\b""")
        private val AMPERSAND = Regex("""\s*(?:&|\+)\s*""")
        private val NON_WORD = Regex("""[^\p{L}\p{N}]+""")

        /** Words naming a version of a recording: titles differing in them are different recordings. */
        private val MARKERS = listOf(
            "live" to Regex("""\blive\b"""),
            "mix" to Regex("""\b(?:re-?mix(?:ed)?|mix|dub|rework)\b"""),
            "edit" to Regex("""\bedit\b"""),
            "explicit" to Regex("""\b(?:explicit|dirty|uncensored)\b"""),
            "clean" to Regex("""\b(?:clean|censored)\b"""),
            "demo" to Regex("""\bdemo\b"""),
            "instrumental" to Regex("""\binstrumental\b"""),
            "acoustic" to Regex("""\b(?:acoustic|unplugged)\b"""),
            "acapella" to Regex("""\ba\s?cap+el+a\b"""),
            "karaoke" to Regex("""\bkaraoke\b"""),
            "reprise" to Regex("""\breprise\b"""),
            "extended" to Regex("""\bextended\b"""),
            "mono" to Regex("""\bmono\b"""),
            "alternate" to Regex("""\b(?:alternate|alt\.?|outtake|take\s*\d+)\b"""),
            "orchestral" to Regex("""\b(?:orchestral|symphonic)\b"""),
            "rehearsal" to Regex("""\brehearsal\b""")
        )

        fun versionMarkers(title: String): Set<String> {
            val folded = title.foldDiacritics().lowercase()
            return MARKERS.mapNotNullTo(HashSet()) { (marker, pattern) -> marker.takeIf { pattern.containsMatchIn(folded) } }
        }

        private fun String.base(steps: Set<SongCopyNormalisation>): String {
            var text = if (SongCopyNormalisation.FoldDiacritics in steps) foldDiacritics() else this
            text = text.lowercase()
            if (SongCopyNormalisation.DropFeaturing in steps) text = text.replace(FEATURING_BRACKET, "").replace(FEATURING_TAIL, "")
            return text
        }

        private fun String.dropEditionQualifiers(): String = QUALIFIER_BRACKET.replace(this) { if (EDITION_WORDS.containsMatchIn(it.value)) "" else it.value }
            .let { text -> QUALIFIER_DASH.find(text)?.takeIf { EDITION_WORDS.containsMatchIn(it.value) }?.let { text.removeRange(it.range) } ?: text }

        private fun String.words(steps: Set<SongCopyNormalisation>): String {
            val text = if (SongCopyNormalisation.Ampersand in steps) replace(AMPERSAND, " and ") else this
            return text.replace(NON_WORD, " ").trim()
        }

        fun normaliseTitle(
            title: String,
            steps: Set<SongCopyNormalisation>
        ): String {
            var text = title.base(steps)
            if (SongCopyNormalisation.DropEditionQualifiers in steps) text = text.dropEditionQualifiers()
            if (SongCopyNormalisation.DropAllTitleQualifiers in steps) {
                // A title that is all qualifier ("(Untitled)") keeps it, rather than becoming blank
                text = text.replace(QUALIFIER_BRACKET, "").replace(QUALIFIER_DASH, "").ifBlank { text }
            }
            return text.words(steps)
        }

        fun normaliseAlbum(
            album: String,
            steps: Set<SongCopyNormalisation>
        ): String {
            var text = album.base(steps)
            if (SongCopyNormalisation.DropEditionQualifiers in steps) text = text.dropEditionQualifiers()
            if (SongCopyNormalisation.Articles in steps) text = text.removeArticles()
            return text.words(steps)
        }

        fun normaliseArtist(
            artist: String,
            steps: Set<SongCopyNormalisation>
        ): String {
            var text = artist.base(steps)
            if (SongCopyNormalisation.Articles in steps) text = text.removeArticles()
            return text.words(steps)
        }

        /** Whether [a] and [b] are at most [max] single-character edits apart (Levenshtein, banded). */
        fun withinEdits(
            a: String,
            b: String,
            max: Int
        ): Boolean {
            if (abs(a.length - b.length) > max) return false
            var previous = IntArray(b.length + 1) { it }
            var current = IntArray(b.length + 1)
            for (i in 1..a.length) {
                current[0] = i
                var rowMin = i
                for (j in 1..b.length) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
                    rowMin = minOf(rowMin, current[j])
                }
                if (rowMin > max) return false
                previous = current.also { current = previous }
            }
            return previous[b.length] <= max
        }

        private inline fun <T : Any> both(
            a: T?,
            b: T?,
            agree: (T, T) -> Boolean
        ): Boolean? = if (a == null || b == null) null else agree(a, b)

        internal fun fnv1a64(text: String): Long {
            var hash = -0x340d631b7bdddcdbL
            for (byte in text.encodeToByteArray()) {
                hash = hash xor (byte.toLong() and 0xff)
                hash *= 0x100000001b3L
            }
            return hash
        }
    }
}
