# Off-store outreach drafts (2026-11-03)

Drafts only; nothing is posted. Every claim holds for the production build 1.0.10: Jellyfin, Emby and Plex streaming;
FLAC and Opus decoders; 10-band EQ; ReplayGain; Android Auto; Chromecast; widgets; sleep timer; batch tag editor; smart
playlists; M3U import. Not in 1.0.10, so never claimed: Subsonic/Navidrome, server downloads, lyrics, scrobbling,
crossfade. Paid app with a 14-day free trial. Source: this repo, Apache 2.0.

URLs below were fetched on 2026-11-03 unless marked "unverified". Facts used by all drafts:

- Play: https://play.google.com/store/apps/details?id=com.simplecityapps.shuttle (unverified, built from the package id)
- Site: https://shuttlemusicplayer.com (unverified, from the brief)
- Source: this repository (public URL unverified; fill in before sending)

Shared description (about 400 characters):

> Shuttle Music is an Android music player for local files and for Jellyfin, Emby and Plex libraries. It has FLAC and
> Opus playback, a 10-band equaliser, ReplayGain, Android Auto, Chromecast, home-screen widgets, a sleep timer, a
> batch tag editor, smart playlists and M3U import. Paid, with a 14-day free trial. Source is Apache 2.0.

## 1. Jellyfin official clients list

- URL: https://jellyfin.org/downloads/clients (fetched).
- Finding: every entry is tagged "Official", and the page describes no submission route. The two Android entries are
  Jellyfin for Android and Android TV. Source repo `jellyfin/jellyfin.org`: its CONTRIBUTING.md returned 404, so
  contribution rules are "check rules".
- Verdict: blocked. It is the official clients page, so a third-party app should not be proposed there. Do not open a
  PR. The routes that fit are section 6 (awesome-jellyfin) and the Reddit templates.
- If the owner still wants to ask, a question (not a PR) in the Jellyfin forum or the repo's discussions is the safe
  form:

> Title: Is there a place for third-party clients on jellyfin.org?
>
> I make Shuttle Music, a paid Android music player (14-day free trial) with Jellyfin, Emby and Plex support; the source
> is Apache 2.0. The clients page lists only official clients. Is there a page or process for third-party clients, or is
> awesome-jellyfin the right place? Thanks.

## 2. Plex forum

- URL: https://forums.plex.tv/t/symfonium-music-player-and-cast-with-plex-support/792644 (fetched). It sits in
  category Apps & Creations, under Dev Community, tagged `android-mobile-legacy`, `chromecast`, `other-third-party`,
  `other-dev`. Category URL and forum rules are unverified: check rules before posting.
- Submit: a new topic in Apps & Creations by the owner's Plex forum account. A form or approval step is unknown.
- Blocker: a forum account, and agreeing the rules of that category. Nothing else.

Title: `Shuttle Music: an Android music player for Plex, Jellyfin and Emby libraries`

> Hi, I'm the developer of Shuttle Music, a paid Android music player (14-day free trial). I make this app on my own.
>
> It plays your Plex music library alongside local files, and also works with Jellyfin and Emby.
>
> - FLAC and Opus playback
> - 10-band equaliser and ReplayGain
> - Android Auto and Chromecast
> - Home-screen widgets and a sleep timer
> - Batch tag editor, smart playlists and M3U import
>
> It does not have lyrics, scrobbling, crossfade or downloads from the server yet.
>
> Play Store: https://play.google.com/store/apps/details?id=com.simplecityapps.shuttle
> Site: https://shuttlemusicplayer.com
> Source (Apache 2.0): https://github.com/timusus/Shuttle2
>
> I'd like to hear what your library needs that this doesn't do. Replies here are the quickest way to reach me.

## 3. Emby

- URL: https://emby.media/community/forum/96-third-party-apps/ (fetched). No pinned posting rules were visible; it
  links "Forum Rules & Info" (not fetched): check rules.
- Submit: a new topic in the Third Party Apps forum. Recent titles follow "AppName + separator + short description".
  A comparable announcement (https://emby.media/community/topic/148035-introducing-traxx-an-android-music-app-built-for-emby-music-libraries/,
  fetched) is about 250 words: personal intro, feature bullets, status, screenshots, an invitation to reply.
- Blocker: an Emby community account.

Title: `Shuttle Music: an Android music player for Emby, Jellyfin and Plex`

> Hi, I'm the developer of Shuttle Music, an Android music player. I make this app myself and I'm posting it here in
> case it is useful to Emby users who listen on their phones.
>
> It streams your Emby music library and also plays local files, with Jellyfin and Plex supported too.
>
> - FLAC and Opus playback
> - 10-band equaliser and ReplayGain
> - Android Auto and Chromecast
> - Widgets and a sleep timer
> - Batch tag editor, smart playlists and M3U import
>
> It is a paid app with a 14-day free trial, and the source is Apache 2.0. Not available yet: lyrics, scrobbling,
> crossfade, and downloads from the server.
>
> Play Store: https://play.google.com/store/apps/details?id=com.simplecityapps.shuttle
> Site: https://shuttlemusicplayer.com
>
> Feedback on how it behaves with your Emby library is welcome.

