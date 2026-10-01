import SwiftUI

/// The one glass token (docs/design/ios-design-language.md §Materials): floating chrome (the mini player, Now
/// Playing's bars and discs) is Liquid Glass on iOS 26; below, a material. The player's chrome keeps its original
/// look (ultra-thin over the artwork backdrop, no edge: the backdrop is already a dark, tinted ground, so a
/// hairline only adds noise). The mini player's card floats over a pale list, so it gets a thick material and the
/// artwork hairline to hold its shape. Content surfaces (cards, notices) are `s2SurfaceContainer`, never glass.
struct GlassFallback {
    let material: Material
    /// Whether the material is drawn with the artwork hairline around it.
    let edged: Bool

    /// Small chrome over the player's artwork backdrop: the bottom bar.
    static let chrome = GlassFallback(material: .ultraThinMaterial, edged: false)
    /// A glyph's disc over the player's backdrop: close, favourite. Regular, as before: a lone glyph needs more
    /// ground than the bar's grouped controls.
    static let disc = GlassFallback(material: .regularMaterial, edged: false)
    /// A larger floating card over a scrolling list (the mini player), where the list behind should read as colour
    /// rather than as text.
    static let card = GlassFallback(material: .thickMaterial, edged: true)
}

extension View {
    /// Liquid Glass in `shape` on iOS 26; `fallback` in `shape` below.
    func glassSurface<S: InsettableShape>(in shape: S, fallback: GlassFallback = .chrome) -> some View {
        modifier(GlassSurface(shape: shape, fallback: fallback))
    }
}

private struct GlassSurface<S: InsettableShape>: ViewModifier {
    let shape: S
    let fallback: GlassFallback

    func body(content: Content) -> some View {
        if #available(iOS 26, *) {
            content.glassEffect(.regular, in: shape)
        } else {
            content
                .background(fallback.material, in: shape)
                .overlay {
                    if fallback.edged {
                        shape.strokeBorder(ArtworkHairline.color, lineWidth: ArtworkHairline.width)
                    }
                }
        }
    }
}
