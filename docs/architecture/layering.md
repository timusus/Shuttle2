# Layering: presentation, domain, data (#443)

Target module layout for the Android app, how today's modules map onto it, and how Gradle enforces
the direction. The source-level rules that guard the same boundaries today live in
`:android:architecture-tests` (see [clean-architecture-audit.md](clean-architecture-audit.md)).

## Target layers

```
presentation  :android:app (composition root + shell), later :android:ui, :android:designsystem
      |
      v
domain        :android:domain  (pure Kotlin/JVM: models, repository and service interfaces, use cases)
      ^
      |
data          :android:mediaprovider:{core,local,jellyfin,emby,plex}, :android:playback,
              :android:downloads, :android:networking, :android:imageloader, :android:saf,
              :android:trial, :android:remote-config
```

- **Domain** is a `org.jetbrains.kotlin.jvm` module: no Android SDK, no Room, no Retrofit, no Hilt
  Android. It holds the models (`Song`, `Album`, `Playlist`, ...), sort orders and queries, the
  repository interfaces, the playback-facing interfaces use cases already consume
  (`QueueOperations`, `PlaybackOperations`), and the shared use cases now in `ui/actions`.
- **Data** implements domain interfaces: Room DAOs and entities, MediaStore/TagLib, the
  Jellyfin/Emby/Plex HTTP clients and DTOs, Media3 playback, downloads, billing, remote config.
  Each data module binds its implementations in its own Hilt module.
- **Presentation** holds composables, ViewModels and screen-specific use cases. It sees domain
  types only; data modules reach it through Hilt, never through an import.
- **Cross-cutting**: `:android:core` (logging, coroutine dispatchers, settings storage, Hilt
  qualifiers) may be used by every layer but depends on nothing of ours. `:android:fixtures` stays
  test/debug-only (already guarded by `verifyFixturesNotInReleaseClasspath`).

## Today's modules mapped onto the target

| Module | Today | Target |
|---|---|---|
| `:android:domain` | Models, sorting, queries, repository interfaces; a plain Kotlin/JVM module (was the Android library `:android:data`) | Domain (steps 3-4 done) |
| `:android:mediaprovider:core` | `MediaProvider`, `MediaInfoProvider`, `MediaImporter`, M3U, import worker | Data (step 4 done: repository interfaces moved to domain) |
| `:android:mediaprovider:local` | Room DB, DAOs, entities, `Local*Repository`, MediaStore/TagLib | Data |
| `:android:mediaprovider:{jellyfin,emby,plex}` | HTTP services, DTOs, auth, providers | Data |
| `:android:playback` | Media3 engine, `PlaybackFacade`, `QueueFacade`, Cast, session; **depends on the three remote provider modules** (only `di/PlaybackEngineModule.kt` imports them) | Data; its operations interfaces move to domain (step 5); provider edges removed (step 2) |
| `:android:imageloader` | Coil artwork fetchers, keys and the app `ImageLoader`; its unused `:emby`/`:jellyfin` edges are gone | Data (platform adapter) |
| `:android:downloads`, `:android:networking`, `:android:saf`, `:android:trial`, `:android:remote-config` | Platform services | Data |
| `:android:core` | Shared utilities, settings, DI qualifiers | Cross-cutting |
| `:android:designsystem` | Compose components, theme | Presentation (no domain or data deps) |
| `:android:app` | Everything UI plus DI root | Presentation + composition root |
| `:android:recyclerview-adapter` | Legacy ViewBinder adapter | Deleted by #381 |

## Allowed dependency directions

| From \ To | domain | data | presentation | core |
|---|---|---|---|---|
| domain | — | no | no | yes |
| data | yes | within its own feature (`mediaprovider:local` → `mediaprovider:core`), and to platform adapters (`networking`, `imageloader`, `saf`); never to a sibling provider | no | yes |
| presentation (`:ui`, `:designsystem`) | yes | **no** | yes | yes |
| composition root (`:android:app`) | yes | yes (to aggregate Hilt modules) | yes | yes |

Forbidden edges today: `playback → mediaprovider:{jellyfin,emby,plex}` (baselined, see below).
Data modules must not depend on sibling provider
implementations; a data module that needs "the right provider" asks for a domain interface map
bound by each provider (`@IntoMap` keyed by `MediaProviderType`).

