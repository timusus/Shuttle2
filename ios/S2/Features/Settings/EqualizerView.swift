import Shared
import SwiftUI

/// The equalizer (#604): the shared `EqualizerViewModel`, as one grouped `Form`, pushed from Settings' Equalizer row
/// (`Route.equalizer`). Each change plays straight away through `IosEqualizer`, which designs the bands at the engine's
/// fixed 48 kHz and hands their coefficients to the engine (`docs/architecture/ios-port/phase-6-playback.md`).
struct EqualizerView: View {
    var body: some View {
        let viewModel = ViewModelCache.shared.viewModel(Route.equalizer.cacheKey) { AppGraph.shared.equalizerViewModel }
        Observing(viewModel.uiState) { state in
            EqualizerContent(
                state: EqualizerState(state),
                onEnabledChange: { viewModel.onEnabledChange(enabled: $0) },
                onPresetSelect: { index in
                    if state.presets.indices.contains(index) { viewModel.onPresetSelect(selected: state.presets[index]) }
                },
                onPreampChange: { viewModel.onPreampGainChange(gainDb: $0) },
                onBandChange: { viewModel.onBandGainChange(frequency: Int32($0), gainDb: $1) },
                onBandChangeFinished: { viewModel.onBandGainChangeFinished() }
            )
        }
    }
}

/// The equalizer's state in plain values.
struct EqualizerState: Equatable {
    struct Band: Equatable, Identifiable {
        var frequency: Int
        var gainDb: Float
        var id: Int { frequency }
    }

    struct ResponsePoint: Equatable {
        var frequencyHz: Float
        var gainDb: Float
    }

    var enabled: Bool
    var presets: [String]
    var selectedPreset: Int
    var preampDb: Float
    var bands: [Band]
    var response: [ResponsePoint]
    /// The attenuation that keeps boosted bands from clipping, in dB: 0, or negative.
    var headroomDb: Float
}

extension EqualizerState {
    init(_ state: EqualizerUiState) {
        self.init(
            enabled: state.enabled,
            presets: state.presets.map { $0.nameKey.localized() },
            selectedPreset: state.presets.firstIndex(of: state.selectedPreset) ?? 0,
            preampDb: state.preampGainDb,
            bands: state.bands.map { Band(frequency: Int($0.frequency), gainDb: $0.gainDb) },
            response: state.frequencyResponse.map { ResponsePoint(frequencyHz: $0.frequencyHz, gainDb: $0.gainDb) },
            headroomDb: state.headroomAttenuationDb
        )
    }
}

/// The equalizer from plain values. As on Android, the preset, preamp and bands are disabled while it's off.
struct EqualizerContent: View {
    /// A band's and the preamp's range, in dB: `IosEqualizer.maxBandGain` and `maxPreampGain`.
    static let gainRange: ClosedRange<Float> = -12...12

    let state: EqualizerState
    var onEnabledChange: (Bool) -> Void = { _ in }
    var onPresetSelect: (Int) -> Void = { _ in }
    var onPreampChange: (Float) -> Void = { _ in }
    var onBandChange: (Int, Float) -> Void = { _, _ in }
    var onBandChangeFinished: () -> Void = {}