## 4. AlternativeTo

- URL: https://alternativeto.net/manage-item/ (fetched: only a sign-in page, so form fields and description limit are
  unknown). Check rules after signing in; the suggestions below keep the description short.
- Submit: sign in, then "Add application" form. Whether an owner-submitted app needs review is unknown.
- Blocker: an AlternativeTo account; the form's exact limit.

| Field | Value |
|---|---|
| Name | Shuttle Music |
| Short description (under 150 characters) | Android music player for local files and Jellyfin, Emby and Plex libraries, with equaliser, ReplayGain and Android Auto. |
| Description | The shared description above |
| Platforms | Android (the iOS app is not in production 1.0.10; omit) |
| Licence | Open source (Apache 2.0); price model: paid, 14-day free trial |
| Tags | music player, jellyfin, emby, plex, equalizer, replaygain, android auto, chromecast, flac, local music |
| Website | https://shuttlemusicplayer.com |
| Disclosure | State "I'm the developer" in the first comment if the form allows one |

"Alternative to" suggestions (factual; only list apps it genuinely overlaps, chosen by the owner, no ranking words):

- Plexamp: also plays a Plex music library.
- Finamp: also plays a Jellyfin music library (listed with Android and music tags in awesome-jellyfin).
- Symfonium: also plays Jellyfin, Emby and Plex libraries (per its forum threads).

## 5. F-Droid and IzzyOnDroid

Facts from the repo:

- At `2c586573c` (`android/app/build.gradle.kts`, `gradle/libs.versions.toml`): the Crashlytics plugin, the
  `google-services` plugin, `firebase-bom`, `firebase-crashlytics`, Firebase Analytics and Config entries,
  `billing-ktx` 8.0.0, Play Review API (`com.google.android.play:review`), and Cast `play-services-cast-framework`.
- At main: no Firebase. Crash reporting is Sentry (`sentry-android`) and analytics is PostHog (`posthog-android`),
  both with keys from `local.properties`. Still present: Play Review (`libs.google.review`), Cast
  (`play-services-cast-framework`, also `media3-cast`), and Play Billing `billing-ktx` 9.1.0 in
  `android/trial/build.gradle`.

