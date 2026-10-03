import AVFoundation
import Testing
@testable import S2

/// The session policy, driven by notifications posted on a private centre against a fake session:
/// pause on interruption and resume only when the system says so and we were playing (Android's
/// transient focus loss), pause when the output device goes away (Android's becoming-noisy).
@MainActor
struct AudioSessionControllerTests {
    private final class FakeSession: AudioSession {
        private(set) var categories: [(AVAudioSession.Category, AVAudioSession.Mode, AVAudioSession.RouteSharingPolicy)] = []
        private(set) var activations: [Bool] = []
        var failActivation = false

        func setCategory(
            _ category: AVAudioSession.Category,
            mode: AVAudioSession.Mode,
            policy: AVAudioSession.RouteSharingPolicy,
            options: AVAudioSession.CategoryOptions
        ) throws {
            categories.append((category, mode, policy))
        }

        func setActive(_ active: Bool, options: AVAudioSession.SetActiveOptions) throws {
            if active && failActivation { throw NSError(domain: "FakeSession", code: 1) }
            activations.append(active)
        }
    }

    private final class Harness {
        let session = FakeSession()
        let center = NotificationCenter()
        let controller: AudioSessionController
        var playing = false
        var pauses: [AudioSessionController.PauseReason] = []
        var resumes = 0
        var resets = 0

        @MainActor init() {
            controller = AudioSessionController(session: session, notificationCenter: center)
            controller.isPlaying = { [unowned self] in playing }
            controller.onPause = { [unowned self] reason in
                pauses.append(reason)
                playing = false
                controller.playbackPaused() // the bridge reports every pause, ours included
            }
            controller.onResume = { [unowned self] in resumes += 1; playing = true }
            controller.onMediaServicesReset = { [unowned self] in resets += 1 }
        }

        func post(_ name: Notification.Name, _ userInfo: [AnyHashable: Any] = [:]) {
            center.post(name: name, object: session, userInfo: userInfo)
        }

        func interruptionBegan() {
            post(AVAudioSession.interruptionNotification, [
                AVAudioSessionInterruptionTypeKey: AVAudioSession.InterruptionType.began.rawValue,
            ])
        }

        func interruptionEnded(shouldResume: Bool) {
            let options: AVAudioSession.InterruptionOptions = shouldResume ? .shouldResume : []
            post(AVAudioSession.interruptionNotification, [
                AVAudioSessionInterruptionTypeKey: AVAudioSession.InterruptionType.ended.rawValue,
                AVAudioSessionInterruptionOptionKey: options.rawValue,
            ])
        }

        func routeChanged(_ reason: AVAudioSession.RouteChangeReason) {
            post(AVAudioSession.routeChangeNotification, [AVAudioSessionRouteChangeReasonKey: reason.rawValue])
        }

        /// A route change between real ports. The notification's routes can't be built in a test, so this
        /// hands the controller the event it reads from one.
        @MainActor func routeChanged(
            _ reason: AVAudioSession.RouteChangeReason,
            from previous: [AVAudioSession.Port],
            to current: [AVAudioSession.Port]
        ) {
            controller.handle(.routeChanged(reason: reason, previousOutputs: previous, currentOutputs: current))
        }
    }

    @Test func configureSetsTheLongFormPlaybackCategory() throws {
        let h = Harness()
        try h.controller.configure()
        #expect(h.session.categories.count == 1)
        #expect(h.session.categories.first?.0 == .playback)
        #expect(h.session.categories.first?.1 == .default)
        #expect(h.session.categories.first?.2 == .longFormAudio)
    }

    @Test func activateAndDeactivateToggleTheSession() throws {
        let h = Harness()
        try h.controller.activate()
        h.controller.deactivate()
        #expect(h.session.activations == [true, false])
    }

    @Test func interruptionWhilePlayingPausesAndResumesWhenTheSystemSaysSo() {
        let h = Harness()
        h.playing = true
        h.interruptionBegan()
        #expect(h.pauses == [.interruption])
        #expect(!h.playing)

        h.interruptionEnded(shouldResume: true)
        #expect(h.resumes == 1)
        #expect(h.playing)
        #expect(h.session.activations == [true])
    }

    @Test func interruptionEndingWithoutShouldResumeStaysPaused() {
        let h = Harness()
        h.playing = true
        h.interruptionBegan()
        h.interruptionEnded(shouldResume: false)
        #expect(h.pauses == [.interruption])
        #expect(h.resumes == 0)
    }

    @Test func interruptionWhilePausedNeverResumes() {
        let h = Harness()
        h.interruptionBegan()
        h.interruptionEnded(shouldResume: true)
        #expect(h.pauses.isEmpty)
        #expect(h.resumes == 0)
    }

    @Test func aPauseDuringTheInterruptionCancelsTheResume() {
        let h = Harness()
        h.playing = true
        h.interruptionBegan()
        h.controller.playbackPaused() // the listener paused from the lock screen meanwhile
        h.interruptionEnded(shouldResume: true)
        #expect(h.resumes == 0)
    }

