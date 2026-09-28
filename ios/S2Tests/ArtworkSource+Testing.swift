import Foundation
@testable import S2

extension ArtworkSource {
    /// A source with at most one url, for tests that need a row "with artwork" without the graph.
    init(id: AnyHashable, load: @escaping () async throws -> String?) {
        self.init(id: id) {
            guard let string = try await load(), let url = URL(string: string) else { return [] }
            return [ArtworkCandidate(url: url)]
        }
    }
}
