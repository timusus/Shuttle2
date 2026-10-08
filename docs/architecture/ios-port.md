# iOS port

Epic: #581 (label `ios-port`). Phases 0–8 shipped; phase 9 (StoreKit 2, AirPlay, CarPlay, WidgetKit, Cast) is per-feature.

S2 gets an iOS app that shares everything from the ViewModel layer down with Android. Only the
presentation layer (Compose on Android, SwiftUI on iOS) and genuinely platform-bound services
(playback engine, local library scanning, billing, Cast/AirPlay, widgets) are per-platform.

The template is Shuttle Podcasts (`~/projects/simplecity-apps/podcasts/main/mobile`); this repo's
`.claude/rules/ios.md` covers the `shared/` KMP module and the iOS app that links it.

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

[Parity checklist](ios-port/parity.md): every user-facing Android feature, weighted by iOS effort,
with a computed completion percentage.
