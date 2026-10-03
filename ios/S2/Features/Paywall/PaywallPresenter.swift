import Shared
import SwiftUI
import UIKit

/// Opens the paywall whenever a gated action asks for it (Kotlin's `ServerAccessGate.paywallRequests`): adding a
/// server once the trial has ended, or streaming a server song before the trial or after it. It presents over
/// whatever is on screen (the setup sheet, Now Playing), from the topmost view controller, as Android's shell opens
/// its paywall over any screen.
@MainActor
final class PaywallPresenter {
    private let requests: ObservePaywallRequests
    private let store: StoreKitManager
    private var task: Task<Void, Never>?

    init(requests: ObservePaywallRequests, store: StoreKitManager) {
        self.requests = requests
        self.store = store
    }

    func start() {
        guard task == nil else { return }
        task = Task { [weak self] in
            guard let flow = self?.requests.invoke() else { return }
            for await source in flow {
                self?.present(source)
            }
        }
    }

    private func present(_ source: PaywallSource) {
        guard let top = Self.topViewController(), !(top is PaywallHostingController) else { return }
        let controller = PaywallHostingController(store: store, source: source)
        top.present(controller, animated: true)
    }

    private static func topViewController() -> UIViewController? {
        let window = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .filter { $0.activationState == .foregroundActive }
            .flatMap(\.windows)
            .first(where: \.isKeyWindow)
        var top = window?.rootViewController
        while let presented = top?.presentedViewController, !presented.isBeingDismissed {
            top = presented
        }
        return top
    }
}

/// The paywall in its own navigation stack, with a Close button.
private final class PaywallHostingController: UIHostingController<AnyView> {
    init(store: StoreKitManager, source: PaywallSource) {
        weak var weakSelf: PaywallHostingController?
        super.init(
            rootView: AnyView(
                NavigationStack {
                    PaywallView(store: store, source: source, onClose: { weakSelf?.dismiss(animated: true) })
                }
                .tint(.s2Accent)
            )
        )
        weakSelf = self
    }

    @available(*, unavailable)
    required init?(coder aDecoder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
}
