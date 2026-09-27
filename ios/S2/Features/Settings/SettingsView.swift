import Shared
import SwiftUI

/// Settings (#589 phase 7, #612): the shared `SettingsViewModel` over `IosSettingsCatalog`, as one grouped `Form`.
/// Presented as a sheet from the gear on the Home and Library roots (`AppShell`), with its own `NavigationStack`
/// on `Navigator.settingsPath`, so the Sources row pushes Sources (and its sign-in) inside the sheet. The catalog
/// holds only the rows iOS acts on; what it leaves out, and why, is on `IosSettingsCatalog`.
struct SettingsView: View {
    var body: some View {
        let viewModel = ViewModelCache.shared.viewModel(Navigator.settingsCacheKey) { AppGraph.shared.settingsViewModel }
        let catalog = AppGraph.shared.settingsCatalog
        Observing(viewModel.uiState) { state in
            SettingsEventsHost(events: state.events, handled: { viewModel.onEventHandled(id: $0) }) { rescanStarted in
                SettingsContent(
                    sections: SettingsSection.sections(catalog: catalog, state: state, rescanStarted: rescanStarted),
                    onToggle: { key, isOn in
                        if let item = catalog.item(key: key) as? SettingItemSwitch {
                            viewModel.onSwitchChange(item: item, checked: isOn)
                        }
                    },
                    onChoose: { key, index in
                        if let item = catalog.item(key: key) as? SettingItemChoice<AnyObject> {
                            viewModel.onChoiceSelect(item: item, optionIndex: Int32(index))
                        }
                    },
                    onAction: { key in
                        if let item = catalog.item(key: key) as? SettingItemAction {
                            viewModel.onAction(action: item.action)
                        }
                    }
                )
            }
        }
        .onAppear { viewModel.onResume() }
    }
}

/// Consumes Settings' one-off events. A rescan (the only one iOS raises) marks the Rescan row until the sheet
/// closes; Sources shows the scan's progress.
private struct SettingsEventsHost<Content: View>: View {
    let events: [PendingEvent<any SettingsUiEvent>]
    let handled: (Int64) -> Void
    @ViewBuilder let content: (Bool) -> Content
    @State private var rescanStarted = false

    var body: some View {
        content(rescanStarted)
            .consumeEvents(events, handled: handled) { event in
                if event is SettingsUiEventRescanStarted { rescanStarted = true }
            }
    }
}

extension SettingsCatalog {
    /// The row stored or keyed under `key`.
    func item(key: String) -> (any SettingItem)? {
        screens.lazy.flatMap(\.items).first { $0.key == key }
    }
}

/// One `Form` section: a header and its rows, in plain values.
struct SettingsSection: Equatable, Identifiable {
    var id: String
    var title: String?
    var rows: [SettingsRow]
}

/// One row, in plain values: what it draws and the key its callback carries back.
enum SettingsRow: Equatable, Identifiable {
    /// Pushes another screen: Sources, from the Sources section.
    case link(id: String, title: String, systemImage: String, route: Route)
    case toggle(key: String, title: String, summary: String?, isOn: Bool, isEnabled: Bool)
    case choice(key: String, title: String, options: [String], selected: Int, isEnabled: Bool)
    case action(key: String, title: String, summary: String?, confirmation: Confirmation?, isEnabled: Bool)

    struct Confirmation: Equatable {
        var title: String
        var message: String
        var confirm: String
    }

    var id: String {
        switch self {
        case .link(let id, _, _, _): id
        case .toggle(let key, _, _, _, _), .choice(let key, _, _, _, _), .action(let key, _, _, _, _): key
        }
    }
}

extension SettingsSection {
    /// The catalog's screens as sections: each screen opens with a section under its own title (holding its first
    /// group, if that group is untitled), and every titled group is a section of its own. The Sources screen's
    /// opening section leads with the row that pushes Sources. Navigate and Slider rows have no iOS form (the iOS
    /// catalog has none), so they're left out rather than drawn as rows that do nothing.
    static func sections(catalog: SettingsCatalog, state: SettingsUiState, rescanStarted: Bool = false) -> [SettingsSection] {
        catalog.screens.flatMap { screen -> [SettingsSection] in
            let destination = screen.destination
            var opening = SettingsSection(id: destination.name, title: destination.title.localized(), rows: [])
            if destination == .sources {
                opening.rows.append(.link(id: "settings.sources", title: "Sources", systemImage: "server.rack", route: .sources))
            }
            var sections: [SettingsSection] = []
            for (index, group) in screen.groups.enumerated() {
                let rows = group.items.compactMap { row(for: $0, catalog: catalog, state: state, rescanStarted: rescanStarted) }
                if let title = group.title {
                    sections.append(SettingsSection(id: "\(destination.name).\(index)", title: title.localized(), rows: rows))
                } else if index == 0 {
                    opening.rows.append(contentsOf: rows)
                } else {
                    sections.append(SettingsSection(id: "\(destination.name).\(index)", title: nil, rows: rows))
                }
            }
            return (opening.rows.isEmpty ? [] : [opening]) + sections
        }
    }

