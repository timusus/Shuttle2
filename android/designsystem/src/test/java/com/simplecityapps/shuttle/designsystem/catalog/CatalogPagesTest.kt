package com.simplecityapps.shuttle.designsystem.catalog

import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File
import org.junit.Test

class CatalogPagesTest {
    private fun entry(id: String) = CatalogEntry(id, id, listOf("default")) {}

    @Test
    fun `new components get an unticked line`() {
        CatalogPages.index(listOf(entry("row-song")), existingIndex = null) shouldContain
            "- [ ] `row-song`: [boards](row-song.md) · approved: — · boards hash: —"
    }

    @Test
    fun `existing lines survive a rewrite and removed components drop out`() {
        val approved = "- [x] `button`: [boards](button.md) · approved: 2026-10-02 @ a1b2c3d · boards hash: 9f3e"
        val existing = "# Component catalogue\n\n$approved\n- [ ] `gone`: [boards](gone.md) · approved: — · boards hash: —\n"
        val index = CatalogPages.index(listOf(entry("button"), entry("row-song")), existing)
        index shouldContain approved
        index shouldContain "- [ ] `row-song`"
        index shouldNotContain "`gone`"
    }

    @Test
    fun `component page links every board of that component`() {
        val shots = catalogShots(listOf(CatalogEntries.first { it.id == "row-song" }))
        val page = CatalogPages.componentPage(shots.first().entry, shots)
        shots.forEach { page shouldContain it.path }
        page.lines().count { it.startsWith("## ") } shouldBe 4
    }

    /** Rewrites the pages next to the boards whenever `recordRoborazziDebug` records them. */
    @Test
    fun `write pages when recording`() {
        if (System.getProperty("roborazzi.test.record") != "true") return
        val dir = File(roborazziSystemPropertyOutputDirectory())
        val indexFile = File(dir, "index.md")
        val shots = catalogShots()
        indexFile.writeText(CatalogPages.index(CatalogEntries, indexFile.takeIf { it.exists() }?.readText()))
        CatalogEntries.forEach { entry -> File(dir, "${entry.id}.md").writeText(CatalogPages.componentPage(entry, shots)) }
    }
}
