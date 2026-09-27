import Shared
import Testing
@testable import S2

/// The POC's server login from the launch environment.
struct DebugServerConfigTests {
    private let full = [
        "S2_SERVER_TYPE": "Jellyfin",
        "S2_SERVER_URL": " https://music.example.com/ ",
        "S2_SERVER_USER": "tim",
        "S2_SERVER_PASSWORD": "secret",
    ]

    @Test func readsAFullConfig() throws {
        let config = try #require(DebugServerConfig(environment: full))
        #expect(config.type == .jellyfin)
        #expect(config.address == "https://music.example.com")
        #expect(config.username == "tim")
        #expect(config.password == "secret")
    }

    @Test func embyIsAccepted() {
        var env = full
        env["S2_SERVER_TYPE"] = "emby"
        #expect(DebugServerConfig(environment: env)?.type == .emby)
    }

    @Test func anEmptyPasswordIsAllowed() {
        var env = full
        env["S2_SERVER_PASSWORD"] = ""
        #expect(DebugServerConfig(environment: env)?.password == "")
    }

    @Test func nilWhenAnythingIsMissingOrTheTypeIsUnknown() {
        for key in full.keys where key != "S2_SERVER_PASSWORD" {
            var env = full
            env[key] = nil
            #expect(DebugServerConfig(environment: env) == nil, "\(key)")
        }
        var env = full
        env["S2_SERVER_PASSWORD"] = nil
        #expect(DebugServerConfig(environment: env) == nil)
        env = full
        env["S2_SERVER_TYPE"] = "plex"
        #expect(DebugServerConfig(environment: env) == nil)
        #expect(DebugServerConfig(environment: [:]) == nil)
    }
}
