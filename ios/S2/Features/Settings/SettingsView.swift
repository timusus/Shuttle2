import Shared
import SwiftUI

/// Settings (#589 phase 7, #612): the shared `SettingsViewModel` over `IosSettingsCatalog`, as one grouped `Form`.
/// Presented as a sheet from the gear on the Home and Library roots (`AppShell`), with its own `NavigationStack`
/// on `Navigator.settingsPath`, so the Sources row pushes Sources (and its sign-in) inside the sheet. The catalog
/// holds only the rows iOS acts on; what it leaves out, and why, is on `IosSettingsCatalog`. The Equalizer row pushes
/// `EqualizerView` the same way, and the Scrobbling row `ScrobblingView` (left out of a build without Last.fm keys,
/// `SettingsUiState.lastFmConfigured`). ReplayGain's pre-amp is its own section, labelled and explained apart from the
/// Equalizer's Preamp (#645).
struct SettingsView: View {
    /// The shared view model's cache key, and the catalog screens to show (nil: all of them, with About). The Audio
    /// sheet's Playback Settings screen is this same view over the Playback & Sound screen alone.
    var cacheKey = Navigator.settingsCacheKey
    var destinations: Set<SettingsDestination>?
    var title = String(localized: "settings_title", table: "Settings")

    var body: some View {
        let viewModel = ViewModelCache.shared.viewModel(cacheKey) { AppGraph.shared.settingsViewModel }
        let catalog = AppGraph.shared.settingsCatalog
        Observing(viewModel.uiState) { state in
            SettingsEventsHost(events: state.events, handled: { viewModel.onEventHandled(id: $0) }) { rescanStarted in
                SettingsContent(
                    sections: SettingsSection.sections(catalog: catalog, state: state, rescanStarted: rescanStarted, destinations: destinations),
                    title: title,
                    showsAbout: destinations == nil,
                    showsPro: destinations == nil,
                    showsDownloads: destinations == nil,
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
                    onSlide: { key, position in
                        if let item = catalog.item(key: key) as? SettingItemSlider<AnyObject> {
                            viewModel.onSliderChange(item: item, position: position)
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
    var footer: String?
}

/// One row, in plain values: what it draws and the key its callback carries back.
enum SettingsRow: Equatable, Identifiable {
    /// Pushes another screen: Sources, from the Sources section, the Equalizer and Scrobbling.
    case link(id: String, title: String, systemImage: String, route: Route, summary: String? = nil)
    case toggle(key: String, title: String, summary: String?, isOn: Bool, isEnabled: Bool)
    case choice(key: String, title: String, options: [String], selected: Int, isEnabled: Bool)
    case action(key: String, title: String, summary: String?, confirmation: Confirmation?, isEnabled: Bool)
    /// A continuous slider over `range`, its value shown by `valueLabel` (nil: not shown).
    case slider(key: String, title: String, value: Float, range: ClosedRange<Float>, valueLabel: String?, isEnabled: Bool)

    struct Confirmation: Equatable {
        var title: String
        var message: String
        var confirm: String
    }

    var id: String {
        switch self {
        case .link(let id, _, _, _, _): id
        case .toggle(let key, _, _, _, _), .choice(let key, _, _, _, _), .action(let key, _, _, _, _),
             .slider(let key, _, _, _, _, _): key
        }
    }
}

extension SettingsSection {
    /// The catalog's screens as sections: each screen opens with a section under its own title (holding its first
    /// group, if that group is untitled), and every titled group is a section of its own. The Sources screen's
    /// opening section leads with the row that pushes Sources. A Navigate row is a link when iOS has its screen (the
    /// Equalizer, Scrobbling) and left out otherwise, rather than drawn as a row that does nothing. Scrobbling is left
    /// out too in a build without Last.fm keys.
    static func sections(
        catalog: SettingsCatalog,
        state: SettingsUiState,
        rescanStarted: Bool = false,
        destinations: Set<SettingsDestination>? = nil
    ) -> [SettingsSection] {
        catalog.screens.flatMap { catalogScreen -> [SettingsSection] in
            let screen = state.lastFmConfigured ? catalogScreen : catalogScreen.withoutScrobbling()
            let destination = screen.destination
            guard destinations?.contains(destination) ?? true else { return [] }
            var opening = SettingsSection(id: destination.name, title: destination.title.localized(), rows: [])
            if destination == .sources {
                opening.rows.append(.link(id: "settings.sources", title: SettingsSection.sourcesTitle, systemImage: "server.rack", route: .sources))
            }
            var sections: [SettingsSection] = []
            for (index, group) in screen.groups.enumerated() {
                let rows = group.items.compactMap { row(for: $0, catalog: catalog, state: state, rescanStarted: rescanStarted) }
                if let title = group.title {
                    let footer = rows.contains { $0.id == replayGainPreampKey } ? replayGainPreampFooter : nil
                    sections.append(SettingsSection(id: "\(destination.name).\(index)", title: title.localized(), rows: rows, footer: footer))
                } else if index == 0 {
                    opening.rows.append(contentsOf: rows)
                } else {
                    sections.append(SettingsSection(id: "\(destination.name).\(index)", title: nil, rows: rows))
                }
            }
            return (opening.rows.isEmpty ? [] : [opening]) + sections
        }
    }

    /// ReplayGain's pre-amp (`PlaybackSettings.PreAmpGain`): named for ReplayGain, so it doesn't read as a second
    /// Equalizer Preamp, and explained beneath its section.
    static let replayGainPreampKey = "preamp_gain"
    static let replayGainPreampTitle = String(localized: "settings_replaygain_preamp_title", table: "Settings")
    static let replayGainPreampFooter = String(localized: "settings_replaygain_preamp_footer", table: "Settings")
    static let sourcesTitle = String(localized: "settings_sources", table: "Settings")

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
                summary: started ? String(localized: "settings_rescan_started", table: "Settings") : summary,
                confirmation: action.confirmation.map {
                    SettingsRow.Confirmation(title: $0.title.localized(), message: $0.message.localized(), confirm: $0.confirm.localized())
                },
                isEnabled: enabled
            )
        case let slider as SettingItemSlider<AnyObject>:
            let value = state.sliderValue(item: slider)
            return .slider(
                key: slider.key,
                title: slider.key == replayGainPreampKey ? replayGainPreampTitle : title,
                value: value,
                range: slider.minimum...slider.maximum,
                valueLabel: slider.isDecibels ? String(format: "%+.1f dB", value) : nil,
                isEnabled: enabled
            )
        case let link as SettingItemNavigate where link.target == .equalizer:
            return .link(id: "settings.equalizer", title: title, systemImage: "slider.vertical.3", route: .equalizer, summary: state.equalizerSummary.localized())
        case let link as SettingItemNavigate where link.target == .scrobbling:
            return .link(id: "settings.scrobbling", title: title, systemImage: "dot.radiowaves.up.forward", route: .scrobbling)
        default:
            return nil
        }
    }
}

/// Settings from plain values.
struct SettingsContent: View {
    let sections: [SettingsSection]
    var title = String(localized: "settings_title", table: "Settings")
    var showsAbout = true
    /// The Shuttle Music Pro section, which reads the app's graph; off for tests of the catalog rows.
    var showsPro = false
    /// The Downloads row, which opens the storage screen (#852); off where Settings shows only some of its screens.
    var showsDownloads = false
    var onToggle: (String, Bool) -> Void = { _, _ in }
    var onChoose: (String, Int) -> Void = { _, _ in }
    var onSlide: (String, Float) -> Void = { _, _ in }
    var onAction: (String) -> Void = { _ in }

    @Environment(\.openURL) private var openURL

    #if DEBUG
    /// Debug builds only: covers drawn by `GeneratedArtwork`, for store screenshots (`DebugArtwork`).
    @AppStorage(DebugArtwork.defaultsKey) private var generatedArtwork = false
    #endif

    /// The action row whose confirmation is showing.
    @State private var confirming: (key: String, confirmation: SettingsRow.Confirmation)?

    var body: some View {
        Form {
            if showsPro {
                ProSettingsSection()
            }
            if showsDownloads {
                Section {
                    NavigationLink(value: Route.downloads) {
                        Label { Text("Downloads") } icon: { IconSquare(systemImage: "arrow.down.circle.fill", style: .filled(SettingsIcon(id: "settings.downloads").color)) }
                    }
                    .accessibilityIdentifier("settings.downloads")
                }
            }
            ForEach(sections) { section in
                Section {
                    ForEach(section.rows) { row in
                        rowView(row)
                    }
                } header: {
                    if let title = section.title { Text(title) }
                } footer: {
                    if let footer = section.footer { Text(footer) }
                }
            }
            if showsAbout {
                Section {
                    LabeledContent {
                        Text(Self.appVersion)
                    } label: {
                        Label { Text("settings_version", tableName: "Settings") } icon: { IconSquare(systemImage: "info", style: .filled(.gray)) }
                    }
                    .accessibilityIdentifier("settings.version")
                    // The licences (FFmpeg's LGPL notice) live in the app's Settings bundle, the iOS place for them.
                    Button {
                        if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) }
                    } label: {
                        Label { Text("settings_acknowledgements", tableName: "Settings") } icon: { IconSquare(systemImage: "doc.text.fill", style: .filled(.gray)) }
                    }
                    .tint(.primary)
                    .accessibilityIdentifier("settings.acknowledgements")
                    // FFmpeg is LGPL-2.1+: the matching source is a release asset on our public repository.
                    Link(destination: Self.ffmpegSourceURL) {
                        Label { Text("settings_ffmpeg_source", tableName: "Settings") } icon: { IconSquare(systemImage: "chevron.left.forwardslash.chevron.right", style: .filled(.gray)) }
                    }
                    .tint(.primary)
                    .accessibilityIdentifier("settings.ffmpegSource")
                    ShareLink(item: DiagnosticsLog(), preview: SharePreview(String(localized: "settings_share_diagnostics", table: "Settings"))) {
                        Label { RowLabel(title: String(localized: "settings_share_diagnostics", table: "Settings"), summary: String(localized: "settings_share_diagnostics_summary", table: "Settings")) } icon: { IconSquare(systemImage: "square.and.arrow.up", style: .filled(.gray)) }
                    }
                    .tint(.primary)
                    .accessibilityIdentifier("settings.shareDiagnostics")
                } header: {
                    Text("settings_about", tableName: "Settings")
                }
            }
            #if DEBUG
            if showsAbout {
                Section {
                    Toggle(isOn: $generatedArtwork) {
                        Text("settings_debug_generated_artwork", tableName: "Settings")
                    }
                    .accessibilityIdentifier("settings.debug.generatedArtwork")
                } header: {
                    Text("settings_debug", tableName: "Settings")
                } footer: {
                    Text("settings_debug_generated_artwork_footer", tableName: "Settings")
                }
            }
            #endif
        }
        .formStyle(.grouped)
        .navigationTitle(title)
        .confirmationDialog(
            confirming?.confirmation.title ?? "",
            isPresented: Binding(get: { confirming != nil }, set: { if !$0 { confirming = nil } }),
            titleVisibility: .visible
        ) {
            if let confirming {
                Button(confirming.confirmation.confirm) { onAction(confirming.key) }
            }
            Button(String(localized: "settings_cancel", table: "Settings"), role: .cancel) {}
        } message: {
            if let confirming { Text(confirming.confirmation.message) }
        }
    }

    @ViewBuilder
    private func rowView(_ row: SettingsRow) -> some View {
        let icon = SettingsIcon(id: row.id)
        switch row {
        case .link(let id, let title, let systemImage, let route, let summary):
            NavigationLink(value: route) {
                Label { RowLabel(title: title, summary: summary) } icon: { IconSquare(systemImage: systemImage, style: .filled(icon.color)) }
            }
            .accessibilityIdentifier(id)
        case .toggle(let key, let title, let summary, let isOn, let isEnabled):
            Toggle(isOn: Binding(get: { isOn }, set: { onToggle(key, $0) })) {
                Label { RowLabel(title: title, summary: summary) } icon: { icon.square }
            }
            .s2Switch()
            .disabled(!isEnabled)
            .accessibilityIdentifier("settings.\(key)")
        case .choice(let key, let title, let options, let selected, let isEnabled):
            Picker(selection: Binding(get: { selected }, set: { onChoose(key, $0) })) {
                ForEach(options.indices, id: \.self) { index in
                    Text(options[index]).tag(index)
                }
            } label: {
                Label { Text(title) } icon: { icon.square }
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
                Label { RowLabel(title: title, summary: summary) } icon: { icon.square }
            }
            .tint(.primary)
            .disabled(!isEnabled)
            .accessibilityIdentifier("settings.\(key)")
        case .slider(let key, let title, let value, let range, let valueLabel, let isEnabled):
            VStack(alignment: .leading, spacing: Spacing.small) {
                LabeledContent {
                    if let valueLabel { Text(valueLabel).monospacedDigit() }
                } label: {
                    Label { Text(title) } icon: { icon.square }
                }
                Slider(value: Binding(get: { value }, set: { onSlide(key, $0) }), in: range) {
                    Text(title)
                }
                .accessibilityIdentifier("settings.\(key)")
                .accessibilityValue(valueLabel ?? String(format: "%.1f", value))
            }
            .disabled(!isEnabled)
        }
    }

    static let ffmpegSourceURL = URL(string: "https://github.com/timusus/Shuttle2/releases/tag/ffmpeg-n7.1.5-source")!

    /// "2026.09.28 (26092801)": the marketing version and build, as Android's About shows its version name.
    static var appVersion: String {
        let info = Bundle.main.infoDictionary
        let version = info?["CFBundleShortVersionString"] as? String ?? "?"
        guard let build = info?["CFBundleVersion"] as? String, build != version else { return version }
        return "\(version) (\(build))"
    }
}

/// A row's leading icon, iOS Settings style: a white symbol on a coloured rounded square (`IconSquare`), picked by
/// the row's key. A key this doesn't know gets a grey gear, so a new catalog row still lines up.
struct SettingsIcon: Equatable {
    let systemImage: String
    let color: Color

