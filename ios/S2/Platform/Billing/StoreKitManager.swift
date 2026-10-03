import Foundation
import Shared
import StoreKit

/// StoreKit 2 for Shuttle Music Pro (#609), after Shuttle Podcasts' `StoreKitManager`. Two non-consumables, their ids
/// in :shared's `AppStoreProducts`: the free 14-day trial and Pro for life. What owning them grants is resolved in
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

    enum RestoreOutcome: Equatable {
        case restored
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

    /// Listens for transactions, then loads the products and reads the current entitlements. Call once at launch.
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
        Task {
            await loadProducts()
            await refreshEntitlements()
        }
    }

    func loadProducts() async {
        guard let loaded = try? await Product.products(for: AppStoreProducts.shared.all) else { return }
        products = Dictionary(loaded.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
    }

    /// Reports the user's verified, unrevoked transactions to Kotlin, which resolves the entitlement from them.
    func refreshEntitlements() async {
        var purchases: [StorePurchase] = []
        for await result in Transaction.currentEntitlements {
            guard case .verified(let transaction) = result, transaction.revocationDate == nil else { continue }
            purchases.append(
                StorePurchase(
                    productId: transaction.productID,
                    purchasedAtEpochMs: Int64(transaction.purchaseDate.timeIntervalSince1970 * 1000)
                )
            )
        }
        entitlements.storeAnswered(purchases: purchases)
    }

    /// Buys `productId`: the free trial starts the 14 days, Lifetime is Pro.
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
        await refreshEntitlements()
        var owned = false
        for await result in Transaction.currentEntitlements {
            if case .verified(let transaction) = result, transaction.revocationDate == nil { owned = true }
        }
        return owned ? .restored : .nothingToRestore
    }
}
