import SwiftUI

/// The one glass token (docs/design/ios-design-language.md §Materials): floating chrome (the mini player, Now
/// Playing's bars and discs) is Liquid Glass on iOS 26; below, a material, edged with the artwork hairline so it
/// holds its shape over a pale list. Content surfaces (cards, notices) are `s2SurfaceContainer`, never glass.
enum GlassFallback {
    /// The default below iOS 26: small chrome over artwork (a disc, the bottom bar).
    static let chrome: Material = .regularMaterial
    /// A larger floating card over a scrolling list (the mini player), where the list behind should read as colour
    /// rather than as text.
    static let card: Material = .thickMaterial
}

extension View {
    /// Liquid Glass in `shape` on iOS 26; `fallback` in `shape` with a hairline edge below.
    func glassSurface<S: InsettableShape>(in shape: S, fallback: Material = GlassFallback.chrome) -> some View {
        modifier(GlassSurface(shape: shape, fallback: fallback))
    }
}

private struct GlassSurface<S: InsettableShape>: ViewModifier {
    let shape: S
    let fallback: Material

    func body(content: Content) -> some View {
        if #available(iOS 26, *) {
            content.glassEffect(.regular, in: shape)
        } else {
            content
                .background(fallback, in: shape)
                .overlay { shape.strokeBorder(ArtworkHairline.color, lineWidth: ArtworkHairline.width) }
        }
    }
}
