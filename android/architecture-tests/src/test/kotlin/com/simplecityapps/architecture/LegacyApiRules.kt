package com.simplecityapps.architecture

import org.junit.Test

/**
 * No new legacy async or UI APIs: coroutines and Flow over RxJava, LiveData, AsyncTask and callback
 * interfaces; Compose + ViewModel over MVP presenters, Fragments and Views. Existing uses are baselined.
 */
class LegacyApiRules {

    @Test
    fun `no RxJava`() = importRule("rxjava", "Use coroutines and Flow, not RxJava") { it.startsWith("io.reactivex") || it.startsWith("com.jakewharton.rx") }

    @Test
    fun `no LiveData`() = importRule("livedata", "Use StateFlow and collectAsStateWithLifecycle, not LiveData") { name ->
        (name.startsWith("androidx.lifecycle.") && ("LiveData" in name || name.endsWith(".liveData") || name.endsWith(".Observer"))) ||
            name.startsWith("androidx.compose.runtime.livedata.")
    }

    @Test
    fun `no AsyncTask`() = importRule("asynctask", "Use coroutines, not AsyncTask") { it == "android.os.AsyncTask" }

    @Test
    fun `no callback listener interfaces`() {
        val violations = Production.scope.interfaces(includeNested = true)
            .filter { declaration -> CALLBACK_SUFFIXES.any { declaration.name.endsWith(it) } }
            .map { it.containingFile.violation(it.fullyQualifiedName ?: "${it.containingFile.fqn}.${it.name}") }
        Baseline.assertMatches(
            "callback-interfaces",
            "Don't declare *Listener/*Callback(s) interfaces; expose a Flow or a suspend function",
            violations,
        )
    }

    @Test
    fun `no MVP presenters or contracts`() {
        val classes = Production.classes
            .filter { declaration -> isMvp(declaration.name) || declaration.parents().any { isMvp(it.name) } }
            .map { it.violation() }
        val interfaces = Production.scope.interfaces(includeNested = true)
            .filter { declaration -> isMvp(declaration.name) || declaration.parents().any { isMvp(it.name) } }
            .map { it.containingFile.violation(it.fullyQualifiedName ?: "${it.containingFile.fqn}.${it.name}") }
        val violations = classes + interfaces
        Baseline.assertMatches(
            "mvp",
            "No new MVP presenters or contracts (BasePresenter, BaseContract); build a Compose screen with a ViewModel",
            violations,
        )
    }

    @Test
    fun `no Fragments outside the allowed hosts`() {
        val violations = Production.classes
            .filter { declaration -> declaration.parents().any { parent -> FRAGMENT_PARENT.matches(parent.name) } }
            .filter { it.fqn !in ALLOWED_FRAGMENT_HOSTS }
            .map { it.violation() }
        Baseline.assertMatches(
            "fragments",
            "No new Fragments: destinations are composables in the app shell (docs/architecture/app-shell.md)",
            violations,
        )
    }

    @Test
    fun `no custom Views or RecyclerView adapters`() {
        val violations = Production.classes
            .filter { declaration -> declaration.parents().any { parent -> VIEW_PARENT.matches(parent.name) && !isMvp(parent.name) } }
            .map { it.violation() }
        Baseline.assertMatches(
            "android-views",
            "No new View subclasses, RecyclerView adapters/view holders or ViewBinders; write a composable",
            violations,
        )
    }

    private fun importRule(rule: String, description: String, forbidden: (String) -> Boolean) {
        val violations = Production.scope.files.flatMap { file ->
            file.importNames.filter(forbidden).map { file.violation("${file.fqn} -> $it") }
        }
        Baseline.assertMatches(rule, description, violations)
    }

    private fun isMvp(name: String) = MVP.matches(name)

    private companion object {
        val CALLBACK_SUFFIXES = listOf("Listener", "Callback", "Callbacks")

        val MVP = Regex("""(\w+\.)*(\w*Presenter|\w*Contract(\.\w+)?)(<.*>)?""")

        val FRAGMENT_PARENT = Regex("""(\w+\.)*\w*Fragment(Compat)?(<.*>)?""")

        val VIEW_PARENT = Regex(
            """(\w+\.)*(View|ViewGroup|\w+Layout|\w*TextView|\w*ImageView|\w*Button|\w*Toolbar|RecyclerView|\w*ViewHolder|\w*ViewBinder|RecyclerView\.Adapter|\w*RecyclerAdapter|ListAdapter|\w*PagerAdapter|FragmentStateAdapter|ArrayAdapter|BaseAdapter|ItemDecoration|\w*Preference)(<.*>)?""",
        )

        /** Fragments that are deliberately kept as hosts. None: the app shell has no Fragment host. */
        val ALLOWED_FRAGMENT_HOSTS = emptySet<String>()
    }
}
