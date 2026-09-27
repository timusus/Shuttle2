package com.simplecityapps.shuttle.sorting

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.test.Test

// Runs against java.text.Collator on the JVM and NSString's localized comparison on iOS; asserts only what
// both agree on in any locale.
class LocaleCollatorTest {
    private val secondary = localeCollator(CollationStrength.Secondary)
    private val tertiary = localeCollator(CollationStrength.Tertiary)

    @Test
    fun `secondary ignores case but not accents`() {
        secondary.compare("abc", "ABC") shouldBe 0
        secondary.compare("resume", "résumé") shouldNotBe 0
    }

    @Test
    fun `tertiary tells case apart`() {
        tertiary.compare("abc", "ABC") shouldNotBe 0
    }

    @Test
    fun `both order by letter before case unlike code point order`() {
        val names = listOf("banana", "Cherry", "apple", "Banana")

        names.sortedWith(secondary).map { it.lowercase() } shouldBe listOf("apple", "banana", "banana", "cherry")
        names.sortedWith(tertiary).map { it.lowercase() } shouldBe listOf("apple", "banana", "banana", "cherry")
    }
}
