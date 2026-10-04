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
    /// The stack's one zoom source among its tiles, for the screen they're on; set with `\.zoomNamespace` by
    /// `routeDestinations`. nil means no tile is ever the source.
    @Entry var zoomTiles: ZoomTiles?
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

/// Which of several tiles showing one item is the zoom source for its screen. An item can be in more than one Home
/// section, or on a detail shelf pushed over Home, and a zoom from a tile the user didn't touch would be wrong, as
/// would two live sources sharing one id (#698).
enum ZoomTile {
    /// A tile's key across the stack: its key on its screen (`tile`) under the screen's (`screen`), so the same
    /// album on Home and on a pushed screen's shelf are different tiles.
    static func key(screen: String, tile: String) -> String {
        "\(screen)|\(tile)"
    }

    /// `id` for the tile last tapped (`activeKey`); otherwise an id of the tile's own, which no screen zooms to.
    static func sourceID(_ id: String, tileKey: String, activeKey: String?) -> String {
        activeKey == tileKey ? id : "tile|\(tileKey)"
    }
}

/// The tile a navigation stack's last tap came from, the one zoom source in its namespace. A tap anywhere in the
/// stack replaces the one before, so a tile on a screen further down stops being a source as soon as another
/// screen's tile is tapped, with nothing to clear when a screen comes back.
@MainActor @Observable
final class ZoomSourceSelection {
    /// `ZoomTile.key(screen:tile:)` of the tile last tapped.
    private(set) var activeKey: String?

    func select(screen: String, tile: String) {
        activeKey = ZoomTile.key(screen: screen, tile: tile)
    }

    func sourceID(_ id: String, screen: String, tile: String) -> String {
        ZoomTile.sourceID(id, tileKey: ZoomTile.key(screen: screen, tile: tile), activeKey: activeKey)
    }
}

/// A screen's view of its stack's `ZoomSourceSelection` (`\.zoomTiles`): its tiles' keys, unique on the screen, are
/// prefixed with the screen's own (`screen`).
struct ZoomTiles {
    let selection: ZoomSourceSelection
    let screen: String

    /// Makes this screen's `tile` the stack's zoom source, on the tap that opens its screen.
    @MainActor func select(_ tile: String) {
        selection.select(screen: screen, tile: tile)
    }
}

extension View {
    /// The zoom source for `id` while this screen's tile `tileKey` is the stack's last tapped (`ZoomTiles.select`),
    /// per `ZoomTile.sourceID`. The id changes rather than the modifier coming and going, so the tile keeps its
    /// identity (and its loaded cover) when it's tapped.
    func zoomSource(id: String, tileKey: String) -> some View {
        modifier(ZoomTileSourceModifier(id: id, tileKey: tileKey))
    }
}

private struct ZoomTileSourceModifier: ViewModifier {
    let id: String
    let tileKey: String
    @Environment(\.zoomTiles) private var zoomTiles

    func body(content: Content) -> some View {
        let sourceID = if let zoomTiles {
            zoomTiles.selection.sourceID(id, screen: zoomTiles.screen, tile: tileKey)
        } else {
            ZoomTile.sourceID(id, tileKey: tileKey, activeKey: nil)
        }
        content.zoomSource(id: sourceID)
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