    @Test func resumeIsSkippedWhenTheSessionCannotBeReactivated() {
        let h = Harness()
        h.playing = true
        h.interruptionBegan()
        h.session.failActivation = true
        h.interruptionEnded(shouldResume: true)
        #expect(h.resumes == 0)
    }

    @Test func headphonesUnpluggedWhilePlayingPauses() {
        let h = Harness()
        h.playing = true
        h.routeChanged(.oldDeviceUnavailable, from: [.headphones], to: [.builtInSpeaker])
        #expect(h.pauses == [.outputDeviceUnavailable])

        h.routeChanged(.newDeviceAvailable, from: [.builtInSpeaker], to: [.headphones]) // plugged back in: Android doesn't resume, nor do we
        #expect(h.resumes == 0)
        #expect(h.pauses.count == 1)
    }

    @Test func otherRouteChangesDoNotPause() {
        let h = Harness()
        h.playing = true
        h.routeChanged(.newDeviceAvailable)
        h.routeChanged(.categoryChange)
        h.routeChanged(.override)
        #expect(h.pauses.isEmpty)
    }

    @Test func unpluggingDuringAnInterruptionCancelsTheResume() {
        let h = Harness()
        h.playing = true
        h.interruptionBegan()
        h.routeChanged(.oldDeviceUnavailable, from: [.bluetoothA2DP], to: [.builtInSpeaker])
        h.interruptionEnded(shouldResume: true)
        #expect(h.resumes == 0)
    }

    @Test func mediaServicesResetReconfiguresAndAsksForARebuild() throws {
        let h = Harness()
        try h.controller.configure()
        h.post(AVAudioSession.mediaServicesWereResetNotification)
        #expect(h.session.categories.count == 2)
        #expect(h.resets == 1)
    }

    @Test func notificationsFromAnotherSessionAreIgnored() {
        let h = Harness()
        h.playing = true
        h.center.post(
            name: AVAudioSession.interruptionNotification,
            object: FakeSession(),
            userInfo: [AVAudioSessionInterruptionTypeKey: AVAudioSession.InterruptionType.began.rawValue]
        )
        #expect(h.pauses.isEmpty)
    }

    // MARK: - Which route changes pause (#715)

    @Test func bluetoothDisconnectingWhilePlayingPauses() {
        let h = Harness()
        h.playing = true
        h.routeChanged(.oldDeviceUnavailable, from: [.bluetoothA2DP], to: [.builtInSpeaker])
        #expect(h.pauses == [.outputDeviceUnavailable])
    }

    /// A Bluetooth device moving between profiles or codecs (a volume key on some headsets) still plays
    /// to the listener: no pause.
    @Test func aBluetoothProfileSwitchDoesNotPause() {
        let h = Harness()
        h.playing = true
        h.routeChanged(.oldDeviceUnavailable, from: [.bluetoothA2DP], to: [.bluetoothHFP])
        #expect(h.pauses.isEmpty)
    }

    /// Nothing personal went away (no previous route, or only the speaker): nothing to stop playing out loud.
    @Test func anOldDeviceThatWasntPersonalDoesNotPause() {
        let h = Harness()
        h.playing = true
        h.routeChanged(.oldDeviceUnavailable)
        h.routeChanged(.oldDeviceUnavailable, from: [.builtInSpeaker], to: [.builtInReceiver])
        #expect(h.pauses.isEmpty)
    }

    @Test(arguments: [
        AVAudioSession.Port.headphones, .bluetoothA2DP, .bluetoothLE, .bluetoothHFP, .usbAudio, .lineOut, .carAudio, .airPlay,
    ])
    func losingAPersonalOutputToTheSpeakerPauses(port: AVAudioSession.Port) {
        #expect(AudioSessionController.pausesOnRouteChange(
            reason: .oldDeviceUnavailable, previousOutputs: [port], currentOutputs: [.builtInSpeaker]
        ))
    }

    @Test func theRouteChangeDecision() {
        let pauses = AudioSessionController.pausesOnRouteChange
        // Wired headphones pulled while Bluetooth is connected: the headphones went away.
        #expect(pauses(.oldDeviceUnavailable, [.headphones], [.bluetoothA2DP]))
        #expect(!pauses(.oldDeviceUnavailable, [.bluetoothLE], [.bluetoothA2DP]))
        #expect(!pauses(.oldDeviceUnavailable, [], []))
        #expect(!pauses(.oldDeviceUnavailable, [.builtInSpeaker], [.builtInSpeaker]))
        for reason: AVAudioSession.RouteChangeReason in [.newDeviceAvailable, .categoryChange, .override, .routeConfigurationChange] {
            #expect(!pauses(reason, [.bluetoothA2DP], [.builtInSpeaker]))
        }
        #expect(!pauses(nil, [.headphones], [.builtInSpeaker]))
    }
}
