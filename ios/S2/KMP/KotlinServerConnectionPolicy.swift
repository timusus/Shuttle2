import Foundation
import S2Playback
import Shared

/// Adapts the Kotlin `ServerRequestPolicy` (custom headers, pinned certificate) for the streaming and artwork sessions.
final class KotlinServerConnectionPolicy: ServerConnectionPolicy, @unchecked Sendable {
    private let policy: ServerRequestPolicy

    init(_ policy: ServerRequestPolicy) {
        self.policy = policy
    }

    func headers(for url: URL) -> [String: String] {
        policy.headers(url: url.absoluteString)
    }

    func trustedCertificate(for url: URL) -> String? {
        policy.trustedCertificate(url: url.absoluteString)
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
