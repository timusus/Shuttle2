import SwiftUI

/// A blurred, full-bleed cover behind Now Playing, after Shuttle Podcasts' `ArtworkBackground`: the artwork
/// scaled to fill, blurred heavily and slightly desaturated, under a system-background scrim that keeps the
/// cover's glow at the top and reaches 96% by the control zone, so a tint that clears 4.5:1 on the scheme's
/// ground (`ContrastSafeTint`) still clears it here whatever the cover. With no source, or before the cover
/// arrives, it is the plain system background.
///
/// Put it in a `.background { }` of the screen; it ignores the safe area itself.
struct ArtworkBackground: View {
    let source: ArtworkSource?

    /// 60 pt of blur over a 1.3x scale: nothing above 256 px survives that, however big the screen.
    static let pixels = 256

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var image: UIImage?

    var body: some View {
        ZStack {
            Group {
                if let image {
                    Image(uiImage: image)
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                        .transition(.opacity)
                } else {
                    Color(.systemBackground)
                }
            }
            .ignoresSafeArea()
            .blur(radius: 60)
            .saturation(0.8)
            .scaleEffect(1.3) // no light edges where the blur runs out

            Self.scrim.ignoresSafeArea()
        }
        .animation(Motion.backdropChange.reduced(reduceMotion), value: image)
        .task(id: source?.id) {
            guard let source, let string = try? await source.load(), let url = URL(string: string) else {
                image = nil
                return
            }
            let loaded = await ArtworkLoader.shared.image(for: url, maxPixelSize: Self.pixels)
            if !Task.isCancelled { image = loaded }
        }
        // A blur of the cover already on screen: nothing for VoiceOver to say.
        .accessibilityHidden(true)
    }

    /// The legibility scrim. The stops are Podcasts' device-measured ones: at 85% a cream cover left tinted
    /// glyphs at 3.9:1 in dark mode, at 92% a dark cover left them at 4.4:1 in light mode.
    static let scrim = LinearGradient(
        stops: [
            .init(color: Color(.systemBackground).opacity(0.3), location: 0),
            .init(color: Color(.systemBackground).opacity(0.6), location: 0.5),
            .init(color: Color(.systemBackground).opacity(0.96), location: 0.72),
            .init(color: Color(.systemBackground).opacity(0.97), location: 1),
        ],
        startPoint: .top,
        endPoint: .bottom
    )
}
