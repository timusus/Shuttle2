package com.simplecityapps.architecture

import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import org.junit.Test

/**
 * Guards the presentation code that layering.md step 8 moves into `:android:ui`. Until that split
 * lands, the `ui` package and the rest of `:android:app` share one Gradle module, so
 * `VerifyModuleLayers` cannot see this edge yet — this Konsist rule is the only thing stopping new
 * ui -> data/provider/app-only imports from growing the pile the move has to fix. See
 * docs/architecture/layering.md.
 */
class UiModuleRules {

    @Test
    fun `ui screens do not import data, provider or app-only declarations`() {
        val declaringFiles = declaredFqnToFiles()
        val violations = movingUiFiles().flatMap { file ->
            file.importNames.mapNotNull { fqn ->
                // An import names every declaration with that FQN: all overloads of a top-level
                // function, which can be spread over several modules sharing a package. Without type
                // resolution the rule can't tell which one a call binds to, so any banned declarer counts.
                val reasons = declaringFiles[fqn].orEmpty().mapNotNull { target ->
                    when {
                        target.module in DATA_AND_PROVIDER_MODULES -> target.module
                        target.module == APP_MODULE && !target.isMovingUiFile() -> "app-only"
                        else -> null
                    }
                }.distinct().sorted()
                if (reasons.isEmpty()) return@mapNotNull null
                file.violation("${file.fqn} -> $fqn (${reasons.joinToString()})")
            }
        }
        Baseline.assertMatches(
            "ui-module-imports",
            "Files under ui/** (outside MainActivity, the shortcut/review helpers and ui/widgets, which stay " +
                "in :android:app) must not import data, provider or other app-only declarations; move the " +
                "dependency behind a domain port or use case",
            violations,
        )
    }

    private fun movingUiFiles() = Production.scope.files.filter { it.isMovingUiFile() }

    /**
     * Every importable production declaration, mapped to the files declaring it: classes, interfaces
     * and objects (nested too), type aliases, and top-level functions (extensions included) and
     * properties. One FQN can have several declaring files, e.g. overloads of a top-level function in
     * different modules that share a package.
     */
    private fun declaredFqnToFiles(): Map<String, Set<KoFileDeclaration>> {
        val index = mutableMapOf<String, MutableSet<KoFileDeclaration>>()
        fun record(fqn: String?, file: KoFileDeclaration) {
            if (fqn != null) index.getOrPut(fqn) { mutableSetOf() } += file
        }
        Production.scope.classesAndInterfacesAndObjects(includeNested = true, includeLocal = false)
            .forEach { record(it.fullyQualifiedName, it.containingFile) }
        Production.scope.typeAliases.forEach { record(it.fullyQualifiedName, it.containingFile) }
        Production.scope.files.forEach { file ->
            val topLevelNames = file.functions(includeNested = false, includeLocal = false).map { it.name } +
                file.properties(includeNested = false).map { it.name }
            topLevelNames.forEach { name -> record(listOfNotNull(file.packagee?.name, name).joinToString("."), file) }
        }
        return index
    }

    /**
     * Files under the `ui` package in `:android:app` that step 8 moves to `:android:ui` — everything
     * except the top-level helpers directly in `ui` (MainActivity, the shortcut/review helpers,
     * ThemeManager) and the `widgets` subpackage, which stay behind in the composition root
     * (layering.md step 8's "Stays" list).
     */
    private fun KoFileDeclaration.isMovingUiFile(): Boolean {
        if (module != APP_MODULE) return false
        val subPath = UI_SUBPATH.find(relativePath)?.groupValues?.get(1) ?: return false
        return "/" in subPath && !subPath.startsWith("widgets/")
    }

    private companion object {
        const val APP_MODULE = ":android:app"

        val UI_SUBPATH = Regex("""/ui/(.+)$""")

        val DATA_AND_PROVIDER_MODULES = setOf(
            ":android:mediaprovider:core",
            ":android:downloads",
            ":android:imageloader",
            ":android:networking",
            ":android:playback",
            ":android:saf",
            ":android:trial",
            ":android:mediaprovider:local",
            ":android:mediaprovider:jellyfin",
            ":android:mediaprovider:emby",
            ":android:mediaprovider:plex",
        )
    }
}
