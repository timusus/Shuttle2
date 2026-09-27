import AVFoundation

/// The `AVAudioSession` calls `AudioSessionController` makes, so tests can stand in for the session.
protocol AudioSession: AnyObject {
    func setCategory(
        _ category: AVAudioSession.Category,
        mode: AVAudioSession.Mode,
        policy: AVAudioSession.RouteSharingPolicy,
        options: AVAudioSession.CategoryOptions
    ) throws
    func setActive(_ active: Bool, options: AVAudioSession.SetActiveOptions) throws
    /// The output's current hardware sample rate, in Hz.
    var sampleRate: Double { get }
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
///   resume when the device returns. That is Android's `ACTION_AUDIO_BECOMING_NOISY`.
/// - **Output sample rate change** on any route change: reported, for the EQ coefficients (nothing
///   consumes it until the equalizer is shared, #588).
/// - **Media services reset**: the session is configured again and the player's owner is told to
///   rebuild its engine and reload the current item at its position.
///
/// `PlaybackSystemCoordinator` owns one: it calls `configure()` at launch, `activate()` before every
/// play and `playbackPaused()` on every pause (through `EngineAudioPlayer`), `deactivate()` when the queue
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
    /// The output's sample rate changed, in Hz. Called once per distinct rate, starting after `configure()`.
    var onOutputSampleRateChanged: (Double) -> Void = { _ in }
    /// Media services were reset: every audio object is invalid. Rebuild the engine and reload.
    var onMediaServicesReset: () -> Void = {}

    private let session: AudioSession
    private let notificationCenter: NotificationCenter
    private var observers: [NSObjectProtocol] = []
    private var resumeAfterInterruption = false
    private var reportedSampleRate: Double?

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
        reportSampleRateIfChanged()
    }

    /// Activates the session. Call before starting playback: it is what makes S2 the Now Playing app.
    /// An explicit play also cancels a pending resume after an interruption.
    func activate() throws {
        resumeAfterInterruption = false
        try session.setActive(true, options: [])
        reportSampleRateIfChanged()
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

    // MARK: - Notifications

    /// A session notification, read on the posting thread so only a value crosses to main.
    private enum SessionEvent: Sendable {
        case interruptionBegan
        case interruptionEnded(shouldResume: Bool)
        case routeChanged(oldDeviceUnavailable: Bool)
        case mediaServicesReset

        init?(_ note: Notification) {
            let info = note.userInfo ?? [:]
            switch note.name {
            case AVAudioSession.interruptionNotification:
                switch (info[AVAudioSessionInterruptionTypeKey] as? UInt).flatMap(AVAudioSession.InterruptionType.init) {
                case .began:
                    self = .interruptionBegan
                case .ended:
                    let options = AVAudioSession.InterruptionOptions(rawValue: info[AVAudioSessionInterruptionOptionKey] as? UInt ?? 0)
                    self = .interruptionEnded(shouldResume: options.contains(.shouldResume))
                default:
                    return nil
                }
            case AVAudioSession.routeChangeNotification:
                let reason = (info[AVAudioSessionRouteChangeReasonKey] as? UInt).flatMap(AVAudioSession.RouteChangeReason.init)
                self = .routeChanged(oldDeviceUnavailable: reason == .oldDeviceUnavailable)
            case AVAudioSession.mediaServicesWereResetNotification:
                self = .mediaServicesReset
            default:
                return nil
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

    private func handle(_ event: SessionEvent) {
        switch event {
        case .interruptionBegan:
            let wasPlaying = isPlaying()
            if wasPlaying { onPause(.interruption) }
            // Set after `onPause`, whose pause may come back through `playbackPaused()`.
            resumeAfterInterruption = wasPlaying
        case let .interruptionEnded(shouldResume):
            let resume = resumeAfterInterruption && shouldResume
            resumeAfterInterruption = false
            guard resume else { return }
            do {
                try session.setActive(true, options: [])
            } catch {
                return // the other app still holds the hardware; stay paused
            }
            onResume()
        case let .routeChanged(oldDeviceUnavailable):
            if oldDeviceUnavailable {
                // Unplugging while an interruption holds playback must not resume onto the speaker.
                resumeAfterInterruption = false
                if isPlaying() { onPause(.outputDeviceUnavailable) }
            }
            reportSampleRateIfChanged()
        case .mediaServicesReset:
            handleMediaServicesReset()
        }
    }

    private func handleMediaServicesReset() {
        resumeAfterInterruption = false
        reportedSampleRate = nil
        try? configure()
        onMediaServicesReset()
    }

    private func reportSampleRateIfChanged() {
        let rate = session.sampleRate
        guard rate > 0, rate != reportedSampleRate else { return }
        reportedSampleRate = rate
        onOutputSampleRateChanged(rate)
    }
}
