import SwiftUI

// MARK: - Playback speed

/// The Audio sheet, from Now Playing's Audio button: the playback speed as a large figure, a 0.5×–2× slider in
/// tenths and preset chips (Shuttle Podcasts' speed sheet), with a button at the bottom that pushes the
/// Equalizer & Playback Settings screen inside the sheet.
struct NowPlayingAudioSheet: View {
    let speed: Float
    let setSpeed: (Float) -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    /// The big figure scales with the largest text style rather than sitting at 56 pt.
    @ScaledMetric(relativeTo: .largeTitle) private var figureSize: CGFloat = 56

    /// The slider's position while dragged; the player's speed is the source of truth otherwise, so a drag doesn't
    /// round-trip through the player every frame.
    @State private var draggingSpeed: Float?
    @State private var detentTrigger = 0

    static let minSpeed: Float = 0.5
    static let maxSpeed: Float = 2
    static let step: Float = 0.1
    static let presets: [Float] = [0.8, 1, 1.2, 1.5, 2]

    var body: some View {
        let shown = draggingSpeed ?? speed
        NavigationStack {
            // A scroll view, not a spacer under the content: at larger text sizes the sheet outgrows its medium detent.
            ScrollView {
                VStack(spacing: Spacing.large) {
                    Text(Self.format(shown))
                        .font(.system(size: figureSize, weight: .bold, design: .rounded))
                        .monospacedDigit()
                        .contentTransition(.numericText())
                        .animation(.snappy.reduced(reduceMotion), value: shown)
                        .padding(.top, Spacing.large)
                        .accessibilityIdentifier("audio.speed.value")
                    slider(shown: shown)
                    presetChips(current: shown)
                    NavigationLink {
                        PlaybackSettingsView()
                    } label: {
                        Label("Equalizer & Playback Settings", systemImage: "slider.vertical.3")
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, Spacing.small)
                    }
                    .buttonStyle(.bordered)
                    .accessibilityIdentifier("audio.playbackSettings")
                }
                .padding(.horizontal, Spacing.large)
                .padding(.bottom, Spacing.large)
            }
            .navigationTitle("Playback Speed")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }

    private func slider(shown: Float) -> some View {
        VStack(spacing: Spacing.xsmall) {
            Slider(
                value: Binding(
                    get: { Double(shown) },
                    set: { newValue in
                        let stepped = Self.snap(Float(newValue))
                        if stepped != draggingSpeed {
                            draggingSpeed = stepped
                            detentTrigger += 1
                        }
                    }
                ),
                in: Double(Self.minSpeed)...Double(Self.maxSpeed),
                step: Double(Self.step),
                onEditingChanged: { editing in
                    if !editing, let dragged = draggingSpeed {
                        setSpeed(dragged)
                        draggingSpeed = nil
                    }
                }
            )
            .sensoryFeedback(.selection, trigger: detentTrigger)
            .accessibilityLabel("Playback speed")
            .accessibilityValue(Self.format(shown))
            .accessibilityIdentifier("audio.speed.slider")
            HStack {
                Text(Self.format(Self.minSpeed))
                Spacer()
                Text(Self.format(Self.maxSpeed))
            }
            .font(.s2Caption)
            .foregroundStyle(.secondary)
        }
    }

    /// Five chips in a row; stacked once the text outgrows a fifth of the width.
    @ViewBuilder
    private func presetChips(current: Float) -> some View {
        if dynamicTypeSize.isAccessibilitySize {
            VStack(spacing: Spacing.smallMedium) { presetButtons(current: current) }
        } else {
            HStack(spacing: Spacing.smallMedium) { presetButtons(current: current) }
        }
    }

    private func presetButtons(current: Float) -> some View {
        ForEach(Self.presets, id: \.self) { preset in
            let isSelected = abs(current - preset) < 0.001
            Button {
                draggingSpeed = nil
                setSpeed(preset)
                detentTrigger += 1
            } label: {
                Text(Self.format(preset))
                    .font(.s2RowSubtitle)
                    .monospacedDigit()
                    .lineLimit(1)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, Spacing.small)
            }
            .buttonStyle(.bordered)
            .tint(isSelected ? Color.accentColor : Color.secondary)
            .accessibilityLabel("\(Self.format(preset)) speed")
            .accessibilityAddTraits(isSelected ? .isSelected : [])
            .accessibilityIdentifier("audio.speed.\(Self.format(preset))")
        }
    }

    /// Rounds to the nearest 0.1 within range, so the value shown and the value sent agree.
    static func snap(_ speed: Float) -> Float {
        min(max((speed / step).rounded() * step, minSpeed), maxSpeed)
    }

    /// "1×", "1.5×": the Audio button's value reads the same.
    static func format(_ speed: Float) -> String {
        let rounded = (speed * 10).rounded() / 10
        return rounded.formatted(.number.precision(.fractionLength(0...1))) + "×"
    }
}

// MARK: - Sleep timer

/// The sleep timer sheet, from Now Playing's moon button (Shuttle Podcasts' sleep timer): the chosen length in large
/// digits, a 5–60 minute slider in fives, Start, and End of Track; once running, the time left and Turn Off Timer.
struct NowPlayingSleepTimerSheet: View {
    let isActive: Bool
    /// Whether Start also waits for the current track to finish.
    let playToEnd: Bool
    let startTimer: (_ minutes: Int, _ playToEnd: Bool) -> Void
    let stopTimer: () -> Void
    /// The time left in milliseconds, ticking while collected: nil when off, 0 while it waits for the track to end.
    var remaining: () -> AsyncStream<Int?> = { AsyncStream { $0.finish() } }

