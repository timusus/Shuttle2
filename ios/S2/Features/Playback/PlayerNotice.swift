import Shared
import SwiftUI

/// A short message Now Playing shows over its content after something happened (a song removed from the queue, added
/// to a playlist), with an optional button that takes it back or retries: iOS's stand-in for Android's snackbar, after
/// Shuttle Podcasts' `UndoToast`. A newer notice replaces the one showing.
struct PlayerNotice: Identifiable, Equatable {
    let id = UUID()
    let message: String
    var actionTitle: String?
    var action: (() -> Void)?

    static func == (lhs: PlayerNotice, rhs: PlayerNotice) -> Bool { lhs.id == rhs.id }

    /// How long a notice stays up.
    static let duration: Duration = .seconds(5)
}

/// What Now Playing does with one of `PlayerViewModel`'s one-shot `PlayerUiEvent`s (Android's `ShellRoute`
/// `PlayerEvents`): show a notice, or open a screen (Go to Album, Go to Artist).
enum PlayerEventOutcome {
    case notice(PlayerNotice)
    case open(Route)

    /// Maps `event`; Undo and a snackbar action call the matching closure. Nil for an event with nothing to show
    /// on iOS (a silent result, or a screen iOS doesn't have yet).
    static func resolve(
        _ event: any PlayerUiEvent,
        undoClearQueue: @escaping () -> Void,
        undoRemoveQueueItem: @escaping () -> Void,
        send: @escaping (any MediaAction) -> Void
    ) -> PlayerEventOutcome? {
        switch event {
        case is PlayerUiEventQueueCleared:
            return .notice(PlayerNotice(message: "Queue cleared", actionTitle: "Undo", action: undoClearQueue))
        case is PlayerUiEventQueueItemRemoved:
            return .notice(PlayerNotice(message: "Removed from queue", actionTitle: "Undo", action: undoRemoveQueueItem))
        case let skipped as PlayerUiEventServerSongSkipped:
            return .notice(PlayerNotice(message: "Skipped “\(skipped.songTitle)” — streaming needs Shuttle Music Pro"))
        case let done as PlayerUiEventMediaActionDone:
            return resolve(done.result, send: send)
        default:
            return nil
        }
    }

    /// Maps a shared action's result: Now Playing's events carry these, and the Library's song lists show the same
    /// notices (`mediaActionResults(_:handled:send:)`), Exclude's with its Undo (#650).
    static func resolve(_ result: any MediaActionResult, send: @escaping (any MediaAction) -> Void) -> PlayerEventOutcome? {
        switch result {
        case let message as MediaActionResultMessage:
            guard let text = MediaActionText.notice(for: message.message) else { return nil }
            guard let snackbar = message.action else { return .notice(PlayerNotice(message: text)) }
            let action = snackbar.action
            return .notice(PlayerNotice(message: text, actionTitle: MediaActionText.title(for: snackbar.label), action: { send(action) }))
        case let navigate as MediaActionResultNavigate:
            switch navigate.target {
            case let album as NavigationTargetAlbum: return .open(.album(album.album))
            case let artist as NavigationTargetAlbumArtist: return .open(.albumArtist(artist.albumArtist))
            default: return nil
            }
        default:
            return nil
        }
    }
}

extension MediaActionText {
    /// The text of a message a song action on Now Playing can report, Android's wording (`MediaActionMessageText.kt`);
    /// phase 5's string catalogue replaces this.
    static func notice(for message: any MediaActionMessage) -> String? {
        switch message {
        case let added as MediaActionMessageAddedToPlaylist:
            songs(added.songCount, "added to \(added.playlistName)")
        case let duplicate as MediaActionMessageAlreadyInPlaylist:
            "\(duplicate.duplicateCount) already in \(duplicate.playlistName)"
        case let failed as MediaActionMessageAddToPlaylistFailed:
            "Failed to add songs to playlist: \(failed.reason ?? "An unknown error occurred")"
        case let created as MediaActionMessagePlaylistCreated:
            "‘\(created.playlistName)’ successfully created"
        case let favourited as MediaActionMessageAddedToFavourites:
            songs(favourited.songCount, "added to Favorites")
        case let unfavourited as MediaActionMessageRemovedFromFavourites:
            songs(unfavourited.songCount, "removed from Favorites")
        case let excluded as MediaActionMessageExcluded:
            songs(excluded.songCount, "excluded")
        case is MediaActionMessageNotFound:
            "Not in your library"
        default:
            alert(for: message)
        }
    }

    /// A snackbar button's title.
    static func title(for label: SnackbarAction.Label) -> String {
        switch label {
        case .undo: "Undo"
        case .addAnyway: "Add Anyway"
        }
    }

    private static func songs(_ count: Int32, _ what: String) -> String {
        count == 1 ? "1 song \(what)" : "\(count) songs \(what)"
    }
}

/// The notice itself: its message and button on a material capsule, dismissed by a tap.
struct PlayerNoticeBanner: View {
    let notice: PlayerNotice
    let onDismiss: () -> Void

    var body: some View {
        HStack(spacing: Spacing.smallMedium) {
            Text(notice.message)
                .font(.subheadline)
                .lineLimit(2)
                .frame(maxWidth: .infinity, alignment: .leading)
            if let title = notice.actionTitle, let action = notice.action {
                Button(title) {
                    onDismiss()
                    action()
                }
                .font(.subheadline.weight(.semibold))
                .buttonStyle(.plain)
                .foregroundStyle(.tint)
                .frame(minHeight: 44)
                .accessibilityIdentifier("playerNotice.action")
            }
        }
        .padding(.horizontal, Spacing.medium)
        .padding(.vertical, Spacing.small)
        .frame(minHeight: 48)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: ArtworkCorner.tile, style: .continuous))
        .shadow(color: .black.opacity(0.18), radius: 12, y: 4)
        .padding(.horizontal, Spacing.medium)
        .contentShape(Rectangle())
        .onTapGesture(perform: onDismiss)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("playerNotice")
    }
}

extension View {
    /// Shows `notice` along the bottom edge, sliding up (fading with Reduce Motion), and clears it after
    /// `PlayerNotice.duration` or a tap. VoiceOver announces it.
    func playerNotice(_ notice: Binding<PlayerNotice?>) -> some View {
        modifier(PlayerNoticeOverlay(notice: notice))
    }
}

private struct PlayerNoticeOverlay: ViewModifier {
    @Binding var notice: PlayerNotice?
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func body(content: Content) -> some View {
        content
            .overlay(alignment: .bottom) {
                if let current = notice {
                    PlayerNoticeBanner(notice: current) { notice = nil }
                        .padding(.bottom, Spacing.small)
                        .transition(reduceMotion ? .opacity : .move(edge: .bottom).combined(with: .opacity))
                        .task(id: current.id) {
                            AccessibilityNotification.Announcement(current.message).post()
                            try? await Task.sleep(for: PlayerNotice.duration)
                            if notice?.id == current.id { notice = nil }
                        }
                }
            }
            .animation(reduceMotion ? .easeInOut(duration: 0.2) : .spring(response: 0.35, dampingFraction: 0.85), value: notice)
    }
}
