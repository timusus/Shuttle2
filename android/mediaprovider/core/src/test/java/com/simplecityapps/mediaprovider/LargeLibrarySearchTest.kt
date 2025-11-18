package com.simplecityapps.mediaprovider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests fuzzy search behavior with realistic library sizes (100s-1000s of items).
 *
 * These tests ensure the algorithm:
 * 1. Finds the right match even when there are many similar results
 * 2. Ranks exact/close matches higher than partial/distant matches
 * 3. Doesn't get "drowned out" by many weak matches
 * 4. Performs well with common words/prefixes shared by many items
 */
class LargeLibrarySearchTest {

    private data class RankedResult(val name: String, val score: Double)

    private fun rankResults(query: String, targets: List<String>): List<RankedResult> = targets
        .map { target ->
            val similarity = StringComparison.jaroWinklerMultiDistance(query, target)
            RankedResult(target, similarity.score)
        }
        .sortedWith(
            compareByDescending<RankedResult> { it.score }
                .thenBy { stripArticlesForSorting(it.name).length }
        )

    // Helper to strip articles for tie-breaking (matches StringComparison.stripArticles behavior)
    private fun stripArticlesForSorting(s: String): String {
        val normalized = s.lowercase().trim()
        val articles = listOf("the", "a", "an", "el", "la", "los", "las", "le", "les", "der", "die", "das")
        for (article in articles) {
            val pattern = "^$article\\s+"
            if (normalized.matches(Regex(pattern + ".*"))) {
                return normalized.replaceFirst(Regex(pattern), "")
            }
        }
        return normalized
    }

    // ===================================================================================
    // COMMON PREFIX SCENARIOS
    // ===================================================================================

    @Test
    fun `large library - many bands with THE prefix`() {
        val targets = listOf(
            "The Beatles",
            "The Who",
            "The Doors",
            "The Rolling Stones",
            "The Clash",
            "The Smiths",
            "The Cure",
            "The Police",
            "The Kinks",
            "The Strokes",
            "The Killers",
            "The National",
            "The White Stripes",
            "The Black Keys",
            "The xx",
            "The Shins",
            "The Pixies",
            "The Velvet Underground",
            "The Beach Boys",
            "The Ramones",
            "The Eagles",
            "The Band",
            "The Byrds",
            "The Animals",
            "The Zombies"
        )

        // Specific query should find the right band
        val beatlesResults = rankResults("beatles", targets)
        assertEquals("The Beatles", beatlesResults[0].name)

        val whoResults = rankResults("who", targets)
        assertTrue(
            "Expected 'The Who' in top 2 for 'who'. Got: ${whoResults.take(2).map { it.name }}",
            whoResults.take(2).any { it.name == "The Who" }
        )

        val strokesResults = rankResults("strokes", targets)
        assertEquals("The Strokes", strokesResults[0].name)

        // Multi-word should work
        val whitestripesResults = rankResults("white stripes", targets)
        assertEquals("The White Stripes", whitestripesResults[0].name)
    }

    @Test
    fun `large library - many bands with BLACK prefix`() {
        val targets = listOf(
            "Black Sabbath",
            "The Black Keys",
            "Black Flag",
            "Blackpink",
            "Black Veil Brides",
            "Black Crowes",
            "Black Label Society",
            "Black Rebel Motorcycle Club",
            "Black Eyed Peas",
            "Black Star",
            "Blackalicious",
            "Blackstreet",
            "Blackmore's Night",
            "Black Lips",
            "Black Moth Super Rainbow",
            // Non-black bands for contrast
            "Red Hot Chili Peppers",
            "Green Day",
            "White Stripes",
            "Blue Oyster Cult",
            "Pink Floyd"
        )

        // Specific black band should be findable
        val sabbathResults = rankResults("sabbath", targets)
        assertEquals("Black Sabbath", sabbathResults[0].name)

        val flagResults = rankResults("flag", targets)
        assertEquals("Black Flag", flagResults[0].name)

        val keysResults = rankResults("black keys", targets)
        assertEquals("The Black Keys", keysResults[0].name)

        // "black" alone should rank all black bands highly
        val blackResults = rankResults("black", targets)
        val top10 = blackResults.take(10)
        val blackBandsInTop10 = top10.count { it.name.contains("Black", ignoreCase = true) }
        assertTrue(
            "Expected at least 9 bands with 'Black' in top 10 for query 'black'. Got $blackBandsInTop10",
            blackBandsInTop10 >= 9
        )
    }

