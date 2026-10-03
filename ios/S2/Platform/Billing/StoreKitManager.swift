import Foundation
import Shared
import StoreKit

/// StoreKit 2 for Shuttle Music Pro (#609), after Shuttle Podcasts' `StoreKitManager`. Two non-consumables, their ids
/// in :shared's `AppStoreProducts`: the free trial and Pro for life. What owning them grants is resolved in
/// Kotlin (`StoreEntitlements`); this only loads and buys the products and reports the user's current transactions to
/// it, at launch, after each purchase or restore, and whenever `Transaction.updates` delivers one (a purchase made on
/// another device, Ask to Buy approval, a refund).
@MainActor
final class StoreKitManager: ObservableObject {
    enum PurchaseOutcome: Equatable {
        case purchased
        case cancelled
        /// Waiting on Ask to Buy or Strong Customer Authentication; `Transaction.updates` delivers it later.
        case pending
        case failed(String)
    }

    /// What Restore Purchases found for this Apple ID.
    enum RestoreOutcome: Equatable {
        case pro
        case trial(daysLeft: Int)
        /// The free trial was had, and has ended (or was revoked).
        case trialEnded
        case nothingToRestore
        case failed(String)
    }

    /// The products the App Store returned, by id. Empty until they load, or if the App Store can't be reached.
    @Published private(set) var products: [String: Product] = [:]
    /// The product being bought, or nil.
    @Published private(set) var purchasing: String?
    @Published private(set) var isRestoring = false

    private let entitlements: StoreEntitlements
    private var updates: Task<Void, Never>?

    init(entitlements: StoreEntitlements) {
        self.entitlements = entitlements
    }

    deinit {
        updates?.cancel()
    }

    var trial: Product? { products[AppStoreProducts.shared.TRIAL] }
    var lifetime: Product? { products[AppStoreProducts.shared.LIFETIME] }

    /// Pro's localized price once the products load, nil before.
    var lifetimePrice: String? {
        #if DEBUG
        if let screenshotPrice { return screenshotPrice }
        #endif
        return lifetime?.displayPrice
    }

    /// Whether the free trial can be started: its product loaded.
    var trialOffered: Bool {
        #if DEBUG
        if screenshotPrice != nil { return true }
        #endif
        return trial != nil
    }

    #if DEBUG
    /// The store screenshots' stand-in for the products loading: StoreKit's test configuration only applies under
    /// Xcode, so a capture would otherwise show "Loading price…" (`ScreenshotHooks`' `paywall` action).
    @Published private(set) var screenshotPrice: String?

    func showScreenshotPrice(_ price: String?) {
        screenshotPrice = price
    }
    #endif

    /// Listens for transactions, reads the entitlements and loads the products. Call once at launch. The entitlements
    /// come from StoreKit's on-device cache, so they're read first and on their own: an App Store that can't be reached
    /// only holds up the products, never whether the user may stream.
    func start() {
        guard updates == nil else { return }
        updates = Task { [weak self] in
            for await result in Transaction.updates {
                guard let self else { return }
                if case .verified(let transaction) = result {
                    await transaction.finish()
                }
                await self.refreshEntitlements()
            }
        }
        Task { _ = await refreshEntitlements() }
        Task { await loadProducts() }
    }

    func loadProducts() async {
        guard let loaded = try? await Product.products(for: AppStoreProducts.shared.all) else { return }
        products = Dictionary(loaded.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
    }

    /// Reports the user's verified transaction for each product to Kotlin, which resolves the entitlement from them.
    /// `Transaction.latest(for:)` rather than `currentEntitlements`, which leaves out a refunded or revoked one: a
    /// revoked trial was still had. Each carries its original purchase date, which a restore or reinstall keeps.
    ///
    /// - Returns: what the transactions alone resolve to, debug overrides aside.
    @discardableResult
    func refreshEntitlements() async -> Entitlement {
        var purchases: [StorePurchase] = []
        for productId in AppStoreProducts.shared.all {
            guard case .verified(let transaction) = await Transaction.latest(for: productId) else { continue }
            purchases.append(
                StorePurchase(
                    productId: transaction.productID,
                    originalPurchasedAtEpochMs: Int64(transaction.originalPurchaseDate.timeIntervalSince1970 * 1000),
                    revoked: transaction.revocationDate != nil
                )
            )
        }
        return entitlements.storeAnswered(purchases: purchases)
    }

    /// Buys `productId`: the free trial starts the trial, Lifetime is Pro.
    func purchase(_ productId: String) async -> PurchaseOutcome {
        if products[productId] == nil { await loadProducts() }
        guard let product = products[productId] else {
            return .failed("The App Store isn't available right now. Try again later.")
        }
        purchasing = productId
        defer { purchasing = nil }
        do {
            switch try await product.purchase() {
            case .success(.verified(let transaction)):
                await transaction.finish()
                await refreshEntitlements()
                return .purchased
            case .success(.unverified):
                return .failed("The App Store couldn't verify the purchase.")
            case .userCancelled:
                return .cancelled
            case .pending:
                return .pending
            @unknown default:
                return .cancelled
            }
        } catch {
            return .failed(error.localizedDescription)
        }
    }

    /// Restore Purchases: `AppStore.sync()` asks the user to sign in if needed and fetches every transaction.
    func restore() async -> RestoreOutcome {
        isRestoring = true
        defer { isRestoring = false }
        do {
            try await AppStore.sync()
        } catch {
            return .failed(error.localizedDescription)
        }
        switch onEnum(of: await refreshEntitlements()) {
        case .pro: return .pro
        case .trial(let trial): return .trial(daysLeft: Int(trial.daysRemainingNow()))
        case .free(let free): return free.trialUsed ? .trialEnded : .nothingToRestore
        case .unknown: return .nothingToRestore
        }
    }
}
