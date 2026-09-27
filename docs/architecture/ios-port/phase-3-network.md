# Phase 3: Networking → Ktor + kotlinx.serialization

Issue #585 (epic #581, phase 3). Replaces Retrofit + OkHttp + Moshi in `android/networking`,
`android/mediaprovider/server`, and `android/mediaprovider/{jellyfin,emby,plex}` with Ktor 3.6 +
kotlinx.serialization in `commonMain`, so those modules compile for iOS. Catalog entries already
exist (`gradle/libs.versions.toml:34-35,154-161`): `ktor = "3.6.0"`, `kotlinx-serialization =
"1.11.0"`, plus `ktor-client-{core,okhttp,darwin,content-negotiation,mock}` and
`ktor-serialization-kotlinx-json`.

House style reference: `~/projects/simplecity-apps/podcasts/main/mobile/shared/src/commonMain/.../network/`
(`HttpClientFactory.kt` + `.android.kt`/`.ios.kt`, `auth/AccountAuthPlugin.kt`,
`auth/AccountTokenRefresher.kt`). This doc follows that shape throughout.

## 1. Inventory

### `android:networking` (10 files, all Retrofit/OkHttp glue — deleted, not ported)
- `retrofit/NetworkResult.kt` — `sealed class NetworkResult<S> { Success, Failure }` + `map`. **Concept
  survives** as a plain multiplatform type.
- `retrofit/{NetworkResultCall,NetworkResultCallAdapter,NetworkResultAdapterFactory}.kt` — Retrofit
  `CallAdapter.Factory` machinery, Android-only (`android.net.ConnectivityManager` in the adapter/
  factory constructors, used only for `NetworkError.hasInternetConnectivity`). No Ktor equivalent
  needed — Ktor calls are plain suspend functions; the mapping to `NetworkResult` becomes a wrapper
  function (§3).
- `retrofit/error/{NetworkError,RemoteServiceError,RemoteServiceHttpError,UnexpectedError,UserFriendlyError}.kt`
  — `RemoteServiceHttpError` wraps a `retrofit2.Response<*>`, deriving `HttpStatusCode` (every 4xx/5xx
  code, `Unknown` fallback) and `isClientError`/`isServerError`. `NetworkError` wraps connectivity
  state + the underlying `Throwable`. Pure Kotlin apart from the `Response<*>` coupling.
- `ErrorHelper.kt` — `Throwable.userDescription()` / `Error.userDescription()`, pure Kotlin, ports as is.

No `@JsonClass`, no Moshi, no interceptors in this module — it's purely the Retrofit result-wrapping
layer. The Moshi provider is one level up, in `android/core/src/main/java/.../di/NetworkingModule.kt`
(`provideMoshi`: `KotlinJsonAdapterFactory` + `Rfc3339DateJsonAdapter` for `java.util.Date` — no other
module registers a custom adapter) and `provideOkHttpClient` (a proxy-aware builder + an
`HttpLoggingInterceptor` gated `Level.NONE` in release, logged through Timber at tag `"OkHttp"`).
Neither DTO nor DI file is in this worktree's edit scope (DI is being rewritten in a parallel
worktree), but their shape drives the shared `HttpClientFactory` in §3.

### `android:mediaprovider:server` (13 files: 6 main, 3 test, 2 testFixtures, ServerSession + QuickConnectAuthentication)
Endpoint count: **0** — this module has no Retrofit services; it's the shared session/credential
skeleton all three providers use.
- `ServerSession.kt` — `withServerSession()`: a `Flow` builder taking `context: Context` **only** to
  call `context.getString(R.string.media_provider_*)` for two user-facing error strings. This is the
  one Android-only leak in the module. **Not part of this brief's scope to redesign i18n**, but it
  blocks a straight KMP move: flag as a phase-3 sub-task (§5, step 2) — replace with a small
  `expect`/injected string-provider interface (mirrors how Podcasts keeps user strings out of
  `commonMain`), not a code fork.
