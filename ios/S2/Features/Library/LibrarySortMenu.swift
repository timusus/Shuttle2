import Shared
import SwiftUI

/// A Library tab's sort menu, the toolbar twin of the artist screen's Songs header menu: a `Picker` of the tab's
/// orders in a `Menu` (a checkmark on the current one), choosing through the ViewModel, which saves it as Android does.
struct LibrarySortMenu<Order: Hashable>: View {
    let identifier: String
    let orders: [Order]
    let sortOrder: Order
    let title: (Order) -> String
    let onSelect: (Order) -> Void

    var body: some View {
        Menu {
            Picker("Sort By", selection: Binding(get: { sortOrder }, set: onSelect)) {
                ForEach(orders, id: \.self) { order in
                    Text(title(order)).tag(order)
                }
            }
        } label: {
            Label("Sort", systemImage: "arrow.up.arrow.down")
        }
        .accessibilityLabel("Sort")
        .accessibilityValue(title(sortOrder))
        .accessibilityIdentifier(identifier)
    }
}

/// The sort orders each Library tab offers, in menu order, and their titles. The ViewModels sort and save; the menu
/// lists only what the shared domain sorts by.
extension SongSortOrder {
    static let libraryMenuOrder: [SongSortOrder] = [.songName, .artistGroupKey, .albumGroupKey, .dateAdded, .playCount, .year]

    var libraryMenuTitle: String {
        switch self {
        case .songName: "Title"
        case .artistGroupKey: "Artist"
        case .albumGroupKey: "Album"
        case .dateAdded: "Date Added"
        case .playCount: "Play Count"
        case .year: "Year"
        default: "Title"
        }
    }
}

extension AlbumSortOrder {
    static let libraryMenuOrder: [AlbumSortOrder] = [.albumName, .artistGroupKey, .year, .dateAdded]

    var libraryMenuTitle: String {
        switch self {
        case .albumName: "Title"
        case .artistGroupKey: "Artist"
        case .year: "Year"
        case .dateAdded: "Date Added"
        default: "Title"
        }
    }
}

extension AlbumArtistSortOrder {
    static let libraryMenuOrder: [AlbumArtistSortOrder] = [.`default`, .albumCount]

    var libraryMenuTitle: String {
        switch self {
        case .albumCount: "Album Count"
        default: "Name"
        }
    }
}

extension GenreSortOrder {
    static let libraryMenuOrder: [GenreSortOrder] = [.`default`, .songCount]

    var libraryMenuTitle: String {
        switch self {
        case .songCount: "Song Count"
        default: "Name"
        }
    }
}

extension PlaylistSortOrder {
    /// `Default` is creation order (the playlist's id), oldest first.
    static let libraryMenuOrder: [PlaylistSortOrder] = [.name, .`default`]

    var libraryMenuTitle: String {
        switch self {
        case .`default`: "Date Created"
        default: "Name"
        }
    }
}

/// The date a row shows under a date-added sort: the medium date, e.g. "12 Mar 2026".
func libraryDateAdded(_ instant: KotlinInstant?) -> String? {
    instant.map { Date(timeIntervalSince1970: TimeInterval($0.toEpochMilliseconds()) / 1000).formatted(date: .abbreviated, time: .omitted) }
}
