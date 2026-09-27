package com.simplecityapps.shuttle.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.CreationExtras
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.createGraph
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.test.Test

/**
 * The contribution pattern every shared ViewModel follows, run on the JVM and on iOS: a commonMain ViewModel
 * contributes itself to `AppScope`, and both ways a platform reaches it work — Android's [AppViewModelFactory]
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

/** Stands in for the app graphs: Android's `AppGraph` is a [ViewModelGraph]; the iOS graph adds the properties. */
@DependencyGraph(AppScope::class)
interface TestAppGraph : ViewModelGraph {
    val plainViewModel: PlainViewModel
    val detailViewModelFactory: DetailViewModel.Factory
}

@ViewModelKey(PlainViewModel::class)
@ContributesIntoMap(AppScope::class)
@Inject
class PlainViewModel : ViewModel()

class DetailViewModel @AssistedInject constructor(
    @Assisted val id: Long,
) : ViewModel() {
    @AssistedFactory
    @ManualViewModelAssistedFactoryKey(Factory::class)
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(id: Long): DetailViewModel
    }
}
