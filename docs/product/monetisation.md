# Monetisation model for the redesign

Status: recommendation, 2026-09-25. Nothing here is implemented yet.

## 1. Owner decisions this builds on

- The trial-expiry playback-speed penalty goes.
- Chromecast and offline downloads/sync stay free.
- A subscription, with a lifetime purchase as an alternative.
- The remote providers (Jellyfin, Emby, Plex) are the paid feature, probably with a trial.

## 2. The current model, from the code

| Fact | Where |
|---|---|
| Five products. Subscriptions: `s2_subscription_full_version_monthly`, `_yearly` and `_yearly_low`. One-time: `s2_iap_full_version` and `s2_iap_full_version_low` | `android/trial/.../BillingManager.kt` |
| The paywall shows monthly, yearly and lifetime by default. When the Remote Config `pricing_tier` is `low` it shows only `_yearly_low` and `_iap_full_version_low` | `android/app/.../ui/screens/trial/PurchaseDialogFragment.kt` |
| The trial is 14 days: Remote Config `trial_length` defaults to 14 and `pre_trial_length` to 0. It counts from the first-seen date, keyed on the device's ANDROID_ID and stored in `filesDir/device` and on `api.shuttlemusicplayer.app/v1/devices/{id}`, so reinstalling doesn't reset it | `TrialManager.kt`, `RemoteConfigModule.kt`, `TrialModule.kt` |
| Nothing is feature-gated. After the trial, playback speed rises 2% a day up to 1.5×, and the trial dialog appears daily (every 4 days during the trial) | `TrialState.kt`, `appinitializers/TrialInitializer.kt`, `MainPresenter.kt` |
| A user counts as a purchaser if `queryPurchasesAsync` (INAPP + SUBS) returns any of the five SKUs. There is no account and no server-side check, and debug builds are always Paid. `purchaseState` is never checked, so a PENDING purchase unlocks the app | `BillingManager.processPurchases` |
| Promo codes are a hidden flow: an email address goes to `v1/promo_code` and the result is shown in a dialog | `PromoCodeService.kt`, `LibraryFragment.kt`, `QueueFragment.kt` |
| Play Billing Library 8.0.0 | `gradle/libs.versions.toml` |

## 3. S2's real numbers (Play Console, Play Developer API, Firebase; read 2026-09-25)

- **Live prices.** The Play Developer API returns these. Remote Config serves `pricing_tier = low` to all users, so the paywall shows only the second and third rows. Despite its name, `_low` is the *higher* lifetime price.

  | Product | US | AU | UK | DE | IN |
  |---|---|---|---|---|---|
  | `s2_iap_full_version` (hidden) | $3.49 | A$4.99 | £3.19 | €3.69 | ₹320 |
  | `s2_iap_full_version_low` (shown) | **$7.99** | A$8.99 | £5.99 | €6.99 | ₹260 |
  | `…_yearly_low` (shown) | **$1.99/yr** | A$2.99 | £1.49 | €1.99 | ₹95 |
  | `…_monthly` (hidden) | $0.99/mo | A$0.99 | £0.69 | €0.50 | ₹29 |

- **Revenue.** Last 12 months (2025-09-20 to 2026-09-20), gross including tax: **A$4,145**. That is 396 lifetime orders (A$3,860, two refunds) and 107 annual-subscription orders (A$285). The lifetime product earns 93% of revenue and takes 79% of orders, even though the annual plan costs a quarter as much. Lifetime revenue to date is A$21,917.
- **Trend.** Monthly revenue fell from A$450–570 (Sep–Dec 2025) to A$200–240 (Jun–Aug 2026). The last 30 days brought A$102, down 60%, and **the 7 days to 20 Sep had no orders at all**. Check that billing isn't broken before reading anything else into this.
- **Users.** Installed audience is 5.41k and roughly flat over the year (5.4k–6.15k). About 2.4k new users arrived in the last 30 days, and Play shows 163k lifetime installs. Only 15 orders came from those 2.4k users, about 0.6%.
- **Ratings.** The Play rating is 3.83 from 1,142 ratings, 478 of them with text. The last 28 days average 3.33. Recent written reviews are few (the Reviews-analysis benchmarks count a single review per topic), and I found no recent review mentioning the paywall or the speed penalty. One review says the trial is 2 weeks, "but for only 3 euro the whole app".
- **Firebase.** 691 users fetched Remote Config in the last 24 hours, which suggests about 13% of the installed audience opts in to analytics. The app logs no custom events, so **no data exists on local vs Jellyfin/Emby/Plex usage**. There are two completed A/B tests. "S2 Pricing": variant A beat the baseline by 361%. I assume, but did not verify, that variant A is the current `low` tier ($7.99 lifetime). "Trial Length": no variant beat the baseline.

