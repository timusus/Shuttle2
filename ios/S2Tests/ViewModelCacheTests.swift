import Testing
@testable import S2

/// The cache hands back the same view model per key and clears each one exactly once, when its screen
/// leaves or it falls out of the LRU window.
@MainActor
struct ViewModelCacheTests {
    private final class FakeViewModel: ClearableViewModel {
        let id: String
        private(set) var clearCount = 0
        init(_ id: String) { self.id = id }
        func clear() { clearCount += 1 }
    }

    @Test func sameKeyReturnsTheSameInstance() {
        let cache = ViewModelCache()
        var created = 0
        let first = cache.viewModel("album:1") { created += 1; return FakeViewModel("1") }
        let second = cache.viewModel("album:1") { created += 1; return FakeViewModel("again") }
        #expect(first === second)
        #expect(created == 1)
    }

    @Test func overflowClearsTheLeastRecentlyUsed() {
        let cache = ViewModelCache(maxSize: 2)
        let a = cache.viewModel("a") { FakeViewModel("a") }
        let b = cache.viewModel("b") { FakeViewModel("b") }
        _ = cache.viewModel("a") { FakeViewModel("a2") } // a is now the most recent
        let c = cache.viewModel("c") { FakeViewModel("c") }

        #expect(b.clearCount == 1)
        #expect(a.clearCount == 0)
        #expect(c.clearCount == 0)
        #expect(cache.keys == ["a", "c"])
    }

    @Test func removeClearsOnceAndForgets() {
        let cache = ViewModelCache()
        let vm = cache.viewModel("x") { FakeViewModel("x") }
        cache.remove("x")
        cache.remove("x")
        #expect(vm.clearCount == 1)
        let fresh = cache.viewModel("x") { FakeViewModel("x2") }
        #expect(fresh !== vm)
    }

    @Test func retainOnlyClearsScreensNoLongerOnAPath() {
        let cache = ViewModelCache()
        let kept = cache.viewModel("artist:1") { FakeViewModel("kept") }
        let popped = cache.viewModel("album:2") { FakeViewModel("popped") }
        cache.retainOnly(["artist:1"])
        #expect(kept.clearCount == 0)
        #expect(popped.clearCount == 1)
        #expect(cache.keys == ["artist:1"])
    }

    @Test func removeAllClearsEverything() {
        let cache = ViewModelCache()
        let a = cache.viewModel("a") { FakeViewModel("a") }
        let b = cache.viewModel("b") { FakeViewModel("b") }
        cache.removeAll()
        #expect(a.clearCount == 1)
        #expect(b.clearCount == 1)
        #expect(cache.keys.isEmpty)
    }

    @Test func customEvictionHookSeesEveryEvictedEntry() {
        var evicted: [String] = []
        let cache = ViewModelCache(maxSize: 1) { evicted.append(($0 as! FakeViewModel).id) }
        _ = cache.viewModel("a") { FakeViewModel("a") }
        _ = cache.viewModel("b") { FakeViewModel("b") }
        cache.remove("b")
        #expect(evicted == ["a", "b"])
    }
}
