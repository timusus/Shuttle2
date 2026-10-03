# App Store Connect answers (Shuttle Music, app 6818057709)

Entered in App Store Connect on 2026-10-03. Keep this file in step with the portal so the next person
does not have to re-derive the answers. The listing text is in `en-AU/`; review notes and the launch
checklist are in `ios/docs/app-store-review.md`.

## Portal state (2026-10-03)

The primary language is English (U.S.), and the `en-AU/` copy went into that localisation.

- App Information: subtitle, Content Rights, category, age rating (4+) and privacy policy URL are saved.
- App Privacy: "Data Not Collected" is saved but **not published**. The owner publishes it, which is an attestation.
- Pricing: Free, available in all 175 countries.
- In-app purchases: both are in all countries, with en-US name and description and review notes. Neither
  has a review screenshot yet. Family Sharing is off on both.
  - Trial (6818776748): $0.
  - Lifetime (6818777051): $9.99, the launch price. The plan is US$14.99 two to four weeks after launch.
- Version 1.0 has these saved:
  - Promotional text, description and keywords.
  - Support URL, marketing URL and copyright ("2026 Simplecity Apps Pty Ltd").
  - Contact information.
  - The demo account: user `appreview` on https://emby.mediaserver.timmalseed.dev (Emby). The owner enters the
    password; it is never written in the repo.
  - Review notes, describing the Emby demo (`ios/docs/app-store-review.md`).
- Version 1.0 still needs:
  - Screenshots and a build.

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

Privacy policy URL: https://simplecityapps.com/privacy (carries a Shuttle Music (iOS) section).

Answer: **Data Not Collected.** Tracking: No.

Verified 2026-10-03 against the repo:

- `ios/S2/PrivacyInfo.xcprivacy`: `NSPrivacyTracking` false, no tracking domains, empty
  `NSPrivacyCollectedDataTypes`. Required-reason API entries only for UserDefaults (CA92.1), file
  timestamps (C617.1) and system boot time (35F9.1).
- No analytics, crash or ads SDK: `ios/project.yml` has only ViewInspector (tests) and the local
  S2Playback package (FFmpeg); `shared/` has no analytics dependency on iOS (`IosSettingsCatalog.kt`:
  "iOS has no crash reporting or analytics"; `IosPlatformModule.kt`: sign-in analytics is a no-op).
- Server address, username and session token live in the Keychain and go only to the user's own server.
  Apple processes purchases; the developer receives no purchase identifiers from the app.
- ATS allows arbitrary loads (`NSAllowsArbitraryLoads`) so plain-http LAN servers work; that is transport
  to a user-chosen server, not collection by us.

Contradictions found: none. Revisit when StoreKit (#609) lands: if the app calls a backend to validate
purchases, or any analytics or crash SDK is added, "Data Not Collected" must change. A StoreKit-only
integration (on-device verification) keeps it as is.

## In-App Purchases

Both products cover what the app sells (playing files on the device is free and not sold): streaming from the user's own Jellyfin, Emby or Plex server. The
descriptions stay in step with the paywall copy (`ProFeatures` in `ios/S2/Features/Paywall/PaywallView.swift`,
which names all three) and with `docs/product/monetisation.md`'s iOS section (guidelines 2.3 and 3.1.1).

- `com.simplecityapps.shuttle.pro.trial`: display name "7-day Free Trial", description "Stream from
  Jellyfin, Emby and Plex free for 7 days".
- `com.simplecityapps.shuttle.pro.lifetime`: display name "Shuttle Music Pro (Lifetime)", description
  "Stream from Jellyfin, Emby and Plex, for life".
