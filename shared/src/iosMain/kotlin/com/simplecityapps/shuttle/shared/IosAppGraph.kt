package com.simplecityapps.shuttle.shared

import androidx.lifecycle.SavedStateHandle
import com.simplecityapps.shuttle.shared.local.IosLocalFiles
import com.simplecityapps.shuttle.shared.playback.IosAudioPlayer
import com.simplecityapps.shuttle.shared.telemetry.IosTelemetry
import com.simplecityapps.shuttle.ui.screens.tageditor.TagEditorViewModel
import com.simplecityapps.shuttle.ui.shell.player.PlayerViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import dev.zacsweers.metrox.viewmodel.ViewModelGraph

/**
 * The iOS app's dependency graph, the twin of Android's `AppGraph`:
 * every `AppScope` contribution the shared modules make, merged from their klibs, plus :shared's iOS containers
 * (`di/`: persistence, networking, playback, the platform seams). Swift builds it once at launch with
 * [createIosAppGraph], handing over what only Swift can make, and reaches a ViewModel through its typed property: a
 * new instance per read, so Swift keeps each in `ViewModelCache`. A ViewModel with arguments comes from its assisted
 * factory's property instead (`albumDetailViewModelFactory.create(groupKey:)`), a new instance per `create`.
 * [metroViewModelFactory] is there too, but Swift can't build its `KClass` keys.
 *
 * What Swift reads is declared per feature in the `Ios*Accessors` interfaces, which the graph extends explicitly so
 * every accessor stays a property of this type: add a feature's accessor to its group, not here.
 *
 * `SettingsViewModel` reads `IosSettingsCatalog` (only the rows iOS acts on) through `IosSettingsEffects`.
 *
 * `EqualizerViewModel` drives `IosEqualizer`, which designs the S2Playback engine's filters.
 *
 * Shared ViewModels are excluded until iOS binds what they need:
 * `TagEditorViewModel` needs a `TagFileAccess`, a tag writer iOS doesn't have: it reads local files' tags with FFmpeg,
 * which doesn't write them in place. Until then nothing offers tag editing on iOS.
 */
@DependencyGraph(
    AppScope::class,
    excludes = [TagEditorViewModel.Factory::class],
)
interface IosAppGraph :
    ViewModelGraph,
    IosPlaybackAccessors,
    IosLibraryAccessors,
    IosCopyAccessors,
    IosServerAccessors,
    IosStoreAccessors,
    IosViewModelAccessors {
    @DependencyGraph.Factory
    fun interface Factory {
        /**
         * [audioPlayer]: the Swift adapter over the S2Playback engine; [storage]: where preferences and the library live;
         * [localFiles]: this device's music files, Swift's `LocalLibrary`; [telemetry]: Sentry and PostHog.
         */
        fun create(
            @Provides audioPlayer: IosAudioPlayer,
            @Provides storage: IosStorage,
            @Provides localFiles: IosLocalFiles,
            @Provides telemetry: IosTelemetry,
            @Provides build: IosBuild
        ): IosAppGraph
    }
}

/** Builds the graph: `IosAppGraphKt.createIosAppGraph(audioPlayer:localFiles:telemetry:isDebug:)` from Swift, once per process. */
fun createIosAppGraph(
    audioPlayer: IosAudioPlayer,
    localFiles: IosLocalFiles,
    telemetry: IosTelemetry,
    isDebug: Boolean
): IosAppGraph = createGraphFactory<IosAppGraph.Factory>().create(audioPlayer, IosStorage(null), localFiles, telemetry, IosBuild(isDebug))

/**
 * A graph with storage of its own, for tests: preferences in the NSUserDefaults suite [isolatedStorage] rather than the
 * standard defaults, and an empty in-memory library rather than the app's database. Tests build several graphs at
 * once, which would otherwise restore each other's saved queue and modes, and query whatever library the simulator's
 * app holds. [localFiles] is none unless a test hands it some; telemetry is always none.
 */
fun createIosAppGraph(
    audioPlayer: IosAudioPlayer,
    isolatedStorage: String,
    localFiles: IosLocalFiles = IosLocalFiles.None
): IosAppGraph = createGraphFactory<IosAppGraph.Factory>().create(audioPlayer, IosStorage(isolatedStorage), localFiles, IosTelemetry.None, IosBuild(isDebug = true))

/**
 * The app's build type, from Swift's `#if DEBUG`: the linked framework is always Release on a device, so
 * `Platform.isDebugBinary` can't say whether the app is a debug build.
 */
class IosBuild(
    val isDebug: Boolean
)

/**
 * Where the graph keeps its preferences and library. [isolatedName] null: the standard defaults and the app's
 * database. Otherwise preferences in the NSUserDefaults suite of that name, and the library in memory.
 */
class IosStorage(
    val isolatedName: String?
)

/**
 * The player's ViewModel, with a fresh `SavedStateHandle`: iOS has no saved state to restore it from, so its open Now
 * Playing panel starts closed each launch. From Swift, `IosAppGraphKt.createPlayerViewModel(graph)`.
 */
fun IosAppGraph.createPlayerViewModel(): PlayerViewModel = playerViewModelFactory.create(SavedStateHandle())
