import Shared
import StoreKit
import SwiftUI

/// What Shuttle Music Pro unlocks on iOS, for the paywall, its trial disclosure, Settings, server sign-in and Restore's
/// messages. Name only what the app has (App Review 2.3, 3.1.1): add downloads when they ship.
enum ProFeatures {
    /// The servers Pro streams from.
    static let servers = ["Jellyfin", "Emby", "Plex"]

    /// "Jellyfin, Emby and Plex".
    static var serverList: String {
        servers.count < 2 ? servers.joined() : servers.dropLast().joined(separator: ", ") + " and " + servers.last!
    }

    /// The paywall's headline feature and Settings' row: "Stream from Jellyfin, Emby and Plex".
    static var headline: String { "Stream from \(serverList)" }

    /// The trial's length in days, from :shared's `AppStoreProducts`: the one place it is defined.
    static var trialDays: Int { Int(AppStoreProducts.shared.TRIAL_DAYS) }

    /// What stops when the trial ends, mid-sentence: "streaming from Jellyfin, Emby and Plex".
    static var afterTrial: String { "streaming from \(serverList)" }

    /// Server sign-in's disclosure, for anyone without Pro or a running trial.
    static var signInDisclosure: String { "Streaming from \(serverList) is part of Shuttle Music Pro. Free for \(ProFeatures.trialDays) days." }
}

/// Where the user stands with Shuttle Music Pro, from the Kotlin `Entitlement`.
enum ProStatus: Equatable {
    /// StoreKit hasn't answered yet.
    case checking
    /// Free, and the free trial is still to have.
    case trialAvailable
    case trial(daysLeft: Int)
    case trialEnded
    case pro

    init(_ entitlement: Entitlement) {
        switch onEnum(of: entitlement) {
        case .unknown: self = .checking
        case .free(let free): self = free.trialUsed ? .trialEnded : .trialAvailable
        case .trial(let trial): self = .trial(daysLeft: Int(trial.daysRemainingNow()))
        case .pro: self = .pro
        }
    }

    /// One line on the user's standing, for the paywall and the Settings row.
    var message: String {
        switch self {
        case .checking: "Checking your purchases with the App Store…"
        case .trialAvailable: "Try streaming from your server free for \(ProFeatures.trialDays) days."
        case .trial(let daysLeft): daysLeft == 1 ? "1 day left in your free trial" : "\(daysLeft) days left in your free trial"
        case .trialEnded: "Your free trial has ended. Upgrade to keep \(ProFeatures.afterTrial)."
        case .pro: "You have Shuttle Music Pro. Thank you for supporting Shuttle Music."
        }
    }
}

/// The Shuttle Music Pro paywall (#609): opened by a gated action (`PaywallPresenter`) or from Settings. It observes
/// the Kotlin entitlement and buys through the app's `StoreKitManager`.
struct PaywallView: View {
    @ObservedObject var store: StoreKitManager
    /// Set when the paywall is presented on its own, so it can close itself once the user has Pro.
    var onClose: (() -> Void)?

    var body: some View {
        Observing(AppGraph.shared.storeEntitlements.entitlement) { entitlement in
            PaywallActions(store: store, status: ProStatus(entitlement), onClose: onClose)
        }
    }
}

/// Runs the paywall's purchases and restores, and reports their outcome.
private struct PaywallActions: View {
    @ObservedObject var store: StoreKitManager
    let status: ProStatus
    let onClose: (() -> Void)?
    @State private var alert: String?

    var body: some View {
        PaywallContent(
            status: status,
            lifetimePrice: store.lifetimePrice,
            trialAvailable: store.trialOffered,
            busy: store.purchasing != nil || store.isRestoring,
            onStartTrial: { buy(AppStoreProducts.shared.TRIAL) },
            onBuy: { buy(AppStoreProducts.shared.LIFETIME) },
            onRestore: restore
        )
        .alert(alert ?? "", isPresented: Binding(get: { alert != nil }, set: { if !$0 { alert = nil } })) {
            Button("OK") {}
        }
        .toolbar {
            if let onClose {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close", action: onClose).accessibilityIdentifier("paywall.close")
                }
            }
        }
    }

    private func buy(_ productId: String) {
        Task {
            switch await store.purchase(productId) {
            case .purchased: onClose?()
            case .cancelled: break
            case .pending: alert = "Your purchase is waiting for approval. It will unlock as soon as it's approved."
            case .failed(let message): alert = message
            }
        }
    }

    private func restore() {
        Task { alert = await store.restore().message }
    }
}

