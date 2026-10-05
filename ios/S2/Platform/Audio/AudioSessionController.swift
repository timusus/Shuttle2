import AVFoundation
import os

/// The `AVAudioSession` calls `AudioSessionController` makes, so tests can stand in for the session.
protocol AudioSession: AnyObject {
    func setCategory(
        _ category: AVAudioSession.Category,
        mode: AVAudioSession.Mode,
        policy: AVAudioSession.RouteSharingPolicy,
        options: AVAudioSession.CategoryOptions
    ) throws
    func setActive(_ active: Bool, options: AVAudioSession.SetActiveOptions) throws
}

extension AVAudioSession: AudioSession {}

/// Owns the app's audio session for music playback (phase 6, docs/architecture/ios-port/phase-6-playback.md):
/// the category, activation, interruptions, route changes and a media-services reset. It never touches
/// the player; it tells the player's owner what to do through the closures below.
///
/// The policy mirrors what ExoPlayer does for Android (`ExoPlayerFactory`: audio focus handled by the
/// player, `setHandleAudioBecomingNoisy(true)`):
/// - **Interruption** (a call, Siri, an alarm, another app taking the session): pause. When it ends
///   with `.shouldResume`, resume, but only if we were playing when it began and nobody played or
///   paused in between. That is Android's transient focus loss. An end without `.shouldResume` stays
///   paused, as a permanent focus loss does. Ducking is the system's job on iOS.
/// - **Route change, `.oldDeviceUnavailable`** (headphones unplugged, Bluetooth gone): pause, and don't
///   resume when the device returns. That is Android's `ACTION_AUDIO_BECOMING_NOISY`. Only when a
///   personal output went away (``pausesOnRouteChange(reason:previousOutputs:currentOutputs:)``): a
///   Bluetooth profile or codec switch on the same device is reported the same way (#715).
/// - **Output sample rate**: none of its business. The engine renders at a fixed 48 kHz and the EQ is
///   designed for that; the main mixer converts to whatever the route runs at (phase-6-playback.md).
/// - **Media services reset**: the session is configured again and the player's owner is told to
///   rebuild its engine and reload the current item at its position.
///
/// `PlaybackSystemCoordinator` owns one: it calls `configure()` at launch, `playRequested()` before every play and
/// `activate()` as the engine readies its output for it, `playbackPaused()` on every pause (all through
/// `EngineAudioPlayer`), `deactivate()` when the queue
/// empties, and sends `onPause`/`onResume` to the Kotlin `IosPlayerController` so the queue's state follows.
@MainActor
final class AudioSessionController {
    enum PauseReason: Equatable {
        case interruption
        case outputDeviceUnavailable
    }

    /// Whether playback is running (or about to), read when an interruption begins.
    var isPlaying: () -> Bool = { false }
    /// Pause playback. Called synchronously on the main thread.
    var onPause: (PauseReason) -> Void = { _ in }
    /// Resume playback after an interruption ended with `.shouldResume`.
    var onResume: () -> Void = {}
    /// Media services were reset: every audio object is invalid. Rebuild the engine and reload.
    var onMediaServicesReset: () -> Void = {}

    /// `nonisolated(unsafe)` for ``activate()``, which runs off the main actor: `AVAudioSession` is thread-safe (Apple
    /// recommends activating it off the main thread), and this is never reassigned.
    private nonisolated(unsafe) let session: AudioSession
    private let notificationCenter: NotificationCenter
    private let log = Logger(subsystem: "com.simplecityapps.shuttle2", category: "session")
    private var observers: [NSObjectProtocol] = []
    private var resumeAfterInterruption = false

    init(session: AudioSession = AVAudioSession.sharedInstance(), notificationCenter: NotificationCenter = .default) {
        self.session = session
        self.notificationCenter = notificationCenter
        observe()
    }

    deinit {
        observers.forEach(notificationCenter.removeObserver)
    }

