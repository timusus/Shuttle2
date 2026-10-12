# Monetisation

Status: strategy settled 2026-10-11 (owner decisions on #380; the annual plan is dropped). The entitlement layer
(`:android:trial`), the Pro gates (#939) and the Android paywall (#380: disclosure and offline + car pitch first,
Lifetime only, opt-in trial, pending purchases shown) are implemented; iOS is separate work.

## Strategy

**A free core plus a Pro bundle sold as one lifetime unlock, with a 14-day trial the user chooses to start.** The pitch
is offline and car, set against Plex Pass and Emby Premiere.

| Free, forever | Pro (one bundle) |
|---|---|
| Local playback, library, playlists, basic EQ, sleep timer, single-song tag edits, Chromecast, widgets | Jellyfin, Emby, Plex and Subsonic servers, and their offline downloads (no per-server pricing) |
| Songs already downloaded keep playing after the trial ends | Android Auto |
| | Batch tag editing |
| | Advanced audio: parametric/AutoEq EQ, replay-gain modes |

- **Android pricing:** $9.99 lifetime is the only offer (`s2_pro_lifetime`). No subscription, no monthly plan.
- **iOS pricing:** $14.99 lifetime, 14-day trial, no scheduled price change. iOS self-hosters pay about 1.5 times
  Android's price, under Narjo's $29.99. $19.99 is only a possible later test, if trial-to-paid holds around 30%. Purchases are per store:
  no cross-platform unlock. Family Sharing is on for Lifetime only. Enrol in Apple's Small Business Program before
  launch.
- **iOS Pro gates:** servers and CarPlay. ReplayGain (Track, Album, Off and pre-amp) is entirely free on iOS, which has
  no advanced modes.
- **Trial:** the existing 14-day trial (no card, app-side, not a Play trial offer), now always opt-in: it used to start
  silently on first Pro use. It is offered, never started silently, in three places: onboarding ("Start 14-day free trial", says local playback stays free; "Not now" dismisses it for good, and the
  paywall and gates still offer the trial), the
  first server added (start / buy lifetime / cancel; starting the trial or buying closes the paywall and the add resumes at the sign-in for the
  type the user chose; closing it unchanged forgets the add), and any Pro gate (servers, Android Auto, batch tag editing,
  advanced EQ and replay-gain modes) reached with no Pro and no trial used. With the trial used, a gate offers purchase
  only. Current non-payers get one fresh trial, announced in the changelog.
- **Disclosure:** the first lines of the listing and the paywall say "free for local files; Pro is a one-time unlock,
  with a 14-day free trial". The listing has a "What's free / what's Pro" block. Play's misleading-claims policy expects paid
  features disclosed, so the current "Stream your music via Jellyfin, Emby or Plex" line must change before release.
- **At expiry:** Pro features lock where they are used (server libraries stay browsable with a lock; playing a server
  song, connecting Android Auto or starting a batch edit opens the paywall). Local playback, settings and downloaded
  songs are untouched. No nag dialogs, no speed change.
- **Fallback:** if 60-day gross revenue falls below 70% of baseline (about A$220/month), move everything to
  trial-then-one-time (the Symfonium/Poweramp model).
- **Grandfathering:** all five legacy SKUs grant Pro indefinitely and are never offered again; leave legacy
  subscription base plans active. Thank existing buyers once ("You already own Pro").
- **No supporter perks** (alternate icons, extra themes): they would not earn revenue. A single dismissible
  local-user card ("made by one developer, Pro unlocks servers") ships only on the fallback trigger.

**Before release:** restore all five legacy SKUs on a release build (failed restores cause 1-star reviews in every
paid player sampled), and confirm the lifetime purchase works. No orders have come in since 13 September (#230).

## Evidence

- **Most revenue is likely local-only users.** 21 of 24 "I paid" reviews (of 262) never mention a server, so about
  70-85% of the ~A$220/month probably comes from local users. Making local free outright put most of it at risk.
- **Users pay a one-time unlock after an honest trial.** Symfonium is one-time ~$5-7 at 4.7 stars with 500K+ installs,
  growing about 6% a month in ratings. Poweramp and Neutron sell to about 7% of installs, free-plus-extras apps (Oto, Pulsar) to about 3%.
- **Subscriptions do badly here:** Plexamp's paywall reviews average 2.9 stars, and no dedicated Android player checked
  sells one. Ads hurt: GoneMAD lost about 0.6 stars. Prices cluster at $5-10 with about $20 as the ceiling.
- **Shuttle's 1-star reviews are about the hidden trial and the speed ramp, not about paying.**
- **Free server clients set the ceiling:** Finamp, Findroid, Tempus (with Android Auto) and Ultrasonic on Android;
  Finamp, Amperfy and substreamer on iOS. Pro has to sell offline and car, not streaming alone.
- **S2's numbers (Play Console, last 12 months to 2026-09-20):** A$4,145 gross; 396 lifetime orders (93% of revenue,
  79% of orders) and 107 annual. Monthly revenue fell from A$450-570 (late 2025) to A$200-240 (mid 2026). Rating 3.83
  from 1,142 ratings (3.33 over the last 28 days). 5.4k installed audience, 163k lifetime installs.
- **Market:** Plex's lifetime pass went from $249.99 to $749.99 in July 2026 and drew a revolt; Plexamp and Finamp
  stream for free and offline is what Plex and Emby charge for. RevenueCat 2026: trials of 17-32 days convert 42.5%
  against 25.5% for under 4 days; Play earns less per install than iOS, and billing failures cause 31% of its churn.
  Download-to-paid is 0.9% on Android against 2.6% on iOS, trial-to-paid about 32.5% on both, and 89% of trials start
  on install day.
- **Play's fee is 10% on the first $1M, plus 5%,** the same for subscriptions and one-time purchases since 30 June 2026.
- **Risk:** Plex may require Plex Pass for third-party remote streaming, which would weaken the Plex half of the pitch.

## Current model, from the code

- Five legacy products: subscriptions `s2_subscription_full_version_monthly`, `_yearly`, `_yearly_low`; one-time
  `s2_iap_full_version` and `s2_iap_full_version_low` (`android/trial/.../ProductIds.kt`). Despite the name, `_low`
  is the higher lifetime price ($7.99 against $3.49). They still grant Pro, as does an existing `s2_pro` annual
  subscription, but are never offered again: the paywall sells only `s2_pro_lifetime`.
- A PENDING purchase (Play waiting on cash or a slow card) doesn't grant Pro; the paywall says it's pending, blocks a
  second purchase, and Restore purchases reports it rather than "nothing to restore".
- `EntitlementRepository` exposes `StateFlow<Entitlement>` (Free, Trial, Pro(source), Unknown). Pro is any PURCHASED
  (not PENDING) purchase among the new and legacy SKUs; INAPP and SUBS are queried on start and foreground, and the
  last-known Pro is cached and fails open for 7 days when Play is unreachable. Playback speed never reads it.
- Server-side verification isn't needed at this scale; if fraud shows up, use the existing `api.shuttlemusicplayer.app`
  backend with the Play Developer API and Real-time Developer Notifications.

## Play Console setup (owner)

1. Deactivate the `annual` base plan of subscription `s2_pro` for new buyers. Existing subscribers keep it.
2. Create one-time product `s2_pro_lifetime` at $9.99 through the one-time-products API (the old `inappproducts` API
   is deprecated). Regional prices set manually: lifetime A$14.99 / £8.99 / €9.99; keep the existing manual
   emerging-market prices (₹260 lifetime) rather than auto-conversion.
3. Launch sale: a one-time-product discount to $7.99 for 30 days (not a lower base price). Afterwards, run a price
   experiment at $9.99 / $12.99 / $14.99 and judge it on revenue per paywall view, since ~500 orders a year is thin.
4. Leave the five legacy products' base plans and prices untouched.

## iOS (StoreKit 2, #609)

Pro is servers and CarPlay: Jellyfin, Emby, Plex and Navidrome streaming and CarPlay need Pro after a 14-day trial.
ReplayGain is entirely free. Downloads and batch edits join Pro when iOS
has them, and not before: the paywall, Settings and App Store Connect descriptions name
only what the app does (guidelines 2.3 and 3.1.1), from one place in Swift (`ProFeatures` in `PaywallView.swift`).
There is no subscription on iOS, only the trial and Lifetime. The trial length is `AppStoreProducts.TRIAL_DAYS` in
`:shared`.

- **Products** (ids in `shared/.../entitlement/AppStoreProducts.kt`): `com.simplecityapps.shuttle.pro.trial`, a free
  non-consumable whose purchase starts the trial, running 14 days from the transaction's `originalPurchaseDate` (a
  restore, reinstall or new device reports the same date, so none restarts it; a refunded trial counts as used); and
  `com.simplecityapps.shuttle.pro.lifetime` ($14.99 in `S2.storekit` and in App Store Connect, at launch and after;
  a refund removes Pro).