    // ===================================================================================
    // GENRE-SPECIFIC SCENARIOS
    // ===================================================================================

    @Test
    fun `large library - metal bands with similar names`() {
        val targets = listOf(
            "Metallica",
            "Metal Church",
            "Death Metal",
            "Metronomy", // Not metal!
            "Megadeth",
            "Slayer",
            "Anthrax",
            "Iron Maiden",
            "Black Sabbath",
            "Judas Priest",
            "Pantera",
            "Sepultura",
            "Lamb of God",
            "Mastodon",
            "Opeth",
            "Gojira",
            "Tool",
            "System of a Down",
            "Rage Against the Machine",
            "Disturbed"
        )

        val metallicaResults = rankResults("metallica", targets)
        assertEquals("Metallica", metallicaResults[0].name)

        val metalResults = rankResults("metal", targets)
        // Either Metallica or Metal Church should be #1
        assertTrue(
            "Expected 'Metallica' or 'Metal Church' first for 'metal'. Got: ${metalResults[0].name}",
            metalResults[0].name == "Metallica" || metalResults[0].name == "Metal Church"
        )
        // Both should be in top 3
        val top3 = metalResults.take(3).map { it.name }
        assertTrue("Expected Metallica in top 3", top3.contains("Metallica"))
        assertTrue("Expected Metal Church in top 3", top3.contains("Metal Church"))

        val megadethResults = rankResults("megadeth", targets)
        assertEquals("Megadeth", megadethResults[0].name)
    }

    @Test
    fun `large library - indie rock bands with similar vibes`() {
        val targets = listOf(
            "Arcade Fire",
            "Vampire Weekend",
            "The National",
            "LCD Soundsystem",
            "Interpol",
            "The Strokes",
            "Yeah Yeah Yeahs",
            "Spoon",
            "Modest Mouse",
            "Death Cab for Cutie",
            "The Shins",
            "Broken Social Scene",
            "Neutral Milk Hotel",
            "Animal Collective",
            "Grizzly Bear",
            "Fleet Foxes",
            "Bon Iver",
            "Sufjan Stevens",
            "The Decemberists",
            "Band of Horses"
        )

        val arcadeResults = rankResults("arcade", targets)
        assertEquals("Arcade Fire", arcadeResults[0].name)

        val vampireResults = rankResults("vampire", targets)
        assertEquals("Vampire Weekend", vampireResults[0].name)

        val neutralResults = rankResults("neutral milk", targets)
        assertEquals("Neutral Milk Hotel", neutralResults[0].name)

        // Test partial multi-word
        val deathcabResults = rankResults("death cab", targets)
        assertEquals("Death Cab for Cutie", deathcabResults[0].name)
    }

    // ===================================================================================
    // NAME SIMILARITY SCENARIOS
    // ===================================================================================

    @Test
    fun `large library - similar artist names with different genres`() {
        val targets = listOf(
            "Queen",
            "Queens of the Stone Age",
            "Queensrÿche",
            "Queen Latifah",
            "Queensway",
            "King Crimson",
            "King Gizzard & the Lizard Wizard",
            "Kings of Leon",
            "The King Blues",
            "Nat King Cole",
            "Prince",
            "Princess Nokia",
            "Duke Ellington",
            "Count Basie",
            "Earl Sweatshirt"
        )

        val queenResults = rankResults("queen", targets)
        assertEquals("Queen", queenResults[0].name)

        val queensStoneResults = rankResults("queens stone", targets)
        assertEquals("Queens of the Stone Age", queensStoneResults[0].name)

        val kingCrimsonResults = rankResults("king crimson", targets)
        assertEquals("King Crimson", kingCrimsonResults[0].name)

        val kingsLeonResults = rankResults("kings leon", targets)
        assertEquals("Kings of Leon", kingsLeonResults[0].name)
    }

    @Test
    fun `large library - bands with numbers`() {
        val targets = listOf(
            "Blink-182",
            "Sum 41",
            "311",
            "3 Doors Down",
            "Three Days Grace",
            "Matchbox Twenty",
            "Maroon 5",
            "Nine Inch Nails",
            "Thirty Seconds to Mars",
            "21 Pilots",
            "50 Cent",
            "2Pac",
            "The 1975",
            "U2",
            "UB40",
            "5 Seconds of Summer",
            "10cc",
            "Front 242",
            "Sevendust",
            "Powerman 5000"
        )

        val blinkResults = rankResults("blink 182", targets)
        assertEquals("Blink-182", blinkResults[0].name)

        val ninResults = rankResults("nine inch nails", targets)
        assertEquals("Nine Inch Nails", ninResults[0].name)

        val u2Results = rankResults("u2", targets)
        assertEquals("U2", u2Results[0].name)

        val sum41Results = rankResults("sum 41", targets)
        assertEquals("Sum 41", sum41Results[0].name)
    }

