#if DEBUG
    import Foundation
    import Observation
    import os
    import Shared

    /// A Jellyfin or Emby login from the launch environment, for the phase 5 proof of concept until Sources and
    /// `ServerSignInView` land in phase 7 (`ios-port/phase-5-ios-app.md` section 3, then deleted with it). DEBUG builds
    /// only. Set `S2_SERVER_TYPE` (`jellyfin` or `emby`), `S2_SERVER_URL`, `S2_SERVER_USER` and `S2_SERVER_PASSWORD`:
    /// `SIMCTL_CHILD_`-prefixed for `xcrun simctl launch`, or exported before `ios/scripts/install-device.sh`, which
    /// hands them to `devicectl` (`.claude/rules/ios.md`, "Running the POC").
    struct DebugServerConfig: Equatable {
        let type: MediaProviderType
        let address: String
        let username: String
        let password: String

        /// Nil unless every variable is set and the type is one iOS can sign in to.
        init?(environment: [String: String]) {
            let value = { (name: String) -> String? in
                environment[name].map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }.flatMap { $0.isEmpty ? nil : $0 }
            }
            guard let type = value("S2_SERVER_TYPE"), let address = value("S2_SERVER_URL"),
                  let username = value("S2_SERVER_USER"), let password = environment["S2_SERVER_PASSWORD"]
            else { return nil }
            switch type.lowercased() {
            case "jellyfin": self.type = .jellyfin
            case "emby": self.type = .emby
            default: return nil
            }
            self.address = address.hasSuffix("/") ? String(address.dropLast()) : address
            self.username = username
            self.password = password
        }
    }

    /// What the last seed did, for the Library's empty state: on a phone there's no console to read.
    @MainActor
    @Observable
    final class DebugServerStatus {
        static let shared = DebugServerStatus()
        var message: String?
    }

    /// Signs in with a `DebugServerConfig` through :shared's `ServerSignIn` (the provider's authentication manager,
    /// then `ConnectServer`, which enables the provider and imports), or just imports when that server's session is
    /// already saved. Logs the server type and outcome, never the password or the session token.
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
                failure = try await signIn.signIn(
                    type: config.type, address: config.address, username: config.username, password: config.password
                )
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
#endif
