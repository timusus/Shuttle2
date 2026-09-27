import Foundation
import S2Playback
import Shared

/// Central access point for the Kotlin dependency graph (`IosAppGraph` in :shared's iosMain) and the
/// platform objects it runs on, built once by `initialize()` at launch (as in Shuttle Podcasts).
enum AppGraph {
    private static var _dependencies: IosAppDependencies?

    /// Everything the app built at launch. Crashes if read before `initialize()`.
    static var dependencies: IosAppDependencies {
        guard let dependencies = _dependencies else {
            fatalError("AppGraph.initialize() must be called before AppGraph is used")
        }
        return dependencies
    }

    /// The Kotlin graph.
    static var shared: IosAppGraph {
        dependencies.graph
    }

    /// Builds the graph and starts the playback system. Call once, from `S2App.init`.
    @MainActor
    static func initialize() {
        guard _dependencies == nil else { return }
        let dependencies = IosAppDependencies()
        dependencies.playbackSystem.start()
        _dependencies = dependencies
    }
}

/// The app's composition point: each object built once, with plain constructors.
@MainActor
final class IosAppDependencies {
    /// The Kotlin `IosAudioPlayer` over the S2Playback engine.
    let audioPlayer: EngineAudioPlayer
    let graph: IosAppGraph
    let audioSession: AudioSessionController
    let nowPlaying: NowPlayingController
    let playbackSystem: PlaybackSystemCoordinator

    init() {
        audioPlayer = EngineAudioPlayer(engine: Self.makeEngine())
        graph = IosAppGraph(audioPlayer: audioPlayer)
        audioSession = AudioSessionController()
        nowPlaying = NowPlayingController()
        playbackSystem = PlaybackSystemCoordinator(
            playback: graph.playerController,
            player: audioPlayer,
            session: audioSession,
            nowPlaying: nowPlaying,
            makeEngine: { try? MusicPlaybackController() }
        )
    }

    /// The engine only fails to build without a stereo float format, which every device has.
    private static func makeEngine() -> AudioEngine {
        do {
            return try MusicPlaybackController()
        } catch {
            fatalError("S2: the playback engine could not be built: \(error)")
        }
    }
}
