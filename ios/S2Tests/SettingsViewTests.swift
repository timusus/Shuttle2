import Foundation
import Shared
import SwiftUI
import Testing
import ViewInspector
@testable import S2

/// Settings: `IosSettingsCatalog` and `SettingsUiState` mapped to the form's sections, the rows from plain values,
/// the Sources and Equalizer rows pushing their screens, and the preamp slider.
@MainActor
struct SettingsViewTests {
    private let catalog = IosSettingsCatalog.shared

    private func key(_ screen: SettingsScreen, _ index: Int) -> String {
        screen.items[index].key!
    }

    // MARK: Section mapping

    @Test func mapsEachScreenToItsTitledSectionsInCatalogOrder() {
        let sections = SettingsSection.sections(catalog: catalog, state: SettingsUiState(values: [:], lastScanDate: nil, equalizerPreset: Equalizer.Presets.shared.custom, lastFmConfigured: false, events: []))
        #expect(sections.map(\.title) == ["Playback & sound", nil, "Replay Gain", "Sources", "Streaming & downloads", "Library", "Artists", "Artwork", "Appearance", "Privacy"])
    }

    /// #645: the Equalizer (with its own Preamp) and ReplayGain's pre-amp are separate sections, the pre-amp named
    /// for ReplayGain and explained in the section's footer.
    @Test func theEqualizerAndReplayGainWithItsPreampAreSeparateSections() throws {
        let preampKey = key(catalog.playbackAndSound, 3)
        #expect(preampKey == SettingsSection.replayGainPreampKey)
        let state = SettingsUiState(values: [preampKey: KotlinFloat(float: -2.5)], lastScanDate: nil, equalizerPreset: Equalizer.Presets.shared.custom, lastFmConfigured: false, events: [])
        let sections = SettingsSection.sections(catalog: catalog, state: state)
        let equalizer = try #require(sections.dropFirst().first)
        let replayGain = try #require(sections.dropFirst(2).first)

        #expect(equalizer.rows == [.link(id: "settings.equalizer", title: "Equalizer", systemImage: "slider.vertical.3", route: .equalizer, summary: "Off")])
        #expect(equalizer.footer == nil)
        #expect(replayGain.title == "Replay Gain")
        #expect(replayGain.rows.first?.id == key(catalog.playbackAndSound, 2))
        #expect(replayGain.rows.last == .slider(key: preampKey, title: "Replay Gain Pre-amp", value: -2.5, range: -12...12, valueLabel: "-2.5 dB", isEnabled: true))
        #expect(replayGain.footer?.contains("separate from the Equalizer's Preamp") == true)
    }

