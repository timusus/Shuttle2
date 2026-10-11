# Play listing draft (#380)

Draft for the owner to approve; nothing is published. It follows `monetisation.md` (strategy settled 2026-10-06) and
replaces the current "Stream your music via Jellyfin, Emby or Plex" line, which Play's misleading-claims policy expects to
disclose that streaming is paid. The repo holds no copy of the current listing, so edit it in Play Console > Store
presence > Main store listing. Update the disclosure line if prices or the trial change.

Differences from the brief to confirm: `monetisation.md` makes Android Auto a Pro feature and only basic EQ free
(parametric/AutoEq is Pro), so the draft says that rather than "Android Auto free".

## Short description (limit 80)

```
Music player for your files and your Jellyfin, Emby, Plex or Subsonic server.
```

(77 characters)

## Full description (limit 4000)

```
Shuttle Music is free for local files. Pro adds server streaming and more: it is a yearly plan or a one-time unlock, with a 14-day free trial.

Shuttle Music plays the music on your phone and, with Pro, music from your own Jellyfin, Emby, Plex or Subsonic/OpenSubsonic server (Navidrome included). It has no catalogue of its own.

WHAT'S FREE
• Playback of music stored on your device, with a library of albums, artists, songs and genres
• Playlists
• Basic equalizer
• Sleep timer
• Editing the tags of a single song
• Chromecast
• Home screen widgets
• Songs you have already downloaded keep playing, even after a trial or subscription ends

WHAT'S PRO
• Jellyfin, Emby, Plex and Subsonic servers, and offline downloads from them. One purchase covers every server; there is no per-server price
• Android Auto
• Batch tag editing
• Advanced audio: parametric and AutoEq equalizer, replay gain modes

TRY PRO FIRST
The 14-day free trial needs no card. It starts the first time you use a Pro feature, such as adding your first server, connecting Android Auto or starting a batch edit, and the app tells you when it does. When it ends, Pro features lock where you use them. Local playback, your settings and your downloaded songs are not affected.

TWO WAYS TO KEEP PRO
• Lifetime: pay once and keep Pro
• Yearly: a yearly plan, cancel any time in Google Play

If you bought Shuttle Music Pro or a subscription earlier, you keep Pro. Use Restore purchases on the Pro screen if it is not showing.

Shuttle Music is made by one developer. Questions or problems: use the support link on this page.
```

(About 1,600 characters; the limit is 4,000. Check the exact count in Play Console.)

## Check before publishing

- Prices are left out on purpose: Play shows the regional price on the Pro screen. Add them only if the owner wants them in the text.
- Add feature lines (gapless, formats, Material 3) from the current listing if wanted; the repo has no copy, so none are invented here.
- The Android Auto and EQ split above must stay in step with the Pro gates (#939).
