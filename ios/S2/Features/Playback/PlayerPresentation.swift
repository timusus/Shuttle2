import SwiftUI

extension View {
    /// Presents Now Playing the way the tier wants it: a full-screen cover in `compact` (Android's player
    /// sheet), a form sheet in `regular` and `wide`, where a phone column stretched over an iPad is mostly
    /// empty. Both presentations are attached and gated on the tier, so a Split View drag across the
    /// breakpoint while the player is up moves it to the other presentation instead of dropping it. The
    /// tier is re-injected: presented content sits outside the root's `.environment(\.layoutTier)`.
    func nowPlayingPresentation<Content: View>(
        isPresented: Binding<Bool>,
        tier: LayoutTier,
        @ViewBuilder content: @escaping () -> Content
    ) -> some View {
        let isCompact = tier == .compact
        return self
            .fullScreenCover(isPresented: isPresented.gated(on: isCompact)) {
                content()
                    .environment(\.layoutTier, tier)
            }
            .sheet(isPresented: isPresented.gated(on: !isCompact)) {
                content()
                    .environment(\.layoutTier, tier)
                    .modifier(FormSheetSizing())
            }
    }
}

private extension Binding<Bool> {
    /// Reads true only while `condition` holds; writes pass through, so a dismissal clears the shared
    /// flag whichever presentation was showing.
    func gated(on condition: Bool) -> Binding<Bool> {
        Binding(get: { wrappedValue && condition }, set: { wrappedValue = $0 })
    }
}

/// The form sheet, or the nearest iOS 17 has: the large detent with the column held to form width.
private struct FormSheetSizing: ViewModifier {
    static let formWidth: CGFloat = 540

    func body(content: Content) -> some View {
        if #available(iOS 18, *) {
            content.presentationSizing(.form)
        } else {
            content
                .frame(maxWidth: Self.formWidth)
                .presentationDetents([.large])
        }
    }
}