## How Gradle enforces it

**Done: a `buildSrc` task, `VerifyModuleLayers`, wired into every module's `check`.** A single
map in `buildSrc` (`ModuleLayers.table`) assigns each project path to a layer; the root
`verifyModuleLayers` task reads the project dependencies declared on every module's production
configurations (`api`, `implementation`, `compileOnly`, `runtimeOnly` and their variant forms, not
`test*`/`androidTest*`) and fails on any edge the table above forbids, with a baseline for today's
forbidden edges that only shrinks (the same ratchet as the Konsist baselines).

| Layer | Modules | May depend on |
|---|---|---|
| core | `core` | nothing of ours |
| domain | `domain` | core |
| data | `mediaprovider:core`, `downloads`, `imageloader`, `networking`, `playback`, `remote-config`, `saf`, `trial` | core, domain, data |
| provider | `mediaprovider:{local,jellyfin,emby,plex}` | core, domain, data (never another provider) |
| presentation | `designsystem` | core, domain, presentation, fixtures |
| composition root | `app` | everything but tooling |
| fixtures | `fixtures` | core, domain |
| tooling | `architecture-tests` | nothing of ours |

Provider implementations are their own layer so "never to a sibling provider" is a layer rule:
only the composition root may depend on one. Data → data stays open for the platform adapters and
`mediaprovider:core`.

- Run it: `./gradlew verifyModuleLayers`. It also runs from every module's `check` and before
  `:android:architecture-tests:test`/`testDebugUnitTest`, so the project-wide unit test sweep (and
  CI) gates on it.
- It fails on a forbidden edge not in the baseline, on a baseline edge that no longer occurs, and on
  a module missing from `ModuleLayers.table`. The message names each edge, its configurations and
  the layers involved.
- Baseline: `android/architecture-tests/src/test/baselines/module-layers.txt`, one `:from -> :to`
  per line. `-PupdateArchitectureBaselines` rewrites it along with the Konsist baselines.
- The rule engine (`ModuleLayerRules`) is pure and unit-tested in `buildSrc/src/test`; those tests
  run whenever `buildSrc` changes, before the main build configures.

Why this over the alternatives:

