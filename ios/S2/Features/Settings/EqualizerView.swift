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

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @ScaledMetric(relativeTo: .body) private var curveHeight: CGFloat = 140

    var body: some View {
        Form {
            Section {
                Toggle(isOn: Binding(get: { state.enabled }, set: onEnabledChange)) {
                    Label { Text("Equalizer") } icon: { IconSquare(systemImage: "slider.vertical.3", style: .filled(.pink)) }
                }
                .accessibilityIdentifier("equalizer.enabled")
                Picker(selection: Binding(get: { state.selectedPreset }, set: onPresetSelect)) {
                    ForEach(state.presets.indices, id: \.self) { index in
                        Text(state.presets[index]).tag(index)
                    }
                } label: {
                    Label { Text("Preset") } icon: { IconSquare(systemImage: "list.bullet", style: .filled(.orange)) }
                }
                .disabled(!state.enabled)
                .accessibilityIdentifier("equalizer.preset")
            }
            Section {
                ResponseCurve(points: state.response, range: Self.gainRange, isEnabled: state.enabled)
                    .frame(height: curveHeight)
                    .padding(.vertical, Spacing.small)
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
                if dynamicTypeSize.isAccessibilitySize {
                    // Ten columns can't hold accessibility-size labels: a slider a row, full width.
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
                } else {
                    BandSliders(bands: state.bands, range: Self.gainRange, onChange: onBandChange, onFinished: onBandChangeFinished)
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

    /// A band column's label: "32", "1k", "16k".
    static func shortFrequency(_ hz: Int) -> String {
        hz < 1_000 ? "\(hz)" : "\(hz.formatted(.number.scale(0.001).precision(.fractionLength(0...1))))k"
    }

    /// A band column's gain: "+6.0", "-3.5".
    static func gain(_ db: Float) -> String {
        String(format: "%+.1f", db)
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
        VStack(alignment: .leading, spacing: Spacing.xsmall) {
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

/// The bands as a row of vertical sliders, a column each: the gain over the slider over the frequency, as a mixing
/// desk (Android's `EqBand`). Each is a `Slider` turned upright, so VoiceOver adjusts it by swiping up and down and
/// reads its frequency and gain.
private struct BandSliders: View {
    let bands: [EqualizerState.Band]
    let range: ClosedRange<Float>
    let onChange: (Int, Float) -> Void
    let onFinished: () -> Void

    @ScaledMetric(relativeTo: .caption2) private var trackLength: CGFloat = 180
    @Environment(\.isEnabled) private var isEnabled
    @Environment(\.artworkTint) private var tint

    var body: some View {
        HStack(alignment: .top, spacing: 0) {
            ForEach(bands) { band in
                VStack(spacing: Spacing.small) {
                    Text(EqualizerContent.gain(band.gainDb))
                        .font(.caption2.monospacedDigit())
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                        .minimumScaleFactor(0.7)
                        .accessibilityHidden(true)
                    Slider(
                        value: Binding(get: { band.gainDb }, set: { onChange(band.frequency, $0) }),
                        in: range,
                        onEditingChanged: { editing in if !editing { onFinished() } }
                    ) {
                        Text(EqualizerContent.frequency(band.frequency))
                    }
                    .tint(isEnabled ? tint : .secondary)
                    .frame(width: trackLength)
                    .rotationEffect(.degrees(-90))
                    .frame(width: Spacing.xlarge, height: trackLength)
                    .accessibilityIdentifier("equalizer.band.\(band.frequency)")
                    .accessibilityValue(EqualizerContent.decibels(band.gainDb))
                    Text(EqualizerContent.shortFrequency(band.frequency))
                        .font(.caption2.weight(.semibold))
                        .lineLimit(1)
                        .minimumScaleFactor(0.7)
                        .accessibilityHidden(true)
                }
                .frame(maxWidth: .infinity)
            }
        }
        .padding(.vertical, Spacing.small)
        .background(alignment: .center) {
            // The 0 dB line, through the middle of the tracks.
            Rectangle()
                .fill(.secondary.opacity(0.25))
                .frame(height: Spacing.hairline)
        }
    }
}

/// The frequency response: gain over a log frequency axis, drawn with `Path`s, with the 0 dB line and a wash of
/// the tint under the curve.
private struct ResponseCurve: View {
    let points: [EqualizerState.ResponsePoint]
    let range: ClosedRange<Float>
    var isEnabled = true

    @Environment(\.artworkTint) private var tint
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        let color = isEnabled ? tint : Color.secondary
        ZStack {
            ZeroLine(range: range)
                .stroke(.secondary.opacity(0.5), style: StrokeStyle(lineWidth: 1, dash: [4, 4]))
            ResponseShape(points: points, range: range, closed: true)
                .fill(LinearGradient(colors: [color.opacity(0.3), color.opacity(0.02)], startPoint: .top, endPoint: .bottom))
            ResponseShape(points: points, range: range, closed: false)
                .stroke(color, style: StrokeStyle(lineWidth: 2.5, lineCap: .round, lineJoin: .round))
        }
        .animation(Motion.tintChange.reduced(reduceMotion), value: points)
        .accessibilityElement()
        .accessibilityLabel("Frequency response")
    }
}

/// Maps gain to height within `range`, top to bottom.
private func responseY(_ gainDb: Float, range: ClosedRange<Float>, height: CGFloat) -> CGFloat {
    let clamped = min(max(gainDb, range.lowerBound), range.upperBound)
    return CGFloat((range.upperBound - clamped) / (range.upperBound - range.lowerBound)) * height
}

private struct ZeroLine: Shape {
    let range: ClosedRange<Float>

    func path(in rect: CGRect) -> Path {
        let y = responseY(0, range: range, height: rect.height)
        return Path { $0.move(to: CGPoint(x: rect.minX, y: y)); $0.addLine(to: CGPoint(x: rect.maxX, y: y)) }
    }
}

/// The response as a line or, `closed`, as the area between it and the 0 dB line.
private struct ResponseShape: Shape {
    let points: [EqualizerState.ResponsePoint]
    let range: ClosedRange<Float>
    let closed: Bool

    func path(in rect: CGRect) -> Path {
        guard let first = points.first, let last = points.last, last.frequencyHz > first.frequencyHz else { return Path() }
        let low = log10(Double(first.frequencyHz))
        let span = log10(Double(last.frequencyHz)) - low
        return Path { path in
            for (index, point) in points.enumerated() {
                let location = CGPoint(
                    x: rect.minX + (log10(Double(point.frequencyHz)) - low) / span * rect.width,
                    y: rect.minY + responseY(point.gainDb, range: range, height: rect.height)
                )
                if index == 0 { path.move(to: location) } else { path.addLine(to: location) }
            }
            if closed {
                let zero = rect.minY + responseY(0, range: range, height: rect.height)
                path.addLine(to: CGPoint(x: rect.maxX, y: zero))
                path.addLine(to: CGPoint(x: rect.minX, y: zero))
                path.closeSubpath()
            }
        }
    }
}
