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

    // MARK: Content

    @Test func beforeTheTrialItDisclosesItsLengthWhatStopsAndThePrice() throws {
        let sut = PaywallContent(status: .trialAvailable, lifetimePrice: "$9.99")
        let disclosure = try sut.inspect().find(viewWithAccessibilityIdentifier: "paywall.disclosure").text().string()

        #expect(disclosure.contains("14 days"))
        #expect(disclosure.contains("streaming and downloading from Jellyfin, Emby and Plex"))
        #expect(disclosure.contains("$9.99"))
        #expect((try? sut.inspect().find(text: "Start 14-day free trial")) != nil)
        #expect((try? sut.inspect().find(text: "$9.99 once")) != nil)
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
