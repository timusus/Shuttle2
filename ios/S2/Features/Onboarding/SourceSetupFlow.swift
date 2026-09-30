import Shared
import SwiftUI

/// Where the source setup opens: the welcome (first run), the server cards (Sources' Add a Server), or straight at a
/// server's sign-in (Sources' Sign In Again).
enum SourceSetupStart: Hashable, Identifiable {
    case welcome
    case chooseSource
    case signIn(MediaProviderType)

    var id: Self { self }
}

/// A step pushed onto the setup's own stack.
enum SourceSetupStep: Hashable {
    case chooseSource
    case signIn(MediaProviderType)
    case importing
}

/// Adding a media server (#624), on the shared `SourceSetupViewModel`: welcome, the server cards, the sign-in
/// (`ServerSignInView`) and the import's progress, in the setup's own `NavigationStack`. `ContentView` opens it at
/// the welcome on a first run (no server, never finished or skipped: `SourceSetupUiState.firstRun`); Sources opens it
/// at the cards or a sign-in in a sheet, so the first run and Add a Server are one implementation. Closing it, from
/// Not Now, Continue or Cancel, finishes the setup (`onFinish`), so the first run never opens by itself again.
///
/// Android has no first run (#379); this is iOS's, since iOS has no music on the device to start from.
struct SourceSetupFlow: View {
    let start: SourceSetupStart
    /// Keeps the sign-in's view model live while the setup is up (`Navigator.sourceSetupLive`); nil in previews.
    let navigator: Navigator?
    let onClose: () -> Void

    @State private var path: [SourceSetupStep] = []

    var body: some View {
        let models = SourceSetupModels.cached()
        NavigationStack(path: $path) {
            root(models)
                .navigationDestination(for: SourceSetupStep.self) { step in
                    destination(step, models)
                }
        }
        .onAppear { navigator?.sourceSetupLive = true }
        .onDisappear { navigator?.sourceSetupLive = false }
    }

    @ViewBuilder
    private func root(_ models: SourceSetupModels) -> some View {
        switch start {
        case .welcome:
            SourceSetupWelcome(onStart: { path.append(.chooseSource) }, onSkip: { close(models) })
        case .chooseSource:
            cards(models).toolbar { cancel(models) }
        case .signIn(let type):
            signIn(type, models).toolbar { cancel(models) }
        }
    }

    @ViewBuilder
    private func destination(_ step: SourceSetupStep, _ models: SourceSetupModels) -> some View {
        switch step {
        case .chooseSource:
            cards(models)
        case .signIn(let type):
            signIn(type, models)
        case .importing:
            Observing(models.setup.uiState) { state in
                SourceSetupImportPage(
                    state: SourceSetupImportState(state.serverImport),
                    onRetry: { LibraryImport.refresh() },
                    onContinue: { close(models) }
                )
            }
            // The server is connected: going back to its sign-in would only sign in again.
            .navigationBarBackButtonHidden()
        }
    }

    private func cards(_ models: SourceSetupModels) -> some View {
        SourceTypeCards(types: MediaProviderType.signInTypes) { type in
            // The entitlement gate (`TryAddServer`, open on iOS until billing lands) before the sign-in.
            if models.setup.onChooseType() { path.append(.signIn(type)) }
        }
    }

    private func signIn(_ type: MediaProviderType, _ models: SourceSetupModels) -> some View {
        ServerSignInView(
            type: type,
            cacheKey: Navigator.sourceSetupSignInCacheKey(type),
            onConnected: { models.setup.onServerConnected(type: type) },
            onFinished: { path.append(.importing) }
        )
    }

    private func cancel(_ models: SourceSetupModels) -> some ToolbarContent {
        ToolbarItem(placement: .cancellationAction) {
            Button("Cancel") { close(models) }
                .accessibilityIdentifier("onboarding.cancel")
        }
    }

    private func close(_ models: SourceSetupModels) {
        models.setup.onFinish()
        onClose()
    }
}

/// The setup's ViewModel, cached under a key that is always live (`Navigator.sourceSetupCacheKey`): `ContentView`
/// reads it at launch for the first run, and the setup follows the import after its sign-in has gone.
final class SourceSetupModels: ViewModelGroup {
    let setup: SourceSetupViewModel

    init(graph: IosAppGraph) {
        setup = graph.sourceSetupViewModel
    }

    var members: [Lifecycle_viewmodelViewModel] { [setup] }

