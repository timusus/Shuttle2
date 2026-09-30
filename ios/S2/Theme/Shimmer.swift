import SwiftUI

/// A skeleton's loading sweep, after Shuttle Podcasts' `ShimmerModifier`: a soft highlight crossing the
/// placeholder shapes it's applied to. With Reduce Motion on there is no sweep; the grey blocks alone still
/// read as loading.
struct ShimmerModifier: ViewModifier {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var phase: CGFloat = -1

    func body(content: Content) -> some View {
        content
            .overlay {
                GeometryReader { proxy in
                    LinearGradient(
                        colors: [.clear, Color(.systemGray4).opacity(0.4), .clear],
                        startPoint: .leading,
                        endPoint: .trailing
                    )
                    .frame(width: proxy.size.width)
                    .offset(x: phase * proxy.size.width)
                }
                .opacity(reduceMotion ? 0 : 1)
                .onAppear {
                    guard !reduceMotion else { return }
                    withAnimation(.linear(duration: 1.2).repeatForever(autoreverses: false)) { phase = 1 }
                }
            }
            .clipped()
            .accessibilityHidden(true)
    }
}

extension View {
    /// Sweeps a loading highlight across this skeleton.
    func shimmer() -> some View {
        modifier(ShimmerModifier())
    }
}

/// A skeleton of a `MediaRow`, for a list whose first page hasn't arrived.
struct MediaRowSkeleton: View {
    var artworkSize: CGFloat = ArtworkSize.row
    var artworkShape: S2Shape = .artworkRow

    @ScaledMetric(relativeTo: .body) private var titleHeight: CGFloat = 14
    @ScaledMetric(relativeTo: .subheadline) private var subtitleHeight: CGFloat = 12

    var body: some View {
        HStack(spacing: Spacing.smallMedium) {
            artworkShape
                .fill(.s2SurfaceFill)
                .frame(width: artworkSize, height: artworkSize)
            VStack(alignment: .leading, spacing: Spacing.small) {
                Capsule().fill(Color(.systemGray5))
                    .frame(maxWidth: 200)
                    .frame(height: titleHeight)
                Capsule().fill(Color(.systemGray6))
                    .frame(maxWidth: 140)
                    .frame(height: subtitleHeight)
            }
            Spacer(minLength: 0)
        }
        .shimmer()
    }
}
