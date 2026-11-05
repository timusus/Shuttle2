import Shared
import SwiftUI

extension PlaylistKind {
    /// This kind's sorts in menu order, labelled as Android's `PlaylistSortChoices.kt` does; the playlist's own
    /// order is named for what it means for the kind.
    var sortChoices: [SongSortChoice<SongListSort>] {
        sorts.map { sort in
            let (title, short) = sort.labels(in: self)
            return SongSortChoice(sort: sort, title: title, shortTitle: short)
        }
    }
}

private extension SongListSort {
    func labels(in kind: PlaylistKind) -> (title: String, short: String) {
        switch self {
        case .albumNewestAdded: ("Album, Recently Added", "New Albums")
        case .albumNewest: ("Album, Newest First", "Newest")
        case .albumOldest: ("Album, Oldest First", "Oldest")
        case .albumTitle: ("Album Title", "Album A–Z")
        case .albumRanked: ("Album, Most Relevant", "Ranked")
        case .playlistOrder:
            switch kind {
            case .user: ("Custom", "Custom")
            case .favourites: ("Recently Favourited", "Recent")
            case .history: ("Recently Played", "Recent")
            default: ("Playlist Order", "Playlist Order")
            }
        case .newestAdded: ("Recently Added", "Recent")
        case .songTitle: ("Song Title", "Title A–Z")
        case .artist: ("Artist", "Artist A–Z")
        case .mostPlayed: ("Most Played", "Most Played")
        }
    }
}
