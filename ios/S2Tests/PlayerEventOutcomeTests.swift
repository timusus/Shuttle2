import Foundation
import Shared
import Testing
@testable import S2

/// How Now Playing turns the shared `PlayerViewModel`'s one-shot events into a notice or a screen to open.
@MainActor
struct PlayerEventOutcomeTests {
    private func resolve(
        _ event: any PlayerUiEvent,
        undoClearQueue: @escaping () -> Void = {},
        undoRemoveQueueItem: @escaping () -> Void = {},
        send: @escaping (any MediaAction) -> Void = { _ in }
    ) -> PlayerEventOutcome? {
        PlayerEventOutcome.resolve(event, undoClearQueue: undoClearQueue, undoRemoveQueueItem: undoRemoveQueueItem, send: send)
    }

    private func notice(_ outcome: PlayerEventOutcome?) throws -> PlayerNotice {
        guard case .notice(let notice) = outcome else {
            Issue.record("Expected a notice, got \(String(describing: outcome))")
            throw CancellationError()
        }
        return notice
    }

    private var selection: any MediaSelection { MediaSelectionSongs(song: TestSongs.demo[0]) }

    @Test func aClearedQueueOffersUndo() throws {
        var undone = false
        let result = try notice(resolve(PlayerUiEventQueueCleared(songCount: 5), undoClearQueue: { undone = true }))
        #expect(result.message == "Queue cleared")
        #expect(result.actionTitle == "Undo")
        result.action?()
        #expect(undone)
    }

    @Test func aRemovedItemOffersUndo() throws {
        var undone = false
        let result = try notice(resolve(PlayerUiEventQueueItemRemoved.shared, undoRemoveQueueItem: { undone = true }))
        #expect(result.message == "Removed from queue")
        result.action?()
        #expect(undone)
    }

    @Test func aSkippedServerSongSaysWhy() throws {
        let result = try notice(resolve(PlayerUiEventServerSongSkipped(songTitle: "Teardrop")))
        #expect(result.message == "Skipped “Teardrop” — streaming needs Shuttle Music Pro")
        #expect(result.action == nil)
    }

    @Test func anActionMessageShowsItsTextAndSendsItsSnackbarAction() throws {
        var sent: [any MediaAction] = []
        let undo = MediaActionInclude(selection: selection)
        let event = PlayerUiEventMediaActionDone(
            result: MediaActionResultMessage(
                message: MediaActionMessageExcluded(songCount: 1),
                action: SnackbarAction(label: .undo, action: undo)
            )
        )
        let result = try notice(resolve(event, send: { sent.append($0) }))
        #expect(result.message == "1 song excluded")
        #expect(result.actionTitle == "Undo")
        result.action?()
        #expect(sent.count == 1)
        #expect(sent.first as? MediaActionInclude == undo)
    }

    /// The Library's song lists get `MediaActionsViewModel`'s results directly, not as a player event: an Exclude
    /// there shows the same notice, its Undo sent back as the Include (#650).
    @Test func aListsExcludeResultShowsTheSameNoticeWithUndo() throws {
        var sent: [any MediaAction] = []
        let undo = MediaActionInclude(selection: selection)
        let result = MediaActionResultMessage(message: MediaActionMessageExcluded(songCount: 1), action: SnackbarAction(label: .undo, action: undo))
        guard case .notice(let shown) = PlayerEventOutcome.resolve(result, send: { sent.append($0) }) else {
            Issue.record("expected a notice")
            return
        }
        #expect(shown.message == "1 song excluded")
        #expect(shown.actionTitle == "Undo")
        shown.action?()
        #expect(sent.first as? MediaActionInclude == undo)
    }

    @Test func aMessageWithoutAnActionHasNoButton() throws {
        let event = PlayerUiEventMediaActionDone(
            result: MediaActionResultMessage(message: MediaActionMessageAddedToPlaylist(playlistName: "Road Trip", songCount: 1), action: nil)
        )
        let result = try notice(resolve(event))
        #expect(result.message == "1 song added to Road Trip")
        #expect(result.actionTitle == nil)
    }

    @Test func goToAlbumAndArtistOpenTheirScreens() {
        let album = Album(
            name: "OK Computer", albumArtist: "Radiohead", artists: ["Radiohead"], songCount: 1, duration: 0,
            year: nil, playCount: 0, lastSongPlayed: nil, lastSongCompleted: nil,
            groupKey: AlbumGroupKey(key: "ok computer", albumArtistGroupKey: AlbumArtistGroupKey(key: "radiohead"), identity: nil),
            mediaProviders: [.shuttle], artworkVersion: nil
        )
        let artist = AlbumArtist(
            name: "Radiohead", artists: ["Radiohead"], albumCount: 1, songCount: 1, playCount: 0,
            groupKey: AlbumArtistGroupKey(key: "radiohead"), mediaProviders: [.shuttle], artworkVersion: nil
        )
        let toAlbum = resolve(PlayerUiEventMediaActionDone(result: MediaActionResultNavigate(target: NavigationTargetAlbum(album: album))))
        let toArtist = resolve(PlayerUiEventMediaActionDone(result: MediaActionResultNavigate(target: NavigationTargetAlbumArtist(albumArtist: artist))))
        guard case .open(let albumRoute) = toAlbum, case .open(let artistRoute) = toArtist else {
            Issue.record("Expected routes")
            return
        }
        #expect(albumRoute == Route.album(album))
        #expect(artistRoute == Route.albumArtist(artist))
    }

    @Test func aSilentResultShowsNothing() {
        #expect(resolve(PlayerUiEventMediaActionDone(result: MediaActionResultNone.shared)) == nil)
    }

    @Test func messagesUseAndroidsWording() {
        #expect(MediaActionText.notice(for: MediaActionMessageAddedToPlaylist(playlistName: "Mix", songCount: 3)) == "3 songs added to Mix")
        #expect(MediaActionText.notice(for: MediaActionMessageAlreadyInPlaylist(playlistName: "Mix", duplicateCount: 2)) == "2 already in Mix")
        #expect(MediaActionText.notice(for: MediaActionMessageAddedToFavourites(songCount: 1)) == "1 song added to Favorites")
        #expect(MediaActionText.notice(for: MediaActionMessageExcluded(songCount: 2)) == "2 songs excluded")
        #expect(MediaActionText.title(for: .addAnyway) == "Add Anyway")
    }
}
