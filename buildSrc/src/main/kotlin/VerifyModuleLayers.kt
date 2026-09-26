import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Fails on any project dependency the layer rules forbid (#443, docs/architecture/layering.md),
 * with a ratchet over today's violations in [baselineFile]: a forbidden edge missing from the
 * baseline fails, and so does a baseline edge that no longer occurs, so the baseline only shrinks.
 *
 * The layer table and rules live in [ModuleLayers]; the root build script collects every module's
 * declared production project dependencies into [declaredDependencies] at configuration time, so
 * the action only sees strings and stays configuration-cache safe.
 */
abstract class VerifyModuleLayers : DefaultTask() {
    /** Every module in the build, so a module missing from the layer table fails even with no edges. */
    @get:Input
    abstract val modules: SetProperty<String>

    /** One `from\tto\tconfiguration` entry per declared production project dependency. */
    @get:Input
    abstract val declaredDependencies: ListProperty<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val baselineFile: RegularFileProperty

    /** Rewrites [baselineFile] from the current graph instead of verifying (`-PupdateArchitectureBaselines`). */
    @get:Input
    abstract val updateBaseline: Property<Boolean>

    @TaskAction
    fun verify() {
        val dependencies = declaredDependencies.get().map { entry ->
            val (from, to, configuration) = entry.split('\t')
            DeclaredDependency(from, to, configuration)
        }
        val file = baselineFile.get().asFile

        if (updateBaseline.get()) {
            val edges = ModuleLayerRules.violations(ModuleLayers.table, ModuleLayers.allowed, dependencies).keys
            val header = file.readLines().takeWhile { it.startsWith("#") }
            file.writeText((header + edges).joinToString("\n", postfix = "\n"))
            return
        }

        val report = ModuleLayerRules.check(
            modules = modules.get(),
            layers = ModuleLayers.table,
            allowed = ModuleLayers.allowed,
            dependencies = dependencies,
            baseline = ModuleLayerRules.parseBaseline(file.readLines()),
        )
        if (report.isClean) return

        throw GradleException(
            buildString {
                appendLine("Module layer check failed (#443, see docs/architecture/layering.md).")
                if (report.unassigned.isNotEmpty()) {
                    appendLine()
                    appendLine("${report.unassigned.size} modules have no layer. Add them to ModuleLayers.table in buildSrc:")
                    report.unassigned.forEach { appendLine("  $it") }
                }
                if (report.newViolations.isNotEmpty()) {
                    appendLine()
                    appendLine("${report.newViolations.size} forbidden dependencies. Remove them rather than adding them to ${file.name}:")
                    report.newViolations.values.forEach { appendLine("  $it") }
                }
                if (report.staleBaseline.isNotEmpty()) {
                    appendLine()
                    appendLine("${report.staleBaseline.size} baseline edges no longer occur. Delete them from ${file.name}:")
                    report.staleBaseline.forEach { appendLine("  $it") }
                }
            },
        )
    }
}