## 4. Market

| App | Model | Price (USD) |
|---|---|---|
| Symfonium (the direct Jellyfin/Emby/Plex/Subsonic rival) | Trial, then a one-time unlock of everything | ~$5.99 |
| Plexamp / Plex Pass | Streaming is free; the Pass adds downloads, EQ and lyrics | $6.99/mo, $69.99/yr, lifetime $749.99 (was $119.99) |
| Poweramp | 15-day trial, then a one-time unlocker | $19.99 (often on sale for $2–7) |
| Neutron / BlackPlayer EX / GoneMAD / Musicolet | Paid up front, or a one-time unlock | $12.99 / $3.49 / ~$7.99 / ~$4–6 |
| Finamp, Jellify, Substreamer, Tempus | Free, mostly open source | $0 |

- No dedicated Android player I checked sells a subscription. Plex charges for a server ecosystem, and Plexamp's music streaming stays free.
- Self-hosters are allergic to subscriptions. Plex's lifetime price went from $119.99 to $749.99 and drew a revolt, and Jellyfin replied with a "$0 price increase" post. Symfonium's reviews praise its one-time fee.
- RevenueCat 2026: hard paywalls convert 10.7% of downloads by day 35, against 2.1% for freemium. Trials of 17–32 days convert 42.5%, against 25.5% for trials under 4 days. 35% of subscription apps also sell a lifetime or consumable. Google Play earns less per install than iOS, and billing failures cause 31% of its churn.

## 5. Recommendation

**Model: local playback is free for good; "S2 Pro" unlocks remote servers. Pro is sold as an annual subscription, a monthly subscription, or a lifetime purchase.** The lifetime option leads, because S2's own buyers pick it 4:1 and the market expects it. The subscription is there for people who would rather pay less up front.

### Free vs Pro