    @MainActor
    static func cached() -> SourceSetupModels {
        ViewModelCache.shared.viewModel(Navigator.sourceSetupCacheKey) { SourceSetupModels(graph: AppGraph.shared) }
    }
}

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

// MARK: - Welcome

/// The first run's welcome: what S2 is, in one line and three honest rows, then Get Started, or Not Now.
struct SourceSetupWelcome: View {
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
                    Text("Your music library, streamed from your own server.")
                        .font(.s2Title3)
                        .foregroundStyle(.s2TextSecondary)
                        .multilineTextAlignment(.center)
                }
                VStack(alignment: .leading, spacing: Spacing.large) {
                    FeatureRow(
                        symbol: "server.rack", color: .purple, title: "Your Server, Your Music",
                        detail: "Sign in to Jellyfin or Emby and play your own collection."
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

// MARK: - Server cards

/// The server types iOS can sign in to, as large tappable cards.
struct SourceTypeCards: View {
    let types: [MediaProviderType]
    let onSelect: (MediaProviderType) -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.medium) {
                VStack(alignment: .leading, spacing: Spacing.xsmall) {
                    Text("Where's your music?")
                        .font(.s2Title)
                        .accessibilityAddTraits(.isHeader)
                    Text("Choose the server your library lives on.")
                        .foregroundStyle(.s2TextSecondary)
                }
                .padding(.bottom, Spacing.small)
                ForEach(types, id: \.self) { type in
                    Button { onSelect(type) } label: {
                        SourceTypeCard(type: type)
                    }
                    .buttonStyle(.pressScale)
                    .accessibilityIdentifier("serverTypePicker.\(type.name)")
                }
            }
            .padding(Spacing.medium)
            .frame(maxWidth: ServerSignInContent.readableWidth)
            .frame(maxWidth: .infinity)
        }
        .background(Color(.systemGroupedBackground))
        .navigationTitle("Add a Server")
        .navigationBarTitleDisplayMode(.inline)
    }
}

private struct SourceTypeCard: View {
    let type: MediaProviderType

    var body: some View {
        HStack(spacing: Spacing.medium) {
            IconSquare(systemImage: type.symbol, style: .filled(type.color), size: .large)
            VStack(alignment: .leading, spacing: Spacing.tiny) {
                Text(type.title).font(.s2Headline)
                Text(type.setupBlurb)
                    .font(.subheadline)
                    .foregroundStyle(.s2TextSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: Spacing.small)
            Image(systemName: "chevron.forward")
                .font(.footnote.weight(.semibold))
                .foregroundStyle(.tertiary)
                .accessibilityHidden(true)
        }
        .multilineTextAlignment(.leading)
        .foregroundStyle(.primary)
        .padding(Spacing.medium)
        .background(
            S2Shape.card
                .fill(Color(.secondarySystemGroupedBackground))
        )
        .contentShape(S2Shape.card)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isButton)
    }
}

extension MediaProviderType {
    /// A server type's line on its setup card.
    var setupBlurb: String {
        switch self {
        case .jellyfin: "The free, open-source media server. Sign in with Quick Connect or a password."
        case .emby: "Sign in with your Emby server's address and account."
        case .plex: "Sign in with your Plex account."
        case .shuttle, .mediaStore: "Music on this device."
        }
    }
}

// MARK: - Import

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
        case .starting: "Connecting to \(serverName)…"
        case .running(_, let message, _): message ?? "Fetching your library…"
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

// MARK: - First-run presentation

extension View {
    /// The first run's setup, over everything: a full-screen cover on compact, a form-sized sheet on regular and
    /// wide. It can't be swiped away; Not Now or Continue closes it.
    func sourceSetupPresentation(isPresented: Binding<Bool>, fullScreen: Bool, navigator: Navigator) -> some View {
        let cover = Binding(get: { isPresented.wrappedValue && fullScreen }, set: { isPresented.wrappedValue = $0 })
        let sheet = Binding(get: { isPresented.wrappedValue && !fullScreen }, set: { isPresented.wrappedValue = $0 })
        let flow = SourceSetupFlow(start: .welcome, navigator: navigator, onClose: { isPresented.wrappedValue = false })
            .interactiveDismissDisabled()
        return fullScreenCover(isPresented: cover) { flow }
            .sheet(isPresented: sheet) {
                if #available(iOS 18, *) {
                    flow.presentationSizing(.form)
                } else {
                    flow
                }
            }
    }
}