extension StoreKitManager.RestoreOutcome {
    /// What Restore Purchases tells the user it found.
    var message: String {
        switch self {
        case .pro: "Shuttle Music Pro has been restored."
        case .trial(let daysLeft): "Your free trial has been restored. " + ProStatus.trial(daysLeft: daysLeft).message + "."
        case .trialEnded: "This Apple ID has already used its free trial. Get Shuttle Music Pro to keep \(ProFeatures.afterTrial)."
        case .nothingToRestore: "No Shuttle Music Pro purchase or free trial found for this Apple ID."
        case .failed: "Couldn't reach the App Store. Please try again."
        }
    }
}

/// The paywall from plain values. Before the trial starts it discloses, ahead of the button that starts it, how long
/// the trial lasts, what stops working after it, and Pro's localized price (App Review guideline 3.1.1).
struct PaywallContent: View {
    let status: ProStatus
    /// Lifetime's localized `displayPrice`; nil while it loads or if the App Store can't be reached.
    let lifetimePrice: String?
    var trialAvailable = true
    var busy = false
    var onStartTrial: () -> Void = {}
    var onBuy: () -> Void = {}
    var onRestore: () -> Void = {}

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Shuttle Music Pro")
                        .font(.largeTitle.bold())
                    Text(status.message)
                        .foregroundStyle(.secondary)
                        .accessibilityIdentifier("paywall.status")
                }

                VStack(alignment: .leading, spacing: 12) {
                    Text("What you get").font(.headline)
                    Label(ProFeatures.headline, systemImage: "server.rack")
                    Label("AirPlay, the equalizer and the rest of the app stay free", systemImage: "checkmark.circle")
                }

                if status == .trialAvailable {
                    Text(trialDisclosure)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                        .accessibilityIdentifier("paywall.disclosure")
                }

                if status != .pro {
                    buttons
                }

                VStack(spacing: 12) {
                    Button("Restore purchases", action: onRestore)
                        .disabled(busy)
                        .accessibilityIdentifier("paywall.restore")
                    LegalLinks()
                }
                .frame(maxWidth: .infinity)
            }
            .padding()
        }
        .navigationTitle("Shuttle Music Pro")
        .navigationBarTitleDisplayMode(.inline)
    }

    private var priceText: String {
        lifetimePrice.map { "\($0) once" } ?? "Loading price…"
    }

    private var trialDisclosure: String {
        "The trial is free and lasts \(ProFeatures.trialDays) days. After it ends, \(ProFeatures.afterTrial) stops until you buy Shuttle "
            + "Music Pro, a one-time purchase of \(lifetimePrice ?? "the price shown"). Nothing is charged when the trial ends."
    }

    @ViewBuilder
    private var buttons: some View {
        VStack(spacing: 12) {
            if status == .trialAvailable {
                Button(action: onStartTrial) {
                    Text("Start \(ProFeatures.trialDays)-day free trial").frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.large)
                .disabled(busy || !trialAvailable)
                .accessibilityIdentifier("paywall.startTrial")
            }
            Button(action: onBuy) {
                VStack {
                    Text(status == .trialAvailable ? "Buy now" : "Get Shuttle Music Pro")
                    Text(priceText).font(.footnote)
                }
                .frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)
            .controlSize(.large)
            .disabled(busy || lifetimePrice == nil)
            .accessibilityIdentifier("paywall.buy")
        }
    }
}

/// The Privacy Policy and Apple's standard Terms of Use (EULA), which App Review wants on every paywall.
struct LegalLinks: View {
    static let privacy = URL(string: "https://simplecityapps.com/privacy")!
    static let terms = URL(string: "https://www.apple.com/legal/internet-services/itunes/dev/stdeula/")!

    var body: some View {
        HStack(spacing: 16) {
            Link("Privacy Policy", destination: Self.privacy)
                .accessibilityIdentifier("paywall.privacy")
            Link("Terms of Use", destination: Self.terms)
                .accessibilityIdentifier("paywall.terms")
        }
        .font(.footnote)
    }
}
