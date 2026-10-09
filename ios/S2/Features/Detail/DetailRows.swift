import Shared
import SwiftUI

/// A numbered track: the track number in `.s2Time` (the playing indicator in its place while it's the current
/// song), the title and an optional subtitle, the duration trailing. The album screen's row, where every track
/// shares the cover so a thumbnail would say nothing.
struct TrackRow: View {
    let number: Int?
    let title: String
    var subtitle: String?
    let durationMs: Int64
    var playback: MediaRowPlayback = .none

    @Environment(\.artworkTint) private var tint
    @ScaledMetric(relativeTo: .caption) private var numberWidth = Spacing.large

    var body: some View {
        HStack(spacing: Spacing.smallMedium) {
            Group {
                if playback != .none {
                    NowPlayingIndicator(isAnimating: playback == .playing)
                        .foregroundStyle(tint)
                        .frame(width: numberWidth * 0.7, height: numberWidth * 0.7)
                } else if let number {
                    Text("\(number)")
                        .font(.s2Time)
                        .foregroundStyle(.s2TextSecondary)
                }
            }
            .frame(width: numberWidth, alignment: .center)
            VStack(alignment: .leading, spacing: Spacing.tiny) {
                Text(title)
                    .lineLimit(1)
                    .foregroundStyle(playback == .none ? AnyShapeStyle(.primary) : AnyShapeStyle(tint))
                    .fontWeight(playback == .none ? nil : .semibold)
                if let subtitle, !subtitle.isEmpty {
                    Text(subtitle)
                        .font(.subheadline)
                        .foregroundStyle(.s2TextSecondary)
                        .lineLimit(1)
                }
            }
            // A text-only row: its separator starts at the title.
            .rowSeparator(.insetToTitle)
            Spacer(minLength: Spacing.small)
            SongDurationText(durationMs: durationMs)
        }
        .contentShape(Rectangle())
    }
}

/// A song's duration as a row's trailing time.
struct SongDurationText: View {
    let durationMs: Int64

    var body: some View {
        Text(Duration.milliseconds(durationMs).formatted(.time(pattern: .minuteSecond)))
            .font(.s2RowMeta)
            .foregroundStyle(.s2TextSecondary)
    }
}
