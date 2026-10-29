import Shared
import SwiftUI

/// What a classical album page does with its rows; mirrors the callbacks of Android's `classicalRows`.
struct ClassicalAlbumActions {
    var onPlayFrom: (Int) -> Void = { _ in }
    var onPlayWork: ([Song]) -> Void = { _ in }
    var onToggleWork: (Int64) -> Void = { _ in }
    var onPlayNext: ([Song]) -> Void = { _ in }
    var onAddToQueue: ([Song]) -> Void = { _ in }
    var onOpenRecording: (AlbumGroupKey) -> Void = { _ in }
    var onOpenComposer: ((String) -> Void)?
}

/// The rows of a classical album page (`AlbumRows.rows`): composer headings, work headers over their movements, act
/// and disc dividers, and lone tracks. Belongs inside the screen's row container.
struct ClassicalAlbumRowsView: View {
    let rows: [any Shared.AlbumRow]
    let album: Album
    let otherRecordings: [KotlinLong: [OtherRecording]]
    let isPlaying: Bool
    let actions: ClassicalAlbumActions
    let downloads: DetailDownloads
    var onSongInfo: (Song) -> Void = { _ in }

    var body: some View {
        ForEach(rows, id: \.key) { row in
            switch onEnum(of: row) {
            case .composerHeading(let heading):
                composerHeading(heading)
            case .actHeader(let act):
                SectionHeader(act.label, style: .disc).pinnedHeader()
            case .discDivider(let divider):
                SectionHeader(localizedString("album_detail_disc", Int32(truncatingIfNeeded: divider.disc)), style: .disc).pinnedHeader()
            case .workHeader(let work):
                workHeader(work)
            case .movement(let movement):
                Button { actions.onPlayFrom(Int(movement.playIndex)) } label: {
                    MovementRow(
                        numeral: movement.numeral,
                        name: movement.name,
                        durationMs: Int64(movement.song.duration),
                        playback: playback(movement.playing)
                    )
                }
                .buttonStyle(.plain)
                .songContextMenu(movement.song, onPlayNext: actions.onPlayNext, onAddToQueue: actions.onAddToQueue, onSongInfo: onSongInfo, downloads: downloads)
            case .track(let track):
                Button { actions.onPlayFrom(Int(track.playIndex)) } label: {
                    TrackRow(
                        number: track.song.track.map { Int($0.intValue) },
                        title: track.title ?? "Unknown",
                        subtitle: guestArtist(track.song),
                        durationMs: Int64(track.song.duration),
                        playback: playback(track.playing)
                    )
                }
                .buttonStyle(.plain)
                .songContextMenu(track.song, onPlayNext: actions.onPlayNext, onAddToQueue: actions.onAddToQueue, onSongInfo: onSongInfo, downloads: downloads)
            }
        }
    }

    @ViewBuilder
    private func composerHeading(_ heading: AlbumRowComposerHeading) -> some View {
        if let composer = heading.composer, let open = actions.onOpenComposer {
            SectionHeader(heading.name) { open(composer) }
                .accessibilityLabel(localizedString("composer_open", heading.name))
        } else {
            SectionHeader(heading.name)
        }
    }

    private func workHeader(_ work: AlbumRowWorkHeader) -> some View {
        let firstSongId = work.songs.first?.id ?? 0
        let recordings = otherRecordings[KotlinLong(value: firstSongId)] ?? []
        return WorkHeaderRow(
            title: work.title,
            detail: work.detail,
            meta: "\(countText(work.count)) · \(clockDuration(ms: work.durationMs))",
            credits: work.credits,
            collapsed: work.tap == .toggleCollapse ? work.collapsed : nil,
            playing: work.playing,
            isPlaying: isPlaying,
            onTap: {
                switch work.tap {
                case .toggleCollapse: actions.onToggleWork(firstSongId)
                case .play: actions.onPlayWork(work.songs)
                }
            },
            onPlay: { actions.onPlayWork(work.songs) }
        )
        .contextMenu {
            Button("Play Next", systemImage: "text.line.first.and.arrowtriangle.forward") { actions.onPlayNext(work.songs) }
            Button("Add to Queue", systemImage: "text.append") { actions.onAddToQueue(work.songs) }
            if !recordings.isEmpty {
                Menu(localizedPlural("album_detail_other_recordings", recordings.count), systemImage: "music.note.list") {
                    ForEach(Array(recordings.enumerated()), id: \.offset) { _, recording in
                        Button(recordingLabel(recording)) { actions.onOpenRecording(recording.groupKey) }
                    }
                }
            }
            DownloadMenuItems(songs: work.songs, downloads: downloads)
        }
    }