    // ===================================================================================
    // COMMON WORDS SCENARIOS
    // ===================================================================================

    @Test
    fun `large library - many bands with LOVE in name`() {
        val targets = listOf(
            "Love",
            "Love and Rockets",
            "Courtney Love",
            "My Bloody Valentine",
            "The Lovin' Spoonful",
            "Modern English", // "I Melt with You" - not relevant
            "Depeche Mode", // "Love song" - not in name
            "The Loveless",
            "Lovely The Band",
            "Lovers Rock",
            "Glove",
            "Dove",
            "Above & Beyond",
            // Contrasting bands
            "Hate Eternal",
            "Joy Division",
            "The Smiths",
            "The Cure"
        )

        val loveResults = rankResults("love", targets)
        // "Love" (exact match) should rank first
        assertEquals("Love", loveResults[0].name)

        val loveRocketsResults = rankResults("love rockets", targets)
        assertEquals("Love and Rockets", loveRocketsResults[0].name)
    }

    @Test
    fun `large library - DAY vs DEAD vs DEATH prefix collision`() {
        val targets = listOf(
            "Day",
            "Daydream",
            "Green Day",
            "Days of the New",
            "Day6",
            "Dead",
            "Deadmau5",
            "Dead Kennedys",
            "Dead Can Dance",
            "The Dead Weather",
            "Grateful Dead",
            "Death",
            "Death Cab for Cutie",
            "Death from Above 1979",
            "Megadeth",
            "Death Grips",
            "Dance",
            "Dancing",
            "Danger Mouse"
        )

        val greenDayResults = rankResults("green day", targets)
        assertEquals("Green Day", greenDayResults[0].name)

        val deadKennedysResults = rankResults("dead kennedys", targets)
        assertEquals("Dead Kennedys", deadKennedysResults[0].name)

        val deathCabResults = rankResults("death cab", targets)
        assertEquals("Death Cab for Cutie", deathCabResults[0].name)

        val gratefulResults = rankResults("grateful dead", targets)
        assertEquals("Grateful Dead", gratefulResults[0].name)
    }

    // ===================================================================================
    // PERFORMANCE & THRESHOLD CHECKS
    // ===================================================================================

    @Test
    fun `large library - weak matches don't pollute top results`() {
        val targets = mutableListOf(
            "The Beatles",
            "Beat Happening",
            "Beatnuts"
        )

        // Add 100 completely unrelated bands
        targets.addAll(
            listOf(
                "Radiohead", "Coldplay", "Muse", "Arctic Monkeys", "Tame Impala",
                "MGMT", "Phoenix", "Empire of the Sun", "Foster the People", "Two Door Cinema Club",
                "The xx", "Alt-J", "Glass Animals", "Foals", "Local Natives",
                "Grimes", "Lorde", "Lana Del Rey", "Florence + The Machine", "St. Vincent",
                "Bon Iver", "Sufjan Stevens", "Iron & Wine", "Fleet Foxes", "Grizzly Bear",
                "Animal Collective", "Panda Bear", "Deerhunter", "Atlas Sound", "The National",
                "Interpol", "The Strokes", "Yeah Yeah Yeahs", "TV on the Radio", "Bloc Party",
                "Franz Ferdinand", "Kaiser Chiefs", "The Libertines", "Babyshambles", "The Kooks",
                "Vampire Weekend", "MGMT", "Passion Pit", "Cut Copy", "Hot Chip",
                "LCD Soundsystem", "The Rapture", "!!! (Chk Chk Chk)", "DFA 1979", "Chromeo",
                "Justice", "Daft Punk", "The Chemical Brothers", "Fatboy Slim", "Moby",
                "Aphex Twin", "Boards of Canada", "Autechre", "Squarepusher", "Flying Lotus",
                "Four Tet", "Jamie xx", "Caribou", "Tycho", "Bonobo",
                "SBTRKT", "Disclosure", "Flume", "Odesza", "Porter Robinson",
                "Madeon", "Zedd", "Avicii", "Calvin Harris", "Deadmau5",
                "Skrillex", "Diplo", "Major Lazer", "Dillon Francis", "Flosstradamus",
                "RL Grime", "Baauer", "Hudson Mohawke", "Rustie", "Cashmere Cat",
                "Kaytranada", "Sango", "Ta-ku", "Jai Paul", "Frank Ocean",
                "The Weeknd", "Drake", "Kanye West", "Tyler, The Creator", "Earl Sweatshirt",
                "Vince Staples", "Kendrick Lamar", "J. Cole", "Chance the Rapper", "Anderson .Paak"
            )
        )

        val beatlesResults = rankResults("beatles", targets)

        // The Beatles should still be #1 despite 100 irrelevant results
        assertEquals(
            "Expected 'The Beatles' to rank first even with 100+ unrelated bands",
            "The Beatles",
            beatlesResults[0].name
        )

        // All 3 beat* bands should be in top 5
        val top5 = beatlesResults.take(5).map { it.name }
        assertTrue("Expected 'The Beatles' in top 5", top5.contains("The Beatles"))
        assertTrue("Expected 'Beat Happening' in top 5", top5.contains("Beat Happening"))
        assertTrue("Expected 'Beatnuts' in top 5", top5.contains("Beatnuts"))

        // Check that scores are properly distributed
        val top5Scores = beatlesResults.take(5).map { it.score }
        val bottom5Scores = beatlesResults.takeLast(5).map { it.score }

        assertTrue(
            "Top 5 average score (${top5Scores.average()}) should be much higher than bottom 5 (${bottom5Scores.average()})",
            top5Scores.average() > bottom5Scores.average() + 0.2
        )
    }

