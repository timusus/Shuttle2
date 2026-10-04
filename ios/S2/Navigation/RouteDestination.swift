import SwiftUI

/// The screen for every `Route`, keyed by case. `navigationDestination(for: Route.self)` and each library
/// category's own root (regular/wide) resolve to this (`docs/architecture/ios-port/phase-5-ios-app.md` section 3).
struct RouteDestinationView: View {
    let route: Route

    var body: some View {
        switch route {
        case .libraryCategory(.songs):
            SongListView()
        case .libraryCategory(.albums):
            AlbumListView()
        case .sources:
            SourcesView()
        case .server(let type):
            ServerDetailView(typeName: type)
        case .libraryCategory(.albumArtists):
            AlbumArtistListView()
        case .libraryCategory(.genres):
            GenreListView()
        case .libraryCategory(.playlists):
            PlaylistListView()
        case .album(let albumKey, let albumArtistKey, let albumIdentity):
            // Album and artist tiles (Home's shelves, an artist's albums) zoom into their screen on iOS 18+; the
            // source's id is the same `cacheKey`.
            AlbumDetailView(albumKey: albumKey, albumArtistKey: albumArtistKey, albumIdentity: albumIdentity)
                .zoomDestination(id: route.cacheKey)
        case .albumArtist(let albumArtistKey):
            AlbumArtistDetailView(albumArtistKey: albumArtistKey)
                .zoomDestination(id: route.cacheKey)
        case .genre(let name):
            GenreDetailView(name: name)
        case .playlist(let id):
            PlaylistDetailView(id: id)
        case .smartPlaylist(let id):
            SmartPlaylistDetailView(id: id)
        case .equalizer:
            EqualizerView()
        case .scrobbling:
            ScrobblingView()
        }
    }
}

extension View {
    /// One `navigationDestination` for every stack, mapping each pushed `Route` to its screen, with the mini player
    /// inset as on the root. Apply it to the stack's root screen, inside the `NavigationStack`: on the stack itself
    /// SwiftUI ignores it and no `NavigationLink` pushes.
    ///
    /// It also gives the stack its zoom namespace (`\.zoomNamespace`), shared by the root and every pushed screen so
    /// a tile on either zooms into the screen it opens; one set higher up (`ContentView`) wins. With it goes the
    /// stack's one zoom source (`\.zoomTiles`): each screen's tiles are keyed under the screen (its route's
    /// `cacheKey`, or the stack's root), and the last tap anywhere in the stack picks the one tile that's a source.
    ///
    /// `insetsMiniPlayer: false` for a stack in a sheet (Settings), which covers the shell and its mini player:
    /// the bar would otherwise draw over the sheet's pushed screens (#682).
    func routeDestinations(showNowPlaying: Binding<Bool>, insetsMiniPlayer: Bool = true) -> some View {
        modifier(RouteDestinationsModifier(showNowPlaying: showNowPlaying, insetsMiniPlayer: insetsMiniPlayer))
    }
}

private struct RouteDestinationsModifier: ViewModifier {
    let showNowPlaying: Binding<Bool>
    let insetsMiniPlayer: Bool

    @Environment(\.zoomNamespace) private var inheritedNamespace
    @Environment(\.zoomTiles) private var inheritedZoomTiles
    @Namespace private var stackNamespace
    @State private var stackSelection = ZoomSourceSelection()
    /// The root screen's key: unique, as a stack inside another (a sheet's) shares the outer one's namespace.
    @State private var rootKey = "root|\(UUID().uuidString)"

    func body(content: Content) -> some View {
        let namespace = inheritedNamespace ?? stackNamespace
        // Sources in one namespace share one selection, so a nested stack takes the outer one's with its namespace.
        let selection = inheritedNamespace.flatMap { _ in inheritedZoomTiles?.selection } ?? stackSelection
        content
            .environment(\.zoomNamespace, namespace)
            .environment(\.zoomTiles, ZoomTiles(selection: selection, screen: rootKey))
            .navigationDestination(for: Route.self) { route in
                RouteDestinationView(route: route)
                    .modifier(OptionalMiniPlayerInset(showNowPlaying: showNowPlaying, isEnabled: insetsMiniPlayer))
                    .environment(\.zoomNamespace, namespace)
                    .environment(\.zoomTiles, ZoomTiles(selection: selection, screen: route.cacheKey))
            }
    }
}

private struct OptionalMiniPlayerInset: ViewModifier {
    let showNowPlaying: Binding<Bool>
    let isEnabled: Bool

    @ViewBuilder
    func body(content: Content) -> some View {
        if isEnabled {
            content.miniPlayerInset(showNowPlaying: showNowPlaying)
        } else {
            content
        }
    }
}
