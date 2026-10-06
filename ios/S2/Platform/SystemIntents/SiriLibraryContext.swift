import Foundation
import Intents
import Shared

/// What Siri is told about the library (#951): the `INMediaUserContext` (how many items, and whether the listener
/// subscribes) and the vocabulary (the artists and playlists they play most), which is how "play the Beatles" finds
/// Shuttle Music among the audio apps. Refreshed shortly after the library changes, not on every write of an import.
enum SiriVocabulary {
    /// Siri accepts a few hundred strings per type; the most played first.
    static let limit = 300

    /// `names` trimmed, without blanks or repeats (ignoring case), at most `limit`.
    static func strings(from names: [String], limit: Int = SiriVocabulary.limit) -> [String] {
        var seen = Set<String>()
        var strings: [String] = []
        for name in names {
            let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !trimmed.isEmpty, seen.insert(trimmed.lowercased()).inserted else { continue }
            strings.append(trimmed)
            if strings.count == limit { break }
        }
        return strings
    }
}

/// Where Siri's authorization is asked for: once, after the first import has put music in the library (so not on a cold
/// first launch, before the listener has seen what the app does), and never again after they've answered.
enum SiriAuthorization {
    private static let askedKey = "siriAuthorizationAsked"

    static func shouldAsk(status: INSiriAuthorizationStatus, libraryItems: Int, asked: Bool) -> Bool {
        status == .notDetermined && libraryItems > 0 && !asked
    }

    @MainActor
    static func askIfDue(libraryItems: Int, defaults: UserDefaults = .standard) {
        guard shouldAsk(status: INPreferences.siriAuthorizationStatus(), libraryItems: libraryItems, asked: defaults.bool(forKey: askedKey)) else { return }
        defaults.set(true, forKey: askedKey)
        INPreferences.requestSiriAuthorization { _ in }
    }
}

@MainActor
final class SiriLibraryContext {
    private let graph: IosAppGraph
    private var task: Task<Void, Never>?
    private var refresh: Task<Void, Never>?

    init(graph: IosAppGraph) {
        self.graph = graph
    }

    /// Follows the library for the app's life. Idempotent.
    func start() {
        guard task == nil else { return }
        task = Task { [graph, weak self] in
            for await state in graph.voiceLibrary.state() {
                self?.libraryChanged(state)
            }
        }
    }

    private func libraryChanged(_ state: VoiceLibraryState) {
        refresh?.cancel()
        refresh = Task { [weak self] in
            // An import changes the library many times a second
            try? await Task.sleep(for: .seconds(3))
            guard !Task.isCancelled else { return }
            await self?.publish(songCount: Int(state.songCount))
        }
    }

    private func publish(songCount: Int) async {
        let context = INMediaUserContext()
        context.numberOfLibraryItems = songCount
        // Locked is free (with the trial had or not); an undecided store counts as Pro, as the gates do
        context.subscriptionStatus = graph.carPlayAccess.locked.value.boolValue ? .notSubscribed : .subscribed
        context.becomeCurrent()
        let library = graph.voiceLibrary
        let artists = (try? await library.topArtistNames(limit: Int32(SiriVocabulary.limit * 2))) ?? []
        let playlists = (try? await library.topPlaylistNames(limit: Int32(SiriVocabulary.limit * 2))) ?? []
        let vocabulary = INVocabulary.shared()
        vocabulary.setVocabularyStrings(NSOrderedSet(array: SiriVocabulary.strings(from: artists)), of: .mediaMusicArtistName)
        vocabulary.setVocabularyStrings(NSOrderedSet(array: SiriVocabulary.strings(from: playlists)), of: .mediaPlaylistTitle)
        SiriAuthorization.askIfDue(libraryItems: songCount)
    }
}

/// The `INPlayMediaIntent` interaction for what the listener starts from the app, so Siri suggests and learns it.
enum SiriDonation {
    /// The item a play of `selection` is, when it is a single artist, album, playlist, genre or song.
    static func candidate(for selection: any MediaSelection) -> SiriMediaCandidate? {
        switch selection {
        case let selection as MediaSelectionAlbumArtists:
            guard selection.albumArtists.count == 1, let artist = selection.albumArtists.first else { return nil }
            return (artist.friendlyArtistName ?? artist.name).map { SiriMediaCandidate(kind: .artist, title: $0, artist: nil) }
        case let selection as MediaSelectionAlbums:
            guard selection.albums.count == 1, let album = selection.albums.first, let name = album.name else { return nil }
            return SiriMediaCandidate(kind: .album, title: name, artist: album.friendlyArtistName)
        case let selection as MediaSelectionPlaylists:
            guard selection.playlists.count == 1, let playlist = selection.playlists.first else { return nil }
            return SiriMediaCandidate(kind: .playlist, title: playlist.name, artist: nil)
        case let selection as MediaSelectionGenres:
            guard selection.genres.count == 1, let genre = selection.genres.first else { return nil }
            return SiriMediaCandidate(kind: .genre, title: genre.name, artist: nil)
        case let selection as MediaSelectionSongs:
            guard selection.songs.count == 1, let song = selection.songs.first, let name = song.name else { return nil }
            return SiriMediaCandidate(kind: .song, title: name, artist: song.albumArtist ?? song.artists.first)
        default:
            return nil
        }
    }

    static func intent(for candidate: SiriMediaCandidate, shuffled: Bool) -> INPlayMediaIntent {
        INPlayMediaIntent(
            mediaItems: [candidate.mediaItem],
            mediaContainer: nil,
            playShuffled: shuffled,
            playbackRepeatMode: .unknown,
            resumePlayback: false,
            playbackQueueLocation: .unknown,
            playbackSpeed: nil,
            mediaSearch: nil
        )
    }

    /// Donates the play `action` is, if it plays one item. A donation is fire-and-forget; it never gets in a play's way.
    static func donate(_ action: any MediaAction) {
        let selection: (any MediaSelection)?
        let shuffled: Bool
        switch action {
        case let action as MediaActionPlay:
            selection = action.selection
            shuffled = false
        case let action as MediaActionShuffle:
            selection = action.selection
            shuffled = true
        default:
            return
        }
        guard let selection, let candidate = candidate(for: selection) else { return }
        INInteraction(intent: intent(for: candidate, shuffled: shuffled), response: nil).donate { _ in }
    }
}
