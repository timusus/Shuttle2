package com.simplecityapps.shuttle.shared

import com.simplecityapps.shuttle.persistence.KeyValueStore
import com.simplecityapps.shuttle.platform.AppVersion
import com.simplecityapps.shuttle.platform.BundledText
import com.simplecityapps.shuttle.ui.screens.library.LibraryEmptyViewModel
import com.simplecityapps.shuttle.ui.screens.settings.excluded.ExcludedSongsViewModel
import com.simplecityapps.shuttle.ui.shell.ShellViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metrox.viewmodel.ViewModelGraph

/**
 * The iOS Metro graph over the shared modules' `AppScope` contributions (#586): the ViewModels
 * :android:presentation contributes to the ViewModel maps, its [AppViewModelFactory][com.simplecityapps.shuttle.di.AppViewModelFactory]
 * binding and core's coroutine bindings, merged here from their klibs as Android's `AppGraph` merges them. Swift
 * reaches a ViewModel through a typed property ([shellViewModel]) or [metroViewModelFactory].
 *
 * Swift passes the platform objects to [Factory.create]. The ViewModels whose dependencies iOS can't provide yet
 * (the repositories and media sources the later waves bring) are excluded until it can.
 * [IosAppGraph] becomes this graph once the Swift side creates it.
 */
@DependencyGraph(
    AppScope::class,
    excludes = [ExcludedSongsViewModel::class, LibraryEmptyViewModel::class],
)
interface SharedAppGraph : ViewModelGraph {
    val shellViewModel: ShellViewModel

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides keyValueStore: KeyValueStore,
            @Provides bundledText: BundledText,
            @Provides appVersion: AppVersion,
        ): SharedAppGraph
    }
}
