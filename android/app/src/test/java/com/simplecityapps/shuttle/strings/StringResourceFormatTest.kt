package com.simplecityapps.shuttle.strings

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

private val PHRASE_PLACEHOLDER = Regex("\\{[a-zA-Z_][a-zA-Z0-9_]*}")
private val FORMAT_ARG = Regex("%(\\d+)\\$([sd])")
private val LOCALE_QUALIFIER = Regex("values-[a-z]{2}(-r[A-Z]{2})?$")

/**
 * Guards the string resources' conversion away from Square Phrase (#469): no `{name}` placeholder
 * should remain in any locale's strings.xml, and every locale's positional format args must match
 * the default string's, since translators may reorder them and a mismatch would crash
 * `String.format` at runtime instead of failing a build.
 */
class StringResourceFormatTest {

    private val repoRoot: File = generateSequence(File(".").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle").exists() }

    private val resRoots = listOf(
        File(repoRoot, "android/app/src/main/res"),
        File(repoRoot, "android/mediaprovider/core/src/main/res"),
    )

    @Test
    fun `no Phrase-style placeholders remain in string resources`() {
        val offenders = mutableListOf<String>()
        for (resRoot in resRoots) {
            for (file in stringXmlFiles(resRoot)) {
                val matches = PHRASE_PLACEHOLDER.findAll(file.readText()).map { it.value }.toSet()
                if (matches.isNotEmpty()) {
                    offenders += "${file.relativeTo(repoRoot)}: $matches"
                }
            }
        }
        assertTrue("Found Phrase-style {placeholder} left in string resources:\n${offenders.joinToString("\n")}", offenders.isEmpty())
    }

    @Test
    fun `every locale's format args match the default string's`() {
        val mismatches = mutableListOf<String>()
        for (resRoot in resRoots) {
            val default = formatArgsByName(File(resRoot, "values"))
            for (dir in resRoot.listFiles { f -> f.isDirectory && LOCALE_QUALIFIER.containsMatchIn(f.name) }.orEmpty()) {
                for ((name, args) in formatArgsByName(dir)) {
                    val expected = default[name] ?: continue
                    if (args != expected) {
                        mismatches += "${resRoot.parentFile.name}/${dir.name}/$name: expected $expected, found $args"
                    }
                }
            }
        }
        assertTrue("Locale format-arg mismatch (translator reordering vs. default):\n${mismatches.joinToString("\n")}", mismatches.isEmpty())
    }

    private fun stringXmlFiles(resRoot: File): List<File> = resRoot.listFiles { f -> f.isDirectory && (f.name == "values" || LOCALE_QUALIFIER.containsMatchIn(f.name)) }
        .orEmpty()
        .flatMap { dir -> dir.listFiles { f -> f.extension == "xml" }.orEmpty().toList() }

    /** Maps each `<string>`/`<plurals>` resource name in [dir] to its positional format args, e.g. `[1: 's', 2: 'd']`. */
    private fun formatArgsByName(dir: File): Map<String, Map<Int, Char>> {
        val result = mutableMapOf<String, MutableMap<Int, Char>>()
        for (file in dir.listFiles { f -> f.extension == "xml" }.orEmpty()) {
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            val root = document.documentElement
            for (i in 0 until root.childNodes.length) {
                val node = root.childNodes.item(i) as? Element ?: continue
                val name = node.getAttribute("name")
                val args = result.getOrPut(name) { mutableMapOf() }
                when (node.tagName) {
                    "string" -> addFormatArgs(args, node.textContent)

                    "plurals" -> for (j in 0 until node.childNodes.length) {
                        val item = node.childNodes.item(j) as? Element ?: continue
                        if (item.tagName == "item") addFormatArgs(args, item.textContent)
                    }
                }
            }
        }
        return result
    }

    private fun addFormatArgs(
        into: MutableMap<Int, Char>,
        text: String
    ) {
        for (match in FORMAT_ARG.findAll(text)) {
            val (position, type) = match.destructured
            into[position.toInt()] = type[0]
        }
    }
}
