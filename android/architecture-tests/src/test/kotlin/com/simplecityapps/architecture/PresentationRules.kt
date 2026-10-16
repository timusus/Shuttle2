package com.simplecityapps.architecture

import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import org.junit.Test

/**
 * Presentation-layer rules: ViewModels and use cases (UDF doc principles 8a–8c), and what Compose code
 * may import.
 */
class PresentationRules {

    @Test
    fun `ViewModels follow the UDF conventions`() {
        val violations = Production.viewModels.flatMap { viewModel ->
            buildList {
                if (!viewModel.name.endsWith("ViewModel")) add(": not named *ViewModel")
                if (!viewModel.isMetroViewModel()) add(": not contributed to the Metro ViewModel maps")
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
            "ViewModels are named *ViewModel, are contributed to the Metro ViewModel maps, and have no Android framework dependencies (UDF 8b)",
            violations,
        )
    }

    @Test
    fun `ViewModels expose one uiState StateFlow and at most one events Flow`() {
        val violations = Production.viewModels.flatMap { viewModel ->
            viewModel.properties(includeNested = false)
                .filter { it.hasPublicOrDefaultModifier }
                .filterNot { property ->
                    val type = property.type?.text.orEmpty()
                    (property.name == "uiState" && type.startsWith("StateFlow<")) ||
                        (property.name == "events" && type.substringBefore('<').endsWith("Flow"))
                }
                .map { viewModel.violation(": public ${it.name}") }
        }
        Baseline.assertMatches(
            "viewmodel-public-api",
            "A ViewModel's public properties are one uiState: StateFlow and at most one events Flow (UDF 6)",
            violations,
        )
    }

    @Test
    fun `ViewModels do not expose Channel-backed event flows`() {
        val violations = Production.viewModels.flatMap { viewModel ->
            CHANNEL_FLOW_CALLS
                .filter { "$it()" in viewModel.text }
                .map { viewModel.violation(": $it()") }
        }
        Baseline.assertMatches(
            "viewmodel-no-channel-events",
            "ViewModels model events whose loss is a bug as consumable UiState, not Channel.receiveAsFlow()/consumeAsFlow() (UDF 4)",
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
                // Metro's `@Inject constructor`, or its class-level `@Inject`
                val injectAnnotations = useCase.annotations + useCase.primaryConstructor?.annotations.orEmpty()
                if (injectAnnotations.none { it.name == "Inject" || it.name == "AssistedInject" }) {
                    add(": no @Inject constructor")
                }
                if (useCase.isForwarder()) add(": forwards one call (inject the dependency instead)")
                if (useCase.name.endsWith("UseCase")) add(": named *UseCase (8c: a verb phrase, no suffix)")
                useCase.containingFile.importNames
                    .filter { name -> UI_PACKAGES.any { name.startsWith(it) } }
                    .forEach { add(": imports $it") }
            }.map { useCase.violation(it) }
        }
        Baseline.assertMatches(
            "usecase-shape",
            "Use cases (classes declaring operator fun invoke) expose only that one public invoke, are @Inject-constructed, import no Android UI, and do more than forward one call (UDF 8a)",
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

    /**
     * Only `invoke`, whose body is one call on a constructor property with `invoke`'s own parameters as the arguments
     * (optionally `.flowOn(..)`/`.first()`): the caller can inject that dependency itself. Mirrors codehealth's
     * `kotlin-usecase-pass-through` rule.
     */
    private fun KoClassDeclaration.isForwarder(): Boolean {
        val invoke = functions(includeNested = false, includeLocal = false).singleOrNull { it.name == "invoke" } ?: return false
        val others = functions(includeNested = false, includeLocal = false)
            .any { it != invoke && it.hasPublicOrDefaultModifier && !it.hasOverrideModifier }
        val constructorParameters = primaryConstructor?.parameters.orEmpty()
        val bodyProperties = properties(includeNested = false).any { property -> constructorParameters.none { it.name == property.name } }
        if (others || bodyProperties || hasSecondaryConstructors() || Regex("""\binit\s*\{""").containsMatchIn(text)) return false
        if (constructorParameters.any { !it.hasValModifier && !it.hasVarModifier }) return false
        val signatureEnd = invoke.text.substringAfter("fun invoke").let { afterName ->
            var depth = 0
            val close = afterName.indexOfFirst { char ->
                if (char == '(') {
                    depth++
                } else if (char == ')') {
                    depth--
                }
                char == ')' && depth == 0
            }
            if (close < 0) return false
            afterName.substring(close + 1)
        }
        val body = signatureEnd.substring(signatureEnd.indexOfFirst { it == '=' || it == '{' }.takeIf { it >= 0 } ?: return false).trim()
        val arguments = invoke.parameters.joinToString("""\s*,\s*""") { Regex.escape(it.name) }
        val call = Regex("""^(=\s*|\{\s*(return\s+)?)(\w+)\s*\.\s*\w+\(\s*$arguments\s*\)(\s*\.\s*(flowOn\([\w.]+\)|first\(\)))*\s*\}?$""")
        val receiver = call.matchEntire(body)?.groupValues?.get(3) ?: return false
        return constructorParameters.any { it.name == receiver }
    }

    /**
     * Contributed to metrox-viewmodel's maps: `@ViewModelKey` on the class, or, for an assisted ViewModel, a
     * nested factory keyed with `@ManualViewModelAssistedFactoryKey` or `@ViewModelAssistedFactoryKey`.
     */
    private fun KoClassDeclaration.isMetroViewModel() = annotations.any { it.name == "ViewModelKey" } ||
        interfaces(includeNested = false).any { factory -> factory.annotations.any { it.name in ASSISTED_FACTORY_KEYS } }

    private fun KoFileDeclaration.isPresentation() = functions(includeNested = true, includeLocal = false).any { function -> function.annotations.any { it.name == "Composable" } } ||
        Production.viewModels.any { it.containingFile.path == path }

    private companion object {
        val ASSISTED_FACTORY_KEYS = setOf("ManualViewModelAssistedFactoryKey", "ViewModelAssistedFactoryKey")

        val CHANNEL_FLOW_CALLS = listOf("receiveAsFlow", "consumeAsFlow")

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
    }
}
