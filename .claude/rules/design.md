---
paths:
  - "android/app/**/ui/**"
  - "android/designsystem/**"
  - "ios/S2/**"
---

# Design

For any UI design, critique or parity work use the global `mobile-design` skill (HIG, Material 3
Expressive, adaptive layouts, native surfaces, evaluation rubric). This file holds the Shuttle facts it
defers to. If the skill and these docs disagree, the docs win; file a `/note` if a doc looks wrong.

## Ground truth

| What | Where |
|---|---|
| Android design language (M3 Expressive spec, tokens, component list, approval gate) | `docs/design/design-language.md` |
| iOS design language (HIG-first tokens, glass, motion) | `docs/design/ios-design-language.md` |
| App shell, navigation, player state model, layout tiers | `docs/architecture/app-shell.md` |
| Redesign inventory and parity checklist | `docs/architecture/redesign-inventory.md` |
| Android tokens and components | `android/designsystem/.../theme/`, `.../component/`, debug `catalog/` |
| iOS tokens and components | `ios/S2/Theme/`, `ios/S2/Components/` |
| Goldens (per component and per screen) | `docs/design/catalog/**`, `docs/design/<screen>/**` |
| Layout tiers in code | Android `ui/shell/adaptive/ShellLayout.kt`; iOS `ios/S2/Theme/LayoutTier.swift` |
| Competitor teardown, feature-expectations checklist, anti-patterns | `docs/design/competitors.md` |

Tokens: `S2Theme`, `S2Spacing`, `S2Shapes`, `S2Preview` (Android); `Spacing` (iOS). Screens never use
literal `dp`/`Color(0x…)`/`Font.system(size:)`.

## Shuttle principles

- **Artwork colour is the signature.** Shuttle pioneered theming the app from album-art colours and was
  known for it; it is the brand's one owned idea. Spend boldness there first and keep it more refined
  than competitors' copies (judge side by side in critiques).
- Artwork-derived colour on media screens beats dynamic colour (dynamic colour for chrome only).
- iOS is the current quality reference for intent; Android's usual gap is screens bypassing the design
  system, so most Android polish is moving screens onto tokens.
- Whole-app audits load `docs/design/competitors.md` for the feature-expectations pass; shape and
  critique cite it too (shape's Reference step as prior art, critiques for its anti-patterns), not
  only whole-app audits.
- Native-surface state: `PlaybackService` (Android) and `NowPlayingController` (iOS) own the media
  session; Android Auto, Cast and the AirPlay picker are present; predictive back is on (targetSdk 36 —
  verify the animations); CarPlay, Wear/watchOS and a QS tile are absent; widgets are Glance
  `NowPlayingWidget` (Android) / `S2Widgets` (iOS); shortcuts are `IntentPerformers`.

## Screenshot evidence

- **`support/scripts/design-shots.sh`**, one background call that leases emulator/simulator lanes and
  writes the whole set:
  `support/scripts/longjob.sh start design-shots -- support/scripts/design-shots.sh --platform both [--screens home,now-playing] [--devices …] [--matrix quick|full] [--contact-sheet]`,
  then `longjob.sh wait design-shots`.
- `--devices` picks form factors: Android `phone,tablet,foldable`, iOS `iphone,ipad`; default
  `phone,iphone` (tablet/foldable are `wm size` overrides, not AVDs). `--matrix` picks theme × text:
  `quick` = light + dark at default text, `full` adds font scale 2.0 / AX5. **Audits use `--matrix full`
  with the default devices** (4 cells per screen per platform). `--ios-source jellyfin` streams from the
  test server instead of importing the artwork `library` fixture locally; `--ios-profile default`
  targets iOS 18.5 instead of the iOS 26 simulator.
- Before the matrix each device plays a seeded listening history (four albums by four artists, two of
  them twice, then Blue Hours paused part-way) so Home shows Jump Back In with a resume card; the plays
  are all from today, so Heavy Rotation, Around This Time and Rediscover stay hidden. `--no-history`
  skips this and saves about 6 minutes per device.
- Output lands in `shots/<run>/<platform>/<screen>__<device>__<theme>__<text>.png` with
  `shots/<run>/manifest.md` (every shot, and every failed flow with its step and last error). Read
  manifest.md first, then only the PNGs needed. `--help` lists the screen names.
- Ad hoc states: `emulator-check` (Maestro `takeScreenshot`) and `ios/scripts/maestro-sim.sh`; real
  devices via the `android-device` / `ios-device` skills (haptics, real colour). Lanes: `remote-emu.sh`
  (lock screen: `remote-emu.sh lockscreen on`) and the `sim-lease.sh` simulator lease — never
  `simctl create`.
- Goldens: Roborazzi in `docs/design/**`, recorded on the Mac via `/verify-ui` /
  `recordRoborazziDebug`; compare with `git diff` of the PNGs or side by side.
- Issue evidence: `gh` cannot upload images, so crop each screenshot to the defect, push the PNGs to
  the orphan `design-evidence` branch of `timusus/Shuttle2` and embed via raw URL
  `![before](https://raw.githubusercontent.com/timusus/Shuttle2/design-evidence/<issue>-<slug>.png)`:

```bash
git fetch origin design-evidence
git worktree add .claude/worktrees/design-evidence design-evidence
cp <cropped>.png .claude/worktrees/design-evidence/<issue>-<slug>.png
git -C .claude/worktrees/design-evidence add . && git -C .claude/worktrees/design-evidence commit -m "#<issue> <slug>"
git -C .claude/worktrees/design-evidence push origin design-evidence
git worktree remove .claude/worktrees/design-evidence
```

File findings with `/note` (label `design`).
