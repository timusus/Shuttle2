#if DEBUG
    import Foundation
    import Observation
    import os
    import Shared

    /// A Jellyfin or Emby login from the launch environment, for the phase 5 proof of concept until Sources and
    /// `ServerSignInView` land in phase 7 (`ios-port/phase-5-ios-app.md` section 3, then deleted with it). DEBUG builds
    /// only. Set `S2_SERVER_TYPE` (`jellyfin` or `emby`) and `S2_SERVER_URL`, then either `S2_SERVER_API_KEY` (the
    /// server's API key, used as the session token for `S2_SERVER_USER`, default `shuttle-test`, as Android's
    /// `DebugRemoteProviderReceiver` does) or `S2_SERVER_USER` and `S2_SERVER_PASSWORD`: `SIMCTL_CHILD_`-prefixed for
    /// `xcrun simctl launch` (`ios/scripts/run-sim-server.sh` does this from the test-server env files), or exported
    /// before `ios/scripts/install-device.sh`, which hands them to `devicectl` (`.claude/rules/ios.md`, "Running the POC").
    struct DebugServerConfig: Equatable {
        enum Credentials: Equatable {
            case password(username: String, password: String)
            /// The key is the access token; the user's Id is looked up by name.
            case apiKey(username: String, key: String)
        }

        static let defaultApiKeyUser = "shuttle-test"

        let type: MediaProviderType
        let address: String
        let credentials: Credentials

        /// Nil unless the type, address and one complete set of credentials are set and the type is one iOS can
        /// sign in to. An API key wins over a password.
        init?(environment: [String: String]) {
            let value = { (name: String) -> String? in
                environment[name].map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }.flatMap { $0.isEmpty ? nil : $0 }
            }
            guard let type = value("S2_SERVER_TYPE"), let address = value("S2_SERVER_URL") else { return nil }
            switch type.lowercased() {
            case "jellyfin": self.type = .jellyfin
            case "emby": self.type = .emby
            default: return nil
            }
            if let key = value("S2_SERVER_API_KEY") {
                credentials = .apiKey(username: value("S2_SERVER_USER") ?? Self.defaultApiKeyUser, key: key)
            } else if let username = value("S2_SERVER_USER"), let password = environment["S2_SERVER_PASSWORD"] {
                credentials = .password(username: username, password: password)
            } else {
                return nil
            }
            self.address = address.hasSuffix("/") ? String(address.dropLast()) : address
        }
    }

    /// What the last seed did, for the Library's empty state: on a phone there's no console to read.
    @MainActor
    @Observable
    final class DebugServerStatus {
        static let shared = DebugServerStatus()
        var message: String?
    }

    /// Signs in with a `DebugServerConfig` through :shared's `ServerSignIn` (the provider's authentication manager, or
    /// an API key saved as the session, then `ConnectServer`, which enables the provider and imports), or just imports
    /// when that server's session is already saved. Logs the server type and outcome, never the password, key or token.
    @MainActor
    enum DebugServerSeed {
        private static let logger = Logger(subsystem: "com.simplecityapps.shuttle", category: "DebugServerSeed")

        static func seed(_ config: DebugServerConfig, graph: IosAppGraph, status: DebugServerStatus = .shared) async {
            let signIn = graph.serverSignIn
            if signIn.isSignedIn(type: config.type, address: config.address) {
                logger.info("\(config.type.name, privacy: .public): session saved, importing")
                graph.mediaSources.enable(type: config.type)
                graph.mediaSources.scan()
                status.message = nil
                return
            }
            logger.info("\(config.type.name, privacy: .public): signing in")
            let failure: String?
            do {
                switch config.credentials {
                case let .password(username, password):
                    failure = try await signIn.signIn(
                        type: config.type, address: config.address, username: username, password: password
                    )
                case let .apiKey(username, key):
                    let userId = try await DebugServerUsers.id(of: username, type: config.type, address: config.address, apiKey: key)
                    failure = signIn.signInWithToken(type: config.type, address: config.address, userId: userId, accessToken: key)
                }
            } catch {
                failure = error.localizedDescription
            }
            if let failure {
                logger.error("\(config.type.name, privacy: .public) sign-in failed: \(failure, privacy: .public)")
                status.message = "\(config.type.name) sign-in failed: \(failure)"
                graph.mediaSources.scan()
            } else {
                logger.info("\(config.type.name, privacy: .public): signed in, importing")
                status.message = nil
            }
        }
    }

    /// Looks a user's Id up by name with a server API key, as `support/scripts/seed-remote-provider.sh` does for
    /// Android: `GET /Users`, authorised with the header each server accepts.
    enum DebugServerUsers {
        struct NotFound: LocalizedError {
            let username: String
            var errorDescription: String? { "no user named \(username) (or the API key was rejected)" }
        }

        private struct User: Decodable {
            let Name: String
            let Id: String
        }

        /// Newer Jellyfin servers only accept the MediaBrowser `Authorization` header; Emby takes `X-Emby-Token`.
        static func request(type: MediaProviderType, address: String, apiKey: String) -> URLRequest? {
            guard let url = URL(string: address + "/Users") else { return nil }
            var request = URLRequest(url: url, timeoutInterval: 20)
            if type == .jellyfin {
                request.setValue(
                    "MediaBrowser Client=\"S2\", Device=\"debug-seed\", DeviceId=\"s2-debug-seed\", Version=\"1.0\", Token=\"\(apiKey)\"",
                    forHTTPHeaderField: "Authorization"
                )
            } else {
                request.setValue(apiKey, forHTTPHeaderField: "X-Emby-Token")
            }
            return request
        }

        static func id(in json: Data, of username: String) -> String? {
            (try? JSONDecoder().decode([User].self, from: json))?.first { $0.Name == username }?.Id
        }

        static func id(of username: String, type: MediaProviderType, address: String, apiKey: String) async throws -> String {
            guard let request = request(type: type, address: address, apiKey: apiKey) else { throw URLError(.badURL) }
            let (data, response) = try await URLSession.shared.data(for: request)
            guard (response as? HTTPURLResponse)?.statusCode == 200, let id = id(in: data, of: username) else {
                throw NotFound(username: username)
            }
            return id
        }
    }
#endif
