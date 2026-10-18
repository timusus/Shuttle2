import SwiftUI

/// The hero's image (#781): `pending` until it has loaded, then the image, or nil when there's nothing to show. Either
/// way the hero runs full bleed, over a neutral fill until (or unless) an image arrives, so the name and Play/Shuffle
/// show at once.
enum ArtistHeroPhoto: Equatable {
    case loaded(UIImage?)
    case pending

    /// The longest side the image is decoded at, in points: past the widest iPhone and the iPad hero column, without
    /// decoding a server's full-size original.
    static let decodePoints: CGFloat = 520

    /// The image, nil while pending or when nothing loaded.
    var image: UIImage? {
        if case .loaded(let image) = self { image } else { nil }
    }
}

/// Loads the hero's `ArtistHeroPhoto` for `source`, then draws `content` with it. An image already in memory shows in the
/// first frame; otherwise `content` draws `.pending` until the load finishes.
struct ArtistHeroPhotoReader<Content: View>: View {
    let source: ArtworkSource
    var preset: ArtistHeroPhoto?
    @ViewBuilder let content: (ArtistHeroPhoto) -> Content

    @Environment(\.displayScale) private var displayScale
    /// The last load's result, under the source's cache key: the hero's source changes when the artist's albums arrive.
    @State private var loaded: (key: String, photo: ArtistHeroPhoto)?

    private var maxPixelSize: Int { Int((ArtistHeroPhoto.decodePoints * displayScale).rounded(.up)) }

    /// The image without waiting: given, already loaded, or already decoded.
    private var known: ArtistHeroPhoto? {
        if let preset { return preset }
        if let loaded, loaded.key == source.cacheKey { return loaded.photo }
        return ArtworkLoader.shared.cached(source, maxPixelSize: maxPixelSize).map { .loaded($0) }
    }

    var body: some View {
        content(known ?? .pending)
            .task(id: "\(source.cacheKey)@\(maxPixelSize)") {
                guard known == nil else { return }
                let key = source.cacheKey
                let image = await ArtworkLoader.shared.image(for: source, maxPixelSize: maxPixelSize)
                loaded = (key, .loaded(image))
            }
    }
}

/// The hero's backdrop: the image filling the frame, anchored to its top so a portrait photo keeps the faces (a
/// neutral fill while it's pending or when there's none), under a scrim at the top for the status bar and the bar buttons and one
/// at the bottom for the name. Its last `fadeHeight` fades out into whatever is behind it, so the photo melts into the
/// page rather than ending in a hard line; the name sits above the fade.
struct ArtistBackdrop: View {
    /// The photo; nil draws the placeholder.
    let image: UIImage?

    /// The height of the fade at the bottom, which the hero keeps its name above.
    static let fadeHeight: CGFloat = Spacing.xlarge
    /// The top scrim's height: past the status and navigation bars on every iPhone.
    static let topScrimHeight: CGFloat = 120

    var body: some View {
        Color.clear
            .overlay(alignment: .top) {
                if let image {
                    Image(uiImage: image)
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                } else {
                    Color(uiColor: .systemGray3)
                }
            }
            .clipped()
            .overlay(alignment: .top) {
                LinearGradient(
                    stops: [.init(color: .black.opacity(0.35), location: 0), .init(color: .black.opacity(0.15), location: 0.5), .init(color: .black.opacity(0), location: 1)],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .frame(height: Self.topScrimHeight)
            }
            .overlay {
                LinearGradient(
                    stops: [.init(color: .black.opacity(0), location: 0.45), .init(color: .black.opacity(0.7), location: 1)],
                    startPoint: .top,
                    endPoint: .bottom
                )
            }
            .mask {
                VStack(spacing: 0) {
                    Rectangle()
                    LinearGradient(
                        stops: [.init(color: .black, location: 0), .init(color: .black.opacity(0.45), location: 0.5), .init(color: .black.opacity(0), location: 1)],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                    .frame(height: Self.fadeHeight)
                }
            }
            .accessibilityHidden(true)
    }
}

/// The full-bleed hero's foreground: the artist's name, large and leading, and the albums · songs line, in white over
/// the bottom of the backdrop's scrim; then Play/Shuffle under the photo, on the page.
struct ArtistBleedHero: View {
    let title: String
    let subtitle: String?
    let bleed: DetailBleed
    var onPlay: () -> Void = {}
    var onShuffle: () -> Void = {}

    @Environment(\.layoutTier) private var layoutTier
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        let inset = AdaptiveLayout.contentInset(layoutTier)
        VStack(alignment: .leading, spacing: Spacing.medium) {
            VStack(alignment: .leading, spacing: Spacing.xsmall) {
                Text(title)
                    .font(.s2LargeTitle)
                    .foregroundStyle(.white)
                    .lineLimit(dynamicTypeSize.isAccessibilitySize ? nil : 2)
                    .minimumScaleFactor(0.75)
                    .accessibilityAddTraits(.isHeader)
                    .detailTitleProbe()
                if let subtitle, !subtitle.isEmpty {
                    Text(subtitle)
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(.white.opacity(0.85))
                }
            }
            .shadow(color: .black.opacity(0.3), radius: 6, y: 1)
            .padding(.horizontal, inset)
            // Clear of the backdrop's fade, so the name stays on the darkest part of the scrim.
            .padding(.bottom, ArtistBackdrop.fadeHeight)
            .frame(maxWidth: .infinity, minHeight: bleed.height, alignment: .bottomLeading)
            HeroActions(onPlay: onPlay, onShuffle: onShuffle)
                .padding(.horizontal, inset)
                .frame(maxWidth: bleed.isColumn || layoutTier == .compact ? .infinity : ArtworkSize.heroRegular + Spacing.xlarge + inset * 2, alignment: .leading)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
