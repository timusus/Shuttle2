import SwiftUI

/// The first run's welcome: what S2 is, in one line and three honest rows, then Get Started, or Not Now. It also says
/// that crash reports and usage data are on (#776): iOS has no upgraders to give Android's one-time Home notice to, so
/// every install hears it here, where it can't be missed (`IosTelemetryStartup` marks that notice shown).
struct SourceSetupWelcome: View {
    static let telemetryDisclosure =
        "Shuttle Music shares anonymous crash reports and usage data to help improve the app. You can turn this off in Settings."

    let onStart: () -> Void
    let onSkip: () -> Void

    var body: some View {
        ScrollView {
            VStack(spacing: Spacing.xlarge) {
                VStack(spacing: Spacing.medium) {
                    Image(systemName: "music.note.house.fill")
                        .font(.s2Glyph(size: 56, weight: .semibold, relativeTo: .largeTitle))
                        .foregroundStyle(.white)
                        .padding(Spacing.large)
                        .background(
                            S2Shape.artworkHero
                                .fill(LinearGradient(colors: [.purple, .indigo], startPoint: .topLeading, endPoint: .bottomTrailing))
                        )
                        .accessibilityHidden(true)
                    Text("Welcome to Shuttle Music")
                        .font(.s2LargeTitle)
                        .multilineTextAlignment(.center)
                        .accessibilityAddTraits(.isHeader)
                        .accessibilityIdentifier("onboarding.welcome")
                    Text("Your music library, on this iPhone or streamed from your own server.")
                        .font(.s2Title3)
                        .foregroundStyle(.s2TextSecondary)
                        .multilineTextAlignment(.center)
                }
                VStack(alignment: .leading, spacing: Spacing.large) {
                    FeatureRow(
                        symbol: "server.rack", color: .purple, title: "Your Music, Wherever It Lives",
                        detail: "Play the music on this iPhone, or sign in to Jellyfin, Emby, Plex or Navidrome and play your own collection."
                    )
                    FeatureRow(
                        symbol: "square.stack", color: .orange, title: "Your Whole Library",
                        detail: "Albums, artists, genres and playlists, imported so they browse instantly."
                    )
                    FeatureRow(
                        symbol: "slider.vertical.3", color: .teal, title: "Tuned to You",
                        detail: "Shape the sound with the built-in equalizer."
                    )
                }
                Text(Self.telemetryDisclosure)
                    .font(.s2Caption)
                    .foregroundStyle(.s2TextSecondary)
                    .multilineTextAlignment(.center)
                    .accessibilityIdentifier("onboarding.telemetryDisclosure")
            }
            .padding(.horizontal, Spacing.large)
            .padding(.top, Spacing.xlarge)
            .frame(maxWidth: ServerSignInContent.readableWidth)
            .frame(maxWidth: .infinity)
        }
        .scrollBounceBehavior(.basedOnSize)
        .safeAreaInset(edge: .bottom) {
            VStack(spacing: Spacing.small) {
                Button(action: onStart) {
                    Text("Get Started").font(.s2Headline).frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .buttonBorderShape(.capsule)
                .controlSize(.large)
                .foregroundStyle(.s2OnAccent)
                .accessibilityIdentifier("onboarding.getStarted")
                Button("Not Now", action: onSkip)
                    .controlSize(.large)
                    .accessibilityIdentifier("onboarding.skip")
            }
            .padding(.horizontal, Spacing.large)
            .padding(.bottom, Spacing.medium)
            .frame(maxWidth: ServerSignInContent.readableWidth)
            .frame(maxWidth: .infinity)
            .background(.bar)
        }
        .toolbar(.hidden, for: .navigationBar)
    }
}

/// One thing S2 does: a tinted glyph, a heading and a line.
private struct FeatureRow: View {
    let symbol: String
    let color: Color
    let title: String
    let detail: String

    var body: some View {
        HStack(alignment: .top, spacing: Spacing.medium) {
            IconSquare(systemImage: symbol, style: .filled(color), size: .card)
            VStack(alignment: .leading, spacing: Spacing.tiny) {
                Text(title).font(.s2Headline)
                Text(detail).font(.subheadline).foregroundStyle(.s2TextSecondary)
            }
            .fixedSize(horizontal: false, vertical: true)
        }
        .accessibilityElement(children: .combine)
    }
}
