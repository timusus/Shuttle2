package com.simplecityapps.shuttle.di

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactory
import kotlin.reflect.KClass

/**
 * Creates every ViewModel from the graph's multibindings: `@ViewModelKey` for plain ones,
 * `@ViewModelAssistedFactoryKey` for those built from [androidx.lifecycle.viewmodel.CreationExtras] (a
 * `SavedStateHandle`), and `@ManualViewModelAssistedFactoryKey` for those given arguments at the call site.
 *
 * In commonMain so a ViewModel contributes once, beside its class, whichever platform builds it: Android's
 * `AppGraph` hands this factory to `metroViewModel()`; the iOS graph (phase 5) exposes each ViewModel, or its
 * assisted `Factory`, as a property Swift reads. See docs/architecture/ios-port/phase-4-viewmodels.md, "Wave 0".
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class AppViewModelFactory
@Inject
constructor(
    override val viewModelProviders: Map<KClass<out ViewModel>, () -> ViewModel>,
    override val assistedFactoryProviders: Map<KClass<out ViewModel>, () -> ViewModelAssistedFactory>,
    override val manualAssistedFactoryProviders: Map<KClass<out ManualViewModelAssistedFactory>, () -> ManualViewModelAssistedFactory>
) : MetroViewModelFactory()