- It follows the precedent already in the build: `VerifyFixturesNotInReleaseClasspath` (#402) is a
  `buildSrc` task hung off `check` from the root `subprojects {}` block, configuration-cache safe.
  One more task in the same place is cheap to read and maintain.
- A third-party plugin (`com.jraska.module.graph.assertion`, dependency-analysis) does the edge
  check too, but adds a plugin and its AGP-compatibility risk for a ~60-line task.
- Declared dependencies are the right thing to check: with `implementation` everywhere (the repo
  uses no `api` project edges today), a module's compile classpath sees exactly what it declares,
  so "no declared edge" means "cannot import". Domain exposes models through its API, so data and
  presentation modules that return domain types declare `api(project(":android:domain"))`; every
  other edge stays `implementation`, so data internals never leak transitively into presentation.
- The composition root is the one module allowed to see everything. Until presentation is split
  out of `:android:app`, the Konsist rules (`presentation-data-imports`, `viewmodel-data-access`)
  are the enforcement inside it.

## Hilt wiring impact

- Domain declares no Hilt modules. Use cases keep `@Inject constructor` (`javax.inject`, available
  on the JVM); domain applies KSP with `dagger-compiler` only, so factories are generated where the
  classes live instead of Dagger regenerating them in `:android:app` with a warning.
- Each data module keeps (or gains) a `@Module @InstallIn(SingletonComponent::class)` that
  `@Binds` its implementations to domain interfaces. The provider → `MediaInfoProvider` bindings move
  out of `playback/di/PlaybackEngineModule.kt` into each provider module as `@IntoMap` entries.
- `:android:app` stays the only `@HiltAndroidApp` module and keeps `implementation` edges to every
  data module so Hilt's aggregating task sees all `@InstallIn` modules. If presentation moves into
  `:android:ui`, that module applies Hilt for `@HiltViewModel` but depends only on domain.
- Test fakes bind domain interfaces, so screen tests stop depending on data modules.

## Migration, in shippable steps

Each step is one worker branch, lands green on its own, and moves code in place: no copies, no
flags, no parallel implementations. Keep package names when moving files between modules, so the
diff is a `git mv` plus build files; rename packages later only if it is ever worth the churn.

1. **After #381 lands**: trim the Konsist baselines (`-PupdateArchitectureBaselines`), delete the
   now-orphaned legacy code found by the audit, and delete `:android:recyclerview-adapter` if #381
   has not.
2. **Guard the graph**: ~~add `VerifyModuleLayers` with the layer map and an allowlist~~ (done;
   `imageloader`'s unused provider dependencies are gone too). Remaining: the baselined edges
   `:android:playback -> :android:mediaprovider:{emby,jellyfin,plex}`. Move the provider
   `MediaInfoProvider` bindings to the provider modules as `@IntoMap` entries, drop the three
   edges, and delete the baseline lines. Baseline ends empty.
3. ~~**Domain models**: remove `Parcelable`/`@Parcelize` from the models (navigation keys are already
   `@Serializable` ids, never models; any `rememberSaveable` of a model switches to an id), then
   convert `:android:data` to a JVM module and rename it `:android:domain`.~~ (done: the one
   real use was a lazy-list key built from `AlbumGroupKey`, now its string form; the parcelers and
   the `kotlin-parcelize` plugin went with no replacement, and `:android:domain` is a
   `kotlin("jvm")` module.)
4. ~~**Repository interfaces**: move `mediaprovider/core/.../repository/**` (interfaces, queries,
   sort orders) into domain. `mediaprovider:core` keeps `MediaImporter`, workers and M3U.~~ (done:
   the 14 files plus `SongPathRemap` moved with their package names unchanged. Every module that used
   them (`app`, `imageloader`, `playback`, `mediaprovider:local`) also uses other `mediaprovider:core`
   symbols (`MediaImporter`, `MediaInfoProvider`, `RemoteArtworkProvider`, `FlowEvent`, M3U, search,
   workers, ...), so no module's `mediaprovider:core` edge dropped — each gained `:android:domain`
   where it didn't already have it.)
5. **Playback interfaces**: move `QueueOperations`, `PlaybackOperations` and the state types they
   expose into domain; `:android:playback` implements them.
6. **Shared use cases**: move `ui/actions` use cases whose dependencies are now all in domain into
   `:android:domain`. The ones that need Android (`ShareSongs`, `DeleteSongs`' SAF deleter) stay in
   the app behind a domain interface.
7. **Burn down `viewmodel-data-access`** screen by screen (audit batches A–C).
8. **Split presentation** out of `:android:app` into `:android:ui` (screens, ViewModels,
   screen use cases) that depends on domain and designsystem only; `:android:app` keeps the
   Application, `MainActivity`, services and DI. `VerifyModuleLayers` then enforces presentation →
   data as a build failure, and the Konsist import rules become a second line of defence.

## Open decisions

| Decision | Options | Recommendation |
|---|---|---|
| Use-case naming | `*UseCase` suffix (as the #443 brief said) vs the verb phrase UDF 8c prescribes | **Keep 8c** (`PlaySongs`, no suffix): every existing use case follows it and the Konsist rule enforces it. |
| Trivial repository reads in ViewModels | UDF 8a allows inline one-liners (`repository.setExcluded(...)`) vs "only through use cases" | **Only through use cases**, including thin `Observe*` ones; update 8a when batch A lands so the doc and the rule agree. |
| Domain module name and place | `:android:domain` vs top-level `:domain`; one module vs `domain:model` + `domain` | **One `:android:domain`**, matching the `android/` layout; split only if build times demand it. |
| Parcelable models | keep `:domain` an Android library for `@Parcelize` vs pure JVM | **Pure JVM** (done in step 3): routes already carry ids, and #381 removed the Fragment-argument users. |
| Graph enforcement | `buildSrc` task vs `module-graph-assertion` plugin | **`buildSrc` task**, per the precedent above. |
| `au.com.simplecityapps` package in `:android:imageloader` | rename to `com.simplecityapps` vs keep (baselined by `package-root`) | **Rename** in the imageloader batch: mechanical, 30 files, no behaviour change. |
| When to split `:android:ui` out of `:android:app` | now vs after batches A–C | **After A–C**, when ViewModels no longer import data types, so the split is a pure move. |
