import Foundation
import Shared
import SwiftUI
import Testing
import ViewInspector
@testable import S2

/// Settings: `IosSettingsCatalog` and `SettingsUiState` mapped to the form's sections, the rows from plain values,
/// the Sources and Equalizer rows pushing their screens, and no preamp outside the Equalizer.
@MainActor
struct SettingsViewTests {
    private let catalog = IosSettingsCatalog.shared

    private func key(_ screen: SettingsScreen, _ index: Int) -> String {
        screen.items[index].key!
    }

    // MARK: Section mapping

    @Test func mapsEachScreenToItsTitledSectionsInCatalogOrder() {
        let sections = SettingsSection.sections(catalog: catalog, state: SettingsUiState(values: [:], lastScanDate: nil, events: []))
        #expect(sections.map(\.title) == ["Playback & sound", nil, "Sources", "Streaming quality", "Library", "Artwork", "Appearance"])
    }

    @Test func thePlaybackSectionLinksTheEqualizerAndHoldsReplayGainButNoPreamp() throws {
        let sections = SettingsSection.sections(catalog: catalog, state: SettingsUiState(values: [:], lastScanDate: nil, events: []))
        let rows = try #require(sections.dropFirst().first?.rows)

        // One Preamp, the Equalizer's (#645): Settings links to it rather than showing a second slider.
        #expect(rows.map(\.id) == ["settings.equalizer", key(catalog.playbackAndSound, 2)])
        #expect(rows.first == .link(id: "settings.equalizer", title: "Equalizer", systemImage: "slider.vertical.3", route: .equalizer))
        #expect(!sections.flatMap(\.rows).map(\.id).contains("preamp_gain"))
    }

    @Test func theSourcesSectionLeadsWithTheRowThatPushesSources() {
        let sections = SettingsSection.sections(catalog: catalog, state: SettingsUiState(values: [:], lastScanDate: nil, events: []))
        let sources = sections.first { $0.title == "Sources" }
        #expect(sources?.rows == [.link(id: "settings.sources", title: "Sources", systemImage: "server.rack", route: .sources)])
    }

    @Test func mapsSwitchesChoicesAndActionsWithTheirStoredValues() {
        let shuffleKey = key(catalog.playbackAndSound, 0)
        let state = SettingsUiState(values: [shuffleKey: KotlinBoolean(bool: true)], lastScanDate: nil, events: [])
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

    @Test func aStartedRescanShowsOnItsRow() {
        let rows = SettingsSection.sections(
            catalog: catalog,
            state: SettingsUiState(values: [:], lastScanDate: nil, events: []),
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
