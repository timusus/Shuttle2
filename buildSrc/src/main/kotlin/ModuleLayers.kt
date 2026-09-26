/**
 * The module layer rules (#443, docs/architecture/layering.md) as pure data and functions, so the
 * rule engine is unit-tested without Gradle. [VerifyModuleLayers] feeds it the declared project
 * dependencies of every module.
 */
enum class ModuleLayer(val label: String) {
    /** Logging, dispatchers, settings storage, Hilt qualifiers. Usable by every layer; depends on nothing of ours. */
    CORE("core"),

    /** Models, repository and service interfaces, use cases. */
    DOMAIN("domain"),

    /** Data and platform services that implement domain interfaces. */
    DATA("data"),

    /** Media provider implementations. Data, but only the composition root may depend on one. */
    PROVIDER("provider"),

    /** Compose components and screens. Sees domain types only, never data. */
    PRESENTATION("presentation"),

    /** `:android:app`: the only module allowed to see everything, to aggregate the Hilt modules. */
    COMPOSITION_ROOT("composition root"),

    /** Test/debug-only fixtures (#402 keeps them off release classpaths). */
    FIXTURES("fixtures"),

    /** Build tooling that parses other modules' sources; nothing depends on it. */
    TOOLING("tooling"),
}

object ModuleLayers {
    /** Every module's layer. A module missing from this table fails [VerifyModuleLayers]. */
    val table: Map<String, ModuleLayer> = mapOf(
        ":android:core" to ModuleLayer.CORE,
        ":android:domain" to ModuleLayer.DOMAIN,
        ":android:mediaprovider:core" to ModuleLayer.DATA,
        ":android:downloads" to ModuleLayer.DATA,
        ":android:imageloader" to ModuleLayer.DATA,
        ":android:networking" to ModuleLayer.DATA,
        ":android:playback" to ModuleLayer.DATA,
        ":android:remote-config" to ModuleLayer.DATA,
        ":android:saf" to ModuleLayer.DATA,
        ":android:trial" to ModuleLayer.DATA,
        ":android:mediaprovider:local" to ModuleLayer.PROVIDER,
        ":android:mediaprovider:jellyfin" to ModuleLayer.PROVIDER,
        ":android:mediaprovider:emby" to ModuleLayer.PROVIDER,
        ":android:mediaprovider:plex" to ModuleLayer.PROVIDER,
        ":android:designsystem" to ModuleLayer.PRESENTATION,
        ":android:app" to ModuleLayer.COMPOSITION_ROOT,
        ":android:fixtures" to ModuleLayer.FIXTURES,
        ":android:architecture-tests" to ModuleLayer.TOOLING,
    )

    /**
     * The layers each layer may depend on (the "Allowed dependency directions" table in layering.md).
     * A data module may use another data module (`mediaprovider:core`, `networking`, `imageloader`,
     * `saf`), never a provider implementation, so no layer but the composition root reaches [ModuleLayer.PROVIDER].
     */
    val allowed: Map<ModuleLayer, Set<ModuleLayer>> = mapOf(
        ModuleLayer.CORE to emptySet(),
        ModuleLayer.DOMAIN to setOf(ModuleLayer.CORE),
        ModuleLayer.DATA to setOf(ModuleLayer.CORE, ModuleLayer.DOMAIN, ModuleLayer.DATA),
        ModuleLayer.PROVIDER to setOf(ModuleLayer.CORE, ModuleLayer.DOMAIN, ModuleLayer.DATA),
        ModuleLayer.PRESENTATION to setOf(ModuleLayer.CORE, ModuleLayer.DOMAIN, ModuleLayer.PRESENTATION, ModuleLayer.FIXTURES),
        ModuleLayer.COMPOSITION_ROOT to ModuleLayer.entries.toSet() - ModuleLayer.TOOLING,
        ModuleLayer.FIXTURES to setOf(ModuleLayer.CORE, ModuleLayer.DOMAIN),
        ModuleLayer.TOOLING to emptySet(),
    )

    private val productionBuckets = listOf("api", "implementation", "compileonly", "runtimeonly")

    /**
     * Whether a configuration declares production dependencies: `api`, `implementation`, `compileOnly`,
     * `runtimeOnly` and their variant forms (`debugImplementation`, `releaseCompileOnly`), but not
     * `test*`/`androidTest*` ones, nor KSP, lint or desugaring configurations.
     */
    fun isProductionBucket(configurationName: String): Boolean {
        val name = configurationName.lowercase()
        return "test" !in name && productionBuckets.any { name.endsWith(it) }
    }
}

/** A project dependency [from] one module [to] another, declared on [configuration]. */
data class DeclaredDependency(val from: String, val to: String, val configuration: String) {
    /** The baseline line: `:from -> :to`. */
    val edge: String get() = "$from -> $to"
}

/** What [ModuleLayerRules.check] found; [isClean] when there is nothing to report. */
data class ModuleLayerReport(
    /** Modules with no entry in the layer table. */
    val unassigned: Set<String>,
    /** Forbidden edges not in the baseline, each mapped to its explanation. */
    val newViolations: Map<String, String>,
    /** Baseline edges that are no longer forbidden-and-declared. */
    val staleBaseline: Set<String>,
) {
    val isClean: Boolean get() = unassigned.isEmpty() && newViolations.isEmpty() && staleBaseline.isEmpty()
}

object ModuleLayerRules {
    /** Every forbidden edge among [dependencies], mapped to a one-line explanation naming the layers and configurations. */
    fun violations(
        layers: Map<String, ModuleLayer>,
        allowed: Map<ModuleLayer, Set<ModuleLayer>>,
        dependencies: Collection<DeclaredDependency>,
    ): Map<String, String> = dependencies
        .groupBy { it.edge }
        .toSortedMap()
        .mapNotNull { (edge, declarations) ->
            val first = declarations.first()
            val fromLayer = layers[first.from] ?: return@mapNotNull null
            val toLayer = layers[first.to] ?: return@mapNotNull null
            val permitted = allowed[fromLayer].orEmpty()
            if (toLayer in permitted) return@mapNotNull null
            val configurations = declarations.map { it.configuration }.distinct().sorted().joinToString(", ")
            val may = if (permitted.isEmpty()) "nothing of ours" else permitted.joinToString(", ") { it.label }
            edge to "$edge ($configurations): ${fromLayer.label} may not depend on ${toLayer.label}; ${fromLayer.label} may depend on $may"
        }
        .toMap()

    /**
     * The ratchet: violations missing from [baseline] are new, baseline entries that no longer violate are
     * stale, and both fail, so the baseline only shrinks. [modules] is every module in the build.
     */
    fun check(
        modules: Set<String>,
        layers: Map<String, ModuleLayer>,
        allowed: Map<ModuleLayer, Set<ModuleLayer>>,
        dependencies: Collection<DeclaredDependency>,
        baseline: Set<String>,
    ): ModuleLayerReport {
        val unassigned = (modules + dependencies.flatMap { listOf(it.from, it.to) }).filterNot { it in layers }.toSortedSet()
        val found = violations(layers, allowed, dependencies)
        return ModuleLayerReport(
            unassigned = unassigned,
            newViolations = found.filterKeys { it !in baseline },
            staleBaseline = (baseline - found.keys).toSortedSet(),
        )
    }

    /** Baseline file lines to edges: trimmed, blank lines and `#` comments dropped. */
    fun parseBaseline(lines: List<String>): Set<String> = lines.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toSet()
}
