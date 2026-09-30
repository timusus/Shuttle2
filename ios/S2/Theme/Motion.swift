import SwiftUI

/// S2's motion: the named animations, press feedback, Reduce Motion, and the zoom and matched-geometry
/// namespaces. After Shuttle Podcasts' `ZoomNamespace`.
///
/// The rule: every animation goes through `.reduced(reduceMotion)` (read the flag from
/// `@Environment(\.accessibilityReduceMotion)`), so with Reduce Motion on the state still changes, in one
/// frame, and slides become fades.
enum Motion {
    /// A press scaling down and back (`pressScale`, the player's buttons).
    static let press = Animation.spring(response: 0.2, dampingFraction: 0.8)
    /// The Now Playing cover shrinking on pause and growing on play.
    static let coverScale = Animation.spring(response: 0.45, dampingFraction: 0.75)
    /// The artwork tint moving to a new cover's.
    static let tintChange = Animation.easeInOut(duration: 0.5)
    /// `ArtworkBackground` cross-fading to a new cover.
    static let backdropChange = Animation.easeInOut(duration: 0.8)
    /// The mini player arriving with the first queued song and leaving when the queue empties.
    static let miniPlayerVisibility = Animation.spring(response: 0.35, dampingFraction: 0.85)
    /// A section unfolding or folding its rows, and its chevron turning (the artist screen's album sections).
    static let disclosure = Animation.snappy(duration: 0.3)

    /// How far `pressScale` scales a pressed view.
    static let pressedScale: CGFloat = 0.96
    /// The Now Playing cover's scale while paused.
    static let pausedCoverScale: CGFloat = 0.88
}

extension Animation {
    /// This animation, or none when Reduce Motion is on. For `.animation(_:value:)` and `withAnimation`, which
    /// both take an optional.
    func reduced(_ reduceMotion: Bool) -> Animation? {
        reduceMotion ? nil : self
    }
}

extension AnyTransition {
    /// This transition, or a plain fade when Reduce Motion is on: the view still arrives.
    func reduced(_ reduceMotion: Bool) -> AnyTransition {
        reduceMotion ? .opacity : self
    }
}

// MARK: - Press feedback

/// A `ButtonStyle` that scales a button down while it is pressed: the player's transport and list rows. It
/// sees the button's own pressed state, including a press that slides off, and never competes with a scroll
/// view's drag the way a `DragGesture(minimumDistance: 0)` would.
struct PressScaleButtonStyle: ButtonStyle {
    var scale: CGFloat = Motion.pressedScale

    func makeBody(configuration: Configuration) -> some View {
        PressScaleBody(configuration: configuration, scale: scale)
    }

    private struct PressScaleBody: View {
        let configuration: Configuration
        let scale: CGFloat
        @Environment(\.accessibilityReduceMotion) private var reduceMotion

        var body: some View {
            configuration.label
                .scaleEffect(configuration.isPressed && !reduceMotion ? scale : 1)
                .animation(Motion.press.reduced(reduceMotion), value: configuration.isPressed)
        }
    }
}

extension ButtonStyle where Self == PressScaleButtonStyle {
    /// A plain button that scales down while pressed.
    static var pressScale: PressScaleButtonStyle { PressScaleButtonStyle() }
}

// MARK: - Zoom and matched-geometry namespaces

extension EnvironmentValues {
    /// The namespace a tile zooms into its detail screen in (iOS 18+). Set once above the navigation stacks
    /// with `@Namespace` + `.environment(\.zoomNamespace, ns)`; nil means no zoom, a plain push.
    @Entry var zoomNamespace: Namespace.ID?
    /// The namespace the mini player's cover and Now Playing's cover match in.
    @Entry var nowPlayingNamespace: Namespace.ID?
}

extension View {
    /// Marks this view as the source of a zoom transition to the screen with `zoomDestination(id:)`. On iOS 17,
    /// or without a `\.zoomNamespace`, it does nothing and the push is the standard one.
    func zoomSource(id: some Hashable) -> some View {
        modifier(ZoomSourceModifier(id: AnyHashable(id)))
    }

    /// Marks this screen as the destination of the zoom from `zoomSource(id:)`. Apply to the pushed screen's root.
    func zoomDestination(id: some Hashable) -> some View {
        modifier(ZoomDestinationModifier(id: AnyHashable(id)))
    }

    /// `matchedGeometryEffect` in `\.nowPlayingNamespace`, when there is one: the cover shared by the mini player
    /// and Now Playing. `isSource` is true on whichever of the two is showing.
    func nowPlayingMatchedGeometry(id: some Hashable, isSource: Bool = true) -> some View {
        modifier(NowPlayingMatchedGeometryModifier(id: AnyHashable(id), isSource: isSource))
    }
}

private struct ZoomSourceModifier: ViewModifier {
    let id: AnyHashable
    @Environment(\.zoomNamespace) private var namespace

    func body(content: Content) -> some View {
        if #available(iOS 18.0, *), let namespace {
            content.matchedTransitionSource(id: id, in: namespace)
        } else {
            content
        }
    }
}

private struct ZoomDestinationModifier: ViewModifier {
    let id: AnyHashable
    @Environment(\.zoomNamespace) private var namespace

    func body(content: Content) -> some View {
        if #available(iOS 18.0, *), let namespace {
            content.navigationTransition(.zoom(sourceID: id, in: namespace))
        } else {
            content
        }
    }
}

private struct NowPlayingMatchedGeometryModifier: ViewModifier {
    let id: AnyHashable
    let isSource: Bool
    @Environment(\.nowPlayingNamespace) private var namespace

    func body(content: Content) -> some View {
        if let namespace {
            content.matchedGeometryEffect(id: id, in: namespace, isSource: isSource)
        } else {
            content
        }
    }
}
