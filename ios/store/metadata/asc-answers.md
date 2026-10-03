# App Store Connect answers (Shuttle Music, app 6818057709)

Drafted 2026-10-03, not yet entered in App Store Connect. Once pasted, add the date here and keep this
file in step with the portal so the next person does not have to re-derive the answers. The listing text
is in `en-AU/`; review notes and the launch checklist are in `ios/docs/app-store-review.md`.

## App Information

- Content Rights: "No, it does not contain, show, or access third-party content." The app ships no
  content and has no catalogue of its own: it plays files from the media server the user runs and signs in
  to. If App Review pushes back, the fallback is "Yes ... and I have the necessary rights", as Shuttle
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