- **Trial consent.** App Review wants a knowing start, so on iOS the first server stream before the trial is refused
  and opens the paywall, which discloses the length, what stops after it and Lifetime's localized `displayPrice` above
  "Start 14-day free trial". Adding a server stays allowed until the trial has been used.
- **Entitlement.** `StoreKitManager` (Swift) reads `Transaction.latest(for:)` at launch, after each purchase or
  restore and on every `Transaction.updates`, and hands verified transactions to `StoreEntitlements` (Kotlin), which
  resolves Pro > Trial > Free > Unknown with the same `resolveEntitlement` as Android. While Unknown, a server stream
  waits up to 5 seconds, then is refused as undecided (no paywall, played again when asked). A queue restored at
  launch never opens the paywall. Restore is `AppStore.sync()`.
- **CarPlay (#946).** It goes through the same `ServerAccessGate` (`IosEntitlementModule`), which on iOS has no
  `startTrial`, so a user who hasn't had the trial is refused too. CarPlay follows `CarPlayAccess.locked`: without Pro
  its root is one upgrade row saying to upgrade on the iPhone, with nothing to browse or play, and it redraws when the
  entitlement changes. It fails open while the store hasn't answered. Now Playing on the car's own screen, for playback started on the phone, isn't
  gated (Android leaves non-car controllers alone too).
- **Paywall:** opened by a refused action and from Settings (Pro row, Restore Purchases); links the Privacy Policy and
  Apple's standard EULA.
- **Testing:** the S2 scheme runs with `ios/S2.storekit`. Debug builds resolve Pro unless Settings' "Debug
  entitlement" picker says otherwise; "App Store" resolves from StoreKit as a release build does.
- **Not on iOS:** account claiming and `appAccountToken`.

## Measuring it

- **Events** (PostHog, `MonetisationAnalytics`): `server_connected{type}`, `trial_started`, `paywall_shown{source}`,
  `paywall_dismissed{source}`, `purchase_started/completed/failed/restored{product}`, plus two that say who buyers are.
  Android and iOS both fire all of them, `entitlement_resolved` and `media_sources` included:
  - `media_sources`, a property on every event: configured source kinds, sorted and comma-separated
    (`jellyfin,local,plex`; `none` with no source).
  - `entitlement_resolved{source}`, once per install, once the entitlement first resolves and an enabled analytics
    backend took the event (while opted out it is offered again next launch, recording the entitlement at that later launch): `none`, `trial`, `pro`, `legacy` or
    `debug`. In the first week, the legacy ones show what share of existing buyers are local-only.
  - Analytics is anonymous and opt-out (on by default; decision on #981), so counts are close to real users, less those
    who turned it off.
- **KPIs** (Play Console): trial-to-paid (target at least 15%), paywall-view-to-purchase, monthly gross against the
  ~A$220 baseline.
- **Guardrails:** the 28-day rating stays at or above 3.3; installed audience and uninstall rate stay flat; refunds
  stay under 5%.
- **Reviews at 30 and 90 days.** Under 8% trial-to-paid: test a 30-day trial before lowering prices. Under 60-day
  gross of 70% of baseline: the fallback above.

## Sources

- Symfonium pricing: https://support.symfonium.app/t/application-pricing/8192
- Plex price changes: https://9to5mac.com/2025/03/19/plex-price-increase-remote-streaming-changes/ , https://linuxiac.com/plex-lifetime-pass-jumps-to-750-jellyfin-responds-with-0-price-increase/
- Poweramp: https://play.google.com/store/apps/details?id=com.maxmpz.audioplayer.unlock
- RevenueCat benchmarks: https://www.revenuecat.com/blog/growth/subscription-app-trends-benchmarks-2026
- Backlash against subscriptions: https://easternherald.com/2026/05/22/plex-750-lifetime-pass-user-revolt-jellyfin/
- S2 revenue, ratings and reviews: Play Console, read 2026-09-25 and 2026-10-06; the full source list is on #380.