    @Test
    fun `large library - threshold prevents garbage results`() {
        val targets = listOf(
            "The Beatles",
            "Radiohead",
            "Pink Floyd",
            "Led Zeppelin",
            "The Rolling Stones",
            "Queen",
            "David Bowie"
        )

        val results = rankResults("xyz123", targets)
        val aboveThreshold = results.filter { it.score >= StringComparison.threshold }

        assertTrue(
            "Query 'xyz123' should not match any band above threshold. Got ${aboveThreshold.size} matches: $aboveThreshold",
            aboveThreshold.isEmpty()
        )
    }

    @Test
    fun `large library - exact match beats all partial matches`() {
        // 1 exact match among 50 partial matches
        val targets = mutableListOf<String>()

        // Add the exact match
        targets.add("Red")

        // Add 50 bands with "red" in them
        repeat(50) { i ->
            targets.add("Red Band Number $i")
        }

        val results = rankResults("red", targets)

        // "Red" should rank #1
        assertEquals(
            "Expected exact match 'Red' to beat all 50 partial matches",
            "Red",
            results[0].name
        )
    }

    // ===================================================================================
    // STRESS TEST SCENARIOS
    // ===================================================================================

    @Test
    fun `stress test - 200 bands with common prefix`() {
        val targets = mutableListOf<String>()

        // Add actual target
        targets.add("The Beatles")

        // Add 199 other "The" bands
        repeat(199) { i ->
            targets.add("The Band $i")
        }

        val results = rankResults("beatles", targets)

        // Beatles should still be findable in top 3
        val top3 = results.take(3).map { it.name }
        assertTrue(
            "Expected 'The Beatles' in top 3 even with 199 'The' bands",
            top3.contains("The Beatles")
        )
    }

    @Test
    fun `stress test - very long band names`() {
        val targets = listOf(
            "Godspeed You! Black Emperor",
            "!!!",
            "And You Will Know Us by the Trail of Dead",
            "I Love You But I've Chosen Darkness",
            "The World Is a Beautiful Place & I Am No Longer Afraid to Die",
            "A Silver Mt. Zion Memorial Orchestra & Tra-La-La Band",
            "65daysofstatic",
            "Battles",
            "Explosions in the Sky",
            "This Will Destroy You"
        )

        val godspeedResults = rankResults("godspeed", targets)
        assertEquals("Godspeed You! Black Emperor", godspeedResults[0].name)

        val trailResults = rankResults("trail of dead", targets)
        assertEquals("And You Will Know Us by the Trail of Dead", trailResults[0].name)

        val beautifulResults = rankResults("beautiful place", targets)
        assertEquals(
            "The World Is a Beautiful Place & I Am No Longer Afraid to Die",
            beautifulResults[0].name
        )
    }
}
