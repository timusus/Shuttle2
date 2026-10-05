# Monetisation

Status: strategy settled 2026-10-06 (owner decisions on #380, which replace the 2026-09-25 ones). The entitlement layer
(`:android:trial`) and the server gate are implemented; widening Pro to the bundle below, and the paywall changes it
needs, are separate work.

## Strategy

**A free core plus a Pro bundle, with a 14-day trial that starts on first use of any Pro feature.** The pitch is
offline and car, set against Plex Pass and Emby Premiere.

| Free, forever | Pro (one bundle) |
|---|---|
| Local playback, library, playlists, basic EQ, sleep timer, single-song tag edits, Chromecast, widgets | Jellyfin, Emby, Plex and Subsonic servers, and their offline downloads (no per-server pricing) |
| Songs already downloaded keep playing after a trial or subscription ends | Android Auto |
| | Batch tag editing |
| | Advanced audio: parametric/AutoEq EQ, replay-gain modes |

- **Android pricing:** $9.99 lifetime is the main purchase (`s2_pro_lifetime`). Annual (`s2_pro`, base plan `annual`,
  $3.99) is a quiet secondary option, retired at 90 days if it stays under 10% of revenue. No monthly plan.
- **iOS pricing:** $19.99 lifetime, $14.99 in the launch month, 14-day trial. iOS self-hosters pay two to three times
  Android prices, and $19.99 stays under Narjo's $29.99. Purchases are per store: no cross-platform unlock.
- **Trial:** 14 days, no card, app-side (not a Play trial offer), starting on first use of any Pro feature: first
  server, first Android Auto connection, first batch edit (extends #488). The use is disclosed at that moment. Current
  non-payers get one fresh trial at cutover, announced in the changelog.
- **Disclosure:** the first lines of the listing and the paywall say "free for local files; Pro is a one-time unlock
  with a 14-day trial". The listing has a "What's free / what's Pro" block. Play's misleading-claims policy expects paid
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
paid player sampled), and confirm the annual purchase works. The payments account notice (#372) made subscriptions
unbuyable from January, and no orders have come in since 13 September (#230).

## Evidence

- **Most revenue is likely local-only users.** 21 of 24 "I paid" reviews (of 262) never mention a server, so about
  70-85% of the ~A$220/month probably comes from local users. Making local free outright put most of it at risk.
- **Users pay a one-time unlock after an honest trial.** Symfonium is one-time $5-7, 4.65 stars and growing about 6% a
  month in ratings. Poweramp and Neutron sell to about 7% of installs, free-plus-extras apps (Oto, Pulsar) to about 3%.
- **Subscriptions do badly here:** Plexamp's paywall reviews average 2.9 stars, and no dedicated Android player checked
  sells one. Ads hurt: GoneMAD lost about 0.6 stars. Prices cluster at $5-10 with about $20 as the ceiling.
- **Shuttle's 1-star reviews are about the hidden trial and the speed ramp, not about paying.**
- **Annual's 7% order share understates demand.** The plan was unbuyable from January (#372). The 2021 "S2 Pricing"
  A/B test says nothing about today's prices.
- **S2's numbers (Play Console, last 12 months to 2026-09-20):** A$4,145 gross; 396 lifetime orders (93% of revenue,
  79% of orders) and 107 annual. Monthly revenue fell from A$450-570 (late 2025) to A$200-240 (mid 2026). Rating 3.83
  from 1,142 ratings (3.33 over the last 28 days). 5.4k installed audience, 163k lifetime installs.
- **Market:** Plex's lifetime pass went from $249.99 to $749.99 in July 2026 and drew a revolt; Plexamp and Finamp
  stream for free and offline is what Plex and Emby charge for. RevenueCat 2026: trials of 17-32 days convert 42.5%
  against 25.5% for under 4 days; Play earns less per install than iOS, and billing failures cause 31% of its churn.

## Current model, from the code

- Five legacy products: subscriptions `s2_subscription_full_version_monthly`, `_yearly`, `_yearly_low`; one-time
  `s2_iap_full_version` and `s2_iap_full_version_low` (`android/trial/.../BillingManager.kt`). Despite the name, `_low`
  is the higher lifetime price ($7.99 against $3.49).
- `EntitlementRepository` exposes `StateFlow<Entitlement>` (Free, Trial, Pro(source), Unknown). Pro is any PURCHASED
  (not PENDING) purchase among the new and legacy SKUs; INAPP and SUBS are queried on start and foreground, and the
  last-known Pro is cached and fails open for 7 days when Play is unreachable. Playback speed never reads it.
- Server-side verification isn't needed at this scale; if fraud shows up, use the existing `api.shuttlemusicplayer.app`
  backend with the Play Developer API and Real-time Developer Notifications.

## Play Console setup (owner)

1. Create subscription `s2_pro` with one base plan, `annual` (P1Y), at $3.99. No monthly plan, no Play trial offer.
2. Create one-time product `s2_pro_lifetime` at $9.99 through the one-time-products API (the old `inappproducts` API
   is deprecated). Regional prices set manually: lifetime A$14.99 / £8.99 / €9.99, annual A$5.99 / £3.49 / €3.99;
   keep the existing manual emerging-market prices (₹260 lifetime) rather than auto-conversion.
3. Launch sale: a one-time-product discount to $7.99 for 30 days (not a lower base price). Afterwards, run a price
   experiment at $9.99 / $12.99 / $14.99 and judge it on revenue per paywall view, since ~500 orders a year is thin.
4. Leave the five legacy products' base plans and prices untouched.

## iOS (StoreKit 2, #609)

Pro is servers today: Jellyfin, Emby and Plex streaming need Pro after a 14-day trial. Android Auto, downloads and
batch edits join Pro when iOS has them, and not before: the paywall, Settings and App Store Connect descriptions name
only what the app does (guidelines 2.3 and 3.1.1), from one place in Swift (`ProFeatures` in `PaywallView.swift`).
There is no subscription on iOS, only the trial and Lifetime. The trial length is `AppStoreProducts.TRIAL_DAYS` in
`:shared`.

- **Products** (ids in `shared/.../entitlement/AppStoreProducts.kt`): `com.simplecityapps.shuttle.pro.trial`, a free
  non-consumable whose purchase starts the trial, running 14 days from the transaction's `originalPurchaseDate` (a
  restore, reinstall or new device reports the same date, so none restarts it; a refunded trial counts as used); and
  `com.simplecityapps.shuttle.pro.lifetime` ($19.99 in `S2.storekit`, $14.99 launch month set in App Store
  Connect; a refund removes Pro).
- **Trial consent.** App Review wants a knowing start, so on iOS the first server stream before the trial is refused
  and opens the paywall, which discloses the length, what stops after it and Lifetime's localized `displayPrice` above
  "Start 14-day free trial". Adding a server stays allowed until the trial has been used.
- **Entitlement.** `StoreKitManager` (Swift) reads `Transaction.latest(for:)` at launch, after each purchase or
  restore and on every `Transaction.updates`, and hands verified transactions to `StoreEntitlements` (Kotlin), which
  resolves Pro > Trial > Free > Unknown with the same `resolveEntitlement` as Android. While Unknown, a server stream
  waits up to 5 seconds, then is refused as undecided (no paywall, played again when asked). A queue restored at
  launch never opens the paywall. Restore is `AppStore.sync()`.
- **Paywall:** opened by a refused action and from Settings (Pro row, Restore Purchases); links the Privacy Policy and
  Apple's standard EULA.
- **Testing:** the S2 scheme runs with `ios/S2.storekit`. Debug builds resolve Pro unless Settings' "Debug
  entitlement" picker says otherwise; "App Store" resolves from StoreKit as a release build does.
- **Not on iOS:** PostHog monetisation events, account claiming and `appAccountToken`.

## Measuring it

- **Events** (PostHog, `MonetisationAnalytics`): `server_connected{type}`, `trial_started`, `paywall_shown{source}`,
  `paywall_dismissed{source}`, `purchase_started/completed/failed/restored{product}`, plus two that say who buyers are
  (Android; iOS has no caller yet):
  - `media_sources`, a property on every event: configured source kinds, sorted and comma-separated
    (`jellyfin,local,plex`; `none` with no source).
  - `entitlement_resolved{source}`, once per install, once the entitlement first resolves and an enabled analytics
    backend took the event (while opted out it is offered again next launch, recording the entitlement at that later launch): `none`, `trial`, `pro`, `legacy` or
    `debug`. In the first week, the legacy ones show what share of existing buyers are local-only.
  - Ship these with or before the gating release. Analytics is opt-in (about 13% of users): read ratios, not counts.
- **KPIs** (Play Console): trial-to-paid (target at least 15%), paywall-view-to-purchase, monthly gross against the
  ~A$220 baseline, lifetime vs annual mix, annual renewal at month 12.
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
