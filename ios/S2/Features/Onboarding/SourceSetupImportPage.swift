import Shared
import SwiftUI

/// `SourceSetupImport` as the import page shows it.
enum SourceSetupImportState: Equatable {
    /// Connected, the import asked for but not reported on yet (or, outside a sign-in, nothing connected).
    case starting(MediaProviderType?)
    case running(MediaProviderType, message: String?, fraction: Double?)
    case ready(MediaProviderType)
    case failed(MediaProviderType, error: String)

    init(_ serverImport: SourceSetupImport) {
        switch onEnum(of: serverImport) {
        case .notStarted:
            self = .starting(nil)
        case .starting(let starting):
            self = .starting(starting.type)
        case .running(let running):
            self = .running(running.type, message: running.message, fraction: running.fraction.map { Double($0.floatValue) })
        case .finished(let finished):
            self = finished.error.map { .failed(finished.type, error: $0) } ?? .ready(finished.type)
        }
    }

    var type: MediaProviderType? {
        switch self {
        case .starting(let type): type
        case .running(let type, _, _), .ready(let type), .failed(let type, _): type
        }
    }

    /// How far through the import is, when the importer knows; 1 once it's done.
    var fraction: Double? {
        switch self {
        case .running(_, _, let fraction): fraction
        case .ready: 1
        case .starting, .failed: nil
        }
    }

    var isReady: Bool {
        if case .ready = self { true } else { false }
    }

    var isFailed: Bool {
        if case .failed = self { true } else { false }
    }
}

/// The connected server's import: a ring filling as it goes, what it's reading, and Continue, which is always there,
/// since the import keeps going while the app is used.
struct SourceSetupImportPage: View {
    let state: SourceSetupImportState
    let onRetry: () -> Void
    let onContinue: () -> Void

    var body: some View {
        ScrollView {
            VStack(spacing: Spacing.large) {
                ImportRing(state: state)
                VStack(spacing: Spacing.small) {
                    Text(title)
                        .font(.s2Title)
                        .multilineTextAlignment(.center)
                        .accessibilityAddTraits(.isHeader)
                        .accessibilityIdentifier("onboarding.importTitle")
                    Text(detail)
                        .foregroundStyle(.s2TextSecondary)
                        .multilineTextAlignment(.center)
                        .lineLimit(3)
                    if let percent {
                        Text(percent, format: .percent.precision(.fractionLength(0)))
                            .font(.s2Headline.monospacedDigit())
                            .contentTransition(.numericText())
                            .accessibilityIdentifier("onboarding.importPercent")
                    }
                }
                if !state.isReady, !state.isFailed {
                    Label("You can start listening now. The import keeps going while you browse.", systemImage: "info.circle")
                        .font(.footnote)
                        .foregroundStyle(.s2TextSecondary)
                }
            }
            .padding(.horizontal, Spacing.large)
            .padding(.top, Spacing.xlarge)
            .frame(maxWidth: ServerSignInContent.readableWidth)
            .frame(maxWidth: .infinity)
        }
        .scrollBounceBehavior(.basedOnSize)
        .safeAreaInset(edge: .bottom) {
            VStack(spacing: Spacing.small) {
                Button(action: onContinue) {
                    Text(state.isReady ? "Start Listening" : "Continue").font(.s2Headline).frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .buttonBorderShape(.capsule)
                .controlSize(.large)
                .foregroundStyle(.s2OnAccent)
                .accessibilityIdentifier("onboarding.continue")
                if state.isFailed {
                    Button("Try Again", action: onRetry)
                        .controlSize(.large)
                        .accessibilityIdentifier("onboarding.retry")
                }
            }
            .padding(.horizontal, Spacing.large)
            .padding(.bottom, Spacing.medium)
            .frame(maxWidth: ServerSignInContent.readableWidth)
            .frame(maxWidth: .infinity)
            .background(.bar)
        }
        .sensoryFeedback(.success, trigger: state.isReady) { _, ready in ready }
        .navigationTitle("Importing")
        .navigationBarTitleDisplayMode(.inline)
    }

    private var isThisDevice: Bool { state.type == .shuttle }

    private var serverName: String { state.type?.title ?? "your server" }

    private var title: String {
        switch state {
        case .starting, .running: "Importing Your Music"
        case .ready: "Your Library Is Ready"
        case .failed: "The Import Didn't Finish"
        }
    }

    private var detail: String {
        switch state {
        case .starting: isThisDevice ? "Looking for music on this iPhone…" : "Connecting to \(serverName)…"
        case .running(_, let message, _): message ?? "Fetching your library…"
        case .ready where isThisDevice: "Your songs on this iPhone are in. Copy more into Shuttle Music in Files, or add a folder in Sources."
        case .ready: "Your songs from \(serverName) are in. Playlists follow in a moment."
        case .failed(_, let error): error
        }
    }

    private var percent: Double? {
        if case .running(_, _, let fraction) = state { fraction } else { nil }
    }
}

/// The import's ring: its track, the part done in the server's colour, and the server's glyph (or a check, or a
/// warning) in the middle. With no known progress, a spinner stands in for the glyph.
private struct ImportRing: View {
    let state: SourceSetupImportState

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        let color = state.isFailed ? Color.s2Error : state.type?.color ?? .s2Accent
        ZStack {
            Circle().stroke(color.opacity(0.18), lineWidth: Spacing.small)
            Circle()
                .trim(from: 0, to: state.fraction ?? 0)
                .stroke(color, style: StrokeStyle(lineWidth: Spacing.small, lineCap: .round))
                .rotationEffect(.degrees(-90))
                .animation(Motion.press.reduced(reduceMotion), value: state.fraction)
            center.foregroundStyle(color)
        }
        .frame(width: ArtworkSize.artistShelf, height: ArtworkSize.artistShelf)
        .accessibilityElement()
        .accessibilityLabel("Import progress")
        .accessibilityValue(accessibilityValue)
        .accessibilityIdentifier("onboarding.importRing")
    }

    @ViewBuilder private var center: some View {
        switch state {
        case .ready:
            Image(systemName: "checkmark").font(.s2Glyph(size: IconSize.hero, weight: .bold, relativeTo: .largeTitle))
        case .failed:
            Image(systemName: "exclamationmark.triangle.fill").font(.s2Glyph(size: 40, relativeTo: .largeTitle))
        case .running(_, _, .some(_)):
            Image(systemName: state.type?.symbol ?? "server.rack").font(.s2Glyph(size: 40, relativeTo: .largeTitle))
        case .starting, .running:
            ProgressView().controlSize(.large)
        }
    }

    private var accessibilityValue: String {
        switch state {
        case .ready: "Done"
        case .failed: "Failed"
        case .running(_, _, .some(let fraction)): fraction.formatted(.percent.precision(.fractionLength(0)))
        case .starting, .running: "In progress"
        }
    }
}
