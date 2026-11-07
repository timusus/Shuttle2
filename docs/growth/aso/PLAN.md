# Shuttle Music ASO and SEO plan (2026-11-02)

Living doc for Play search, the site and off-store acquisition. Tooling: the shared `aso` command (devtools `docs/aso.md`), configured by `.aso.toml`; keywords in `docs/growth/aso/keywords.txt`; `/growth-report` for the weekly read. Baseline data:
`baseline-2026-11-02/`. Listing text is code in `android/app/src/main/play/listings/`; every change is a commit plus a
row in `experiments.md`.

## Where we are

| Fact | Value | Source |
|---|---|---|
| Production build | 1.0.10 on production, beta and alpha; the rewrite (2026.10.13) is internal only. Listing claims must hold for 1.0.10 | Play API tracks |
| Installs | ~55/day in Sep, ~35/day late Oct; 28d acquisitions 1.86k (-23%) | bulk overview, Play Console |
| Impressions | 507k last 28d (-41%); step from ~22k to ~10k/day on 19-22 Oct | Play Console |
| Explore visitors | ~180-250/day in Sep, ~90/day from 22 Oct; "Other" traffic steady at ~120-150 | bulk traffic_source |
| Cause | Submission 325, published 22 Oct: title "Shuttle Music Player" became "Shuttle Music: Jellyfin & Plex", short and full description rewritten in every locale, new en-US icon | Play Console, Wayback |
| Rating | 3.75 public; 3.43 last 28d (-0.57) | Play Console |
| Search ranks (US/GB/AU/CA) | shuttle music player 1-2; jellyfin audio 3; jellyfin music 5; plex music 4; plex music player 5-10; media server music player 5-11; emby music 21-22 (AU); absent from top 30 for music player, offline music player, flac player, subsonic, navidrome | scrape 2026-11-02 |
| Experiments | "New Listing" en-US short description test, 11 Jan to 10 Sep 2026: control "A premium music player for local, Plex, Emby & Jellyfin playback" kept | Play Console |
| Custom listings | Jellyfin, Plex and Emby search keyword listings, live since 3 Oct, no data yet | Play Console |
| Install languages (Oct) | en-US, es-US, pt-BR, en-GB, en-IN, ru, es-419, fr | bulk language |
| Google search (site) | 5-31 Oct: 84 clicks, 1.18k impressions, avg position 5.1, 58 queries, almost all branded ("shuttle music player" 29/185 at 2.4). Seven feature pages live 3 Nov, indexing requested; sitemap resubmitted | Search Console, `sc-domain:shuttlemusicplayer.com` |
| Managed publishing | off: an applied edit goes live once review passes | Play Console |

## Rules for listing copy

Title 30, short 80, full 4,000. No ranking or price words, no "free" or "no ads" in the title, no competitor names, no
keyword lists, no caps or emoji. Every claim true for the production build. One developer: "me", never "we".

## Plan

1. Restore the title family that carried search: "Shuttle Music Player: ..." plus the tested short description.
2. English locales as title cells, held 28 days, read by rank: en-US/en-GB "Shuttle Music Player: Jellyfin".
3. Translated titles and short descriptions in the install languages, with the local "music player" phrase.
4. When the rewrite reaches production, rewrite the copy for the free plus Pro model (draft in `docs/product/`).
5. Site: per-feature landing pages, hreflang, sitemap and Search Console.
6. Off-store drafts in `outreach.md`; the owner sends.
