import AVFAudio
import CarPlay
import Intents
import Shared
import UIKit

/// Siri's media domain (#951): "Hey Siri, play <artist, album, song, playlist or genre> on Shuttle Music", on the phone
/// and in CarPlay, and a bare "play music", which Siri may route here without naming the app once Shuttle is the
/// listener's preferred audio app. The app handles it in its own process (`AppDelegate.application(_:handlerFor:)`):
/// resolution searches the library through `SiriMediaResolver`, and `handle` answers `.handleInApp`, which has iOS call
/// `application(_:handle:completionHandler:)`, where `SiriMediaPlayer` starts playback with the app awake in the
/// background. No Intents extension: it would need the library, which only the app process opens.

/// Where a Siri request stands with CarPlay's Pro gate (#946).
@MainActor
enum CarPlayConnection {
    /// Set by `CarPlaySceneDelegate` for the life of a car connection.
    static var isConnected = false

    /// Whether a request is made through CarPlay. The scene delegate's flag is late when Siri launches the app from
    /// the car, so the connected CarPlay scene and the car's audio route count too.
    static var isActive: Bool {
        isActive(
            sceneConnected: isConnected,
            hasCarPlayScene: UIApplication.shared.connectedScenes.contains { $0 is CPTemplateApplicationScene },
            routeOutputs: AVAudioSession.sharedInstance().currentRoute.outputs.map(\.portType)
        )
    }

    static func isActive(sceneConnected: Bool, hasCarPlayScene: Bool, routeOutputs: [AVAudioSession.Port]) -> Bool {
        sceneConnected || hasCarPlayScene || routeOutputs.contains(.carAudio)
    }
}

/// The latest matches handed out, at most `capacity`, the oldest dropped first.
struct RecentMatches<Match> {
    private var order: [String] = []
    private var matches: [String: Match] = [:]
    let capacity: Int

    init(capacity: Int) {
        self.capacity = capacity
    }

    subscript(identifier: String) -> Match? {
        get { matches[identifier] }
        set {
            order.removeAll { $0 == identifier }
            guard let newValue else {
                matches[identifier] = nil
                return
            }
            matches[identifier] = newValue
            order.append(identifier)
            while order.count > capacity { matches[order.removeFirst()] = nil }
        }
    }

    var identifiers: [String] { order }
}

/// The Kotlin `VoiceLibrary` as `SiriMediaSearching`, keeping the matches it hands out so the play that follows a
/// resolution plays that very item.
@MainActor
final class KotlinSiriLibrary: SiriMediaSearching {
    private let library: VoiceLibrary
    private var matches = RecentMatches<VoiceMatch>(capacity: 50)

    init(_ library: VoiceLibrary) {
        self.library = library
    }

    func search(query: String, kinds: [SiriMediaKind], limit: Int) async -> [SiriMediaCandidate] {
        let found = (try? await library.search(query: query, kinds: kinds.map(\.voiceKind), limit: Int32(limit))) ?? []
        return found.map { match in
            let candidate = SiriMediaCandidate(kind: SiriMediaKind(match.kind), title: match.title, artist: match.artist)
            matches[candidate.identifier] = match
            return candidate
        }
    }

    /// The action that plays `item`: the match it was resolved to, or, when the app was relaunched since (a donated
    /// interaction, a retry), the best match for its title.
    func action(for item: INMediaItem, shuffled: Bool) async -> (any MediaAction)? {
        if let identifier = item.identifier, let match = matches[identifier] {
            return library.play(match: match, shuffled: shuffled)
        }
        guard let title = item.title else { return nil }
        let kinds = SiriMediaKind.kinds(for: item.type)
        for candidate in await search(query: title, kinds: kinds, limit: 10) where candidate.title == title && candidate.artist == item.artist {
            if let match = matches[candidate.identifier] { return library.play(match: match, shuffled: shuffled) }
        }
        return nil
    }
}