- `ServerCredentialStore.kt` — wraps `SecurePreferenceManager` (already an interface; check it is/will
  be KMP — likely already handled in phase 2's shared-prefs work). Holds username/password/access
  token/user id/`canDownload`, all `String`/`Boolean`. No Android types. Ports as is.
  Note (#577): this store's `authenticatedCredentials` setter is the natural place to force-clear on a
  401 (`= null`), and `loginCredentials` already never persists a Plex 2FA `authCode` — the AuthPlugin
  in §3 should call into a shared "clear + signal re-sign-in" hook here rather than each provider
  re-implementing it.
- `Credentials.kt` — `LoginCredentials`/`AuthenticatedCredentials`, plain data classes. Ports as is.
- `PagedFlow.kt` — `pagedFlow()` fetches pages via a `suspend (offset, limit) -> NetworkResult<Page<T>>`
  lambda; pure Kotlin except the `NetworkResult` type it consumes (§3 keeps that type, so this needs
  no change) and a `Timber.e` call (multiplatform logger — check phase 2's "shared logger" decision).
- `DirectPlayFormats.kt` — pure Kotlin, no networking dependency at all. Ports untouched.
- `QuickConnectAuthentication.kt` — a plain interface (`QuickConnectCode`, `QuickConnectPollState`
  enum, 4 suspend methods), no networking types. Ports untouched.
- Custom Moshi adapters: **none.**
- `testFixtures/FixtureServer.kt` wraps `mockwebserver3.MockWebServer` with a route/fixture dispatcher
  used by all three providers' tests (`respond(path, fixture, code, method, query)`,
  `requestsTo(path)`). This is the one shared test seam that must be re-created on Ktor's `MockEngine`
  (§3) — same call shape, so provider tests barely change.

### `android:mediaprovider:jellyfin` (27 files)
- Retrofit endpoints: **27** `@GET/@POST/@Headers` annotations across 4 services — `UserService` (7:
  authenticate, me, quickConnect enabled/initiate x2/connect, authenticateWithQuickConnect),
  `ItemsService`, `JellyfinTranscodeService`, `PlaybackReportingService`. All auth calls build the
  `Authorization: MediaBrowser ...` header per-call via `mediaBrowserAuthorization()`
  (`http/MediaBrowserAuthorization.kt`) — **no OkHttp interceptor/authenticator**, so there's nothing
  to port to a Ktor `Auth` plugin config; it becomes a Ktor request header set the same way, or a
  small `HttpMessageBuilder` extension.
- `@JsonClass` DTOs: **7** (`AuthenticationResult`, `User`+`Policy`, `SessionInfo`, `Item`+`ArtistItem`
  [2 classes], `QueryResult`, `QuickConnectResult`). All use `@Json(name = "PascalCase")` for Jellyfin's
  PascalCase API and `: Serializable` (drop on the KMP DTOs — `Serializable` is JVM-only and appears
  unused outside intents/bundles; verify no `Parcelable`-adjacent usage before dropping). No
  polymorphic, lenient, or custom date adapters; `Item.dateCreated` is a raw `String?`, not a `Date`.
- Auth/401 handling: `JellyfinAuthenticationManager.authenticate()`/`pollQuickConnect()` inspect
  `RemoteServiceHttpError.httpStatusCode == Unauthorized` inline and clear
  `credentialStore.authenticatedCredentials` on a failed **sign-in** call only — a 401 on a
  already-authenticated call (sync, playback reporting) is **not** handled today (#577).
- Timeout: `readTimeout(90, SECONDS)` on the Jellyfin Retrofit client (`di/JellyfinMediaProviderModule.kt`,
  DI file, not edited here but informs the shared client config).
- Android-only: none besides the DI module's `Context`/`getSystemService()` (DI, out of scope).

### `android:mediaprovider:emby` (22 files)
Structurally identical to Jellyfin (`X-Emby-Token`-flavoured but same `MediaBrowserAuthorization`
helper file duplicated per module — a good candidate to hoist into `server` once both are on Ktor,
flagged as a follow-up, not part of this brief). 19 endpoint annotations, `@JsonClass` DTOs: 6
(no `Policy`/QuickConnect — Emby has no Quick Connect). Same per-call auth header, same missing
mid-session-401 handling, same 90s read timeout.

### `android:mediaprovider:plex` (22 files)
- Retrofit endpoints: 15, in `UserService`/`ItemsService`/`PlaybackReportingService`.
- `@JsonClass` DTOs: 3 (`AuthenticationResult`+`User`, `QueryResult`, plus whatever `MetadataToSongTest`
  covers — check `http/` package for the metadata response shape not caught by the grep, likely folded
  into `QueryResult`).
- **Two OkHttp `Interceptor`s, unique to Plex** (no equivalent in Jellyfin/Emby):
  - `PlexArtworkTokenInterceptor` — adds `X-Plex-Token` to artwork requests whose origin matches the
    signed-in server (scheme+host+port check against `ServerCredentialStore.address`), so the token
    never appears in a URL any cache/log might capture. Runs as a network interceptor in
    `imageloader`'s Coil `OkHttpClient` (see §2), not in the API `OkHttpClient` — **this interceptor's
    logic has to move to whatever Coil uses on Android and to iOS's image-loading stack** (§2), not
    just to Ktor.
  - `PlexClientHeaderInterceptor` / `plexClientHeaders()` — adds the 5 `X-Plex-*` client-identity
    headers to every request. This one **is** part of the Plex API client and maps directly to a Ktor
    `defaultRequest { header(...) }` block or a small plugin.
- 401 handling: same per-call inline check as Jellyfin/Emby, same #577 gap.
- Plex-specific: `LoginCredentials.authCode` (2FA code, sent once, never persisted) — no networking
  change needed beyond passing it through the new auth call.

### Android-only APIs, module-wide
`android.util.Base64`: **none found** anywhere in these 5 modules. `android.net.ConnectivityManager`:
only in `android:networking`'s Retrofit adapters (deleted, §3 replaces the concept) and the DI-layer
Coil client (out of scope, §2). `android.content.Context`: `ServerSession.kt` (string resources, see
above) and DI modules (out of scope). `android.net.Uri`: none in these modules.

## 2. Other networking consumers

| Consumer | Current stack | iOS plan |
|---|---|---|
| Last.fm scrobbling (`android/scrobbling/.../lastfm/LastFmApi.kt`) | Retrofit `@FormUrlEncoded @POST` to `ws.audioscrobbler.com`, `Response<LastFmScrobbleResponse>` (raw Retrofit `Response`, not `NetworkResult`) | Small Ktor client in `commonMain`, `submitForm`/`FormDataContent` for the URL-encoded body. Low endpoint count (1), do alongside the "providers as a batch" step (§5) or as its own quick follow-up — not blocking. |
| S2 API / trial (`android/trial/.../PromoCodeService.kt`) | Retrofit `@GET` with `NetworkResult<PromoCode>`, hard-coded Basic-Auth credentials for `api.shuttlemusicplayer.app` (public by decision, see project memory) | Same shared `HttpClientFactory` + a `BasicAuth`-equivalent default header (Ktor's `Auth { basic { } }` plugin, or a plain `Authorization: Basic` default header since the credential is fixed, not per-session). |
| Playback reporting (`{jellyfin,emby,plex}/http/PlaybackReportingService.kt`) | Retrofit, folded into the provider modules — covered in §1. | Same as its provider. |
| Coil image loading (`android/imageloader/.../di/CoilModule.kt`) | `coil3` (already Coil 3) with `OkHttpNetworkFetcherFactory`, a dedicated `artworkHttpClient` carrying the S2 Basic-Auth authenticator, a wifi-only network interceptor for `S2_ARTWORK_HOST`, and per-provider `remoteArtworkInterceptors` (Plex's token interceptor plugs in here via `Multibinds`). | **Coil 3 is multiplatform**, but its network layer is engine-pluggable (`coil3.network.okhttp` today; `coil3.network.ktor` exists and is the natural swap once this module and DI are both Ktor). Recommend: keep Android on `OkHttpNetworkFetcherFactory` (zero risk, DI already wired) and give iOS its own `coil3.network.ktor.KtorNetworkFetcherFactory` backed by the same shared `HttpClient` factory from §3, with the Plex-token and wifi-only checks reimplemented as a Ktor `HttpClientPlugin` shared in `commonMain` instead of an OkHttp interceptor (one shared plugin, two engines — not two artwork loaders). This is phase-5/9 iOS work, not phase 3, but the Ktor client this phase builds is what it will need — call it out now so the shared plugin lives in `mediaprovider:server`/`imageloader` common code from day one instead of Android-only. |
| Downloads (`android/downloads`) | No direct HTTP client — consumes the stream/download URLs built by each provider's `AuthenticationManager` (`buildJellyfinPath`/`buildDownloadPath`/etc., §1) and hands them to Media3's `DownloadManager`/`ExoPlayer` data source. | Unaffected by this phase. URL-building functions stay (they're pure string building, already shareable); only the DTOs/services that produce the ids and tokens feeding them move to Ktor. |
| Chromecast / streaming URLs handed to Media3 | Built the same way as downloads (provider URL builders); no NanoHTTPD found in this codebase (no local HTTP server for Cast) — Cast plays the same authenticated URLs directly. | Stays platform (`CastPlayer`, Media3), but the URL-building functions it calls are the same shared ones. No change needed beyond what §1's provider port already does. |

## 3. Proposed shape

New/changed `commonMain` structure, mirroring Podcasts:

```
android/networking/src/commonMain/kotlin/com/simplecityapps/networking/
  HttpClientFactory.kt          // expect fun createPlatformHttpClient(preconfigured: Any? = null): HttpClient
                                 // fun createHttpClient(json: Json = S2Json, configure: HttpClientConfig<*>.() -> Unit = {}): HttpClient
  NetworkResult.kt               // unchanged shape, no Retrofit dependency
  ErrorHelper.kt                 // unchanged
  error/*.kt                     // RemoteServiceHttpError now wraps Ktor's HttpResponse (status code, not retrofit2.Response)
  KtorNetworkResult.kt           // NEW: suspend inline fun <reified T> HttpClient.networkResult(block): NetworkResult<T>
                                  //  — catches IOException/ konnektivity errors -> NetworkError,
                                  //    non-2xx -> RemoteServiceHttpError(response.status),
                                  //    success -> Success(body<T>())
android/networking/src/androidMain/kotlin/.../HttpClientFactory.android.kt   // OkHttp engine, `preconfigured` hook for the shared app OkHttpClient
android/networking/src/iosMain/kotlin/.../HttpClientFactory.ios.kt           // Darwin engine, HttpTimeout + HttpRequestRetry as in Podcasts (evaluate need for the alt-svc/H3 workaround per-host)
```

- **Json config** (one shared `Json` instance, `android:networking`): `ignoreUnknownKeys = true`
  (servers add fields over time — Jellyfin/Emby/Plex all evolve their APIs), `isLenient = false`
  (nothing in the current DTOs needs leniency), `explicitNulls` — **default (true) unless a provider
  turns out to distinguish absent vs. null**, none observed in this inventory. Per-provider
  `@SerialName("PascalCase"/"snake_case")` replaces `@Json(name = ...)` 1:1.
- **Auth / 401 handling**: give `android:mediaprovider:server` a shared Ktor plugin analogous to
  `AccountAuthPlugin`, but simpler — these APIs build the auth header explicitly per call today
  (`mediaBrowserAuthorization`, `plexClientHeaders`), so keep that per-call style (don't force a
  bearer-token model that doesn't fit token-in-query-param cases like Jellyfin's `ApiKey=`
  stream URLs) but **do** centralize the 401 reaction: a small `on(Send)` plugin or a thin wrapper
  function in `server` (`suspend fun <T> ServerCredentialStore.authenticated(call: suspend () ->
  NetworkResult<T>): NetworkResult<T>`) that clears `authenticatedCredentials` and raises a
  "needs re-sign-in" signal (a `SharedFlow`/callback the app-layer sign-in UI observes) whenever the
  wrapped call comes back `RemoteServiceHttpError(Unauthorized)`. This is the fix for #577 and should
  land as part of this phase since all three providers already funnel through `server`. No
  single-flight refresh needed (no refresh-token flow here, unlike Podcasts' OAuth) — just clear +
  signal, then the existing re-authenticate-on-next-sync path takes over.
- **Per-provider API classes**: replace each Retrofit `interface XService` with a plain class taking
  an `HttpClient` and exposing the same suspend functions (`suspend fun authenticate(...):
  NetworkResult<AuthenticationResult>`), built with `client.networkResult<T> { get(url) { ... } }`.
  Keep today's extension-function-over-thin-interface split (e.g. `UserService.authenticate(...)`
  wrapping `authenticateImpl`) — collapse the two into one function per endpoint; the `Impl` split
  existed only to give Retrofit a stable interface method while call sites had a friendlier
  signature, which a plain class no longer needs.
- **Error mapping**: `RemoteServiceHttpError` takes `HttpStatusCode` (Ktor's own enum, drop the
  hand-rolled 40-case one in `error/RemoteServiceHttpError.kt` — Ktor's `io.ktor.http.HttpStatusCode`
  already covers this) and the response body text for diagnostics instead of `retrofit2.Response<*>`.
  `NetworkError` wraps a Ktor `IOException`/`UnresolvedAddressException`/etc.; connectivity checking
  (`ConnectivityManager`) becomes a platform `expect fun hasInternetConnectivity(): Boolean` (Android:
  `ConnectivityManager`; iOS: `NWPathMonitor` or simply omit and treat all `IOException`s as
  "server unreachable" — the current Android string already collapses this to two messages).
- **Tests**: `FixtureServer` (§1) is rebuilt on `io.ktor.client.engine.mock.MockEngine` — a
  `MockEngineConfig`-backed router keyed on method+path+query, same `respond(path, fixture, code,
  method, query)` / `requestsTo(path)` API, so the ~20 provider tests that call it need only their
  `HttpClient` construction changed, not their assertions. `ktor-client-mock` is already in the
  catalog. Drop `MockWebServer` (5 usages) and the `okhttp3.mockwebserver3` test dependency once all
  three providers move.
- **What stays androidMain**: `HttpClientFactory.android.kt` (OkHttp engine + `preconfigured` hook so
  the app's existing proxy-aware, Timber-logged `OkHttpClient` — `core/.../NetworkingModule.kt` — still
  backs the Android engine, preserving `BuildConfig.PROXY_ENABLED` and the `"OkHttp"` Timber tag
  without rewriting that DI file, which belongs to the parallel DI worktree); Coil's `OkHttpClient`
  wiring (§2, unaffected this phase); `ServerSession`'s `Context.getString` shim until it's replaced
  by an injected string provider (§5 step 2, small and independent — do it early since it blocks
  `server`'s KMP move).

## 4. Moshi → kotlinx.serialization pitfalls in these DTOs

- **`: Serializable` (java.io.Serializable) on every `@JsonClass` DTO** (Jellyfin/Emby/Plex) — JVM-only
  marker interface, drop it. Grep confirms it's used purely as decoration from an older Bundle-passing
  pattern; verify nothing still puts these DTOs directly into an `Intent` extra before deleting (a
  quick `grep -rn "putExtra.*AuthenticationResult\|putExtra.*Item\b"` before the provider step).
- **Nullable fields with no default vs. Moshi's implicit null-if-absent**: Moshi treats a missing
  non-null-without-default field as a deserialization *error* only if the Kotlin type is non-null;
  today's DTOs already default risky fields (`artists: List<String> = emptyList()`, `policy: Policy? =
  null`), so this mostly transfers cleanly — but audit every non-null, no-default field (e.g.
  `Item.id: String`, `AuthenticationResult.accessToken: String`) to confirm the server truly always
  sends it, since kotlinx.serialization is *stricter* about missing keys by default (throws
  `MissingFieldException`) where Moshi would sometimes silently null it and let a downstream NPE
  happen at first use. Recommend giving genuinely-optional-but-currently-non-null fields an explicit
  default rather than relying on "the server has never omitted it yet."
- **Unknown keys**: Moshi ignores unrecognised JSON keys by default; kotlinx.serialization throws
  unless `ignoreUnknownKeys = true` is set on the shared `Json` (§3 already sets this — required, since
  Jellyfin/Emby/Plex all add response fields between versions and none of these DTOs use
  `@JsonClass(generateAdapter = true, generator = "...")`-style strictness today).
  QuickConnectResult (`http/QuickConnectResult.kt`) especially needs this — its shape has already
  changed across Jellyfin versions (`initiateQuickConnect`'s POST/GET fallback).
- **Field naming**: `@Json(name = "PascalCase")` → `@SerialName("PascalCase")`, mechanical 1:1 rename
  across all 16 `@JsonClass` DTOs (7 Jellyfin + 6 Emby + 3 Plex). No naming-strategy shortcut exists
  for kotlinx.serialization the way `Moshi.Builder().add(PascalCaseJsonAdapterFactory)` might have —
  each field needs its own explicit annotation, same as today.
- **Enums**: none of the inventoried DTOs use a Moshi/JSON-backed enum (the two enums found,
  `QuickConnectPollState` and `HttpStatusCode`, are internal, not deserialized) — no
  `@JsonClass(generateAdapter = false)`-style enum adapter migration needed. If a future DTO adds one,
  kotlinx.serialization enums default to exact-name match and throw on an unknown value unless
  annotated `@Serializable(with = ...)` — worth a one-line callout in the provider step's brief so a
  worker doesn't get surprised by a server adding an enum value.
- **Defaults**: kotlinx.serialization requires `@Serializable` classes' defaulted properties to be
  declared with `= value` same as Kotlin defaults generally — direct port, no behaviour change, since
  every defaulted Moshi field here is already a normal Kotlin default parameter.
- **No polymorphic or custom date adapters found** — `Rfc3339DateJsonAdapter` lives only in
  `core`'s shared Moshi instance (unused by these 5 modules; `Item.dateCreated` is `String?`), so
  nothing here needs a `kotlinx-datetime` migration. If `core`'s Moshi instance is retired later,
  check its other consumers separately (out of scope for phase 3).

## 5. Ordered steps

| # | Step | Scope | Verify | Risk |
|---|---|---|---|---|
| 1 | **Networking core**: add `commonMain`/`androidMain`/`iosMain` source sets to `android:networking` (convention plugin from phase 0), build `HttpClientFactory`, shared `Json`, `NetworkResult`/error types on Ktor's `HttpStatusCode`, the `networkResult{}` wrapper, delete the Retrofit `CallAdapter` classes. | `android:networking` only | `./gradlew :android:networking:testDebugUnitTest :android:networking:compileKotlinIosSimulatorArm64` (new commonTest covering `networkResult{}` success/failure/IOException mapping against `MockEngine`) | Low — no consumers yet; the module's public API (`NetworkResult`, `userDescription()`) is preserved so downstream modules don't move in the same step. |
| 2 | **`server` module**: KMP-ify (mirrors phase 0's `:android:domain` conversion — same convention plugin, same `unit-test`'s source-set mapping), fix `ServerSession`'s `Context.getString` (inject a small string-provider interface instead), rebuild `FixtureServer` on `MockEngine`, land the #577 401-clear-and-signal wrapper. | `android:mediaprovider:server` only | `./gradlew :android:mediaprovider:server:test :android:mediaprovider:server:compileKotlinIosSimulatorArm64`; existing `ServerSessionTest`/`ServerCredentialStoreTest`/`PagedFlowTest` pass unchanged in behaviour, plus a new test for the 401 signal | Medium — the #577 fix changes observable behaviour (credentials now clear on a mid-session 401); needs a `reviewer` pass checking every provider's call sites don't double-clear or race the existing sign-in-time clear. |
| 3 | **Jellyfin/Emby/Plex as one batch** (structurally near-identical, do together so the shared patterns — auth header building, `networkResult{}` usage, `MockEngine` fixture wiring — land once and get copied, not reinvented three times): convert all `http/*Service.kt` to Ktor-backed classes, all `@JsonClass` DTOs to `@Serializable`, keep `PlexClientHeaderInterceptor`'s logic as a `defaultRequest` block, move `PlexArtworkTokenInterceptor` to the Coil/imageloader boundary only (§2 — do not port it into the Ktor client, it never belonged to the API client). | 3 provider modules, non-overlapping files — safe to run as 3 parallel `worker` briefs *if* step 2 has already landed (they all depend on `server`'s new shape), otherwise sequential | `./gradlew :android:mediaprovider:{jellyfin,emby,plex}:test :android:mediaprovider:{jellyfin,emby,plex}:compileKotlinIosSimulatorArm64` then the phase-3 checkpoint: MockEngine-based provider tests + Maestro sign-in/sync flows against a real Jellyfin/Emby/Plex fixture server (`support/scripts/seed-remote-provider.sh`) | Medium-high — 68 endpoints total across 16 DTOs; the highest-risk single item is Plex's artwork-token interceptor relocation (a regression here leaks a token into a Coil-cached URL or breaks artwork loading silently, only visible on-device) and the `MissingFieldException` strictness change (§4) surfacing on a real server response shape this inventory didn't fixture-test. |
| 4 | **Cleanup**: delete `android:networking`'s dead Retrofit-adapter classes if step 1 left placeholders, delete `MockWebServer`/`retrofit2`/`moshi` dependencies from these 5 modules' `build.gradle(.kts)`, hoist the duplicated `MediaBrowserAuthorization.kt` (Jellyfin/Emby) into `server` (noted as a follow-up, not required for phase 3's checkpoint). | Same 5 modules | Full module verify (`support/scripts/remote-build.sh --local -q testDebugUnitTest :android:app:assembleDebug`) | Low, but don't skip — leftover Retrofit/Moshi deps on a module that no longer uses them is exactly the "code fork" pattern the project avoids. |

Checkpoint (per `docs/architecture/ios-port.md` phase 3): MockEngine unit tests across the 3 provider
modules + `server`, and a Maestro sign-in + sync flow run on the emulator against real Jellyfin/Emby/Plex
fixture servers (`seed-remote-provider.sh`), before phase 4 (ViewModels) starts.

## Top risks (summary)

1. **#577's 401 handling changes real behaviour** (credentials now clear + signal on a mid-session
   401) — needs careful review so it doesn't fire on the sign-in-time 401 path that already handles
   itself, and doesn't regress silently for users who are mid-playback when a token is revoked.
2. **Plex's artwork-token interceptor doesn't map to the API `HttpClient` at all** — it lives at the
   Coil boundary and must be re-homed as a shared Ktor plugin usable from both Coil-on-OkHttp
   (Android, unchanged) and Coil-on-Ktor (iOS, future phase), or the token leaks into a cached URL.
3. **kotlinx.serialization's default strictness** (`MissingFieldException` on an absent key Moshi
   would have tolerated) can surface only against a real server, not the existing fixtures — the
   provider batch step should re-run against live Jellyfin/Emby/Plex test servers, not just replay
   the current JSON fixtures.

## 6. What landed

Steps 1 to 3 moved the networking core and each provider's HTTP services and DTOs to Ktor in commonMain. A second
pass then moved the rest of each provider's code into commonMain, one commit per module.

**`:android:mediaprovider:server`.** In commonMain: `ServerCredentialStore`, `withServerSession`, `pagedFlow`,
`checkSession`, `QuickConnectAuthentication`, `DirectPlayFormats`, `ServerStrings` and `mediaBrowserAuthorization`. The JVM-only APIs they used
have KMP replacements:
- An expect `Lock` replaces `synchronized`. It is `NSLock` on iOS.
- `Logger` replaces Timber.

Only two things stay in androidMain, because both read a `Context`:
- `ResourceServerStrings`, the contributed `ServerStrings` binding.
- `isDebuggable`.

**Jellyfin, Emby and Plex.** Each module's commonMain now holds:
- the provider, the authentication manager, the playback reporter and the artwork provider;
- a `*MediaProviderModule` that builds them from a `@Named("<Provider>HttpClient") HttpClient`.

The JVM-only APIs they used have KMP replacements:
- `ServerStrings` replaces `Context.getString`.
- Ktor's `parseUrl` replaces `Uri.parse`.
- `kotlin.uuid.Uuid` replaces `java.util.UUID`.
- `Logger` replaces Timber.
- For Plex, `formUrlEncode` replaces `URLEncoder`. It gives the same bytes, and a host test checks that.

Each module also has a `*AndroidModule` in androidMain. It keeps:
- the OkHttp-backed client (the app's proxy and logging, with a 90s read timeout);
- the `MediaInfoProvider`, whose `MediaInfo` carries an `android.net.Uri`;
- for Jellyfin and Emby, the credential store, because its debug-build sign-in reads a `Context`;
- for Plex, `PlexArtworkTokenInterceptor`, the OkHttp interceptor on the image loader's client.

Plex's credential store has no debug-build seed, so it is common.

New interfaces and seams:
- `ServerStrings.unknownName`.
- `PlexStrings` holds Plex's missing-music-library message. On Android it is `ResourcePlexStrings`.
- `PLEX_PLATFORM` is the `X-Plex-Platform` and `X-Plex-Device` value: "Android" on Android, "iOS" on iOS.
- `MediaProviderTypeKey` moved to `:android:mediaprovider:core`'s commonMain.

**What iOS must provide.** To build the Jellyfin provider, the iOS graph must supply:
- `@Named("JellyfinHttpClient") HttpClient`, a Darwin client from `createHttpClient`;
- `@Named("JellyfinCredentialStore") ServerCredentialStore`, as `ServerCredentialStore(securePreferenceManager, prefix = "jellyfin")`;
- a `ServerStrings`;
- `ClientIdentity`;
- `SecurePreferenceManager`.

Emby needs the same, with the `Emby` names. Plex needs its HTTP client (with `sendPlexClientHeaders`), a
`ServerStrings` and a `PlexStrings`. The artwork URL building (`ArtworkUrls`, phase 5) belongs in `:shared`, not here.

**Tests.** Every provider and server test runs in commonTest, on the Android host and the iOS simulator, except:
- each provider's `MediaInfoProvider` test;
- the Plex artwork interceptor test;
- the `formUrlEncode` parity test.

These three need Android or the JVM. Supporting changes:
- `FixtureServer` fixtures are read through an expect `readFixture`.
- Kotlin/Native bundles no test resources, so on iOS `readFixture` reads the module's `src/commonTest/resources`.
  `s2.kmp-library` passes that path to simulator tests as `S2_TEST_RESOURCES`.
- Native test names can't contain `,`, `(`, `)` or `#`, so the moved tests' names use ` - ` instead.
