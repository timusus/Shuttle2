import AVFoundation
import os

// MARK: - Output: starting, pausing and rebuilding the engine, the position ticker (engine queue)

extension MusicPlaybackController {
    func releaseHold() {
        timelineLock.withLock {
            timeline.heard = timeline.held ?? timeline.heard
            timeline.held = nil
        }
    }

    /// Stops the output while nothing plays. A running engine renders silence, and iOS takes an app
    /// whose output runs for one that is playing: the lock screen and Control Center would show it
    /// playing, with a pause button, after it paused or stopped (#691). `startEngineIfNeeded` starts
    /// it again before the node plays.
    /// Paused, it's prepared again, so the next play's start has nothing to allocate (#687).
    func pauseEngine() {
        guard case .realtime = renderingMode, engine.isRunning else { return }
        engine.pause()
        engine.prepare()
    }

    /// False (logged) if the owner refused to ready the output (``activateOutput``), or the engine is
    /// stopped and won't start: the audio session couldn't be activated, in a call or with another app
    /// holding the hardware. The node must not play then; on a stopped engine it raises.
    private func startEngineIfNeeded() -> Bool {
        if let activation = pendingActivation {
            pendingActivation = nil
            if startTiming?.nodePlayedAt == nil { startTiming?.sessionAwaitedAt = StartupTiming.now() }
            let activated = activation.wait()
            if startTiming?.nodePlayedAt == nil { startTiming?.sessionActivatedAt = activation.activatedAt }
            guard activated else {
                engineLog.error("output not activated; paused")
                return false
            }
        }
        guard !engine.isRunning else { return true }
        do {
            let started = StartupTiming.now()
            try startEngine(engine)
            if startTiming?.nodePlayedAt == nil { startTiming?.engineStartMs = Int(((StartupTiming.now() - started) * 1000).rounded()) }
            return true
        } catch {
            engineLog.error("engine start failed: \(String(describing: error), privacy: .public)")
            return false
        }
    }

    /// Starts the engine and plays the node from where the stream is; false, with nothing played, if
    /// the engine won't start. The node starts on `startFrames` and the rest is decoded as it plays.
    func startPlaying() -> Bool {
        guard startEngineIfNeeded() else { return false }
        fill(aheadFrames: Self.startFrames)
        playNode()
        releaseHold()
        setState(.playing)
        startTicker()
        fill()
        return true
    }

    /// A route change stopped the engine and it wouldn't start again while the route settled (#715):
    /// loading, the start is tried again up to `startRetryAttempts` times, `startRetryDelay` apart. A
    /// pause, play, load, seek or stop meanwhile drops it; the last failure stays paused, as a play
    /// the engine can't start does.
    func retryStart(attempt: Int = 1) {
        stopTicker()
        setState(.loading)
        let generation = self.generation
        engineQueue.asyncAfter(deadline: .now() + startRetryDelay) { [weak self] in
            guard let self, self.generation == generation, playWhenReady, state == .loading else { return }
            if startPlaying() {
                engineLog.notice("engine started on retry \(attempt)")
            } else if attempt < Self.startRetryAttempts {
                retryStart(attempt: attempt + 1)
            } else {
                engineLog.error("engine didn't start after \(attempt) retries; paused")
                stayPaused()
            }
        }
    }

    /// A play the engine couldn't start: paused, as if it had been asked to pause, so the owner and
    /// Now Playing show it paused. Nothing plays until the next play. A play refused while already
    /// paused changes no state, and is answered with paused all the same (``answerCommand()``).
    /// Not a decode failure: the track stays put.
    func stayPaused() {
        playWhenReady = false
        dropStartTiming()
        stopTicker()
        setState(.paused)
    }

    private func startTicker() {
        guard case .realtime = renderingMode, ticker == nil else { return }
        let timer = DispatchSource.makeTimerSource(queue: engineQueue)
        timer.schedule(deadline: .now() + .milliseconds(100), repeating: .milliseconds(100))
        timer.setEventHandler { [weak self] in
            self?.fill()
            self?.updateTimeline()
            self?.logBufferHealth()
        }
        timer.resume()
        ticker = timer
    }

    /// Seconds buffered ahead of the playhead while playing (#897): scheduled on the node, plus what the stream has
    /// fetched past the decoder. Logged every `healthLogSeconds`, and as it drops under `lowBufferSeconds`.
    private func logBufferHealth() {
        guard state == .playing, let current else { return }
        let scheduled = Double(max(0, outputIndex - playedStreamIndex())) / outputSampleRate
        let network = (current.source as? FFmpegTrackSource)?.networkBufferedSeconds
        let ahead = scheduled + (network ?? 0)
        let now = StartupTiming.now()
        let low = ahead < Self.lowBufferSeconds && network != nil && !drained
        let due = lastHealthLog.map { now - $0.at >= Self.healthLogSeconds } ?? true
        guard due || (low && lastHealthLog?.low == false) else { return }
        lastHealthLog = (now, low)
        let networkText = network.map { String(format: "%.1f", $0) } ?? "-"
        let line = "buffer: \(String(format: "%.1f", ahead)) s ahead (scheduled \(String(format: "%.1f", scheduled)) s, network \(networkText) s) uid \(current.track.uid)"
        if low { engineLog.warning("\(line, privacy: .public)") } else { engineLog.info("\(line, privacy: .public)") }
    }

    func stopTicker() {
        ticker?.cancel()
        ticker = nil
    }

    /// The route or device changed and the engine stopped: rebuild from where the listener was. The
    /// node's clock stopped with the engine, so that is the last tick's reading (#714). An engine that
    /// won't start while the route settles is tried again (#715). Changes posted before the queue gets
    /// to them are one rebuild, the last's: each would re-seek the track, and a transcode's owner would
    /// re-open the stream for every one (#813).
    @objc func engineConfigurationChanged(_ notification: Notification) {
        let change = configurationChangeLock.withLock {
            configurationChanges += 1
            return configurationChanges
        }
        engineQueue.async { [self] in
            guard change == configurationChangeLock.withLock({ configurationChanges }) else { return }
            guard current != nil else { return }
            let renderTime = player.lastRenderTime == nil ? "nil" : "valid"
            updateTimeline()
            let frame = currentMediaFrame()
            let snapshot = timelineLock.withLock { timeline }
            let held = snapshot.held.map(String.init) ?? "nil"
            engineLog.notice("""
                engine configuration changed: stream \(snapshot.playedStreamIndex(nodeTime: self.nodeSampleTime())), \
                heard \(snapshot.heard), held \(held, privacy: .public), render time \(renderTime, privacy: .public), \
                frame \(frame), playWhenReady \(self.playWhenReady), running \(self.engine.isRunning)
                """)
            restart(atFrame: frame, retryingStart: true)
        }
    }
}
