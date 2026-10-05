import Foundation
import S2Playback
import Shared

/// The Kotlin `ServerRequestPolicy` (a server's custom headers and pinned certificate, #894) as the `ServerConnectionPolicy`
/// the streaming and artwork sessions ask. The one rule, shared with the Ktor client and the downloads (#921).
final class KotlinServerConnectionPolicy: ServerConnectionPolicy, @unchecked Sendable {
    private let policy: ServerRequestPolicy

    init(_ policy: ServerRequestPolicy) {
        self.policy = policy
    }

    func headers(for url: URL) -> [String: String] {
        policy.headers(url: url.absoluteString)
    }

    func redirected(_ request: URLRequest, from origin: URL?) -> URLRequest {
        policy.redirected(request: request, origin: origin) as URLRequest
    }

    func handleChallenge(
        _ challenge: URLAuthenticationChallenge,
        completion: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void
    ) {
        policy.handleChallenge(challenge: challenge) { disposition, credential in
            completion(URLSession.AuthChallengeDisposition(rawValue: Int(truncating: disposition)) ?? .performDefaultHandling, credential)
        }
    }
}
