import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleLayerRulesTest {
    private val layers = mapOf(
        ":core" to ModuleLayer.CORE,
        ":domain" to ModuleLayer.DOMAIN,
        ":playback" to ModuleLayer.DATA,
        ":networking" to ModuleLayer.DATA,
        ":jellyfin" to ModuleLayer.PROVIDER,
        ":emby" to ModuleLayer.PROVIDER,
        ":designsystem" to ModuleLayer.PRESENTATION,
        ":app" to ModuleLayer.COMPOSITION_ROOT,
    )

    private fun check(dependencies: List<DeclaredDependency>, baseline: Set<String> = emptySet(), modules: Set<String> = layers.keys) = ModuleLayerRules.check(modules, layers, ModuleLayers.allowed, dependencies, baseline)

    private fun dep(from: String, to: String, configuration: String = "implementation") = DeclaredDependency(from, to, configuration)

    @Test
    fun allowedEdgesAreClean() {
        val report = check(
            listOf(
                dep(":domain", ":core"),
                dep(":playback", ":domain"),
                dep(":playback", ":networking"),
                dep(":jellyfin", ":networking"),
                dep(":designsystem", ":domain"),
                dep(":app", ":jellyfin"),
                dep(":app", ":designsystem"),
            ),
        )
        assertTrue(report.toString(), report.isClean)
    }

    @Test
    fun dataToProviderIsForbidden() {
        val violations = ModuleLayerRules.violations(layers, ModuleLayers.allowed, listOf(dep(":playback", ":jellyfin")))
        assertEquals(setOf(":playback -> :jellyfin"), violations.keys)
        val message = violations.getValue(":playback -> :jellyfin")
        assertTrue(message, message.startsWith(":playback -> :jellyfin (implementation): data may not depend on provider"))
    }

    @Test
    fun siblingProviderIsForbidden() {
        assertEquals(setOf(":emby -> :jellyfin"), check(listOf(dep(":emby", ":jellyfin"))).newViolations.keys)
    }

    @Test
    fun presentationToDataIsForbidden() {
        assertEquals(setOf(":designsystem -> :playback"), check(listOf(dep(":designsystem", ":playback"))).newViolations.keys)
    }

    @Test
    fun coreDependsOnNothingOfOurs() {
        val message = check(listOf(dep(":core", ":domain"))).newViolations.getValue(":core -> :domain")
        assertTrue(message, message.endsWith("core may depend on nothing of ours"))
    }

    @Test
    fun oneEdgeDeclaredOnSeveralConfigurationsIsOneViolation() {
        val violations = ModuleLayerRules.violations(
            layers,
            ModuleLayers.allowed,
            listOf(dep(":playback", ":emby", "releaseCompileOnly"), dep(":playback", ":emby", "debugImplementation")),
        )
        assertEquals(1, violations.size)
        assertTrue(violations.values.single().contains("(debugImplementation, releaseCompileOnly)"))
    }

    @Test
    fun baselinedViolationPasses() {
        assertTrue(check(listOf(dep(":playback", ":jellyfin")), baseline = setOf(":playback -> :jellyfin")).isClean)
    }

    @Test
    fun violationMissingFromBaselineFails() {
        val report = check(
            listOf(dep(":playback", ":jellyfin"), dep(":playback", ":emby")),
            baseline = setOf(":playback -> :jellyfin"),
        )
        assertEquals(setOf(":playback -> :emby"), report.newViolations.keys)
        assertTrue(report.staleBaseline.isEmpty())
    }

    @Test
    fun staleBaselineEntryFails() {
        val report = check(listOf(dep(":playback", ":domain")), baseline = setOf(":playback -> :jellyfin"))
        assertFalse(report.isClean)
        assertEquals(setOf(":playback -> :jellyfin"), report.staleBaseline)
        assertTrue(report.newViolations.isEmpty())
    }

    @Test
    fun allowedEdgeInBaselineIsStale() {
        val report = check(listOf(dep(":playback", ":networking")), baseline = setOf(":playback -> :networking"))
        assertEquals(setOf(":playback -> :networking"), report.staleBaseline)
    }

    @Test
    fun moduleWithoutLayerFails() {
        val report = check(listOf(dep(":playback", ":newmodule")), modules = layers.keys + ":orphan")
        assertEquals(setOf(":newmodule", ":orphan"), report.unassigned)
        assertFalse(report.isClean)
    }

    @Test
    fun productionBuckets() {
        listOf("api", "implementation", "compileOnly", "runtimeOnly", "debugImplementation", "releaseCompileOnly", "compileOnlyApi")
            .forEach { assertTrue(it, ModuleLayers.isProductionBucket(it)) }
        listOf("testImplementation", "androidTestImplementation", "testDebugImplementation", "ksp", "kspDebug", "lintChecks", "coreLibraryDesugaring", "debugRuntimeClasspath")
            .forEach { assertFalse(it, ModuleLayers.isProductionBucket(it)) }
    }

    @Test
    fun baselineParsingSkipsCommentsAndBlanks() {
        assertEquals(setOf(":a -> :b"), ModuleLayerRules.parseBaseline(listOf("# header", "", "  :a -> :b  ")))
    }

    @Test
    fun everyLayerHasRules() {
        assertEquals(ModuleLayer.entries.toSet(), ModuleLayers.allowed.keys)
    }
}
