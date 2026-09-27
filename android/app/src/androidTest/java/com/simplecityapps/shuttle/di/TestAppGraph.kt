package com.simplecityapps.shuttle.di

import android.app.Application
import com.simplecityapps.shuttle.smoke.SmokeTestSuite
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metrox.viewmodel.ViewModelGraph

/**
 * The instrumented tests' graph: the app's contributions, with the `Test*Module` containers in this source set
 * replacing the modules they name.
 */
@DependencyGraph(AppScope::class)
interface TestAppGraph : ViewModelGraph {
    fun inject(test: SmokeTestSuite)

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides application: Application
        ): TestAppGraph
    }
}
