package com.simplecityapps.shuttle.model

// Whole words or phrases of a genre; "Space Opera" and "Rock Opera" are removed first since they are not the art form.
private val classicalGenreWords = listOf(
    "classical", "opera", "operas", "baroque", "chamber", "choral", "lieder", "mass", "art song", "early music", "orchestral",
    "symphony", "symphonic", "symphonies", "concerto", "concertos", "romantic era", "operetta", "operettas"
)
private val notOperaPhrases = listOf("space opera", "rock opera")

// Whole words ("Jazz", "Latin") and roots that also start compounds ("Dancehall", "Metalcore", "Folktronica"); hyphens split, so "Hip-Hop" and "Dance-Pop" match their parts.
private val popularGenreWords = setOf(
    "jazz", "hop", "hiphop", "r&b", "rnb", "country", "blues", "synth", "synthwave", "synthpop", "britpop", "altrock", "kpop", "jpop", "latin", "alternative", "alt", "gospel"
)
private val popularGenreRoots = listOf(
    "rock", "metal", "pop", "punk", "dance", "techno", "electro", "folk", "indie", "hip", "rap", "house", "trance", "ska", "reggae", "grunge", "emo", "funk", "disco", "soul"
)

// Words that start a root without being the genre.
private val notPopularGenreWords = setOf("popular", "electroacoustic", "soulful")
private val sacredGenreWords = setOf("mass", "sacred", "choral", "motet", "requiem")
private val screenGenreWords = setOf("soundtrack", "score")
private val genreWordSplit = Regex("""[^a-z0-9&]+""")
private val songGenre = Regex("""\bsongs?\b|lieder|\blied\b""", RegexOption.IGNORE_CASE)

internal fun List<Song>.isSongGenre() = any { song -> song.genres.any { songGenre.containsMatchIn(it) } }

private fun List<String>.genreTokens(): List<String> = flatMap { genre -> genre.split(';', '/', ',').map { it.trim().lowercase() } }.filter { it.isNotEmpty() }

private fun String.genreWords(): List<String> = split(genreWordSplit).filter { it.isNotEmpty() }

private fun String.hasClassicalGenreWord(): Boolean {
    var padded = " ${genreWords().joinToString(" ")} "
    notOperaPhrases.forEach { padded = padded.replace(" $it ", " ") }
    return classicalGenreWords.any { padded.contains(" $it ") }
}

// "Latin" alone is popular; beside a sacred word ("Latin Mass") it is the language.
private fun String.hasPopularGenreWord(): Boolean {
    val words = genreWords()
    val sacred = words.any { it in sacredGenreWords }
    return words.any { word ->
        word !in notPopularGenreWords && !(word == "latin" && sacred) && (word in popularGenreWords || popularGenreRoots.any { word.startsWith(it) })
    }
}

/** Whether [genres] name a classical genre; any popular word vetoes ("Orchestral Pop", "Chamber Jazz"), "Romantic" is no genre; "Chamber Music", "Symphonic", "Operetta", "Latin Mass" and "Baroque; Vocal" are. */
internal fun hasClassicalGenre(genres: List<String>): Boolean = genres.genreTokens().any { it.hasClassicalGenreWord() && !it.hasPopularGenreWord() }

/** Whether [genres] name a popular genre; Soundtrack and Score are neutral, since film scores are often orchestral. */
internal fun hasNonClassicalGenre(genres: List<String>): Boolean = genres.genreTokens().any { it.hasPopularGenreWord() }

internal fun Song.hasPopularGenre() = hasNonClassicalGenre(genres) || genres.genreTokens().any { token -> token.genreWords().any { it in screenGenreWords } }