private extension SiriMediaKind {
    init(_ kind: VoiceMediaKind) {
        switch kind {
        case .artist: self = .artist
        case .album: self = .album
        case .song: self = .song
        case .playlist: self = .playlist
        case .genre: self = .genre
        }
    }

    var voiceKind: VoiceMediaKind {
        switch self {
        case .artist: .artist
        case .album: .album
        case .song: .song
        case .playlist: .playlist
        case .genre: .genre
        }
    }
}

/// Starts what Siri resolved, through the same paths as the app's own buttons (`AppIntentPerformer`).
@MainActor
final class SiriMediaPlayer {
    /// The resolved item of a request that named nothing.
    static let resumeIdentifier = "shuttle.resume"

    private let graph: IosAppGraph
    private let performer: AppIntentPerformer
    private let library: KotlinSiriLibrary
    private let resolver: SiriMediaResolver

    init(graph: IosAppGraph, performer: AppIntentPerformer) {
        self.graph = graph
        self.performer = performer
        library = KotlinSiriLibrary(graph.voiceLibrary)
        resolver = SiriMediaResolver(library: library)
    }

    /// CarPlay is Shuttle Music Pro (#946), and so is a request made through it: the car can't show the paywall, so a
    /// locked car is refused as the car's own browse is. The phone's Siri plays what the phone's buttons do, which
    /// local playback never gates.
    var isBlockedByCarPlayGate: Bool {
        CarPlayConnection.isActive && graph.carPlayAccess.locked.value.boolValue
    }

    func resolve(_ intent: INPlayMediaIntent) async -> [INPlayMediaMediaItemResolutionResult] {
        if isBlockedByCarPlayGate {
            return [.unsupported(forReason: .subscriptionRequired)]
        }
        switch await resolver.resolve(SiriMediaRequest(intent.mediaSearch)) {
        case .resume:
            let item = INMediaItem(identifier: Self.resumeIdentifier, title: nil, type: .music, artwork: nil)
            return [.success(with: item)]
        case .match(let candidate):
            return [.success(with: candidate.mediaItem)]
        case .noMatch:
            return [.unsupported()]
        }
    }

    func play(_ intent: INPlayMediaIntent) async -> INPlayMediaIntentResponseCode {
        guard !isBlockedByCarPlayGate else { return .failure }
        let shuffled = intent.playShuffled ?? false
        do {
            if let item = intent.mediaItems?.first, item.identifier != Self.resumeIdentifier {
                guard let action = await library.action(for: item, shuffled: shuffled) else { return .failure }
                try await performer.play(action)
            } else if shuffled {
                try await performer.shuffleLibrary()
            } else {
                try await performer.setPlaying(true)
            }
        } catch {
            return .failure
        }
        applyRepeatMode(intent.playbackRepeatMode)
        return .success
    }

    private func applyRepeatMode(_ mode: INPlaybackRepeatMode) {
        let repeatMode: RepeatMode
        switch mode {
        case .all: repeatMode = .all
        case .one: repeatMode = .one
        case .none: repeatMode = .off
        default: return
        }
        graph.playerController.queueOperations.setRepeatMode(repeatMode: repeatMode)
    }
}

/// The intent handler Siri gets from the app delegate: resolves in the app, then hands playback to the delegate.
final class SiriMediaHandler: NSObject, INPlayMediaIntentHandling {
    @MainActor
    static let shared = SiriMediaHandler()

    func resolveMediaItems(for intent: INPlayMediaIntent, with completion: @escaping ([INPlayMediaMediaItemResolutionResult]) -> Void) {
        Task { @MainActor in
            completion(await AppGraph.dependencies.siriPlayer.resolve(intent))
        }
    }

    func handle(intent: INPlayMediaIntent, completion: @escaping (INPlayMediaIntentResponse) -> Void) {
        completion(INPlayMediaIntentResponse(code: .handleInApp, userActivity: nil))
    }
}