**F-Droid** (https://f-droid.org/docs/Inclusion_Policy/, fetched): the policy forbids proprietary tracking or
advertising libraries and analytics, names Google Play Services, Firebase and Crashlytics, and wants "a FLOSS
alternative or a build flavour that doesn't require these". Apps that need Play Services to build are rejected. The
page does not mention billing, but Play Billing is a proprietary Google library with no FLOSS build.
**Verdict: no, would not be accepted as-is** (at either commit). Sentry and PostHog are SDKs sending data to a
service; whether they count as the forbidden "analytics" is for the reviewers, and at minimum they would need to be
off by default or removed.

**IzzyOnDroid** (https://izzyondroid.org/docs/general/AppInclusionPolicy/ appeared in search; the page itself was not
fetched; the GitLab README is archived and points to a wiki that did not resolve): per the search summary it tolerates
few proprietary components and no ad or analytics trackers. **Verdict: no, not as-is**, for the same reason
(unverified policy details). A second blocker: it is a paid app with a trial gate. A flavour has to be fully usable
without Play Billing, so "paid" would need a different model there (for example, donations), which is the owner's
call.

Flavour needed for either (`foss`): drop `:android:trial` and Play Billing (the entitlement gate becomes always
unlocked), drop Play Review, drop Sentry and PostHog (or make them build-time no-ops), and replace `play-services-cast`
(the Cast framework is proprietary; `media3-cast` depends on it), which removes Chromecast from that flavour. Native
decoders in `android/app/libs` (FLAC/Opus AARs, built by `support/scripts/build-media3-decoders.sh`) would need to be
built from source in the F-Droid recipe. Also needed: a reproducible build and a metadata file in `fdroiddata`.

No request text is drafted, since neither is viable today. Recommend not pursuing until the owner decides on a
free flavour; this would cost the Cast feature and the paid model.

## 6. Awesome lists

### awesome-jellyfin

- URL: https://github.com/awesome-jellyfin/awesome-jellyfin (fetched). Clients are generated into `CLIENTS.md` from
  source files, so do not edit `CLIENTS.md`; new items can be proposed through an issue. The README format is
  `- [Name](repo URL) - Short description.`
- CONTRIBUTING.md (fetched): the project should be at least 4 weeks old, actively maintained, with a clear README;
  descriptions "short and simple, but descriptive and non-promotional"; Conventional Commits. The criteria are written
  around open-source repositories; the 🎵 table has OSS / Free / Paid columns, so a paid app can be shown, and the
  rules don't forbid it. The source-file format used for `CLIENTS.md` was not found: check rules and mirror an
  existing entry such as Finamp's.
- Blockers: the Jellyfin-related change must be proposed to the maintainers; the paid status. Confirm in the issue.

Issue text (title: `Add Shuttle Music (Android music client)`):

> I'm the developer of Shuttle Music. Suggesting it for the Android music clients list.
>
> - Repo: https://github.com/timusus/Shuttle2 (Apache 2.0)
> - Play Store: https://play.google.com/store/apps/details?id=com.simplecityapps.shuttle
> - Site: https://shuttlemusicplayer.com
> - Description: Android music player for local files and Jellyfin, Emby and Plex, with a 10-band equaliser,
>   ReplayGain, Android Auto and Chromecast.
> - OSS: yes. Free: no (paid, 14-day free trial). Music-focused: yes.
>
> I wasn't sure where client entries live in the source; point me to the file and I'll send a PR instead.

README-style line if that format is used:

`- [Shuttle Music](https://github.com/timusus/Shuttle2) - Android music player for local, Jellyfin, Emby and Plex libraries. 🔺 Paid`

### awesome-selfhosted

- URL: https://github.com/awesome-selfhosted/awesome-selfhosted, rules at
  https://raw.githubusercontent.com/awesome-selfhosted/awesome-selfhosted-data/master/CONTRIBUTING.md (fetched).
  Entries are YAML files in `awesome-selfhosted-data` via PR or an issue.
- Blocker: the list is of software you host. Shuttle is a client with no server component, so it doesn't fit. The
  rules also require a first release more than four months old and a free licence (met), but clients are not
  explicitly accepted. The audio section's contents were not read: unverified. **Verdict: skip.** If the owner wants
  certainty, ask in an issue first; no entry is drafted.

### awesome-android music lists

- `JStumpp/awesome-android` was searched for; the repo itself was not fetched (https://github.com/Cloudef/awesome-android
  returned 404). Per a search index (awesome.ecosyste.ms), a list with a Music section exists and includes Odyssey and
  Music-Player-GO. Entry format and rules: unverified, check rules.
- Likely entry (confirm the format first):

`- [Shuttle Music](https://github.com/timusus/Shuttle2) - Music player for local files and Jellyfin, Emby and Plex libraries.`

- Blocker: the list may expect free apps only.

## 7. Reddit answer templates

Never post as a drive-by. Answer only when the question is asked, check each subreddit's self-promotion rule first (not
fetched: check rules), and leave the disclosure in.

### r/jellyfin: "Which music app for Jellyfin on Android?"

> Disclosure: I make Shuttle Music, so weigh this accordingly.
>
> I'm one developer. It plays a Jellyfin library and local files, with FLAC and Opus, a 10-band EQ, ReplayGain,
> Android Auto, Chromecast, widgets, a sleep timer, a batch tag editor, smart playlists and M3U import. It's paid with
> a 14-day free trial; the source is Apache 2.0. It doesn't do lyrics, scrobbling, crossfade or downloads from the
> server. Play Store: <link>.
>
> There are other Jellyfin music apps too, free and open source ones among them. What matters most to you, offline use,
> Android Auto, the equaliser? That narrows it down.

### r/PleX: "Which music app for Plex on Android?"

> Disclosure: I make Shuttle Music.
>
> It plays your Plex music library as well as local files, with FLAC and Opus, a 10-band EQ, ReplayGain, Android Auto,
> Chromecast and widgets. Paid, 14-day free trial, source Apache 2.0. No lyrics, scrobbling, crossfade or server
> downloads yet. Play Store: <link>.
>
> Plexamp is Plex's own music app, so try it too and compare on your own library. Tell me what you need and I'll say
> honestly whether Shuttle does it.

### r/emby: "Which music app for Emby on Android?"

> Disclosure: I make Shuttle Music, a paid Android app with a 14-day trial.
>
> It streams an Emby music library and plays local files. It has FLAC and Opus, a 10-band EQ, ReplayGain, Android
> Auto, Chromecast, widgets, a sleep timer, a batch tag editor, smart playlists and M3U import. The source is Apache
> 2.0. It doesn't do lyrics, scrobbling, crossfade or downloads from the server. Play Store: <link>.
>
> The Emby forum's Third Party Apps section lists other options. Happy to answer questions about how it handles Emby.

## Open problems

- Public repo URL and Play link placeholders (`https://github.com/timusus/Shuttle2`, `https://play.google.com/store/apps/details?id=com.simplecityapps.shuttle`) need filling in.
- Rules not fetched: Emby forum rules, Plex Apps & Creations rules, subreddit self-promotion rules, AlternativeTo form
  limits, IzzyOnDroid inclusion policy.
- iOS availability is not claimed anywhere, since 1.0.10 is Android.
