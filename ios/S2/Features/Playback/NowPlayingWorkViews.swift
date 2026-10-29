import SwiftUI

/// A song inside a work (after Android's `ClassicalTitle`): the composer in accent caps, the work largest with its key
/// and catalogue under it, then the movement as the title, the performers and the quality badge. Android's classical
/// title has no artist or album line to open, so none is drawn here either.
struct ClassicalTitleBlock<Actions: View>: View {
    let work: NowPlayingWorkState
    let secondaryInk: Color
    var qualityBadge: String?
    var spokenQualityBadge: String?
    /// Nil until the composer route is wired: the composer is then plain text.
    var onOpenComposer: (() -> Void)?
    @ViewBuilder var accessibilityActions: () -> Actions

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.tiny) {
            if let composer = work.composer {
                composerText(composer)
            }
            Text(work.work)
                .font(.s2HeroTitle)
                .lineLimit(2)
                .foregroundStyle(.primary)
                .accessibilityAddTraits(.isHeader)
                .accessibilityActions { accessibilityActions() }
                .accessibilityIdentifier("nowPlaying.title")
            if let detail = work.detail {
                MarqueeText(detail)
                    .font(.title3)
                    .foregroundStyle(secondaryInk)
                    .accessibilityIdentifier("nowPlaying.workDetail")
            }
            MarqueeText(work.movement)
                .font(.title3.weight(.semibold))
                .accessibilityIdentifier("nowPlaying.movement")
            if let performers = work.performers {
                MarqueeText(performers)
                    .font(.body)
                    .foregroundStyle(secondaryInk)
                    .accessibilityIdentifier("nowPlaying.performers")
            }
            if let badge = qualityBadge {
                Text(badge)
                    .font(.caption.weight(.medium))
                    .foregroundStyle(secondaryInk)
                    .padding(.top, Spacing.tiny)
                    .accessibilityLabel("Audio quality, \(spokenQualityBadge ?? badge)")
                    .accessibilityIdentifier("nowPlaying.quality")
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    @ViewBuilder
    private func composerText(_ composer: String) -> some View {
        let text = Text(composer.uppercased())
            .font(.subheadline.weight(.semibold))
            .tracking(1.5)
            .foregroundStyle(.tint)
            .lineLimit(1)
        if let onOpenComposer {
            Button(action: onOpenComposer) { text.contentShape(Rectangle()) }
                .buttonStyle(.plain)
                .accessibilityLabel(localizedString("composer_open", composer))
                .accessibilityIdentifier("nowPlaying.composer")
        } else {
            text.accessibilityIdentifier("nowPlaying.composer")
        }
    }
}

/// The ribbon over the current work, above the scrubber (after Android's `WorkRibbonBar` / `S2WorkRibbon`). A tap on a
/// segment jumps to its first movement; one too thin to aim at, a continuous bar, or the caption opens the movement list.
struct WorkRibbonView: View {
    let work: NowPlayingWorkState
    let positionMs: Int
    let secondaryInk: Color
    var onJump: (Int64) -> Void = { _ in }
    var onOpenList: () -> Void = {}

    private static let minJumpWidth: CGFloat = 24
    private static let barHeight: CGFloat = 4
    private static let gap: CGFloat = 4

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.tiny) {
            bar
            Button(action: onOpenList) {
                Text(work.caption(positionMs: positionMs))
                    .font(.caption)
                    .foregroundStyle(secondaryInk)
                    .padding(.vertical, Spacing.xsmall)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(work.caption(positionMs: positionMs))
            .accessibilityHint(localizedString("player_work_show_movements"))
            .accessibilityIdentifier("nowPlaying.workCaption")
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var bar: some View {
        GeometryReader { proxy in
            let total = CGFloat(max(1, work.segments.reduce(0) { $0 + $1.durationMs }))
            let gap = work.continuous ? 0 : Self.gap
            let usable = proxy.size.width - gap * CGFloat(max(0, work.segments.count - 1))
            HStack(spacing: gap) {
                ForEach(Array(work.segments.enumerated()), id: \.offset) { index, segment in
                    let fraction = max(0.001, CGFloat(segment.durationMs) / total)
                    let width = usable * fraction
                    segmentBar(index)
                        .frame(width: width, height: Self.barHeight)
                        .frame(height: 24)
                        .contentShape(Rectangle())
                        .onTapGesture { tap(segment, width: width) }
                        .accessibilityElement()
                        .accessibilityLabel(width >= Self.minJumpWidth && !work.continuous ? localizedString("player_work_play_movement", "\(segment.ordinal)") : localizedString("player_work_show_movements"))
                        .accessibilityAddTraits(.isButton)
                }
            }
            .frame(height: 24)
        }
        .frame(height: 24)
    }

    private func tap(_ segment: NowPlayingWorkState.Segment, width: CGFloat) {
        if !work.continuous, width >= Self.minJumpWidth, let uid = segment.queueUID {
            onJump(uid)
        } else {
            onOpenList()
        }
    }

    /// Earlier segments are muted, the current one fills with the accent as the movement plays.
    private func segmentBar(_ index: Int) -> some View {
        let shape = Capsule()
        return shape.fill(secondaryInk.opacity(index < work.currentSegment ? 0.6 : 0.3))
            .overlay(alignment: .leading) {
                if index == work.currentSegment {
                    GeometryReader { proxy in
                        shape.fill(.tint)
                            .frame(width: proxy.size.width * work.currentFill(positionMs: positionMs))
                    }
                }
            }
            .clipShape(shape)
    }
}

/// Every movement of the work under its composer and title; a tap on a row jumps to it (after Android's `WorkMovementSheet`).
struct WorkMovementSheet: View {
    let work: NowPlayingWorkState
    var onSelect: (Int64) -> Void = { _ in }

    var body: some View {
        NavigationStack {
            List {
                ForEach(Array(work.movements.enumerated()), id: \.offset) { index, movement in
                    Button {
                        if let uid = movement.queueUID { onSelect(uid) }
                    } label: {
                        HStack(spacing: Spacing.medium) {
                            Text(movement.numeral)
                                .font(.subheadline.monospacedDigit())
                                .foregroundStyle(.secondary)
                                .frame(minWidth: 28, alignment: .trailing)
                            Text(movement.name)
                                .foregroundStyle(index == work.currentMovement ? AnyShapeStyle(.tint) : AnyShapeStyle(.primary))
                                .frame(maxWidth: .infinity, alignment: .leading)
                            Text(formatDuration(ms: movement.durationMs))
                                .font(.subheadline.monospacedDigit())
                                .foregroundStyle(.secondary)
                        }
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(index == work.currentMovement ? .isSelected : [])
                    .accessibilityIdentifier("workMovement.\(index)")
                }
            }
            .listStyle(.plain)
            .navigationTitle(work.work)
            .navigationBarTitleDisplayMode(.inline)
            .safeAreaInset(edge: .top, spacing: 0) {
                if let composer = work.composer {
                    Text(composer)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.tint)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, Spacing.medium)
                        .padding(.vertical, Spacing.xsmall)
                }
            }
        }
        .presentationDetents([.medium, .large])
    }

    private func formatDuration(ms: Int) -> String {
        let seconds = max(0, ms / 1000)
        return "\(seconds / 60):" + String(format: "%02d", seconds % 60)
    }
}