    var body: some View {
        Form {
            Section {
                Toggle("Equalizer", isOn: Binding(get: { state.enabled }, set: onEnabledChange))
                    .accessibilityIdentifier("equalizer.enabled")
                Picker("Preset", selection: Binding(get: { state.selectedPreset }, set: onPresetSelect)) {
                    ForEach(state.presets.indices, id: \.self) { index in
                        Text(state.presets[index]).tag(index)
                    }
                }
                .disabled(!state.enabled)
                .accessibilityIdentifier("equalizer.preset")
            }
            Section {
                ResponseCurve(points: state.response, range: Self.gainRange)
                    .frame(height: 140)
                    .accessibilityIdentifier("equalizer.response")
                if state.headroomDb < -0.05 {
                    Text("Lowered by \(Self.decibels(-state.headroomDb, signed: false)) so boosted bands don't clip")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                        .accessibilityIdentifier("equalizer.headroom")
                }
            } header: {
                Text("Response")
            }
            Section {
                GainSlider(
                    id: "equalizer.preamp",
                    title: "Preamp",
                    value: state.preampDb,
                    range: Self.gainRange,
                    onChange: onPreampChange
                )
            }
            .disabled(!state.enabled)
            Section {
                ForEach(state.bands) { band in
                    GainSlider(
                        id: "equalizer.band.\(band.frequency)",
                        title: Self.frequency(band.frequency),
                        value: band.gainDb,
                        range: Self.gainRange,
                        onChange: { onBandChange(band.frequency, $0) },
                        onFinished: onBandChangeFinished
                    )
                }
            } header: {
                Text("Bands")
            }
            .disabled(!state.enabled)
        }
        .formStyle(.grouped)
        .navigationTitle("Equalizer")
    }

    /// "32 Hz", "1 kHz", "16 kHz".
    static func frequency(_ hz: Int) -> String {
        hz < 1_000 ? "\(hz) Hz" : "\(hz.formatted(.number.scale(0.001).precision(.fractionLength(0...1)))) kHz"
    }

    /// "+3.5 dB", or "3.5 dB" unsigned.
    static func decibels(_ db: Float, signed: Bool = true) -> String {
        String(format: signed ? "%+.1f dB" : "%.1f dB", db)
    }
}

/// A gain in dB: the title and value, over a slider. `onFinished` runs when a drag ends.
private struct GainSlider: View {
    let id: String
    let title: String
    let value: Float
    let range: ClosedRange<Float>
    let onChange: (Float) -> Void
    var onFinished: () -> Void = {}

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            LabeledContent(title) {
                Text(EqualizerContent.decibels(value)).monospacedDigit()
            }
            Slider(
                value: Binding(get: { value }, set: onChange),
                in: range,
                onEditingChanged: { editing in if !editing { onFinished() } }
            ) {
                Text(title)
            }
            .accessibilityIdentifier(id)
            .accessibilityValue(EqualizerContent.decibels(value))
        }
    }
}

/// The frequency response: gain over a log frequency axis, with the 0 dB line.
private struct ResponseCurve: View {
    let points: [EqualizerState.ResponsePoint]
    let range: ClosedRange<Float>

    var body: some View {
        Canvas { context, size in
            let zero = y(0, height: size.height)
            context.stroke(
                Path { $0.move(to: CGPoint(x: 0, y: zero)); $0.addLine(to: CGPoint(x: size.width, y: zero)) },
                with: .color(.secondary.opacity(0.5)),
                style: StrokeStyle(lineWidth: 1, dash: [4, 4])
            )
            guard let first = points.first, let last = points.last, last.frequencyHz > first.frequencyHz else { return }
            let low = log10(Double(first.frequencyHz))
            let span = log10(Double(last.frequencyHz)) - low
            let curve = Path { path in
                for (index, point) in points.enumerated() {
                    let location = CGPoint(
                        x: (log10(Double(point.frequencyHz)) - low) / span * size.width,
                        y: y(point.gainDb, height: size.height)
                    )
                    if index == 0 { path.move(to: location) } else { path.addLine(to: location) }
                }
            }
            context.stroke(curve, with: .color(.accentColor), style: StrokeStyle(lineWidth: 2, lineJoin: .round))
        }
        .accessibilityElement()
        .accessibilityLabel("Frequency response")
    }

    private func y(_ gainDb: Float, height: CGFloat) -> CGFloat {
        let clamped = min(max(gainDb, range.lowerBound), range.upperBound)
        return CGFloat((range.upperBound - clamped) / (range.upperBound - range.lowerBound)) * height
    }
}
