import AppIntents

/// The App Shortcuts Siri, Spotlight and the Shortcuts app offer without any set-up (#758). Each phrase names the app;
/// Play Playlist's first phrase also names a playlist, from `PlaylistEntityQuery.suggestedEntities()`, which
/// `updateAppShortcutParameters()` refreshes after launch.
struct ShuttleShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: ShuffleLibraryIntent(),
            phrases: [
                "Shuffle my library in \(.applicationName)",
                "Shuffle all in \(.applicationName)",
                "Shuffle \(.applicationName)",
            ],
            shortTitle: "Shuffle All",
            systemImageName: "shuffle"
        )
        AppShortcut(
            intent: PlayPlaylistIntent(),
            phrases: [
                "Play \(\.$playlist) in \(.applicationName)",
                "Play the playlist \(\.$playlist) in \(.applicationName)",
                "Play a playlist in \(.applicationName)",
            ],
            shortTitle: "Play Playlist",
            systemImageName: "music.note.list"
        )
        AppShortcut(
            intent: TogglePlaybackIntent(),
            phrases: [
                "Play or pause \(.applicationName)",
                "Pause \(.applicationName)",
                "Resume \(.applicationName)",
            ],
            shortTitle: "Play or Pause",
            systemImageName: "playpause"
        )
        AppShortcut(
            intent: SkipToNextIntent(),
            phrases: [
                "Next song in \(.applicationName)",
                "Skip this song in \(.applicationName)",
            ],
            shortTitle: "Next Song",
            systemImageName: "forward"
        )
    }
}
