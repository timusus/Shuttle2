import SwiftUI

/// How Now Playing is presented in a tier: a full-screen cover in `compact` (Android's player sheet), a form
/// sheet in `regular` and `wide`, where a phone column stretched over an iPad is mostly empty.
enum NowPlayingPresentationStyle: Equatable {
    case fullScreenCover
    case formSheet

    static func resolve(for tier: LayoutTier) -> NowPlayingPresentationStyle {
        tier == .compact ? .fullScreenCover : .formSheet
    }
}

/// How a sheet opened from inside Now Playing (the queue) is presented, after Shuttle Podcasts'
/// `playerSheet`: a sheet over the full-screen player in `compact`, a popover anchored to its button in
/// `regular` and `wide`, where a second sheet over the form sheet would cover it.
enum PlayerSubSheetStyle: Equatable {
    case sheet
    case popover

    static func resolve(for tier: LayoutTier) -> PlayerSubSheetStyle {
        tier == .compact ? .sheet : .popover
    }

    /// The popover's size in `regular` and `wide`.
    static let popoverSize = CGSize(width: 380, height: 460)
}

extension View {
    /// Presents Now Playing the way the tier wants it (`NowPlayingPresentationStyle`). Both presentations are
    /// attached and gated on the tier, so a Split View drag across the breakpoint while the player is up
    /// moves it to the other presentation instead of dropping it. The tier is re-injected: presented content
    /// sits outside the root's `.environment(\.layoutTier)`.
    func nowPlayingPresentation<Content: View>(
        isPresented: Binding<Bool>,
        tier: LayoutTier,
        @ViewBuilder content: @escaping () -> Content
    ) -> some View {
        let style = NowPlayingPresentationStyle.resolve(for: tier)
        return self
            .fullScreenCover(isPresented: isPresented.gated(on: style == .fullScreenCover)) {
                content()
                    .environment(\.layoutTier, tier)
            }
            .sheet(isPresented: isPresented.gated(on: style == .formSheet)) {
                content()
                    .environment(\.layoutTier, tier)
                    .modifier(FormSheetSizing())
            }
    }

    /// Presents a sheet from inside Now Playing the way the tier wants it (`PlayerSubSheetStyle`); attach it to
    /// the button that opens it, which the popover anchors to.
    func playerSheet<Content: View>(
        isPresented: Binding<Bool>,
        tier: LayoutTier,
        @ViewBuilder content: @escaping () -> Content
    ) -> some View {
        let style = PlayerSubSheetStyle.resolve(for: tier)
        return self
            .sheet(isPresented: isPresented.gated(on: style == .sheet)) {
                content()
                    .environment(\.layoutTier, tier)
                    .presentationDetents([.medium, .large])
                    .presentationDragIndicator(.visible)
            }
            .popover(isPresented: isPresented.gated(on: style == .popover)) {
                content()
                    .environment(\.layoutTier, tier)
                    .frame(width: PlayerSubSheetStyle.popoverSize.width, height: PlayerSubSheetStyle.popoverSize.height)
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