    init(id: String) {
        (systemImage, color) = switch id {
        case "settings.sources": ("server.rack", .blue)
        case "settings.equalizer": ("slider.vertical.3", .pink)
        case "settings.scrobbling": ("dot.radiowaves.up.forward", .red)
        case "settings.downloads": ("arrow.down.circle.fill", .green)
        case "pref_retain_shuffle_on_new_queue": ("shuffle", .orange)
        case "replaygain_mode": ("waveform", .purple)
        case "preamp_gain": ("speaker.wave.2.fill", .indigo)
        case "pref_streaming_quality_unmetered": ("wifi", .cyan)
        case "pref_streaming_quality_metered": ("antenna.radiowaves.left.and.right", .green)
        case "pref_transcode_format": ("waveform.circle.fill", .orange)
        case "pref_download_quality": ("arrow.down.circle.fill", .blue)
        case "pref_media_rescan": ("arrow.clockwise", .teal)
        case "artwork_local_only": ("photo.fill", .mint)
        case "pref_show_home_on_launch": ("house.fill", .red)
        case "pref_theme_colour_from_artwork": ("paintpalette.fill", .pink)
        case "pref_crash_reporting": ("ladybug.fill", .red)
        case "pref_firebase_analytics": ("chart.bar.fill", .blue)
        default: ("gearshape.fill", .gray)
        }
    }

    var square: IconSquare { IconSquare(systemImage: systemImage, style: .filled(color)) }
}

/// A row's title, with its summary beneath in the secondary style.
private struct RowLabel: View {
    let title: String
    let summary: String?

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.tiny) {
            Text(title)
            if let summary {
                Text(summary).font(.subheadline).foregroundStyle(.secondary)
            }
        }
    }
}
