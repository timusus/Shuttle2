import Shared
import Testing

@testable import S2

/// Song Info's Copies section: the pills and the footer that say which copy plays and why.
struct SongInfoCopiesTests {
    @Test func theStatesReadAsTheirPills() {
        #expect(SongCopyState.playing.label == "Playing")
        #expect(SongCopyState.downloaded.label == "Downloaded")
        #expect(SongCopyState.available.label == nil)
    }

    @Test func unreachableMissingAndOfflineCopiesAreQuiet() {
        #expect([SongCopyState.unreachable, .missing, .offline].allSatisfy { $0.isUnavailable })
        #expect([SongCopyState.playing, .primary, .downloaded, .available].allSatisfy { !$0.isUnavailable })
    }

    @Test func theFooterSaysHowTheCopyWasChosen() {
        #expect(CopyChoicePrimary.shared.footer == "You made this copy primary.")
        #expect(CopyChoiceAlbumPrimary.shared.footer == "This album's primary source.")
        #expect(CopyChoiceAutomatic(preferred: .bestQuality).footer.contains("best quality"))
    }
}
