package com.simplecityapps.architecture

import org.junit.Test

/** Naming and package conventions the codebase already follows almost everywhere. */
class ConventionRules {

    @Test
    fun `production code lives under com_simplecityapps`() {
        val violations = Production.scope.files
            .filter { file -> file.packagee?.name?.startsWith("com.simplecityapps") != true }
            .map { it.violation("${it.fqn}: package ${it.packagee?.name ?: "<none>"}") }
        Baseline.assertMatches("package-root", "Production code lives under the com.simplecityapps package", violations)
    }

    @Test
    fun `Android components and Hilt and Room declarations carry their conventional suffix`() {
        val bySuperclass = Production.classes.flatMap { declaration ->
            SUPERCLASS_SUFFIXES
                .filter { (parents, _) -> declaration.parents().any { it.name.substringBefore('<') in parents } }
                .filterNot { (_, suffix) -> declaration.name.endsWith(suffix) }
                .map { (_, suffix) -> declaration.violation(": not named *$suffix") }
        }
        val byAnnotation = Production.scope.classesAndInterfacesAndObjects(includeNested = true, includeLocal = false).flatMap { declaration ->
            ANNOTATION_SUFFIXES
                .filter { (annotation, _) -> declaration.annotations.any { it.name == annotation } }
                .filterNot { (_, suffix) -> declaration.name.endsWith(suffix) }
                .map { (annotation, suffix) ->
                    declaration.containingFile.violation("${declaration.fullyQualifiedName ?: declaration.name}: @$annotation not named *$suffix")
                }
        }
        val repositoryImplementations = Production.classes
            .filter { declaration -> declaration.parents().any { it.name.endsWith("Repository") } }
            .filterNot { it.name.endsWith("Repository") }
            .map { it.violation(": implements a *Repository but not named *Repository") }
        Baseline.assertMatches(
            "naming-conventions",
            "Subclasses and annotated declarations carry their conventional suffix (*ViewModel, *Worker, *Service, *Module, *Dao, ...)",
            bySuperclass + byAnnotation + repositoryImplementations,
        )
    }

    private companion object {
        val SUPERCLASS_SUFFIXES = listOf(
            setOf("ViewModel", "AndroidViewModel") to "ViewModel",
            setOf("Worker", "CoroutineWorker", "ListenableWorker") to "Worker",
            setOf("Service", "LifecycleService", "MediaLibraryService", "MediaSessionService", "MediaBrowserServiceCompat") to "Service",
            setOf("Activity", "ComponentActivity", "AppCompatActivity", "FragmentActivity") to "Activity",
            setOf("BroadcastReceiver") to "Receiver",
            setOf("RoomDatabase") to "Database",
        )

        val ANNOTATION_SUFFIXES = listOf(
            "Module" to "Module",
            "Dao" to "Dao",
            "Database" to "Database",
            "HiltViewModel" to "ViewModel",
            "HiltWorker" to "Worker",
        )
    }
}
