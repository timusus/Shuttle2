import Foundation
import Shared
import SwiftUI
import Testing
import ViewInspector
@testable import S2

/// The equalizer: the shared `EqualizerUiState` in plain values, and each control reporting back.
@MainActor
struct EqualizerViewTests {
    private func state(enabled: Bool = true, headroomDb: Float = 0) -> EqualizerState {
        EqualizerState(
            enabled: enabled,
            presets: ["Flat", "Custom", "Bass boost"],
            selectedPreset: 0,
            preampDb: 0,
            bands: [.init(frequency: 32, gainDb: 0), .init(frequency: 1_000, gainDb: 6), .init(frequency: 16_000, gainDb: -3)],
            response: [.init(frequencyHz: 20, gainDb: 0), .init(frequencyHz: 1_000, gainDb: 6), .init(frequencyHz: 20_000, gainDb: -3)],
            headroomDb: headroomDb
        )
    }

    // MARK: State

    @Test func theViewModelsStateMapsToPlainValues() {
        let graph = makeTestGraph(audioPlayer: EngineAudioPlayer(engine: FakeAudioEngine()))
        let mapped = EqualizerState(graph.equalizerViewModel.uiState.value as! EqualizerUiState)

        #expect(mapped.presets.contains("Flat"))
        #expect(mapped.presets.contains("Bass boost"))
        #expect(mapped.presets.count == 6)
        #expect(mapped.bands.map(\.frequency) == [32, 63, 125, 250, 500, 1_000, 2_000, 4_000, 8_000, 16_000])
        #expect(!mapped.response.isEmpty)
    }

    @Test func labelsFrequenciesInHertzAndKilohertz() {
        #expect(EqualizerContent.frequency(32) == "32 Hz")
        #expect(EqualizerContent.frequency(1_000) == "1 kHz")
        #expect(EqualizerContent.frequency(16_000) == "16 kHz")
        #expect(EqualizerContent.decibels(6) == "+6.0 dB")
        #expect(EqualizerContent.decibels(-3.5) == "-3.5 dB")
    }

    // MARK: Content

    @Test func showsEachBandWithItsGain() throws {
        let sut = EqualizerContent(state: state())
        #expect((try? sut.inspect().find(text: "1 kHz")) != nil)
        // A band column shows its gain short, over its slider; the slider reads the full "+6.0 dB" to VoiceOver.
        #expect((try? sut.inspect().find(text: "+6.0")) != nil)
        #expect((try? sut.inspect().find(text: "1k")) != nil)
        #expect(try sut.inspect().find(viewWithAccessibilityIdentifier: "equalizer.band.1000").accessibilityValue().string() == "+6.0 dB")
        #expect((try? sut.inspect().find(viewWithAccessibilityIdentifier: "equalizer.band.16000").slider()) != nil)
        #expect((try? sut.inspect().find(viewWithAccessibilityIdentifier: "equalizer.response")) != nil)
    }

    @Test func switchingItOnReportsIt() throws {
        var enabled: Bool?
        let sut = EqualizerContent(state: state(enabled: false), onEnabledChange: { enabled = $0 })
        try sut.inspect().find(viewWithAccessibilityIdentifier: "equalizer.enabled").toggle().tap()
        #expect(enabled == true)
    }

    @Test func choosingAPresetReportsItsIndex() throws {
        var chosen: Int?
        let sut = EqualizerContent(state: state(), onPresetSelect: { chosen = $0 })
        try sut.inspect().find(viewWithAccessibilityIdentifier: "equalizer.preset").picker().select(value: 2)
        #expect(chosen == 2)
    }

    // ViewInspector sets a slider's position as a fraction of its range: 0.75 of ±12 dB is +6 dB.

    @Test func movingABandReportsItsFrequencyAndGain() throws {
        var moved: (Int, Float)?
        let sut = EqualizerContent(state: state(), onBandChange: { moved = ($0, $1) })
        try sut.inspect().find(viewWithAccessibilityIdentifier: "equalizer.band.1000").slider().setValue(0.75)
        #expect(moved?.0 == 1_000)
        #expect(moved?.1 == 6)
    }

    @Test func movingThePreampReportsIt() throws {
        var preamp: Float?
        let sut = EqualizerContent(state: state(), onPreampChange: { preamp = $0 })
        try sut.inspect().find(viewWithAccessibilityIdentifier: "equalizer.preamp").slider().setValue(0.25)
        #expect(preamp == -6)
    }

    @Test func theBandsAreDisabledWhileItsOff() throws {
        let sut = EqualizerContent(state: state(enabled: false))
        let band = try sut.inspect().find(viewWithAccessibilityIdentifier: "equalizer.band.32").slider()
        #expect(band.isDisabled())
    }

    @Test func showsTheHeadroomOnlyWhenBoostsArePulledBack() throws {
        #expect((try? EqualizerContent(state: state()).inspect().find(viewWithAccessibilityIdentifier: "equalizer.headroom")) == nil)
        let lowered = EqualizerContent(state: state(headroomDb: -6))
        #expect((try? lowered.inspect().find(text: "Lowered by 6.0 dB so boosted bands don't clip")) != nil)
    }
}
