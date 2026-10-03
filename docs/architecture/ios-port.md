# iOS port

Epic: #581 (phases #582–#591, label `ios-port`).

S2 gets an iOS app that shares everything from the ViewModel layer down with Android. Only the
presentation layer (Compose on Android, SwiftUI on iOS) and genuinely platform-bound services
(playback engine, local library scanning, billing, Cast/AirPlay, widgets) are per-platform.

The template is Shuttle Podcasts (`~/projects/simplecity-apps/podcasts/main/mobile`): see its
`.claude/rules/shared-kmp.md` and `.claude/rules/ios.md` (the former says SQLDelight; it's Room KMP now).

## Decisions

- **Shared ViewModels**: `androidx.lifecycle` KMP `ViewModel` + `viewModelScope`, state as `StateFlow`.
  `SavedStateHandle` is KMP (lifecycle 2.9+), so PlayerViewModel/FolderListViewModel are not special.
- **DI: Metro everywhere.** Android moves off Hilt entirely (one graph, no dual DI). Assisted
  ViewModels use Metro assisted factories; iOS gets an `IosAppGraph` with a `Factory.create(...)` that
  Swift calls with platform objects.
- **Swift bridge: SKIE** (Flows → `Observing`/async). Swift→Kotlin flows via small writable-flow
  helpers, as in Podcasts (`WritableFlow`, `CombineFlowBridge.swift`, dedupe Equatable publishers).
- **Persistence**: Room KMP (already 2.8.x), `BundledSQLiteDriver` on iOS. The 26 migrations stay.
- **Network**: Ktor + kotlinx.serialization replace Retrofit/OkHttp/Moshi.
- **Collation**: small expect/actual comparator (`java.text.Collator` / `NSString.localizedStandardCompare`).
- **Framework**: an umbrella KMP module producing a static `Shared.framework`, linked from an XcodeGen
  project at `ios/project.yml`. Rebuild the framework before every Xcode build.
- **Playback on iOS**: queue/shuffle/repeat policy in Kotlin (`IosPlayerController` implementing
  `PlaybackOperations`/`QueueOperations`); Swift implements a thin Kotlin `IosAudioPlayer` interface over
  AVQueuePlayer/AVAudioEngine. `MPNowPlayingInfoCenter`, remote commands, background audio in Swift.
- **iOS layout**: `LayoutTier` (compact/regular/wide) from geometry; `TabView` on compact, sidebar
  `TabView`/`NavigationSplitView` above; mini player as a `safeAreaInset`. Navigation and screens mirror
  Android's shell (Home/Library/Search, player sheet or pane) but follow the HIG where they differ.
- **iOS MVP source**: Jellyfin/Emby/Plex (all-shared Kotlin once on Ktor). Local files on iOS
  (Documents + Files-app folders as security-scoped bookmarks, metadata read with FFmpeg) followed in phase 8.
- Existing modules convert in place (no parallel copies); paths keep their `android/` prefix for now.

## Phases

Each phase ends at a checkpoint where builds, emulator/simulator runs and Maestro are batched. Between
checkpoints, workers compile only the module they touch (`compileKotlinIosSimulatorArm64`,
`compileDebugKotlinAndroid`) and write commonTest unit tests alongside.

| # | Phase | Checkpoint |
|---|---|---|
| 0 | Toolchain spike (SKIE vs Kotlin 2.4.20), KMP convention plugin + catalog, `:android:domain` → KMP | domain iOS compile, domain tests, assembleDebug |
| 1 | Hilt → Metro across Android | assembleDebug, unit tests, one Maestro smoke |
| 2 | Data: Room KMP + repositories to commonMain, prefs behind shared interfaces, shared logger; MediaStore/SAF stay androidMain | Room migration tests, assembleDebug |
| 3 | Networking + `server` + Jellyfin/Emby/Plex → Ktor/kotlinx.serialization | MockEngine tests, Maestro sign-in + sync |
| 4 | ViewModels → shared `presentation` module (easy 18 → assisted 9 → Player/Folder; Paywall behind `Entitlements`) | **Android parity gate**: full verify + Maestro batch |
| 5 | iOS skeleton: `Shared.framework`, XcodeGen project, graph + ViewModel cache, adaptive shell, Library + detail screens | first simulator build, ViewInspector |
| 6 | iOS playback: IosPlayerController + Swift audio player, now-playing, mini player, Now Playing, queue | simulator run, Maestro iOS flow |
| 7 | Remaining iOS screens: Home, Search, Settings, Sources/sign-in, playlists, Song info, sleep timer | ViewInspector + Maestro batch |
| 8 | iOS local library (Files folders, metadata) — done, #590 | device check |
| 9 | iOS platform features: StoreKit 2, AirPlay, CarPlay, WidgetKit, Cast | per feature |

Phase designs: [2 data](ios-port/phase-2-data.md), [3 network](ios-port/phase-3-network.md),
[4 ViewModels](ios-port/phase-4-viewmodels.md) and its [platform seams](ios-port/phase-4-platform-seams.md),
[5 iOS app](ios-port/phase-5-ios-app.md), [6 playback](ios-port/phase-6-playback.md).

[Parity checklist](ios-port/parity.md): every user-facing Android feature, weighted by iOS effort,
with a computed completion percentage.

During phases 0–4, other sessions don't start new work in `android/app`, `android/domain` or DI modules.
