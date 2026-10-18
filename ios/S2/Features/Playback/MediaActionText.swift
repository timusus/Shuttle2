import Shared
import SwiftUI

/// The text for the few `MediaActionMessage`s the POC shows; phase 5's string catalogue replaces this.
enum MediaActionText {
    static func alert(for message: any MediaActionMessage) -> String? {
        switch message {
        case let failed as MediaActionMessagePlaybackFailed:
            failed.reason.map { "Couldn't play: \($0)" } ?? "Couldn't play."
        case is MediaActionMessageNoSongs:
            "There's nothing to play."
        case let failed as MediaActionMessageDownloadFailed:
            String(localized: "Couldn't download \(Int(failed.songCount)) songs. Check you're signed in to the server and try again.")
        default:
            nil
        }
    }
}
