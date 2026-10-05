import AppIntents

// The playback App Intents (#758): Siri, Shortcuts and Spotlight's Play or Pause and Next Song, the widgets' buttons and
// the Control Center control. This file is compiled into both the app and the widget extension, so the widgets can name
// the intents for their buttons; as `AudioPlaybackIntent`s they always run in the app's process, which registers the
// `PlaybackIntentPerforming` that carries them out (`IntentPerformers`). Nothing here imports Shared: the extension
// doesn't link the Kotlin framework.

/// What the playback intents ask of the app. Called on the main thread.
@MainActor
protocol PlaybackIntentPerforming: AnyObject {
    /// Pauses when playback is wanted, else plays (something, if nothing is queued).
    func togglePlayback() async throws
    /// Plays or pauses, for the Control Center control's toggle.
    func setPlaying(_ playing: Bool) async throws
    func skipToNext() async throws
}

/// Where an intent finds the app's performer. The app sets it at launch (`AppGraph.initialize()`); the widget
/// extension never does, and an intent that finds none fails rather than doing nothing silently.
@MainActor
enum IntentPerformers {
    static var playback: (any PlaybackIntentPerforming)?

    static func requirePlayback() throws -> any PlaybackIntentPerforming {
        guard let playback else { throw ShuttleIntentError.notReady }
        return playback
    }
}

/// Why an intent couldn't do what it was asked; Siri and Shortcuts say the description.
enum ShuttleIntentError: Error, Equatable, CustomLocalizedStringResourceConvertible {
    /// No performer: the intent ran outside the app.
    case notReady
    /// The library has nothing to play.
    case noSongs
    /// The playlist asked for is no longer in the library.
    case playlistNotFound
    case playbackFailed

    var localizedStringResource: LocalizedStringResource {
        switch self {
        case .notReady: "Shuttle Music isn't ready yet. Open it and try again."
        case .noSongs: "There's nothing in your library to play yet."
        case .playlistNotFound: "That playlist isn't in your library any more."
        case .playbackFailed: "Shuttle Music couldn't play that."
        }
    }
}

/// Plays or pauses: Siri's "Pause Shuttle Music", the widgets' play/pause button.
struct TogglePlaybackIntent: AudioPlaybackIntent {
    static var title: LocalizedStringResource { "Play or Pause" }
    static var description: IntentDescription { "Plays or pauses Shuttle Music. With nothing queued, shuffles your library." }

    @MainActor
    func perform() async throws -> some IntentResult {
        try await IntentPerformers.requirePlayback().togglePlayback()
        return .result()
    }
}

/// Skips to the next song.
struct SkipToNextIntent: AudioPlaybackIntent {
    static var title: LocalizedStringResource { "Next Song" }
    static var description: IntentDescription { "Skips to the next song in Shuttle Music's queue." }

    @MainActor
    func perform() async throws -> some IntentResult {
        try await IntentPerformers.requirePlayback().skipToNext()
        return .result()
    }
}

/// Plays or pauses to match `value`: the Control Center control's toggle.
struct SetPlaybackIntent: SetValueIntent, AudioPlaybackIntent {
    static var title: LocalizedStringResource { "Set Playing" }
    static var description: IntentDescription { "Plays or pauses Shuttle Music." }
    /// Hidden from Shortcuts: Play or Pause covers it there.
    static var isDiscoverable: Bool { false }

    @Parameter(title: "Playing")
    var value: Bool

    @MainActor
    func perform() async throws -> some IntentResult {
        try await IntentPerformers.requirePlayback().setPlaying(value)
        return .result()
    }
}
