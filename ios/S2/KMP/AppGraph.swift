import Shared

/// Central access point for the Kotlin dependency graph (`IosAppGraph` in :shared's iosMain).
///
/// Built on first access. Once the graph needs platform objects (the audio player, preferences,
/// the keychain) this becomes an explicit `initialize()` at launch, as in Shuttle Podcasts.
enum AppGraph {
    static let shared = IosAppGraph()
}