    /// Sets the playback category: `.playback` so audio runs with the screen locked and the ring/silent
    /// switch on, `.longFormAudio` so AirPlay 2 and the route picker treat S2 as a music app.
    func configure() throws {
        try session.setCategory(.playback, mode: .default, policy: .longFormAudio, options: [])
    }

    /// An explicit play, about to be made: it cancels a pending resume after an interruption.
    func playRequested() {
        resumeAfterInterruption = false
    }

    /// Activates the session. Call before starting playback: it is what makes S2 the Now Playing app. Any thread: the
    /// engine calls it off the main thread while the track opens, as `setActive` can block for tens of milliseconds
    /// (#687).
    nonisolated func activate() throws {
        try session.setActive(true, options: [])
    }

    /// Deactivates the session on stop, so another app's paused audio can resume.
    func deactivate() {
        resumeAfterInterruption = false
        try? session.setActive(false, options: .notifyOthersOnDeactivation)
    }

    /// Playback was paused by anyone other than this controller. A pause during an interruption means
    /// the listener doesn't want playback back when it ends, as on Android.
    func playbackPaused() {
        resumeAfterInterruption = false
    }

    // MARK: - Route changes

    /// Outputs that are the listener's own (worn, plugged in or in their car), grouped by kind: unplugged
    /// or disconnected, the audio would carry on out loud from the speaker.
    nonisolated private static let personalOutputKinds: [AVAudioSession.Port: String] = [
        .headphones: "headphones",
        .bluetoothA2DP: "bluetooth",
        .bluetoothLE: "bluetooth",
        .bluetoothHFP: "bluetooth",
        .usbAudio: "usb",
        .lineOut: "lineOut",
        .carAudio: "car",
        .airPlay: "airPlay",
    ]

    /// Whether a route change pauses: the old device went away (Apple's "headphones unplugged" case) and
    /// it was a personal output (``personalOutputKinds``) of a kind the new route no longer has. A
    /// Bluetooth device switching profile (A2DP to HFP) or codec is still Bluetooth, and plays on.
    nonisolated static func pausesOnRouteChange(
        reason: AVAudioSession.RouteChangeReason?,
        previousOutputs: [AVAudioSession.Port],
        currentOutputs: [AVAudioSession.Port]
    ) -> Bool {
        guard reason == .oldDeviceUnavailable else { return false }
        let remaining = Set(currentOutputs.compactMap { personalOutputKinds[$0] })
        return previousOutputs.contains { port in
            personalOutputKinds[port].map { !remaining.contains($0) } ?? false
        }
    }

    // MARK: - Notifications

    /// A session notification, read on the posting thread so only a value crosses to main.
    enum SessionEvent: Sendable {
        /// `reason`: what the system said interrupted us (another app's audio, a muted built-in mic, a route that went).
        case interruptionBegan(reason: String)
        case interruptionEnded(shouldResume: Bool)
        case routeChanged(
            reason: AVAudioSession.RouteChangeReason?,
            previousOutputs: [AVAudioSession.Port],
            currentOutputs: [AVAudioSession.Port]
        )
        case mediaServicesReset

        init?(_ note: Notification) {
            let info = note.userInfo ?? [:]
            switch note.name {
            case AVAudioSession.interruptionNotification:
                switch (info[AVAudioSessionInterruptionTypeKey] as? UInt).flatMap(AVAudioSession.InterruptionType.init) {
                case .began:
                    let reason = (info[AVAudioSessionInterruptionReasonKey] as? UInt)
                        .flatMap(AVAudioSession.InterruptionReason.init)
                    self = .interruptionBegan(reason: Self.name(reason))
                case .ended:
                    let options = AVAudioSession.InterruptionOptions(rawValue: info[AVAudioSessionInterruptionOptionKey] as? UInt ?? 0)
                    self = .interruptionEnded(shouldResume: options.contains(.shouldResume))
                default:
                    return nil
                }
            case AVAudioSession.routeChangeNotification:
                let reason = (info[AVAudioSessionRouteChangeReasonKey] as? UInt).flatMap(AVAudioSession.RouteChangeReason.init)
                let previous = info[AVAudioSessionRouteChangePreviousRouteKey] as? AVAudioSessionRouteDescription
                let current = (note.object as? AVAudioSession)?.currentRoute
                self = .routeChanged(
                    reason: reason,
                    previousOutputs: previous?.outputs.map(\.portType) ?? [],
                    currentOutputs: current?.outputs.map(\.portType) ?? []
                )
            case AVAudioSession.mediaServicesWereResetNotification:
                self = .mediaServicesReset
            default:
                return nil
            }
        }

