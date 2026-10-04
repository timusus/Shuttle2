package com.simplecityapps.architecture

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Kotlin/Native refuses to compile some backtick test names, so `iosSimulatorArm64Test` breaks (#821). */
class NativeTestNameRules {

    @Test
    fun `commonTest function names avoid characters Kotlin Native rejects`() {
        val root = File(requireNotNull(System.getProperty("architecture.repoRoot")))
        val violations = listOf("android", "shared").map { File(root, it) }
            .filter { it.exists() }
            .flatMap { dir ->
                dir.walkTopDown()
                    .onEnter { it.name != "build" && !it.name.startsWith(".") }
                    .filter { it.isFile && it.extension == "kt" && "/src/commonTest/" in it.invariantPath() }
                    .flatMap { file ->
                        file.readLines().mapIndexedNotNull { index, line ->
                            val name = BACKTICK_FUN.find(line)?.groupValues?.get(1)
                            if (name != null && name.any { it in ILLEGAL }) "${file.relativeTo(root).invariantPath()}:${index + 1}: `$name`" else null
                        }
                    }
            }
        assertTrue(
            "Backtick test names in commonTest must not contain any of $ILLEGAL (Kotlin/Native rejects them):\n" +
                violations.joinToString("\n"),
            violations.isEmpty(),
        )
    }

    private fun File.invariantPath() = invariantSeparatorsPath

    private companion object {
        val BACKTICK_FUN = Regex("""\bfun\s+`([^`]*)`""")
        const val ILLEGAL = ",;:./\\<>[]"
    }
}
