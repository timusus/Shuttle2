import AppIntents

// The library App Intents (#758): Shuffle All and Play Playlist for Siri, Shortcuts and Spotlight. App-only, unlike the
// playback intents (SystemSurfaces/PlaybackIntents.swift), which the widgets' buttons need too.

/// What the library intents ask of the app. Called on the main thread.
@MainActor
protocol LibraryIntentPerforming: AnyObject {
    func shuffleLibrary() async throws
    func playPlaylist(id: Int64, shuffled: Bool) async throws
    /// The library's playlists, for the playlist parameter.
    func playlists() async throws -> [PlaylistEntity]
}

extension IntentPerformers {
    @MainActor static var library: (any LibraryIntentPerforming)?

    @MainActor
    static func requireLibrary() throws -> any LibraryIntentPerforming {
        guard let library else { throw ShuttleIntentError.notReady }
        return library
    }
}

/// Shuffles the whole library, as Home's Shuffle All does (Android's Shuffle all shortcut, #761).
struct ShuffleLibraryIntent: AudioPlaybackIntent {
    static var title: LocalizedStringResource { "Shuffle All" }
    static var description: IntentDescription { "Shuffles every song in your Shuttle Music library." }

    @MainActor
    func perform() async throws -> some IntentResult {
        try await IntentPerformers.requireLibrary().shuffleLibrary()
        return .result()
    }
}

/// Plays, or shuffles, one of the library's playlists.
struct PlayPlaylistIntent: AudioPlaybackIntent {
    static var title: LocalizedStringResource { "Play Playlist" }
    static var description: IntentDescription { "Plays one of your playlists in Shuttle Music." }

    @Parameter(title: "Playlist")
    var playlist: PlaylistEntity

    @Parameter(title: "Shuffle", default: false)
    var shuffle: Bool

    static var parameterSummary: some ParameterSummary {
        Summary("Play \(\.$playlist)") {
            \.$shuffle
        }
    }

    @MainActor
    func perform() async throws -> some IntentResult {
        try await IntentPerformers.requireLibrary().playPlaylist(id: Int64(playlist.id), shuffled: shuffle)
        return .result()
    }
}

extension PlayPlaylistIntent {
    init(playlist: PlaylistEntity, shuffle: Bool = false) {
        self.init()
        self.playlist = playlist
        self.shuffle = shuffle
    }
}

/// A playlist as Siri, Shortcuts and Spotlight see it.
struct PlaylistEntity: AppEntity, Equatable {
    static var typeDisplayRepresentation: TypeDisplayRepresentation { "Playlist" }
    static var defaultQuery: PlaylistEntityQuery { PlaylistEntityQuery() }

    /// The Kotlin `Playlist.id`.
    let id: Int
    let name: String
    let songCount: Int

    var displayRepresentation: DisplayRepresentation {
        let subtitle: LocalizedStringResource = LocalizedStringResource(stringLiteral: pluralized(songCount, .song))
        return DisplayRepresentation(title: "\(name)", subtitle: subtitle)
    }
}

/// Finds playlists by id, by name (Siri's "Play Road Trip in Shuttle Music") and lists them all as suggestions.
struct PlaylistEntityQuery: EntityStringQuery {
    @MainActor
    func entities(for identifiers: [Int]) async throws -> [PlaylistEntity] {
        try await IntentPerformers.requireLibrary().playlists().filter { identifiers.contains($0.id) }
    }

    @MainActor
    func entities(matching string: String) async throws -> [PlaylistEntity] {
        try await IntentPerformers.requireLibrary().playlists().filter { $0.name.localizedCaseInsensitiveContains(string) }
    }

    @MainActor
    func suggestedEntities() async throws -> [PlaylistEntity] {
        try await IntentPerformers.requireLibrary().playlists()
    }
}
