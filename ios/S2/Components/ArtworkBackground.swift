import SwiftUI

/// Now Playing's full-bleed backdrop (#624): the cover's colour top to bottom, with a blurred, saturated copy of the
/// cover glowing through the top half, after Apple Music and Shuttle Podcasts.
///
/// Three layers, all from `palette` (`PlayerPalette`): a gradient from the hue's `glow` to its `ground`; the cover
/// scaled to fill, blurred heavily and saturated, masked so it is strong behind the cover and fades out toward the
/// controls; and a scrim of the ground that reaches 85% by the control zone, so the tint and captions, which
/// `PlayerPalette` measures with the cover showing through (`textShowThrough`, `controlShowThrough`), still clear AA
/// whatever the cover. With no source, or before the cover arrives, it is the palette's gradient alone.
///
/// Put it in a `.background { }` of the screen; it ignores the safe area itself.
struct ArtworkBackground: View {
    let source: ArtworkSource?
    let palette: PlayerPalette
    /// Where the controls sit: under the cover, or beside it (from `AdaptiveLayout.twoColumnMinWidth`), where they
    /// run up into the top half and the cover can only show faintly anywhere.
    var layout: Layout = .stacked
    /// Where the cover comes from; tests hand in one with a stubbed network.
    var loader: ArtworkLoader = .shared

    enum Layout {
        case stacked
        case sideBySide
    }

    /// 60 pt of blur over a 1.3x scale: nothing above 256 px survives that, however big the screen.
    static let pixels = 256
    /// The blurred cover's saturation boost: a blur averages colours toward grey, this brings them back.
    static let saturation = 1.5

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.colorSchemeContrast) private var colorSchemeContrast
    @State private var image: UIImage?

    var body: some View {
        let ground = ContrastSafeTint.color(palette.ground)
        ZStack {
            LinearGradient(
                colors: [ContrastSafeTint.color(palette.glow), ground],
                startPoint: .top,
                endPoint: .center
            )

            if let image {
                Image(uiImage: image)
                    .resizable()
                    .aspectRatio(contentMode: .fill)
                    .blur(radius: 60)
                    .saturation(Self.saturation)
                    .scaleEffect(1.3) // no light edges where the blur runs out
                    .mask(Self.artworkMask)
                    .transition(.opacity)
            }

            LinearGradient(
                stops: Self.scrimStops(layout: layout, increasedContrast: colorSchemeContrast == .increased).map {
                    .init(color: ground.opacity($0.opacity), location: $0.location)
                },
                startPoint: .top,
                endPoint: .bottom
            )
        }
        .ignoresSafeArea()
        .animation(Motion.backdropChange.reduced(reduceMotion), value: image)
        .animation(Motion.tintChange.reduced(reduceMotion), value: palette)
        .task(id: source?.id) {
            let loaded = await Self.image(for: source, loader: loader)
            if !Task.isCancelled { image = loaded }
        }
        // A blur of the cover already on screen: nothing for VoiceOver to say.
        .accessibilityHidden(true)
    }

    /// The cover to blur: the first of `source`'s candidates that loads (the media server's image, else the S2
    /// artwork API's), from `loader`'s cache when it is there, decoded at `pixels`. Nil with no source or no cover.
    @MainActor
    static func image(for source: ArtworkSource?, loader: ArtworkLoader) async -> UIImage? {
        guard let source else { return nil }
        return await loader.image(for: source, maxPixelSize: pixels)
    }

    /// The blurred cover's strength down the screen: rich behind the cover, faint by the controls.
    static let artworkMask = LinearGradient(
        stops: [
            .init(color: .black.opacity(0.9), location: 0),
            .init(color: .black.opacity(0.7), location: 0.45),
            .init(color: .black.opacity(0.25), location: 0.75),
            .init(color: .black.opacity(0.15), location: 1),
        ],
        startPoint: .top,
        endPoint: .bottom
    )

    /// The ground's scrim over the blurred cover, as (opacity, location). Together with `artworkMask`, no more of the
    /// cover than `PlayerPalette` allows for shows where there is ink: stacked, the title from 60% down
    /// (about 14%) and the scrubber, transport and capsule from 70% (under 6%); side by side, under 6% anywhere.
    static func scrimStops(layout: Layout, increasedContrast: Bool) -> [(opacity: Double, location: Double)] {
        switch (layout, increasedContrast) {
        case (.sideBySide, true): [(1, 0), (1, 1)]
        case (.sideBySide, false): [(0.94, 0), (0.95, 1)]
        case (.stacked, true): [(0.2, 0), (0.6, 0.45), (1, 0.6), (1, 1)]
        case (.stacked, false): [(0.05, 0), (0.25, 0.4), (0.7, 0.6), (0.85, 0.7), (0.9, 1)]
        }
    }
}
