import CarPlay
import Foundation
import Observation

/// The buttons under CarPlay's Now Playing transport, as values: what they show follows the player's shuffle and
/// repeat modes. Play, pause, skip and seek are the template's own, driven by `NowPlayingController`'s remote
/// commands, so they aren't here.
enum CarPlayNowPlayingButton: Equatable {
    case shuffle(on: Bool)
    case repeatMode(NowPlayingRepeat)

    /// The template shows at most five.
    static let limit = 5

    static func set(shuffleOn: Bool, repeatMode: NowPlayingRepeat) -> [CarPlayNowPlayingButton] {
        [.shuffle(on: shuffleOn), .repeatMode(repeatMode)]
    }

    var systemImage: String {
        switch self {
        case .shuffle(let on): on ? "shuffle.circle.fill" : "shuffle"
        case .repeatMode(.off): "repeat"
        case .repeatMode(.all): "repeat.circle.fill"
        case .repeatMode(.one): "repeat.1.circle.fill"
        }
    }
}

/// CarPlay's shared `CPNowPlayingTemplate` (#692, from Shuttle Podcasts): its shuffle and repeat buttons, and Up
/// Next, which opens the queue. Follows `PlayerBinding`, the phone's Now Playing state.
@MainActor
final class CarPlayNowPlayingController: NSObject, CPNowPlayingTemplateObserver {
    private let binding: PlayerBinding
    private var showQueue: (() -> Void)?
    private var attached = false
    private var shownButtons: [CarPlayNowPlayingButton] = []
    private var hasQueue = false

    init(binding: PlayerBinding) {
        self.binding = binding
    }

    func attach(showQueue: @escaping () -> Void) {
        self.showQueue = showQueue
        attached = true
        let template = CPNowPlayingTemplate.shared
        template.add(self)
        template.upNextTitle = CarPlayText.upNext
        template.isAlbumArtistButtonEnabled = false
        update()
    }

    func detach() {
        attached = false
        showQueue = nil
        shownButtons = []
        let template = CPNowPlayingTemplate.shared
        template.remove(self)
        template.updateNowPlayingButtons([])
    }

    /// Redraws the buttons from the player's state, and again whenever it changes, until detached.
    private func update() {
        guard attached else { return }
        let state = withObservationTracking {
            binding.nowPlaying
        } onChange: { [weak self] in
            Task { @MainActor in self?.update() }
        }
        let template = CPNowPlayingTemplate.shared
        let queued = !state.queue.isEmpty
        if queued != hasQueue {
            hasQueue = queued
            template.isUpNextButtonEnabled = queued
        }
        let buttons = CarPlayNowPlayingButton.set(shuffleOn: state.shuffleOn, repeatMode: state.repeatMode)
        guard buttons != shownButtons else { return }
        shownButtons = buttons
        template.updateNowPlayingButtons(buttons.prefix(CarPlayNowPlayingButton.limit).map(makeButton))
    }

    private func makeButton(_ button: CarPlayNowPlayingButton) -> CPNowPlayingButton {
        let image = UIImage(systemName: button.systemImage) ?? UIImage()
        return CPNowPlayingImageButton(image: image) { [weak self] _ in
            guard let self else { return }
            switch button {
            case .shuffle: binding.actions.toggleShuffle()
            case .repeatMode: binding.actions.toggleRepeat()
            }
        }
    }

    nonisolated func nowPlayingTemplateUpNextButtonTapped(_ nowPlayingTemplate: CPNowPlayingTemplate) {
        MainActor.assumeIsolated { showQueue?() }
    }

    nonisolated func nowPlayingTemplateAlbumArtistButtonTapped(_ nowPlayingTemplate: CPNowPlayingTemplate) {}
}
