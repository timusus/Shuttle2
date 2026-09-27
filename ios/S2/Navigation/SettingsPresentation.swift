import SwiftUI

extension View {
    /// The Settings entry point: a gear toolbar item on the Home and Library roots
    /// (`docs/architecture/ios-port/phase-5-ios-app.md` section 2), presenting a sheet with its own
    /// `NavigationStack`. Real settings screens land in phase 7 (#589).
    func settingsGear(_ navigator: Navigator) -> some View {
        toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    navigator.showsSettings = true
                } label: {
                    Image(systemName: "gearshape")
                }
                .accessibilityIdentifier("settingsGear")
                .accessibilityLabel("Settings")
            }
        }
    }
}

/// Placeholder Settings screen (phase 7, #589).
struct SettingsPlaceholderView: View {
    var body: some View {
        Text("Settings")
            .navigationTitle("Settings")
    }
}
