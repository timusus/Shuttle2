import Shared
import SwiftUI

/// The playing song's menu, on a long press of Now Playing's cover or title: the actions the ViewModel offers it that
/// iOS has a screen for, in its order, with Add to Playlist as a submenu and Exclude marked destructive.
struct NowPlayingSongMenu: View {
    let songActions: [NowPlayingSongAction]
    let playlists: [PlaylistOption]
    let onAction: (NowPlayingSongAction) -> Void
    let onNewPlaylist: () -> Void
    let onAddToPlaylist: (PlaylistChoice) -> Void

    var body: some View {
        ForEach(songActions, id: \.self) { action in
            switch action {
            case .addToPlaylist:
                Menu {
                    NowPlayingPlaylistChoices(playlists: playlists, onNewPlaylist: onNewPlaylist, onChoose: onAddToPlaylist)
                } label: {
                    Label(action.title, systemImage: action.systemImage)
                }
            case .exclude:
                Button(action.title, systemImage: action.systemImage, role: .destructive) { onAction(action) }
            default:
                Button(action.title, systemImage: action.systemImage) { onAction(action) }
            }
        }
    }
}

/// Where Add to Playlist can put the song: a new playlist, Favorites, or one of `playlists`.
struct NowPlayingPlaylistChoices: View {
    let playlists: [PlaylistOption]
    let onNewPlaylist: () -> Void
    let onChoose: (PlaylistChoice) -> Void

    var body: some View {
        Button("New Playlist…", systemImage: "plus", action: onNewPlaylist)
        Button("Favorites", systemImage: "heart") { onChoose(.favourites) }
        ForEach(playlists) { playlist in
            Button(playlist.name) { onChoose(.playlist(id: playlist.id)) }
        }
    }
}
