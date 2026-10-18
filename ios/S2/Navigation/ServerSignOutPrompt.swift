import Shared

/// The alert for a server whose session expired mid-session: Android's "Signed out of <server>" snackbar.
struct ServerSignOutPrompt: Equatable {
    let type: MediaProviderType

    var title: String { "Signed Out of \(type.title)" }
    var message: String { "Your \(type.title) session has expired. Sign in again to keep streaming from it." }
}
