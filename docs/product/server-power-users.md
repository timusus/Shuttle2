# What self-hosting power users expect (2026-10-05)

What Navidrome/Subsonic users expect from a client.
Demand signal: GitHub issue votes, the Symfonium support forum, Play and App Store reviews (Reddit wasn't
reachable).

## What changed since the spike

- **Navidrome API keys** (OpenSubsonic `apiKey`, navidrome#6219) merged 2026-09-28, so they ship in the
  release after 0.64.2. LMS accepts only `apiKey`.
- **The OpenSubsonic `transcoding` extension** (`getTranscodeDecision` / `getTranscodeStream`) is in
  Navidrome since 0.61.0 (navidrome#4990), LMS 3.73 and DroppedNeedle. The client posts its direct-play and
  transcoding profiles and bitrate caps; the server decides direct play vs transcode, never upscales, and
  returns a stream that seeks by re-requesting with `offset` (seconds). HTTP only, no HLS.
- **Navidrome 0.64 has an experimental Jellyfin music API** (`Jellyfin.Enabled`), so the Jellyfin provider
  may partly work against it. Untested.
- **Narjo (iOS)** covers the same five server types as Shuttle, plus mTLS, custom headers and self-signed
  certificates ($29.99 lifetime, 4.3★). Its main complaint is a weak offline mode.

## Symfonium

- **Praised:** separate Wi-Fi, mobile and download/cache quality; transcoding for offline caches; an
  alternate (LAN) address; several servers at once; Navidrome Shares; word-synced lyrics; mTLS;
  sonic similarity (AudioMuse); one-time price.
- **Transcoding settings:** Wi-Fi and mobile maximum bitrate; force a re-transcode when Wi-Fi drops;
  separate offline cache quality; Opus by default with a per-provider "Transcode to MP3" switch (for
  Sonos/Chromecast); "ignore server transcoding settings" (ask for raw when no transcode is needed);
  Now Playing shows the delivered codec.
- **Complaints:** busy UI and no settings search; Android Auto can't rate or seek forward; no play-queue
  sync with the server (won't implement, and users want it, citing Feishin); alternate-address switching
  sometimes freezes; slow syncs (see the audit).

## Transcoding: what users expect

- Original at home, capped on mobile, a separate (often Opus 128–320) quality for downloads.
- Format choice: original/raw, Opus (best quality per bit), MP3 (for Cast/Sonos/compatibility), AAC.
- Never upscale a lower-bitrate source; never transcode lossy to lossless.
- Seeking must work in transcoded streams (legacy `stream` needs `timeOffset`; a client that doesn't know
  the server forced a transcode can't seek).
- Gapless should hold; Opus and AAC transcodes are reported as slightly gappy.
- Codecs the device can't decode should be transcoded automatically (Amperfy #672 is the failure case).

## Ranked expectations (T = table stakes, D = differentiator)

1. T Reliable offline: download/cache by album or playlist, a browsable offline library.
2. T Separate Wi-Fi, mobile and download quality.
3. T Gapless and seeking that hold on transcoded streams.
4. T Android Auto / CarPlay with full browsing.
5. T Library picker (Navidrome multi-library, navidrome#192, 106 votes).
6. T Two-way stars, ratings and playlists; scrobble and now playing.
7. T Connection robustness: self-signed certificates, custom headers (Cloudflare Access, Authelia), LAN/WAN fallback.
8. D Transcoding extension with legacy `format`/`maxBitRate`/`timeOffset` fallback.
9. D Several servers merged into one library (#774).
10. D Word-synced lyrics (`songLyrics`).
11. D Play queue sync across devices (`savePlayQueue`/`getPlayQueue`); Symfonium refuses it.
12. D Instant mix / similar songs / sonic similarity (#509).
13. D mTLS and `apiKey` auth.
14. D Internet radio and Shares.
15. D Opinionated settings with search.

## Sources

Symfonium: [Play listing](https://play.google.com/store/apps/details?id=app.symfonik.music.player),
[transcoding docs](https://docs.symfonium.app/wiki/settings/settings-playback-decoding-and-transcoding/),
[Subsonic provider docs](https://docs.symfonium.app/wiki/providers/subsonic-opensubsonic-media-provider-configuration/),
[offline cache](https://docs.symfonium.app/wiki/other/offline-media-cache-and-downloads/),
forum threads [9390](https://support.symfonium.app/t/9390) (queue sync),
[11191](https://support.symfonium.app/t/11191) (seeking forced transcodes),
[13046](https://support.symfonium.app/t/13046) (Opus offline cache),
[10548](https://support.symfonium.app/t/10548) (no upscaling),
[15011](https://support.symfonium.app/t/15011) (MP3 for Sonos),
[9958](https://support.symfonium.app/t/9958) (secondary address),
[10052](https://support.symfonium.app/t/10052) (custom headers),
[feature request votes](https://support.symfonium.app/c/feature-requests/8/l/top).
OpenSubsonic: [getTranscodeDecision](https://opensubsonic.netlify.app/docs/endpoints/gettranscodedecision/),
[getTranscodeStream](https://opensubsonic.netlify.app/docs/endpoints/gettranscodestream/).
Navidrome: [#4990](https://github.com/navidrome/navidrome/pull/4990),
[0.64.0 release](https://github.com/navidrome/navidrome/releases/tag/v0.64.0),
[configuration](https://navidrome.org/docs/usage/configuration-options).
Other clients: [Narjo](https://apps.apple.com/us/app/id6748359006),
[play:Sub](https://apps.apple.com/us/app/play-sub-music-streamer/id955329386),
[Amperfy](https://apps.apple.com/us/app/amperfy-music/id1530145038),
[Substreamer](https://apps.apple.com/app/substreamer/id1012991665), GitHub issues of Feishin (#47, #44),
Tempus (#102, #68), Finamp (#24, #44), Amperfy (#672, #471).