    private func recordingLabel(_ recording: OtherRecording) -> String {
        eyebrow(recording.leadPerformer ?? recording.albumArtist, recording.year.map { String($0.intValue) })
    }

    private func playback(_ playing: Bool) -> MediaRowPlayback {
        playing ? (isPlaying ? .playing : .paused) : .none
    }

    private func guestArtist(_ song: Song) -> String? {
        guard let artist = song.friendlyArtistName, artist != (album.friendlyArtistName ?? album.albumArtist) else { return nil }
        return artist
    }

    private func countText(_ text: UiText) -> String {
        guard let plural = text as? UiTextPlural else { return "" }
        return localizedPlural(plural.key.key, Int(plural.count))
    }
}

/// A work's header: title, detail line (key, catalogue, arrangement), count and duration, credits, and a trailing
/// disclosure (a long work folds) or Play button (a short one plays on tap).
struct WorkHeaderRow: View {
    let title: String
    let detail: String?
    let meta: String
    let credits: String?
    /// nil for a work that doesn't fold.
    let collapsed: Bool?
    let playing: Bool
    var isPlaying: Bool = false
    let onTap: () -> Void
    let onPlay: () -> Void

    @Environment(\.artworkTint) private var tint

    var body: some View {
        HStack(spacing: Spacing.smallMedium) {
            Button(action: onTap) {
                VStack(alignment: .leading, spacing: Spacing.tiny) {
                    Text(title)
                        .textRole(.rowTitle)
                        .fontWeight(.semibold)
                        .foregroundStyle(playing ? AnyShapeStyle(tint) : AnyShapeStyle(.primary))
                        .multilineTextAlignment(.leading)
                    if let detail {
                        Text(detail).textRole(.rowSubtitle).foregroundStyle(.s2TextSecondary).multilineTextAlignment(.leading)
                    }
                    if let credits {
                        Text(credits).textRole(.rowSubtitle).foregroundStyle(.s2TextSecondary).multilineTextAlignment(.leading)
                    }
                    Text(meta).textRole(.rowMeta).foregroundStyle(.s2TextSecondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            if let collapsed {
                Image(systemName: collapsed ? "chevron.down" : "chevron.up")
                    .foregroundStyle(.s2TextSecondary)
                    .accessibilityHidden(true)
            }
            Button(action: onPlay) {
                if playing {
                    NowPlayingIndicator(isAnimating: isPlaying).foregroundStyle(tint).frame(width: 20, height: 20)
                } else {
                    Image(systemName: "play.fill").foregroundStyle(tint)
                }
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Play \(title)")
        }
        .accessibilityIdentifier("classical.workHeader")
    }
}

/// A movement: its numeral and name share a baseline, the duration trailing. A name that wraps leaves the numeral
/// beside its first line.
struct MovementRow: View {
    let numeral: String?
    let name: String
    let durationMs: Int64
    var playback: MediaRowPlayback = .none

    @Environment(\.artworkTint) private var tint
    @ScaledMetric(relativeTo: .caption) private var numeralWidth = Spacing.large

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: Spacing.smallMedium) {
            Group {
                if playback != .none {
                    NowPlayingIndicator(isAnimating: playback == .playing)
                        .foregroundStyle(tint)
                        .frame(width: numeralWidth * 0.7, height: numeralWidth * 0.7)
                        .alignmentGuide(.firstTextBaseline) { $0[.bottom] }
                } else {
                    Text(numeral ?? "")
                        .font(.s2Time)
                        .foregroundStyle(.s2TextSecondary)
                }
            }
            .frame(width: numeralWidth, alignment: .trailing)
            Text(name)
                .textRole(.rowTitle)
                .foregroundStyle(playback == .none ? AnyShapeStyle(.primary) : AnyShapeStyle(tint))
                .fontWeight(playback == .none ? nil : .semibold)
                .multilineTextAlignment(.leading)
                .rowSeparator(.insetToTitle)
            Spacer(minLength: Spacing.small)
            SongDurationText(durationMs: durationMs)
        }
        .contentShape(Rectangle())
    }
}
