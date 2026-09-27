import Foundation
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

    private let apiKey = [
        "S2_SERVER_TYPE": "emby",
        "S2_SERVER_URL": "http://192.168.1.2:8096",
        "S2_SERVER_API_KEY": " abc123 ",
    ]

    @Test func readsAFullConfig() throws {
        let config = try #require(DebugServerConfig(environment: full))
        #expect(config.type == .jellyfin)
        #expect(config.address == "https://music.example.com")
        #expect(config.credentials == .password(username: "tim", password: "secret"))
    }

    @Test func embyIsAccepted() {
        var env = full
        env["S2_SERVER_TYPE"] = "emby"
        #expect(DebugServerConfig(environment: env)?.type == .emby)
    }

    @Test func anEmptyPasswordIsAllowed() {
        var env = full
        env["S2_SERVER_PASSWORD"] = ""
        #expect(DebugServerConfig(environment: env)?.credentials == .password(username: "tim", password: ""))
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

    @Test func anApiKeyDefaultsToTheTestUser() throws {
        let config = try #require(DebugServerConfig(environment: apiKey))
        #expect(config.type == .emby)
        #expect(config.address == "http://192.168.1.2:8096")
        #expect(config.credentials == .apiKey(username: "shuttle-test", key: "abc123"))
    }

    @Test func anApiKeyTakesTheUserName() {
        var env = apiKey
        env["S2_SERVER_USER"] = "tim"
        #expect(DebugServerConfig(environment: env)?.credentials == .apiKey(username: "tim", key: "abc123"))
    }

    @Test func anApiKeyWinsOverAPassword() {
        var env = full
        env["S2_SERVER_API_KEY"] = "abc123"
        #expect(DebugServerConfig(environment: env)?.credentials == .apiKey(username: "tim", key: "abc123"))
    }

    @Test func anApiKeyStillNeedsTheTypeAndAddress() {
        for key in ["S2_SERVER_TYPE", "S2_SERVER_URL"] {
            var env = apiKey
            env[key] = nil
            #expect(DebugServerConfig(environment: env) == nil, "\(key)")
        }
        var env = apiKey
        env["S2_SERVER_API_KEY"] = "  "
        #expect(DebugServerConfig(environment: env) == nil)
    }

    @Test func jellyfinUsesTheMediaBrowserHeaderAndEmbyTheTokenHeader() throws {
        let jellyfin = try #require(DebugServerUsers.request(type: .jellyfin, address: "http://h:8096", apiKey: "k"))
        #expect(jellyfin.url?.absoluteString == "http://h:8096/Users")
        #expect(jellyfin.value(forHTTPHeaderField: "Authorization")?.hasPrefix("MediaBrowser ") == true)
        #expect(jellyfin.value(forHTTPHeaderField: "Authorization")?.contains("Token=\"k\"") == true)
        #expect(jellyfin.value(forHTTPHeaderField: "X-Emby-Token") == nil)
        let emby = try #require(DebugServerUsers.request(type: .emby, address: "https://h", apiKey: "k"))
        #expect(emby.value(forHTTPHeaderField: "X-Emby-Token") == "k")
        #expect(emby.value(forHTTPHeaderField: "Authorization") == nil)
    }

    @Test func findsTheUserIdByName() {
        let json = Data(#"[{"Name":"admin","Id":"a1"},{"Name":"shuttle-test","Id":"s2"}]"#.utf8)
        #expect(DebugServerUsers.id(in: json, of: "shuttle-test") == "s2")
        #expect(DebugServerUsers.id(in: json, of: "nobody") == nil)
        #expect(DebugServerUsers.id(in: Data("{}".utf8), of: "shuttle-test") == nil)
    }
}