        private static func name(_ reason: AVAudioSession.InterruptionReason?) -> String {
            switch reason {
            case .default: "another app's audio"
            case .builtInMicMuted: "built-in mic muted"
            case let other?: "reason \(other.rawValue)"
            case nil: "unknown"
            }
        }
    }

    private func observe() {
        let names = [
            AVAudioSession.interruptionNotification,
            AVAudioSession.routeChangeNotification,
            AVAudioSession.mediaServicesWereResetNotification,
        ]
        observers = names.map { name in
            notificationCenter.addObserver(forName: name, object: session, queue: nil) { [weak self] note in
                guard let event = SessionEvent(note) else { return }
                // Route changes arrive on a background thread; everything here runs on main, inline
                // when the notification was posted there.
                if Thread.isMainThread {
                    MainActor.assumeIsolated { self?.handle(event) }
                } else {
                    DispatchQueue.main.async { [weak self] in self?.handle(event) }
                }
            }
        }
    }

    func handle(_ event: SessionEvent) {
        switch event {
        case let .interruptionBegan(reason):
            let wasPlaying = isPlaying()
            log.notice("interruption began: \(reason, privacy: .public), playing \(wasPlaying)")
            if wasPlaying { onPause(.interruption) }
            // Set after `onPause`, whose pause may come back through `playbackPaused()`.
            resumeAfterInterruption = wasPlaying
        case let .interruptionEnded(shouldResume):
            let resume = resumeAfterInterruption && shouldResume
            log.notice("interruption ended, shouldResume \(shouldResume), resuming \(resume)")
            resumeAfterInterruption = false
            guard resume else { return }
            do {
                try session.setActive(true, options: [])
            } catch {
                log.error("session didn't reactivate after the interruption: \(String(describing: error), privacy: .public)")
                return // the other app still holds the hardware; stay paused
            }
            onResume()
        case let .routeChanged(reason, previousOutputs, currentOutputs):
            let pauses = Self.pausesOnRouteChange(
                reason: reason, previousOutputs: previousOutputs, currentOutputs: currentOutputs
            )
            log.notice("""
                route changed: \(Self.name(reason), privacy: .public), \
                from \(previousOutputs.map(\.rawValue), privacy: .public) \
                to \(currentOutputs.map(\.rawValue), privacy: .public), pauses \(pauses)
                """)
            if pauses {
                // Unplugging while an interruption holds playback must not resume onto the speaker.
                resumeAfterInterruption = false
                if isPlaying() { onPause(.outputDeviceUnavailable) }
            }
        case .mediaServicesReset:
            handleMediaServicesReset()
        }
    }

    private static func name(_ reason: AVAudioSession.RouteChangeReason?) -> String {
        switch reason {
        case .newDeviceAvailable: "newDeviceAvailable"
        case .oldDeviceUnavailable: "oldDeviceUnavailable"
        case .categoryChange: "categoryChange"
        case .override: "override"
        case .wakeFromSleep: "wakeFromSleep"
        case .noSuitableRouteForCategory: "noSuitableRouteForCategory"
        case .routeConfigurationChange: "routeConfigurationChange"
        case let other?: "reason \(other.rawValue)"
        case nil: "unknown"
        }
    }

    private func handleMediaServicesReset() {
        log.notice("media services were reset")
        resumeAfterInterruption = false
        try? configure()
        onMediaServicesReset()
    }
}
