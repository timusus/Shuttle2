package com.simplecityapps.shuttle.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.CreationExtras
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraph
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.reflect.KClass
import kotlin.test.Test

/**
 * The contribution pattern every shared ViewModel follows, run on the JVM and on iOS: a commonMain ViewModel
 * contributes itself to the graph's scope, and both ways a platform reaches it work — Android's [AppViewModelFactory]
 * (through [ViewModelGraph.metroViewModelFactory]) and the iOS graph's typed properties.
 */
class ViewModelContributionTest {
    private val graph = createGraph<TestAppGraph>()

    @Test
    fun factoryCreatesAKeyedViewModel() {
        graph.metroViewModelFactory.create(PlainViewModel::class, CreationExtras.Empty).shouldBeInstanceOf<PlainViewModel>()
    }

    @Test
    fun factoryCreatesAnAssistedViewModel() {
        graph.metroViewModelFactory.createManuallyAssistedFactory(DetailViewModel.Factory::class)().create(7).id shouldBe 7
    }

    @Test
    fun graphPropertiesReachTheSameViewModels() {
        graph.plainViewModel.shouldBeInstanceOf<PlainViewModel>()
        graph.detailViewModelFactory.create(3).id shouldBe 3
    }
}

/**
 * Stands in for the app graphs: Android's `AppGraph` is a [ViewModelGraph]; the iOS graph adds the properties. Scoped
 * to [ContributionTestScope] rather than `AppScope`, which would pull in every real ViewModel and its dependencies;
 * :shared's `IosAppGraphTest` covers the real `AppScope` graph on iOS.
 */
@DependencyGraph(ContributionTestScope::class)
interface TestAppGraph : ViewModelGraph {
    val plainViewModel: PlainViewModel
    val detailViewModelFactory: DetailViewModel.Factory

    /** [AppViewModelFactory] binds itself in `AppScope`; this graph builds the same factory over its own maps. */
    @Provides
    fun viewModelFactory(
        viewModelProviders: Map<KClass<out ViewModel>, () -> ViewModel>,
        assistedFactoryProviders: Map<KClass<out ViewModel>, () -> ViewModelAssistedFactory>,
        manualAssistedFactoryProviders: Map<KClass<out ManualViewModelAssistedFactory>, () -> ManualViewModelAssistedFactory>,
    ): MetroViewModelFactory = AppViewModelFactory(viewModelProviders, assistedFactoryProviders, manualAssistedFactoryProviders)
}

/** The test graph's scope, standing in for `AppScope`. */
abstract class ContributionTestScope private constructor()

@ViewModelKey(PlainViewModel::class)
@ContributesIntoMap(ContributionTestScope::class)
@Inject
class PlainViewModel : ViewModel()

class DetailViewModel @AssistedInject constructor(
    @Assisted val id: Long,
) : ViewModel() {
    @AssistedFactory
    @ManualViewModelAssistedFactoryKey(Factory::class)
    @ContributesIntoMap(ContributionTestScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(id: Long): DetailViewModel
    }
}
