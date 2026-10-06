import Foundation
import Intents
import Testing
@testable import S2

/// Siri's media domain (#951): what a spoken request resolves to in the library, what Siri is told about it, and when
/// it's asked for permission.
@MainActor
struct SiriMediaResolverTests {
    private final class FakeLibrary: SiriMediaSearching {
        var hits: [String: [SiriMediaCandidate]] = [:]
        private(set) var searches: [(query: String, kinds: [SiriMediaKind])] = []

        func search(query: String, kinds: [SiriMediaKind], limit: Int) async -> [SiriMediaCandidate] {
            searches.append((query, kinds))
            return (hits[query] ?? []).filter { kinds.contains($0.kind) }
        }
    }

    private let library = FakeLibrary()
    private let beatles = SiriMediaCandidate(kind: .artist, title: "The Beatles", artist: nil)
    private let abbeyRoad = SiriMediaCandidate(kind: .album, title: "Abbey Road", artist: "The Beatles")
    private let abbeyRoadCover = SiriMediaCandidate(kind: .album, title: "Abbey Road", artist: "Tribute Band")
    private let roadTrip = SiriMediaCandidate(kind: .playlist, title: "Road Trip", artist: nil)

    private func resolve(_ request: SiriMediaRequest) async -> SiriMediaResolution {
        await SiriMediaResolver(library: library).resolve(request)
    }

    @Test func aRequestNamingNothingResumes() async {
        #expect(await resolve(SiriMediaRequest(mediaType: .music)) == .resume)
        #expect(await resolve(SiriMediaRequest(name: "  ")) == .resume)
        #expect(library.searches.isEmpty)
    }

    @Test func aNameSearchesTheKindsOfItsType() async {
        library.hits["The Beatles"] = [beatles]

        #expect(await resolve(SiriMediaRequest(mediaType: .artist, name: "The Beatles")) == .match(beatles))
        #expect(library.searches.first?.kinds == [.artist])
    }

    @Test func aNameWithNoTypeSearchesEveryKind() async {
        library.hits["Road Trip"] = [roadTrip]

        #expect(await resolve(SiriMediaRequest(name: "Road Trip")) == .match(roadTrip))
        #expect(Set(library.searches.first?.kinds ?? []) == Set(SiriMediaKind.allCases))
    }

    @Test func theNamedArtistsItemComesFirst() async {
        library.hits["Abbey Road"] = [abbeyRoadCover, abbeyRoad]

        let request = SiriMediaRequest(name: "Abbey Road", artist: "the beatles")
        #expect(await resolve(request) == .match(abbeyRoad))
        // Neither an artist nor a genre is called "Abbey Road" by someone
        #expect(library.searches.first?.kinds.contains(.artist) == false)
    }

    @Test func anArtistNobodyMatchesLeavesTheSearchOrder() async {
        library.hits["Abbey Road"] = [abbeyRoadCover, abbeyRoad]

        #expect(await resolve(SiriMediaRequest(name: "Abbey Road", artist: "Wings")) == .match(abbeyRoadCover))
    }

    @Test func anArtistAloneSearchesArtists() async {
        library.hits["The Beatles"] = [beatles]

        #expect(await resolve(SiriMediaRequest(artist: "The Beatles")) == .match(beatles))
        #expect(library.searches.first?.kinds == [.artist])
    }

    @Test func anAlbumNameSearchesAlbums() async {
        library.hits["Abbey Road"] = [abbeyRoad]

        #expect(await resolve(SiriMediaRequest(album: "Abbey Road")) == .match(abbeyRoad))
    }

    @Test func eachGenreIsTriedUntilOneMatches() async {
        let jazz = SiriMediaCandidate(kind: .genre, title: "Jazz", artist: nil)
        library.hits["Jazz"] = [jazz]

        #expect(await resolve(SiriMediaRequest(genres: ["Polka", "Jazz"])) == .match(jazz))
        #expect(library.searches.map(\.query) == ["Polka", "Jazz"])
    }

    @Test func nothingFoundIsNoMatch() async {
        #expect(await resolve(SiriMediaRequest(name: "Nonexistent")) == .noMatch)
    }

    @Test(arguments: [INMediaItemType.podcastShow, .audioBook, .movie, .tvShow, .musicVideo, .radioStation])
    func whatTheLibraryDoesntHoldIsNoMatchWithoutSearching(type: INMediaItemType) async {
        #expect(await resolve(SiriMediaRequest(mediaType: type, name: "Anything")) == .noMatch)
        #expect(library.searches.isEmpty)
    }

    @Test func aCandidateRoundTripsThroughItsMediaItem() {
        let item = abbeyRoad.mediaItem

        #expect(item.identifier == abbeyRoad.identifier)
        #expect(item.title == "Abbey Road")
        #expect(item.type == .album)
        #expect(item.artist == "The Beatles")
        #expect(abbeyRoad.identifier != abbeyRoadCover.identifier)
    }

    @Test func aDonationIsAnIntentForTheItemAndHowItWasPlayed() {
        let intent = SiriDonation.intent(for: roadTrip, shuffled: true)

        #expect(intent.mediaItems?.first?.identifier == roadTrip.identifier)
        #expect(intent.playShuffled == true)
    }

    @Test func vocabularyIsTrimmedDistinctAndBounded() {
        let names = ["Radiohead", " Björk ", "radiohead", "", "  ", "Portishead", "Massive Attack"]

        #expect(SiriVocabulary.strings(from: names) == ["Radiohead", "Björk", "Portishead", "Massive Attack"])
        #expect(SiriVocabulary.strings(from: names, limit: 2) == ["Radiohead", "Björk"])
    }

    @Test func siriIsAskedOnceAfterTheFirstImportOnly() {
        #expect(SiriAuthorization.shouldAsk(status: .notDetermined, libraryItems: 120, asked: false))
        #expect(!SiriAuthorization.shouldAsk(status: .notDetermined, libraryItems: 0, asked: false))
        #expect(!SiriAuthorization.shouldAsk(status: .notDetermined, libraryItems: 120, asked: true))
        #expect(!SiriAuthorization.shouldAsk(status: .authorized, libraryItems: 120, asked: false))
        #expect(!SiriAuthorization.shouldAsk(status: .denied, libraryItems: 120, asked: false))
    }
}
