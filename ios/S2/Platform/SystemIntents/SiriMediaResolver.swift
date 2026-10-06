import Intents

// Siri's media domain (#951): what a spoken "play X" asks for, resolved against the library. SiriKit's own types are
// here only where they carry the request in and the answer out; the decisions are plain Swift, tested without Siri.

/// What a request can name, as Siri's `INMediaItemType`s the library has.
enum SiriMediaKind: Equatable, CaseIterable {
    case artist, album, song, playlist, genre

    var itemType: INMediaItemType {
        switch self {
        case .artist: .artist
        case .album: .album
        case .song: .song
        case .playlist: .playlist
        case .genre: .genre
        }
    }

    /// The kinds a request of `type` can mean: its own, or any of ours for "music" or no type at all; none for what the
    /// library doesn't hold (podcasts, audiobooks, video, radio).
    static func kinds(for type: INMediaItemType) -> [SiriMediaKind] {
        switch type {
        case .artist: [.artist]
        case .album: [.album]
        case .song: [.song]
        case .playlist: [.playlist]
        case .genre: [.genre]
        case .unknown, .music: [.artist, .album, .playlist, .song, .genre]
        default: []
        }
    }
}

/// A library item a request matched.
struct SiriMediaCandidate: Equatable {
    let kind: SiriMediaKind
    let title: String
    /// Whose album or song it is.
    let artist: String?

    /// What names it to Siri and back: a donation, a resolution and the handle that follows agree on it.
    var identifier: String { "\(kind):\(title)\u{1F}\(artist ?? "")" }

    var mediaItem: INMediaItem {
        INMediaItem(identifier: identifier, title: title, type: kind.itemType, artwork: nil, artist: artist)
    }
}

/// The library's search, as the resolver asks it: the best matches first.
@MainActor
protocol SiriMediaSearching {
    func search(query: String, kinds: [SiriMediaKind], limit: Int) async -> [SiriMediaCandidate]
}

/// What the request names, from an `INMediaSearch`.
struct SiriMediaRequest: Equatable {
    var mediaType: INMediaItemType = .unknown
    var name: String?
    var artist: String?
    var album: String?
    var genres: [String] = []

    init(
        mediaType: INMediaItemType = .unknown,
        name: String? = nil,
        artist: String? = nil,
        album: String? = nil,
        genres: [String] = []
    ) {
        self.mediaType = mediaType
        self.name = name.nonBlank
        self.artist = artist.nonBlank
        self.album = album.nonBlank
        self.genres = genres.compactMap(\.nonBlank)
    }

    init(_ search: INMediaSearch?) {
        self.init(
            mediaType: search?.mediaType ?? .unknown,
            name: search?.mediaName,
            artist: search?.artistName,
            album: search?.albumName,
            genres: search?.genreNames ?? []
        )
    }

    /// "Play music": nothing is named.
    var namesNothing: Bool {
        name == nil && artist == nil && album == nil && genres.isEmpty
    }
}

private extension Optional where Wrapped == String {
    var nonBlank: String? {
        guard let trimmed = self?.trimmingCharacters(in: .whitespacesAndNewlines), !trimmed.isEmpty else { return nil }
        return trimmed
    }
}

private extension String {
    var nonBlank: String? { Optional(self).nonBlank }
}

enum SiriMediaResolution: Equatable {
    /// Nothing named: resume the queue, or shuffle the library.
    case resume
    case match(SiriMediaCandidate)
    case noMatch
}

/// Turns a request into the one library item it most likely means, using the same search as the Search screen.
@MainActor
struct SiriMediaResolver {
    let library: any SiriMediaSearching
    /// How many hits to rank by artist; the search's own order stands otherwise.
    var searchLimit = 10

    func resolve(_ request: SiriMediaRequest) async -> SiriMediaResolution {
        guard !request.namesNothing else { return .resume }
        let kinds = SiriMediaKind.kinds(for: request.mediaType)
        guard !kinds.isEmpty else { return .noMatch }
        for search in searches(for: request, kinds: kinds) {
            let hits = await library.search(query: search.query, kinds: search.kinds, limit: searchLimit)
            if let best = Self.rank(hits, byArtist: request.artist).first { return .match(best) }
        }
        return .noMatch
    }

    private struct Search {
        let query: String
        let kinds: [SiriMediaKind]
    }

    /// The searches to try, most specific first: the name, else the album, artist, then each genre (Siri puts a playlist's name in the name too). With a
    /// name and an artist ("Abbey Road by The Beatles") the name isn't the artist's, so an artist isn't a candidate.
    private func searches(for request: SiriMediaRequest, kinds: [SiriMediaKind]) -> [Search] {
        var searches: [Search] = []
        if let name = request.name {
            let named = request.artist == nil ? kinds : kinds.filter { $0 != .artist && $0 != .genre }
            searches.append(Search(query: name, kinds: named.isEmpty ? kinds : named))
        }
        if let album = request.album, kinds.contains(.album) {
            searches.append(Search(query: album, kinds: [.album]))
        }
        if let artist = request.artist, request.name == nil, kinds.contains(.artist) {
            searches.append(Search(query: artist, kinds: [.artist]))
        }
        if kinds.contains(.genre) {
            searches += request.genres.map { Search(query: $0, kinds: [.genre]) }
        }
        return searches
    }

    /// `hits` with the artist's own first when one was named, and only theirs when any are.
    static func rank(_ hits: [SiriMediaCandidate], byArtist artist: String?) -> [SiriMediaCandidate] {
        guard let artist else { return hits }
        let theirs = hits.filter { $0.artist?.range(of: artist, options: [.caseInsensitive, .diacriticInsensitive]) != nil }
        return theirs.isEmpty ? hits : theirs
    }
}
