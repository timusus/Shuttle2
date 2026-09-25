import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

/**
 * Fails if this module's release runtime classpath transitively resolves `:android:fixtures`
 * (#402) — fixtures are compileOnly/debug-only for previews and sample screenshots, and R8 does
 * not flag live release code that references them, so misuse only crashes at runtime.
 *
 * Resolving [rootComponent] walks the dependency graph only; it never triggers building or
 * signing a release variant, so this runs in `check` without a release keystore.
 */
abstract class VerifyFixturesNotInReleaseClasspath : DefaultTask() {
    @get:Input
    abstract val modulePath: Property<String>

    @get:Internal
    abstract val rootComponent: Property<ResolvedComponentResult>

    @TaskAction
    fun verify() {
        val chain = findFixtures(rootComponent.get(), mutableSetOf(), emptyList())
        if (chain != null) {
            throw GradleException(
                "${modulePath.get()}'s release runtime classpath resolves :android:fixtures via " +
                    chain.joinToString(" -> ") +
                    ". Fixtures are compileOnly/debug-only (sample-library previews and screenshots); " +
                    "release code must not depend on them."
            )
        }
    }

    private fun findFixtures(
        component: ResolvedComponentResult,
        visited: MutableSet<ResolvedComponentResult>,
        chain: List<String>
    ): List<String>? {
        if (!visited.add(component)) return null
        for (dependency in component.dependencies.filterIsInstance<ResolvedDependencyResult>()) {
            val selected = dependency.selected
            val id = selected.id
            val label = if (id is ProjectComponentIdentifier) id.projectPath else id.displayName
            val nextChain = chain + label
            if (id is ProjectComponentIdentifier && id.projectPath == ":android:fixtures") {
                return nextChain
            }
            val nested = findFixtures(selected, visited, nextChain)
            if (nested != null) return nested
        }
        return null
    }
}