    private static func row(
        for item: any SettingItem,
        catalog: SettingsCatalog,
        state: SettingsUiState,
        rescanStarted: Bool
    ) -> SettingsRow? {
        let enabled = state.isEnabled(item: item, catalog: catalog)
        let title = item.title.localized()
        let summary = item.summary?.localized()
        switch item {
        case let toggle as SettingItemSwitch:
            return .toggle(key: toggle.key, title: title, summary: summary, isOn: state.isOn(item: toggle), isEnabled: enabled)
        case let choice as SettingItemChoice<AnyObject>:
            return .choice(
                key: choice.key,
                title: title,
                options: choice.options.map { $0.label.localized() },
                selected: Int(state.selectedIndex(item: choice)),
                isEnabled: enabled
            )
        case let action as SettingItemAction:
            guard let key = action.key else { return nil }
            let started = rescanStarted && action.action == .rescan
            return .action(
                key: key,
                title: title,
                summary: started ? "Scanning your music" : summary,
                confirmation: action.confirmation.map {
                    SettingsRow.Confirmation(title: $0.title.localized(), message: $0.message.localized(), confirm: $0.confirm.localized())
                },
                isEnabled: enabled
            )
        default:
            return nil
        }
    }
}

/// Settings from plain values.
struct SettingsContent: View {
    let sections: [SettingsSection]
    var onToggle: (String, Bool) -> Void = { _, _ in }
    var onChoose: (String, Int) -> Void = { _, _ in }
    var onAction: (String) -> Void = { _ in }

    @Environment(\.openURL) private var openURL

    /// The action row whose confirmation is showing.
    @State private var confirming: (key: String, confirmation: SettingsRow.Confirmation)?

    var body: some View {
        Form {
            ForEach(sections) { section in
                Section {
                    ForEach(section.rows) { row in
                        rowView(row)
                    }
                } header: {
                    if let title = section.title { Text(title) }
                }
            }
            Section {
                LabeledContent("Version", value: Self.appVersion)
                    .accessibilityIdentifier("settings.version")
                // The licences (FFmpeg's LGPL notice) live in the app's Settings bundle, the iOS place for them.
                Button("Acknowledgements") {
                    if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) }
                }
                .accessibilityIdentifier("settings.acknowledgements")
            } header: {
                Text("About")
            }
        }
        .formStyle(.grouped)
        .navigationTitle("Settings")
        .confirmationDialog(
            confirming?.confirmation.title ?? "",
            isPresented: Binding(get: { confirming != nil }, set: { if !$0 { confirming = nil } }),
            titleVisibility: .visible
        ) {
            if let confirming {
                Button(confirming.confirmation.confirm) { onAction(confirming.key) }
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            if let confirming { Text(confirming.confirmation.message) }
        }
    }

    @ViewBuilder
    private func rowView(_ row: SettingsRow) -> some View {
        switch row {
        case .link(let id, let title, let systemImage, let route):
            NavigationLink(value: route) {
                Label(title, systemImage: systemImage)
            }
            .accessibilityIdentifier(id)
        case .toggle(let key, let title, let summary, let isOn, let isEnabled):
            Toggle(isOn: Binding(get: { isOn }, set: { onToggle(key, $0) })) {
                RowLabel(title: title, summary: summary)
            }
            .disabled(!isEnabled)
            .accessibilityIdentifier("settings.\(key)")
        case .choice(let key, let title, let options, let selected, let isEnabled):
            Picker(title, selection: Binding(get: { selected }, set: { onChoose(key, $0) })) {
                ForEach(options.indices, id: \.self) { index in
                    Text(options[index]).tag(index)
                }
            }
            .disabled(!isEnabled)
            .accessibilityIdentifier("settings.\(key)")
        case .action(let key, let title, let summary, let confirmation, let isEnabled):
            Button {
                if let confirmation {
                    confirming = (key, confirmation)
                } else {
                    onAction(key)
                }
            } label: {
                RowLabel(title: title, summary: summary)
            }
            .tint(.primary)
            .disabled(!isEnabled)
            .accessibilityIdentifier("settings.\(key)")
        }
    }

    /// "2026.09.28 (26092801)": the marketing version and build, as Android's About shows its version name.
    static var appVersion: String {
        let info = Bundle.main.infoDictionary
        let version = info?["CFBundleShortVersionString"] as? String ?? "?"
        guard let build = info?["CFBundleVersion"] as? String, build != version else { return version }
        return "\(version) (\(build))"
    }
}

/// A row's title, with its summary beneath in the secondary style.
private struct RowLabel: View {
    let title: String
    let summary: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title)
            if let summary {
                Text(summary).font(.subheadline).foregroundStyle(.secondary)
            }
        }
    }
}
