package com.simplecityapps.shuttle.model

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class WorkTitleTest {
    private fun title(
        core: String,
        nickname: String? = null,
        key: String? = null,
        catalogue: String? = null,
        arrangement: String? = null
    ) = WorkTitle(core, nickname, key, catalogue, arrangement)

    @Test
    fun `titles parse into core key catalogue nickname and arrangement`() {
        val cases = mapOf(
            "Symphony No. 5 in C minor, Op. 67" to title("Symphony No. 5", key = "C minor", catalogue = "Op. 67"),
            "Symphony No. 5 in C minor" to title("Symphony No. 5", key = "C minor"),
            "Symphony No. 3 in E-flat major, op. 55, Eroica" to title("Symphony No. 3", nickname = "Eroica", key = "E-flat major", catalogue = "Op. 55"),
            "Symphony No. 3 \"Eroica\"" to title("Symphony No. 3", nickname = "Eroica"),
            "Piano Sonata No. 8 in C minor, Op. 13 “Pathétique”" to title("Piano Sonata No. 8", nickname = "Pathétique", key = "C minor", catalogue = "Op. 13"),
            "Piano Sonata No. 2 in F♯ minor" to title("Piano Sonata No. 2", key = "F♯ minor"),
            "Piano Sonata, Op. 10 No. 3" to title("Piano Sonata", catalogue = "Op. 10 No. 3"),
            "Sonata in D, D. 850" to title("Sonata in D", catalogue = "D. 850"),
            "Mass in B minor, BWV 232" to title("Mass in B minor", catalogue = "BWV 232"),
            "Symphony No. 52 in C minor, Hob. I:52" to title("Symphony No. 52", key = "C minor", catalogue = "Hob. I:52"),
            "Piano Sonata in E-flat, Hob. XVI:52" to title("Piano Sonata in E-flat", catalogue = "Hob. XVI:52"),
            "Violin Concerto in A minor, BWV 1041a" to title("Violin Concerto in A minor", catalogue = "BWV 1041a"),
            "Piano Concerto No. 20 in D minor, K. 466" to title("Piano Concerto No. 20", key = "D minor", catalogue = "K. 466"),
            "Concerto RV 537" to title("Concerto", catalogue = "RV 537"),
            "Cantata BWV 140" to title("Cantata", catalogue = "BWV 140"),
            "Symphony No. 9, WAB 109" to title("Symphony No. 9", catalogue = "WAB 109"),
            "Chaconne, BWV 1004 (arr. Busoni)" to title("Chaconne", catalogue = "BWV 1004", arrangement = "Busoni"),
            "Symphony No. 3, Eroica" to title("Symphony No. 3", nickname = "Eroica")
        )
        cases.forEach { (input, expected) -> parseWorkTitle(input) shouldBe expected }
    }

    @Test
    fun `number and catalogue abbreviations take their conventional case`() {
        parseWorkTitle("Symphonie no. 5") shouldBe title("Symphonie No. 5")
        parseWorkTitle("Symphony no. 2 in C minor, op. 67") shouldBe title("Symphony No. 2", key = "C minor", catalogue = "Op. 67")
        parseWorkTitle("Piano Sonatas nos. 1-3") shouldBe title("Piano Sonatas Nos. 1-3")
        parseWorkTitle("Cantata, bwv 140").catalogue shouldBe "BWV 140"
        parseWorkTitle("Sonata in A, d. 959") shouldBe title("Sonata in A, d. 959")
        parseWorkTitle("Piano Sonata, op. 10 no. 3").catalogue shouldBe "Op. 10 No. 3"
    }

    @Test
    fun `a comma tail is a nickname only when it is a single capitalised word`() {
        parseWorkTitle("Symphony No. 5, Part 1") shouldBe title("Symphony No. 5, Part 1")
        parseWorkTitle("Suite No. 2, Prelude") shouldBe title("Suite No. 2, Prelude")
        parseWorkTitle("Cantata No. 140, Wachet auf") shouldBe title("Cantata No. 140, Wachet auf")
    }

    @Test
    fun `single letters are not catalogue numbers without their dot`() {
        listOf("K2", "D12", "S1", "Vitamin B 12", "Symphony in B 12", "Piano Sonata K 310").forEach {
            parseWorkTitle(it).catalogue shouldBe null
            catalogueSearchKey(it) shouldBe null
            catalogueSearchKeys(it) shouldBe emptyList()
        }
    }

    @Test
    fun `a D number must follow a comma or the start`() {
        parseWorkTitle("Sonata D. 850").catalogue shouldBe null
        catalogueSearchKeys("D. 850") shouldBe listOf("d850")
        catalogueSearchKeys("Sonata in A, D. 959") shouldBe listOf("d959")
    }

    @Test
    fun `strict single letter forms are catalogue numbers`() {
        parseWorkTitle("Sonata, K. 310").catalogue shouldBe "K. 310"
        parseWorkTitle("Sonata, KV 310").catalogue shouldBe "KV 310"
        parseWorkTitle("Partita, S. 5").catalogue shouldBe "S. 5"
        parseWorkTitle("Partita, S 5").catalogue shouldBe "S 5"
        parseWorkTitle("Sonata, B. 12").catalogue shouldBe "B. 12"
    }

    @Test
    fun `a title without a number keeps its key`() {
        parseWorkTitle("Mass in B minor") shouldBe title("Mass in B minor")
        parseWorkTitle("Fantasie in F minor") shouldBe title("Fantasie in F minor")
    }

    @Test
    fun `German and French keys are left unparsed`() {
        parseWorkTitle("Symphonie Nr. 5 c-moll") shouldBe title("Symphonie Nr. 5 c-moll")
        parseWorkTitle("Sinfonie Nr. 3 in Es-Dur") shouldBe title("Sinfonie Nr. 3 in Es-Dur")
    }

    @Test
    fun `comma separated catalogue numbers show the last`() {
        val cases = mapOf(
            "Die Winterreise, op. 89, D. 911" to title("Die Winterreise", catalogue = "D. 911"),
            "Symphony No. 5 in C minor, Op. 67, Hob. I:5" to title("Symphony No. 5", key = "C minor", catalogue = "Hob. I:5")
        )
        cases.forEach { (input, expected) -> parseWorkTitle(input) shouldBe expected }
    }

    @Test
    fun `doubt returns the title unchanged`() {
        parseWorkTitle("Concerto, BWV 1041 / RV 522") shouldBe title("Concerto, BWV 1041 / RV 522")
        parseWorkTitle("Symphony No. 5 in C minor for orchestra") shouldBe title("Symphony No. 5 in C minor for orchestra")
        parseWorkTitle("  Goldberg Variations  ") shouldBe title("Goldberg Variations")
        parseWorkTitle("") shouldBe title("")
    }

    @Test
    fun `the key D is not a Deutsch number`() {
        parseWorkTitle("Symphony No. 9 in D major") shouldBe title("Symphony No. 9", key = "D major")
    }

    @Test
    fun `catalogue search keys normalise to one form`() {
        val cases = mapOf(
            "op67" to "op67",
            "Op. 67" to "op67",
            "OP 67" to "op67",
            "BWV1007" to "bwv1007",
            "BWV 1007" to "bwv1007",
            "K.466" to "k466",
            "KV 466" to "k466",
            "Hob. XVI:52" to "hobxvi:52",
            "Op. 10 No. 3" to "op10"
        )
        cases.forEach { (input, expected) -> catalogueSearchKey(input) shouldBe expected }
        catalogueSearchKey("beethoven") shouldBe null
        catalogueSearchKey("Symphony No. 5") shouldBe null
    }

    @Test
    fun `catalogue search keys are found inside a longer text`() {
        catalogueSearchKeys("Symphony No. 5 in C minor, Op. 67") shouldBe listOf("op67")
        catalogueSearchKeys("Goldberg Variations") shouldBe emptyList()
    }
}
