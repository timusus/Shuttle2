import AppIntents

/// The shortcuts Siri and Spotlight offer with no set-up; Play Playlist's first phrase names a playlist, refreshed after launch.
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
                // No "Pause"/"Resume" phrases: a toggle would do the opposite when the state isn't what the
                // listener thinks, and Siri's own media commands already pause and resume the playing app
                "Play or pause \(.applicationName)",
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
