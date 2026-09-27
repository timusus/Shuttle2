package com.simplecityapps.shuttle.di

import android.app.Application
import com.simplecityapps.shuttle.ShuttleApplication
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metrox.viewmodel.ViewModelGraph

/**
 * The app's dependency graph, created once by [ShuttleApplication]. Every module contributes its bindings with
 * `@ContributesTo(AppScope::class)`; Android entry points (activities, services, receivers) inject through
 * contributed `Injector` interfaces (see [appGraph]). ViewModels come from its [ViewModelGraph.metroViewModelFactory].
 */
@DependencyGraph(AppScope::class)
interface AppGraph : ViewModelGraph {
    fun inject(application: ShuttleApplication)

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides application: Application
        ): AppGraph
    }
}