| Free, forever | Pro |
|---|---|
| All local playback, library, playlists, tag editor, EQ, replay gain, sleep timer, Android Auto, widgets | Streaming from Jellyfin, Emby and Plex (all three; there's no per-server pricing) |
| Chromecast, for local and remote | Adding and syncing more than zero remote servers after the trial |
| Downloaded remote songs keep playing after a trial or subscription ends (they are on the device) | New downloads from a server (this needs server access, which is Pro) |
| Themes | Optional supporter perks: extra themes, accent colours, app icons. They are cheap to build and give local-only users a reason to pay |

The owner kept downloads free, but downloading from a server needs server access. My reading is that downloads are never a separate upsell and never disappear, rather than that free users can download from a server. Confirm this.

### Prices (US anchor; use Play's local-currency pricing templates)

| Product | Price | Why |
|---|---|---|
| Lifetime | **$14.99** | $7.99 sells with almost no price resistance (the A/B test above), and Poweramp charges $19.99. Symfonium's ~$6 caps what streaming-only users will accept, so don't go above $19.99. |
| Annual | **$5.99/yr** | Cheap enough not to feel like "a subscription tax". Lifetime is 2.5× the annual price, the usual ratio. |
| Monthly | **$1.49/mo** | For people who want to try it for a month. Twelve months cost $17.88, so annual looks like the better deal. |

Run Play Console price experiments on lifetime ($9.99 / $14.99 / $19.99) for the first 8–12 weeks. At about 500 orders a year that is statistically thin, so judge on revenue per paywall view, not on conversion.

### Trial mechanics

- **A 14-day remote trial, with no card, starting the first time a server is connected.** It is app-side and deliberately not a Play free-trial offer: a card-required Play trial would put off exactly the self-hosters who arrive to evaluate a Jellyfin client, and it can't cover the lifetime product. S2's own A/B test found no trial length that beat 14 days.
- Record the trial start on the existing device backend (`DeviceService`, keyed on ANDROID_ID) as a new `remoteTrialStartedAt` field, so reinstalling doesn't reset it. This is a backend change.
- **At expiry**, remote libraries stay visible and browsable, with a small lock. Tapping play on a remote song opens the paywall sheet, and remote items already in the queue are skipped with a snackbar. Local playback, settings and downloaded songs are untouched. There are no nag dialogs, no speed change, and nothing reduced in quality.
- **At cutover**, every existing non-paying user gets one fresh 14-day remote trial when they update. Announce it in the changelog. This converts the people who have been tolerating the speed penalty rather than cutting them off overnight.

### Grandfathering

- Any of the five legacy SKUs grants Pro, whichever one the user has. Lifetime owners (about 400 in the last year) have Pro forever. Legacy annual subscribers ($1.99/yr) have Pro for as long as they keep renewing; leave their base plans active and never migrate their price.
- Stop offering the legacy products, but keep them in the entitlement set indefinitely.
- Thank existing buyers once, with a message along the lines of "You already own S2 Pro". Many of them are local-only users who paid to avoid the speed penalty; they lose nothing.

### Paywall placement

1. **Add-server screen**, before connecting: "Streaming from Jellyfin, Emby and Plex is part of S2 Pro. Free for 14 days." This is the disclosure that keeps the listing honest.
2. **Trial end**, when the user taps play on a remote song. This is the highest-intent moment.
3. **Settings → S2 Pro**: status, restore purchases, promo code (make it visible and drop the five-tap easter egg), and manage subscription (a deep link to Play).
4. **A trial chip** in the Library top bar for the last 3 days only. Replaces today's always-visible ring and daily dialog.

Show one sheet with three plan cards: Lifetime (preselected, marked "Best value"), Annual, Monthly. Put the price plus a one-line summary of Pro on each card.

### Play listing wording risk

The listing's second line is "Stream your music via Jellyfin, Emby or Plex" and it never says the feature is paid. Once streaming is gated, reviewers will call that bait-and-switch, and Play's misleading-claims policy expects paid features to be disclosed. Before the release ships:

- Change the short and long descriptions to "Free local music player. Stream from Jellyfin, Emby and Plex with S2 Pro (14-day free trial)."
- Keep "Free" next to local, Chromecast and Android Auto.
- Add a "What's free / what's Pro" block to the description.

Also expect the "Shuttle+ was abandoned" crowd to reappear in reviews. Draft replies in advance that point to the free local tier.

### Play Billing implementation notes

- **Subscription.** One new subscription product, `s2_pro`, with two auto-renewing base plans: `annual` (P1Y) and `monthly` (P1M). Add no Play free-trial offer at launch, because the trial is app-side. Offers can come later, for example a win-back for lapsed subscribers or a first-year intro price, both targeted with offer tags.
- **Lifetime.** A new one-time product, `s2_pro_lifetime`, with a single "buy" purchase option. Build it with the new one-time-products API; the old `inappproducts` API now returns "migrate to the new publishing API".
- **Entitlement.** Pro means any PURCHASED (not PENDING) purchase among `s2_pro`, `s2_pro_lifetime` and the five legacy SKUs.
  - Query INAPP and SUBS on start and on every foreground, and handle `onPurchasesUpdated`.
  - Acknowledge within 3 days; the current code already does this.
  - Cache the last-known entitlement with a timestamp, and fail open for 7 days when Play is unreachable. Streaming needs the network anyway.
- **Architecture.** Replace `TrialState` and `BillingState` with one `EntitlementRepository` that exposes `StateFlow<Entitlement>` (Free, RemoteTrial(daysLeft), RemoteTrialExpired, Pro(source)).
  - Only the remote-provider playback path and the add-server flow read it. Playback speed never does.
  - Delete `TrialInitializer`'s speed code, `TrialState.Expired.multiplier`, the `pricing_tier` Remote Config key and the `_low` switch.
- **Server-side verification** isn't needed at this scale. If fraud shows up, verify through the existing `api.shuttlemusicplayer.app` backend using the Play Developer API and Real-time Developer Notifications.
- **Bug to fix in any case:** `processPurchases` ignores `purchaseState`, so a PENDING purchase unlocks the app today.

## 6. Measuring it

- **Instrument first.** Add Firebase events: `provider_connected{type}`, `remote_trial_started`, `paywall_viewed{source}`, `purchase_started/completed{product}` and `remote_trial_expired`. Add a user property `media_sources` (local / jellyfin / emby / plex, multi-valued).
  - Ship these **one release before** the paywall, so the share of remote users is known before gating starts.
  - Analytics is opt-in (about 13% of users), so read ratios, not absolute counts.
- **KPIs, from Play Console → Buyer conversion, Revenue and Subscriptions → Retention/Cancellations:**
  - Trial-to-paid (target: at least 15% of remote trials)
  - Paywall-view-to-purchase
  - Monthly gross revenue against the 2026 run-rate of about A$220/month
  - Mix of lifetime vs subscription
  - Annual renewal rate at month 12
- **Guardrails:**
  - The 28-day rating stays at or above 3.3 and ideally recovers, now that the speed-penalty complaints are gone.
  - Installed audience and the uninstall rate stay flat.
  - Refund rate stays under 5%.
- **Review at 30 and 90 days.** If trial-to-paid is under 8%, test a 30-day trial before lowering prices. If lifetime takes more than 90% of revenue, the subscription is noise and can be simplified away.

## 7. Assumptions and open questions

1. **Share of remote users is unknown.** If most of today's buyers are local-only, free local playback could cut revenue. The supporter perks and the one-release instrumentation lead time are the hedges.
2. **Which variant won "S2 Pricing" isn't confirmed.** Check the A/B test's detail page.
3. **Zero orders since about 13 Sep 2026.** Verify billing on a release build before attributing any drop to the new model.
4. **Revenue scale.** At about 500 orders a year, price matters less than volume. The larger lever is a better rating and a free local tier that brings users in.
5. **Downloads.** How "downloads stay free" applies to servers that need Pro needs the owner's confirmation (section 5).

## Sources

- Symfonium pricing: https://support.symfonium.app/t/application-pricing/8192 , https://play.google.com/store/apps/details?id=app.symfonik.music.player
- Plex price changes: https://9to5mac.com/2025/03/19/plex-price-increase-remote-streaming-changes/ , https://linuxiac.com/plex-lifetime-pass-jumps-to-750-jellyfin-responds-with-0-price-increase/ , https://forums.plex.tv/t/plexamp-and-the-plexpass-requirement-for-remote-streaming/916078
- Poweramp: https://play.google.com/store/apps/details?id=com.maxmpz.audioplayer.unlock
- Neutron, BlackPlayer EX, GoneMAD, Musicolet: https://play.google.com/store/apps/details?id=com.neutroncode.mp , https://play.google.com/store/apps/details?id=com.kodarkooperativet.blackplayerex , https://play.google.com/store/apps/details?id=gonemad.gmmp , https://play.google.com/store/apps/details?id=in.krosbits.musicolet
- Free Jellyfin/Subsonic clients: https://github.com/jmshrv/finamp , https://github.com/Jellify-Music/App , https://substreamer.org/ , https://f-droid.org/en/packages/com.eddyizm.degoogled.tempus/
- RevenueCat benchmarks: https://www.revenuecat.com/blog/growth/subscription-app-trends-benchmarks-2026 , https://www.revenuecat.com/state-of-subscription-apps-2025
- Backlash against moving to subscriptions: https://easternherald.com/2026/05/22/plex-750-lifetime-pass-user-revolt-jellyfin/ , https://talk.macpowerusers.com/t/a-sad-goodbye-to-fantastical/36667 , https://www.indiehackers.com/post/subscriptions-vs-one-time-payments-a-developers-honest-take-f153e48960
- S2 listing: https://play.google.com/store/apps/details?id=com.simplecityapps.shuttle
- S2 revenue, ratings, statistics and Remote Config/A/B data: Play Console and Firebase console, read 2026-09-25.