    /// #946, as Android's #939: choosing Track or Album asks the shared Pro gate. Without Pro the stored mode stays,
    /// and keeps applying, and the paywall is asked for; with Pro the choice is stored. Off is never gated.
    @Test func turningReplayGainOnWithoutProKeepsTheStoredModeAndAsksForThePaywall() async throws {
        let graph = makeTestGraph(audioPlayer: EngineAudioPlayer(engine: FakeAudioEngine()))
        let item = try #require(catalog.item(key: key(catalog.playbackAndSound, 2)) as? SettingItemChoice<AnyObject>)
        let viewModel = graph.settingsViewModel
        // uiState is shared while collected, and the paywall requests aren't replayed: both need a collector
        let observed = Observed()
        let observers = [
            Task { for await source in graph.observePaywallRequests.invoke() { observed.requests.append(source) } },
            Task { for await state in viewModel.uiState { observed.selected = state.selectedIndex(item: item) } },
        ]
        defer { observers.forEach { $0.cancel() } }

        // The entitlement resolves on the graph's scope, after the call that changes it returns
        func status() -> ProStatus { ProStatus(graph.storeEntitlements.entitlement.value) }

        // Free, as the App Store answers with nothing bought
        graph.storeEntitlements.setDebugOverrideNamed(name: "Store")
        _ = graph.storeEntitlements.storeAnswered(purchases: [])
        #expect(await waitUntil { status() == .trialAvailable })
        viewModel.onChoiceSelect(item: item, optionIndex: 2)
        #expect(await waitUntil { observed.selected == 2 })
        // Album, again until the requests' collector has started: each one is refused
        #expect(await waitUntil {
            if observed.requests.isEmpty { viewModel.onChoiceSelect(item: item, optionIndex: 1) }
            return !observed.requests.isEmpty
        })
        await drainMainQueue()
        #expect(observed.selected == 2)
        #expect(Set(observed.requests) == [PaywallSource.advancedAudio])

        graph.storeEntitlements.setDebugOverrideNamed(name: "Pro")
        #expect(await waitUntil { status() == .pro })
        let refusals = observed.requests.count
        viewModel.onChoiceSelect(item: item, optionIndex: 0)
        #expect(await waitUntil { observed.selected == 0 })
        #expect(observed.requests.count == refusals)
    }

    @MainActor private final class Observed {
        var requests: [PaywallSource] = []
        var selected: Int32?
    }

    /// #765: the Equalizer row reads Off until the equalizer is enabled, then names the preset in use.
    @Test func theEqualizerRowNamesThePresetInUseOnceEnabled() throws {
        let link = try #require(catalog.playbackAndSound.items.compactMap { $0 as? SettingItemNavigate }.first)
        let enabledKey = try #require(link.stateSetting).key
        let state = SettingsUiState(values: [enabledKey: KotlinBoolean(bool: true)], lastScanDate: nil, equalizerPreset: Equalizer.Presets.shared.flat, lastFmConfigured: false, events: [])
        let equalizer = try #require(SettingsSection.sections(catalog: catalog, state: state).dropFirst().first)

        #expect(equalizer.rows == [.link(id: "settings.equalizer", title: "Equalizer", systemImage: "slider.vertical.3", route: .equalizer, summary: "Flat")])
    }

    /// #503: the Scrobbling row pushes the Scrobbling screen, in its own section after Replay Gain, and a build without
    /// Last.fm keys leaves it out.
    @Test func theScrobblingRowShowsOnlyWhenLastFmIsConfigured() throws {
        func sections(lastFmConfigured: Bool) -> [SettingsSection] {
            SettingsSection.sections(catalog: catalog, state: SettingsUiState(values: [:], lastScanDate: nil, equalizerPreset: Equalizer.Presets.shared.custom, lastFmConfigured: lastFmConfigured, events: []))
        }
        let scrobbling = try #require(sections(lastFmConfigured: true).dropFirst(3).first)

        #expect(scrobbling.rows == [.link(id: "settings.scrobbling", title: "Scrobbling", systemImage: "dot.radiowaves.up.forward", route: .scrobbling)])
        #expect(!sections(lastFmConfigured: false).flatMap(\.rows).contains { $0.id == "settings.scrobbling" })
    }

    @Test func aSectionsFooterShowsBeneathIt() throws {
        let sut = SettingsContent(sections: [SettingsSection(id: "rg", title: "Replay Gain", rows: [], footer: "Explained")])
        #expect((try? sut.inspect().find(text: "Explained")) != nil)
    }

    @Test func theSourcesSectionLeadsWithTheRowThatPushesSources() {
        let sections = SettingsSection.sections(catalog: catalog, state: SettingsUiState(values: [:], lastScanDate: nil, equalizerPreset: Equalizer.Presets.shared.custom, lastFmConfigured: false, events: []))
        let sources = sections.first { $0.title == "Sources" }
        #expect(sources?.rows == [.link(id: "settings.sources", title: "Sources", systemImage: "server.rack", route: .sources)])
    }

    @Test func mapsSwitchesChoicesAndActionsWithTheirStoredValues() {
        let shuffleKey = key(catalog.playbackAndSound, 0)
        let state = SettingsUiState(values: [shuffleKey: KotlinBoolean(bool: true)], lastScanDate: nil, equalizerPreset: Equalizer.Presets.shared.custom, lastFmConfigured: false, events: [])
        let rows = SettingsSection.sections(catalog: catalog, state: state).flatMap(\.rows)

        #expect(rows.contains(.toggle(
            key: shuffleKey,
            title: "Keep shuffle mode",
            summary: "When a new queue is selected, shuffle mode won't be disabled",
            isOn: true,
            isEnabled: true
        )))
        #expect(rows.contains(.choice(
            key: key(catalog.sources, 0),
            title: "On Wi-Fi",
            options: ["Original", "320 kbps", "192 kbps", "128 kbps"],
            selected: 0,
            isEnabled: true
        )))
        #expect(rows.contains(.action(
            key: "pref_media_rescan",
            title: "Rescan",
            summary: "Rescan the selected media providers",
            confirmation: nil,
            isEnabled: true
        )))
    }

    /// #776: Settings > Privacy has Android's two switches, crash reporting and usage analytics, each on until turned
    /// off and each with its own icon.
    @Test func privacyHasTheCrashReportingAndAnalyticsSwitches() throws {
        let crashKey = key(catalog.privacy, 0)
        let analyticsKey = key(catalog.privacy, 1)
        let state = SettingsUiState(values: [crashKey: KotlinBoolean(bool: true), analyticsKey: KotlinBoolean(bool: false)], lastScanDate: nil, equalizerPreset: Equalizer.Presets.shared.custom, lastFmConfigured: false, events: [])
        let privacy = try #require(SettingsSection.sections(catalog: catalog, state: state).first { $0.title == "Privacy" })

        #expect(privacy.rows == [
            .toggle(
                key: "pref_crash_reporting",
                title: "Crash reporting",
                summary: "Anonymous crash statistics, which help to track down and resolve bugs",
                isOn: true,
                isEnabled: true
            ),
            .toggle(
                key: "pref_firebase_analytics",
                title: "Usage analytics",
                summary: "Anonymous statistics about how the app is used, which help decide what to improve",
                isOn: false,
                isEnabled: true
            ),
        ])
        #expect(SettingsIcon(id: crashKey).systemImage == "ladybug.fill")
        #expect(SettingsIcon(id: analyticsKey).systemImage == "chart.bar.fill")
    }

    @Test func aStartedRescanShowsOnItsRow() {
        let rows = SettingsSection.sections(
            catalog: catalog,
            state: SettingsUiState(values: [:], lastScanDate: nil, equalizerPreset: Equalizer.Presets.shared.custom, lastFmConfigured: false, events: []),
            rescanStarted: true
        ).flatMap(\.rows)
        guard case .action(_, _, let summary, _, _)? = rows.first(where: { $0.id == "pref_media_rescan" }) else {
            Issue.record("no rescan row")
            return
        }
        #expect(summary == "Scanning your music")
    }

    // MARK: Content

    @Test func theSourcesRowIsALinkToTheSourcesRoute() throws {
        let sut = SettingsContent(sections: [
            SettingsSection(id: "sources", title: "Sources", rows: [
                .link(id: "settings.sources", title: "Sources", systemImage: "server.rack", route: .sources)
            ])
        ])
        let link = try sut.inspect().find(viewWithAccessibilityIdentifier: "settings.sources").navigationLink()
        #expect((try? link.labelView().find(text: "Sources")) != nil)
    }

    @Test func togglingARowReportsItsKeyAndValue() throws {
        var toggled: (String, Bool)?
        let sut = SettingsContent(
            sections: [SettingsSection(id: "s", title: nil, rows: [
                .toggle(key: "shuffle", title: "Keep shuffle mode", summary: nil, isOn: false, isEnabled: true)
            ])],
            onToggle: { toggled = ($0, $1) }
        )
        try sut.inspect().find(viewWithAccessibilityIdentifier: "settings.shuffle").toggle().tap()
        #expect(toggled?.0 == "shuffle")
        #expect(toggled?.1 == true)
    }

    @Test func choosingAnOptionReportsItsIndex() throws {
        var chosen: (String, Int)?
        let sut = SettingsContent(
            sections: [SettingsSection(id: "s", title: nil, rows: [
                .choice(key: "metered", title: "On mobile data", options: ["Original", "320 kbps"], selected: 0, isEnabled: true)
            ])],
            onChoose: { chosen = ($0, $1) }
        )
        try sut.inspect().find(viewWithAccessibilityIdentifier: "settings.metered").picker().select(value: 1)
        #expect(chosen?.0 == "metered")
        #expect(chosen?.1 == 1)
    }

    @Test func slidingReportsTheKeyAndPosition() throws {
        var slid: (String, Float)?
        let sut = SettingsContent(
            sections: [SettingsSection(id: "s", title: nil, rows: [
                .slider(key: "preamp", title: "Preamp", value: 0, range: -12...12, valueLabel: "+0.0 dB", isEnabled: true)
            ])],
            onSlide: { slid = ($0, $1) }
        )
        let slider = try sut.inspect().find(viewWithAccessibilityIdentifier: "settings.preamp").slider()
        try slider.setValue(0.625) // a fraction of the range: 3 dB
        #expect(slid?.0 == "preamp")
        #expect(slid?.1 == 3)
        #expect((try? sut.inspect().find(text: "+0.0 dB")) != nil)
    }

    @Test func tappingAnActionWithoutConfirmationRunsIt() throws {
        var ran: String?
        let sut = SettingsContent(
            sections: [SettingsSection(id: "s", title: nil, rows: [
                .action(key: "pref_media_rescan", title: "Rescan", summary: nil, confirmation: nil, isEnabled: true)
            ])],
            onAction: { ran = $0 }
        )
        try sut.inspect().find(viewWithAccessibilityIdentifier: "settings.pref_media_rescan").button().tap()
        #expect(ran == "pref_media_rescan")
    }

    @Test func showsTheVersionAndAcknowledgements() throws {
        let sut = SettingsContent(sections: [])
        #expect((try? sut.inspect().find(text: "Version")) != nil)
        #expect((try? sut.inspect().find(viewWithAccessibilityIdentifier: "settings.acknowledgements")) != nil)
        let link = try sut.inspect().find(ViewType.Link.self) { try $0.accessibilityIdentifier() == "settings.ffmpegSource" }
        #expect(try link.url() == SettingsContent.ffmpegSourceURL)
        #expect(SettingsContent.ffmpegSourceURL.absoluteString == "https://github.com/timusus/shuttle-playback/tree/0.5.0/scripts")
    }

    @Test func eachRowLeadsWithItsIconSquare() throws {
        #expect(SettingsIcon(id: "settings.equalizer") == SettingsIcon(id: "settings.equalizer"))
        #expect(SettingsIcon(id: "replaygain_mode").systemImage == "waveform")
        #expect(SettingsIcon(id: "pref_media_rescan").systemImage == "arrow.clockwise")
        // A row the mapping doesn't know still gets a square, so the column lines up.
        #expect(SettingsIcon(id: "something_new").systemImage == "gearshape.fill")
        let sut = SettingsContent(sections: [
            SettingsSection(id: "s", title: nil, rows: [.toggle(key: "shuffle", title: "Shuffle", summary: nil, isOn: false, isEnabled: true)]),
        ])
        #expect((try? sut.inspect().find(viewWithAccessibilityIdentifier: "settings.shuffle").find(IconSquare.self)) != nil)
    }
}
