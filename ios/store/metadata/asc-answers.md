# App Store Connect answers (Shuttle Music, app 6818057709)

Entered in App Store Connect on 2026-10-03. Keep this file in step with the portal so the next person
does not have to re-derive the answers. The listing text is in `en-AU/`; review notes and the launch
checklist are in `ios/docs/app-store-review.md`.

## Portal state (2026-10-03)

The primary language is English (U.S.), and the `en-AU/` copy went into that localisation.

- App Information: subtitle, Content Rights, category, age rating (4+) and privacy policy URL are saved.
- App Privacy: the portal still holds the earlier "Data Not Collected" (saved, never published). The answers
  below (#776, Sentry and PostHog) are **not yet entered**; the owner enters and publishes them, which is an
  attestation.
- Pricing: Free, available in all 175 countries.
- In-app purchases: both are in all countries, with en-US name and description and review notes. Each
  carries the 7-day paywall (`iap-review/paywall.png`) as its review screenshot.
  - Trial (6818776748): $0, "7-day Free Trial". Family Sharing off.
  - Lifetime (6818777051): $9.99, the launch price. The plan is US$14.99 two to four weeks after launch
    (#762). Family Sharing is on, which can't be turned off.
- Version 1.0 has these saved:
  - Promotional text, description and keywords.
  - Support URL, marketing URL and copyright ("2026 Simplecity Apps Pty Ltd").
  - Contact information.
  - The demo account: user `appreview` on https://emby.mediaserver.timmalseed.dev (Emby). The owner enters the
    password; it is never written in the repo.
  - Review notes, describing the Emby demo (`ios/docs/app-store-review.md`), with the FFmpeg source URL.
  - Screenshots: six each for iPhone 6.5" (used for every iPhone size) and iPad 13".
- Version 1.0 still needs a build.

## App Information

- Content Rights: "No, it does not contain, show, or access third-party content." The app ships no
  content and has no catalogue of its own: it plays audio files the user puts on the device, or from the
  media server the user runs and signs in to. If App Review pushes back, the fallback is "Yes ... and I have the necessary rights", as Shuttle
  Podcasts did; the owner's call.
- Categories: primary Music, secondary none.
- Age rating questionnaire (all "None" / "No"; calculated 4+):
  - In-app controls: parental controls No, age assurance No.
  - Capabilities: unrestricted web access No (no browser or web view), user-generated content No,
    social media No, messaging No, advertising No.
  - Mature themes: profanity None, horror/fear None, alcohol/tobacco/drugs None.
  - Medical/wellness: None / No.
  - Sexuality: mature or suggestive themes None, sexual content None, graphic None.
  - Violence: cartoon None, realistic None, prolonged graphic None, guns None.
  - Chance-based: all None / No.
  - No age override, no age suitability URL.
  - Reasoning: the app has no content of its own. What a user plays is their own library, which they
    chose and control. Answering "None" throughout matches how other server-client music apps rate.

## App Privacy

Privacy policy URL: https://simplecityapps.com/privacy (carries a Shuttle Music (iOS) section; it must
describe the crash reports and usage analytics below before submission).

Not yet entered in the portal (#776). Tracking: **No**. Every type is **Not Linked to You** and not used
for tracking:

| Data type | Purpose | Source |
|---|---|---|
| Usage Data > Product Interaction | Analytics | PostHog: paywall, purchase, sign-in and onboarding events |
| Usage Data > Other Usage Data | Analytics | PostHog: the app lifecycle events the SDK sends itself (app opened/backgrounded) |
| Purchases > Purchase History | Analytics | PostHog: `purchase_started`/`_completed`/`_failed`/`_restored` with the product and a bucketed reason |
| Identifiers > Device ID | Analytics | PostHog's random install id; declared conservatively, never the IDFA or IDFV |
| Diagnostics > Crash Data | App Functionality | Sentry crash reports, including uncaught Kotlin exceptions |
| Diagnostics > Performance Data | App Functionality | Sentry app hangs (2 s) and app start traces (5%) |
| Diagnostics > Other Diagnostic Data | App Functionality | Sentry sessions and scrubbed warning/error breadcrumbs |

Both are on by default and each turns off in Settings > Privacy; the first run's welcome says so.

- **PostHog:** the EU cloud, client IP discarded (project setting), no person profiles (`identified_only`,
  `identify` never called). Screen views, autocapture, session replay, surveys and feature flags are off.
  Events carry enums and buckets only: never titles, artists, server addresses, usernames or search text.
- **Sentry** (`s2-ios`): `sendDefaultPii` off; no screenshots, view hierarchy, network breadcrumbs or
  failed-request capture; every message, breadcrumb and extra goes through the shared `TelemetryScrubber`
  (URLs, hosts, IPs, file paths, emails, `user=`/`token=` values) before it leaves the device.
- `ios/S2/PrivacyInfo.xcprivacy` declares the same seven types (Linked and Tracking false),
  `NSPrivacyTracking` false and no tracking domains. Both SDKs link statically; their own manifests'
  required-reason APIs (UserDefaults CA92.1, file timestamps C617.1, system boot time 35F9.1) are the ones
  the app already declares. Other Usage Data is declared to match PostHog's own manifest: its lifecycle
  events (app opened/backgrounded) are usage data the SDK collects by itself.
- No ATT prompt, no IDFA, no ads SDK. Server address, username and session token live in the Keychain and go
  only to the user's own server. Apple processes purchases; StoreKit verifies them on the device.
- ATS allows arbitrary loads (`NSAllowsArbitraryLoads`) so plain-http LAN servers work; that is transport
  to a user-chosen server, not collection by us.

## In-App Purchases

Both products cover what the app sells (playing files on the device is free and not sold): streaming from the user's own Jellyfin, Emby or Plex server. The
descriptions stay in step with the paywall copy (`ProFeatures` in `ios/S2/Features/Paywall/PaywallView.swift`,
which names all three) and with `docs/product/monetisation.md`'s iOS section (guidelines 2.3 and 3.1.1).

- `com.simplecityapps.shuttle.pro.trial`: display name "7-day Free Trial", description "Stream from
  Jellyfin, Emby and Plex free for 7 days".
- `com.simplecityapps.shuttle.pro.lifetime`: display name "Shuttle Music Pro (Lifetime)", description
  "Stream from Jellyfin, Emby and Plex, for life".
