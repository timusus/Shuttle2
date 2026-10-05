import Foundation
import Shared
import SwiftUI
import Testing
import ViewInspector
@testable import S2

/// The Shuttle Music Pro paywall (#609): the Kotlin entitlement mapped to the user's standing, the trial disclosure
/// shown before the trial starts (guideline 3.1.1), and the buttons each standing offers.
@MainActor
struct PaywallViewTests {
    // MARK: Status

    @Test func mapsEachEntitlementToTheUsersStanding() {
        #expect(ProStatus(EntitlementUnknown.shared) == .checking)
        #expect(ProStatus(EntitlementFree(trialUsed: false)) == .trialAvailable)
        #expect(ProStatus(EntitlementFree(trialUsed: true)) == .trialEnded)
        #expect(ProStatus(EntitlementPro(source: .lifetime)) == .pro)
    }

    @Test func aRunningTrialCountsItsDaysLeft() {
        #expect(ProStatus.trial(daysLeft: 3).message == "3 days left in your free trial")
        #expect(ProStatus.trial(daysLeft: 1).message == "1 day left in your free trial")
    }

    // MARK: Funnel

    /// #776: a showing is `paywall_shown` from its source, then `paywall_dismissed` unless the user got Pro or a trial.
    @Test func aVisitIsShownThenDismissedUnlessItConverted() {
        let recording = RecordingAnalytics()
        let visit = PaywallVisit(source: .settings, analytics: MonetisationAnalytics(analytics: recording))

        visit.appeared()
        visit.appeared()
        visit.disappeared()
        visit.appeared()
        visit.converted()
        visit.disappeared()

        #expect(recording.events.map(\.0) == ["paywall_shown", "paywall_dismissed", "paywall_shown"])
        #expect(recording.events.allSatisfy { $0.1["source"] as? String == "settings" })
    }

    @Test func onlyARestoredPurchaseOrTrialCountsAsConverted() {
        #expect(StoreKitManager.RestoreOutcome.pro.restoredPro)
        #expect(StoreKitManager.RestoreOutcome.trial(daysLeft: 3).restoredPro)
        #expect(!StoreKitManager.RestoreOutcome.trialEnded.restoredPro)
        #expect(!StoreKitManager.RestoreOutcome.nothingToRestore.restoredPro)
        #expect(!StoreKitManager.RestoreOutcome.failed("x").restoredPro)
    }

    // MARK: Content

    @Test func beforeTheTrialItDisclosesItsLengthWhatStopsAndThePrice() throws {
        let sut = PaywallContent(status: .trialAvailable, lifetimePrice: "$9.99")
        let disclosure = try sut.inspect().find(viewWithAccessibilityIdentifier: "paywall.disclosure").text().string()

        #expect(disclosure.contains("7 days"))
        #expect(disclosure.contains("streaming from Jellyfin, Emby, Plex and Navidrome stops"))
        #expect(disclosure.contains("$9.99"))
        #expect((try? sut.inspect().find(text: "Start 7-day free trial")) != nil)
        #expect((try? sut.inspect().find(text: "$9.99 once")) != nil)
    }

    @Test func namesOnlyWhatIOSHas() throws {
        let sut = PaywallContent(status: .trialAvailable, lifetimePrice: "$9.99")
        let disclosure = try sut.inspect().find(viewWithAccessibilityIdentifier: "paywall.disclosure").text().string()
        let copy = [disclosure, ProFeatures.headline, ProFeatures.signInDisclosure, ProStatus.trialEnded.message]

        #expect((try? sut.inspect().find(text: "Stream from Jellyfin, Emby, Plex and Navidrome")) != nil)
        for line in copy {
            #expect(line.contains("Plex"))
            #expect(!line.localizedCaseInsensitiveContains("download"))
        }
    }

    // MARK: Restore

    @Test func restoreSaysWhatItFound() {
        #expect(StoreKitManager.RestoreOutcome.pro.message == "Shuttle Music Pro has been restored.")
        #expect(
            StoreKitManager.RestoreOutcome.trial(daysLeft: 3).message
                == "Your free trial has been restored. 3 days left in your free trial."
        )
        #expect(StoreKitManager.RestoreOutcome.trialEnded.message.contains("already used its free trial"))
        #expect(StoreKitManager.RestoreOutcome.nothingToRestore.message.contains("No Shuttle Music Pro purchase or free trial"))
    }

    @Test func startingTheTrialAndBuyingCallTheirActions() throws {
        var started = false
        var bought = false
        let sut = PaywallContent(status: .trialAvailable, lifetimePrice: "$9.99", onStartTrial: { started = true }, onBuy: { bought = true })

        try sut.inspect().find(viewWithAccessibilityIdentifier: "paywall.startTrial").button().tap()
        try sut.inspect().find(viewWithAccessibilityIdentifier: "paywall.buy").button().tap()

        #expect(started)
        #expect(bought)
    }

    @Test func afterTheTrialOnlyProIsOffered() {
        let sut = PaywallContent(status: .trialEnded, lifetimePrice: "$9.99")

        #expect((try? sut.inspect().find(viewWithAccessibilityIdentifier: "paywall.startTrial")) == nil)
        #expect((try? sut.inspect().find(viewWithAccessibilityIdentifier: "paywall.disclosure")) == nil)
        #expect((try? sut.inspect().find(text: "Get Shuttle Music Pro")) != nil)
    }

    @Test func proOffersNothingToBuyButKeepsRestoreAndTheLegalLinks() {
        let sut = PaywallContent(status: .pro, lifetimePrice: "$9.99")

        #expect((try? sut.inspect().find(viewWithAccessibilityIdentifier: "paywall.buy")) == nil)
        #expect((try? sut.inspect().find(viewWithAccessibilityIdentifier: "paywall.restore")) != nil)
        #expect((try? sut.inspect().find(viewWithAccessibilityIdentifier: "paywall.privacy")) != nil)
        #expect((try? sut.inspect().find(viewWithAccessibilityIdentifier: "paywall.terms")) != nil)
    }

    @Test func buyingWaitsForThePrice() throws {
        let sut = PaywallContent(status: .trialEnded, lifetimePrice: nil)

        #expect(try sut.inspect().find(viewWithAccessibilityIdentifier: "paywall.buy").button().isDisabled())
        #expect((try? sut.inspect().find(text: "Loading price…")) != nil)
    }
}

/// The events a `MonetisationAnalytics` captured, in order.
private final class RecordingAnalytics: NSObject, Analytics {
    private(set) var events: [(String, [String: Any])] = []

    var isCapturing: Bool { true }

    func capture(event: String, properties: [String: Any]) {
        events.append((event, properties))
    }

    func register(name: String, value: Any) {}
}
