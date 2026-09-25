package com.simplecityapps.architecture

import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import org.junit.Test

/**
 * Presentation-layer rules: ViewModels and use cases (UDF doc principles 8a–8c), and what Compose code
 * may import.
 */
class PresentationRules {

    @Test
    fun `ViewModels reach repositories and data sources only through use cases`() {
        val violations = Production.viewModels.flatMap { viewModel ->
            val file = viewModel.containingFile
            val imported = file.importNames.filter(::isDataAccess)
            val injected = viewModel.constructorTypeNames().map(file::resolve).filter(::isDataAccess)
            (imported + injected).distinct().map { viewModel.violation(" -> $it") }
        }
        Baseline.assertMatches(
            "viewmodel-data-access",
            "ViewModels must not depend on repositories, DAOs, Room, Retrofit, OkHttp, MediaProvider or MediaImporter; inject a use case",
            violations,
        )
    }

    @Test
    fun `ViewModels follow the UDF conventions`() {
        val violations = Production.viewModels.flatMap { viewModel ->
            buildList {
                if (!viewModel.name.endsWith("ViewModel")) add(": not named *ViewModel")
                if (viewModel.annotations.none { it.name == "HiltViewModel" }) add(": not @HiltViewModel")
                if (viewModel.hasParentWithName("AndroidViewModel")) add(": extends AndroidViewModel (8b)")
                viewModel.constructorTypeNames()
                    .filter { it in setOf("Context", "Application", "Resources") }
                    .forEach { add(": injects $it (8b)") }
                viewModel.containingFile.importNames
                    .filter { it.startsWith("android.") }
                    .forEach { add(": imports $it (8b)") }
            }.map { viewModel.violation(it) }
        }
        Baseline.assertMatches(
            "viewmodel-conventions",
            "ViewModels are named *ViewModel, are @HiltViewModel, and have no Android framework dependencies (UDF 8b)",
            violations,
        )
    }

    @Test
    fun `use cases have a single public operator invoke and no UI dependencies`() {
        val violations = useCases().flatMap { useCase ->
            val functions = useCase.functions(includeNested = false, includeLocal = false)
            buildList {
                if (functions.count { it.name == "invoke" } > 1) add(": more than one invoke")
                functions
                    .filter { it.hasPublicOrDefaultModifier && !it.hasOverrideModifier && it.name != "invoke" }
                    .forEach { add(": public fun ${it.name}") }
                if (useCase.primaryConstructor?.annotations.orEmpty().none { it.name == "Inject" || it.name == "AssistedInject" }) {
                    add(": no @Inject constructor")
                }
                if (useCase.name.endsWith("UseCase")) add(": named *UseCase (8c: a verb phrase, no suffix)")
                useCase.containingFile.importNames
                    .filter { name -> UI_PACKAGES.any { name.startsWith(it) } }
                    .forEach { add(": imports $it") }
            }.map { useCase.violation(it) }
        }
        Baseline.assertMatches(
            "usecase-shape",
            "Use cases (classes declaring operator fun invoke) expose only that one public invoke, are @Inject-constructed, and import no Android UI",
            violations,
        )
    }

    @Test
    fun `Compose UI and ViewModels do not import data-layer packages`() {
        val violations = Production.scope.files
            .filter { it.isPresentation() }
            .flatMap { file ->
                file.importNames
                    .filter { name -> DATA_LAYER_PACKAGES.any { it.matches(name) } }
                    .map { file.violation("${file.fqn} -> $it") }
            }
        Baseline.assertMatches(
            "presentation-data-imports",
            "Files declaring @Composable functions or ViewModels must not import Room, Retrofit/OkHttp, DAOs, entities or network DTOs",
            violations,
        )
    }

    private fun useCases() = Production.classes.filter { declaration ->
        declaration !in Production.viewModels &&
            declaration.functions(includeNested = false, includeLocal = false).any { it.name == "invoke" && it.hasOperatorModifier }
    }

    private fun KoFileDeclaration.isPresentation() = functions(includeNested = true, includeLocal = false).any { function -> function.annotations.any { it.name == "Composable" } } ||
        Production.viewModels.any { it.containingFile.path == path }

    private companion object {
        val UI_PACKAGES = listOf(
            "android.view.",
            "android.widget.",
            "android.app.Activity",
            "android.app.Dialog",
            "androidx.compose.",
            "androidx.fragment.",
            "androidx.appcompat.",
            "androidx.navigation.",
            "androidx.recyclerview.",
            "androidx.lifecycle.ViewModel",
            "com.google.android.material.",
        )

        val DATA_LAYER_PACKAGES = listOf(
            Regex("""androidx\.room\..*"""),
            Regex("""retrofit2\..*"""),
            Regex("""okhttp3\..*"""),
            Regex("""com\.simplecityapps\.localmediaprovider\.local\.data\.room\..*"""),
            Regex("""com\.simplecityapps\.provider\.\w+\.http\..*"""),
            Regex("""com\.simplecityapps\.networking\.retrofit\..*"""),
        )

        fun isDataAccess(fqn: String): Boolean {
            val simpleName = fqn.substringAfterLast('.')
            return simpleName.endsWith("Repository") ||
                simpleName.endsWith("Dao") ||
                simpleName.endsWith("MediaProvider") ||
                simpleName == "MediaImporter" ||
                fqn.startsWith("androidx.room.") ||
                fqn.startsWith("retrofit2.") ||
                fqn.startsWith("okhttp3.")
        }
    }
}
