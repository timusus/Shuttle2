import Shared
import SwiftUI

/// A shelf tile's long-press actions on its whole album, as the Albums list offers them. Nil actions leave the tile
/// without a menu.
struct DetailAlbumActions {
    var onPlay: ((Album) -> Void)?
    var onPlayNext: ((Album) -> Void)?
    var onAddToQueue: ((Album) -> Void)?
}

/// A detail screen's shelf of album tiles under a `SectionHeader`, as one `List` section: a tap opens the tile's
/// album (`onAlbumTap`), and the tapped tile is the zoom source for it. Only the tapped one, as on Home
/// (`ZoomSourceSelection`): the album can also be on a Home shelf or another screen's shelf still live under this
/// screen in the stack. A long press opens
/// `albumActions`, when given.
struct DetailAlbumShelf: View {
    let title: String
    let albums: [Album]
    let subtitle: (Album) -> String?
    let onAlbumTap: (Album) -> Void
    var albumActions = DetailAlbumActions()
    var tileIdentifier = "detailTile.album"

    @Environment(\.layoutTier) private var layoutTier
    @Environment(\.zoomTiles) private var zoomTiles

    var body: some View {
        let inset = AdaptiveLayout.contentInset(layoutTier)
        Section {
            VStack(alignment: .leading, spacing: Spacing.smallMedium) {
                SectionHeader(title)
                    .padding(.horizontal, inset)
                Shelf(inset: inset) {
                    ForEach(albums, id: \.stableId) { album in
                        let tileKey = "detailShelf|\(album.stableId)"
                        Button {
                            zoomTiles?.select(tileKey)
                            onAlbumTap(album)
                        } label: {
                            AlbumTileLabel(album: album, subtitle: subtitle(album))
                        }
                        .buttonStyle(.pressScale)
                        .zoomSource(id: Route.album(album).cacheKey, tileKey: tileKey)
                        .modifier(DetailAlbumMenu(album: album, subtitle: subtitle(album), actions: albumActions))
                        .accessibilityIdentifier(tileIdentifier)
                    }
                }
            }
            .padding(.vertical, Spacing.small)
            .listRowInsets(EdgeInsets())
            .listRowBackground(Color.clear)
        }
        .rowSeparator(.none)
    }
}

/// A shelf tile's context menu from its `DetailAlbumActions`: only the actions given, and none at all without any.
private struct DetailAlbumMenu: ViewModifier {
    let album: Album
    let subtitle: String?
    let actions: DetailAlbumActions

    func body(content: Content) -> some View {
        if actions.onPlay == nil && actions.onPlayNext == nil && actions.onAddToQueue == nil {
            content
        } else {
            // The tile itself as the preview: inside a List the default lifts the whole shelf row.
            content.contextMenu {
                DetailAlbumMenuItems(album: album, actions: actions)
            } preview: {
                AlbumTileLabel(album: album, subtitle: subtitle)
                    .padding(Spacing.smallMedium)
            }
        }
    }
}

/// The items of a shelf tile's context menu, a view of its own so tests can inspect it (ViewInspector can't open a
/// `.contextMenu`).
struct DetailAlbumMenuItems: View {
    let album: Album
    let actions: DetailAlbumActions

    var body: some View {
        if let onPlay = actions.onPlay {
            Button("Play", systemImage: "play") { onPlay(album) }
        }
        if let onPlayNext = actions.onPlayNext {
            Button("Play Next", systemImage: "text.line.first.and.arrowtriangle.forward") { onPlayNext(album) }
        }
        if let onAddToQueue = actions.onAddToQueue {
            Button("Add to Queue", systemImage: "text.append") { onAddToQueue(album) }
        }
    }
}