    @Environment(\.dismiss) private var dismiss
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @ScaledMetric(relativeTo: .largeTitle) private var pickerFigureSize: CGFloat = 52
    @ScaledMetric(relativeTo: .largeTitle) private var remainingFigureSize: CGFloat = 44

    @State private var minutes: Double = 30
    @State private var finishTrack: Bool?
    @State private var remainingMs: Int?
    @State private var detentTrigger = 0

    static let minMinutes: Double = 5
    static let maxMinutes: Double = 60
    static let step: Double = 5

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: Spacing.large) {
                    if isActive {
                        activeTimer
                    } else {
                        picker
                    }
                }
                .padding(.horizontal, Spacing.large)
                .padding(.vertical, Spacing.medium)
            }
            .navigationTitle("Sleep Timer")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
        .task(id: isActive) {
            guard isActive else { return remainingMs = nil }
            for await ms in remaining() { remainingMs = ms }
        }
    }

    private var picker: some View {
        VStack(spacing: Spacing.large) {
            Text("\(Int(minutes)) min")
                .font(.system(size: pickerFigureSize, weight: .bold, design: .rounded))
                .monospacedDigit()
                .contentTransition(.numericText())
                .animation(.snappy.reduced(reduceMotion), value: minutes)
                .accessibilityIdentifier("sleepTimer.minutes")

            VStack(spacing: Spacing.xsmall) {
                Slider(
                    value: Binding(
                        get: { minutes },
                        set: { newValue in
                            let stepped = (newValue / Self.step).rounded() * Self.step
                            if stepped != minutes {
                                minutes = stepped
                                detentTrigger += 1
                            }
                        }
                    ),
                    in: Self.minMinutes...Self.maxMinutes,
                    step: Self.step
                )
                .sensoryFeedback(.selection, trigger: detentTrigger)
                .accessibilityLabel("Sleep timer length")
                .accessibilityValue("\(Int(minutes)) minutes")
                .accessibilityIdentifier("sleepTimer.slider")
                HStack {
                    Text("\(Int(Self.minMinutes)) min")
                    Spacer()
                    Text("\(Int(Self.maxMinutes)) min")
                }
                .font(.s2Caption)
                .foregroundStyle(.secondary)
            }

            Toggle("Also finish the current track", isOn: Binding(
                get: { finishTrack ?? playToEnd },
                set: { finishTrack = $0 }
            ))
            .font(.s2RowSubtitle)
            .accessibilityIdentifier("sleepTimer.playToEnd")

            Button {
                startTimer(Int(minutes), finishTrack ?? playToEnd)
                dismiss()
            } label: {
                Text("Start")
                    .fontWeight(.semibold)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, Spacing.small)
            }
            .buttonStyle(.borderedProminent)
            .accessibilityIdentifier("sleepTimer.start")

            Button {
                startTimer(0, true)
                dismiss()
            } label: {
                Label("End of Track", systemImage: "forward.end")
                    .fixedSize(horizontal: false, vertical: true)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, Spacing.xsmall)
            }
            .buttonStyle(.bordered)
            .accessibilityIdentifier("sleepTimer.endOfTrack")
        }
    }

    private var activeTimer: some View {
        VStack(spacing: Spacing.medium) {
            Image(systemName: "moon.fill")
                .font(.largeTitle)
                .foregroundStyle(Color.accentColor)
                .symbolEffect(.pulse, isActive: !reduceMotion)
                .accessibilityHidden(true)

            if let remainingMs, remainingMs > 0 {
                Text(Self.remainingText(ms: remainingMs))
                    .font(.system(size: remainingFigureSize, weight: .bold, design: .rounded))
                    .monospacedDigit()
                    .accessibilityIdentifier("sleepTimer.remaining")
                Text("remaining")
                    .font(.s2RowSubtitle)
                    .foregroundStyle(.secondary)
            } else {
                Text("End of track")
                    .font(.s2Title)
                    .accessibilityIdentifier("sleepTimer.remaining")
                Text("Playback pauses when the track ends")
                    .font(.s2RowSubtitle)
                    .foregroundStyle(.secondary)
            }

            Button(role: .destructive) {
                stopTimer()
                dismiss()
            } label: {
                Label("Turn Off Timer", systemImage: "xmark.circle")
                    .fixedSize(horizontal: false, vertical: true)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, Spacing.xsmall)
            }
            .buttonStyle(.bordered)
            .accessibilityIdentifier("sleepTimer.stop")
        }
    }

    /// "12m 05s", or "45s" under a minute.
    static func remainingText(ms: Int) -> String {
        let total = ms / 1000
        let (minutes, seconds) = (total / 60, total % 60)
        return minutes > 0 ? "\(minutes)m \(String(format: "%02d", seconds))s" : "\(seconds)s"
    }
}

#Preview("Audio") {
    NowPlayingAudioSheet(speed: 1.25) { _ in }
}

#Preview("Sleep timer") {
    NowPlayingSleepTimerSheet(isActive: false, playToEnd: false, startTimer: { _, _ in }, stopTimer: {})
}

#Preview("Sleep timer on") {
    NowPlayingSleepTimerSheet(
        isActive: true, playToEnd: false, startTimer: { _, _ in }, stopTimer: {},
        remaining: { AsyncStream { $0.yield(754_000) } }
    )
}
