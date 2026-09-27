import SwiftUI
import Testing
@testable import S2

struct LayoutTierTests {
    @Test func compactSizeClassIsCompactAtAnyWidth() {
        #expect(LayoutTier.resolve(horizontalSizeClass: .compact, containerWidth: 1400) == .compact)
    }

    @Test func noSizeClassYetIsCompact() {
        #expect(LayoutTier.resolve(horizontalSizeClass: nil, containerWidth: 800) == .compact)
    }

    @Test func regularWidthSplitsAtTheWideThreshold() {
        #expect(LayoutTier.resolve(horizontalSizeClass: .regular, containerWidth: 0) == .regular)
        #expect(LayoutTier.resolve(horizontalSizeClass: .regular, containerWidth: 999) == .regular)
        #expect(LayoutTier.resolve(horizontalSizeClass: .regular, containerWidth: 1000) == .wide)
    }

    @Test func onlyCompactGetsTheTabBar() {
        #expect(ShellContainer.resolve(for: .compact) == .tabBar)
        let sidebar: ShellContainer
        if #available(iOS 18, *) {
            sidebar = .sidebarTabs
        } else {
            sidebar = .splitView
        }
        #expect(ShellContainer.resolve(for: .regular) == sidebar)
        #expect(ShellContainer.resolve(for: .wide) == sidebar)
    }
}
