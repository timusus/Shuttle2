import Shared
import SwiftUI

/// Settings' Shuttle Music Pro section: the user's standing, which opens the paywall, and Restore Purchases. Debug
/// builds add the entitlement override.
struct ProSettingsSection: View {
    var body: some View {
        Observing(AppGraph.shared.storeEntitlements.entitlement) { entitlement in
            ProSettingsRows(store: AppGraph.dependencies.storeKit, status: ProStatus(entitlement))
        }
    }
}

private struct ProSettingsRows: View {
    @ObservedObject var store: StoreKitManager
    let status: ProStatus
    @State private var alert: String?

    var body: some View {
        Section {
            NavigationLink {
                PaywallView(store: store)
            } label: {
                Label {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Shuttle Music Pro")
                        Text(status == .trialAvailable ? "Stream from Jellyfin, Emby and Plex" : status.message)
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                } icon: {
                    IconSquare(systemImage: "star.fill", style: .filled(.orange))
                }
            }
            .accessibilityIdentifier("settings.pro")
            Button {
                Task {
                    switch await store.restore() {
                    case .restored: alert = "Your purchase has been restored"
                    case .nothingToRestore: alert = "No Shuttle Music Pro purchase found for this Apple ID"
                    case .failed: alert = "Couldn't reach the App Store. Please try again."
                    }
                }
            } label: {
                Label { Text("Restore purchases") } icon: { IconSquare(systemImage: "arrow.clockwise", style: .filled(.gray)) }
            }
            .tint(.primary)
            .disabled(store.isRestoring)
            .accessibilityIdentifier("settings.restorePurchases")
            #if DEBUG
            DebugEntitlementPicker()
            #endif
        }
        .alert(alert ?? "", isPresented: Binding(get: { alert != nil }, set: { if !$0 { alert = nil } })) {
            Button("OK") {}
        }
    }
}

/// Debug builds resolve Pro unless overridden; Store resolves from StoreKit (the S2 scheme's `S2.storekit` in the
/// simulator), as a release build does. Kept in UserDefaults and applied at launch (`AppGraph.initialize`).
enum DebugEntitlement {
    static let defaultsKey = "debug.entitlementOverride"
    /// Kotlin `DebugEntitlementOverride` names, with their labels.
    static let options: [(name: String, label: String)] = [
        ("None", "Default (Pro)"), ("Free", "Free"), ("Trial", "Trial"), ("Pro", "Pro"), ("Store", "App Store"),
    ]
}

#if DEBUG
private struct DebugEntitlementPicker: View {
    @AppStorage(DebugEntitlement.defaultsKey) private var override = "None"

    var body: some View {
        Picker("Debug entitlement", selection: $override) {
            ForEach(DebugEntitlement.options, id: \.name) { option in
                Text(option.label).tag(option.name)
            }
        }
        .onChange(of: override) { _, name in
            AppGraph.shared.storeEntitlements.setDebugOverrideNamed(name: name)
        }
        .accessibilityIdentifier("settings.debugEntitlement")
    }
}
#endif
