# Evaluate — evidence and the strict reviewer

Self-critique is optimistic. Judging is a separate pass: a fresh-context reviewer (Agent tool,
`reviewer` or `general-purpose`, model `sonnet`; `opus` for a redesign sign-off) that gets the screenshots,
the brief/plan and this rubric — not the implementation reasoning. It reports scores and defects; it
does not fix.

## Evidence matrix

Capture before judging. Use the cheapest source that shows the thing.

| Axis | Values |
|---|---|
| Form factor | phone portrait; phone landscape; foldable/book or iPad split (compact-regular); tablet/desktop expanded; tabletop (player only) |
| Theme | light; dark (Android: also one accent + dynamic colour once) |
| Text | default; 200% / AX5 |
| State | populated; empty; loading; error/offline; long text |

Default source for a screen audit: **`design-shots.sh`**, one background call that leases an
emulator and/or simulator, applies the matrix outside the flows and writes the whole set:

```bash
support/scripts/longjob.sh start design-shots -- support/scripts/design-shots.sh --platform both [--screens home,now-playing] [--devices phone,tablet,foldable,iphone,ipad] [--matrix quick|full] [--contact-sheet]
support/scripts/longjob.sh wait design-shots
```

`--devices` picks form factors (Android `phone,tablet,foldable`, iOS `iphone,ipad`; default `phone,iphone`;
tablet/foldable are `wm size` overrides, not AVDs). `--matrix` picks only theme × text: `quick` is light + dark
at default text; `full` adds large text (font scale 2.0 / AX5). **For audits use `--matrix full` with the default
devices** (4 cells per screen per platform). Both platforms use the artwork `library` fixture, so shots show
covers and ArtworkTheme colour; iOS imports it as a local library (`--ios-source jellyfin` for the test server)
on the iOS 26 simulator (`--ios-profile default` for iOS 18.5). Before the matrix, each device plays a
listening history through the app (four albums by four artists, two of them twice, then Blue Hours paused
part-way), so Home has Jump Back In with a resume card. The plays are all from today, so Heavy Rotation,
Around This Time and Rediscover stay hidden. `--no-history` skips this, and saves about 6 minutes per device.
Output lands in `shots/<run>/<platform>/<screen>__<device>__<theme>__<text>.png`
with `shots/<run>/manifest.md` (every shot, and every failed flow with its step and last error). Read
manifest.md first, then Read only the PNGs the audit needs. `--help` lists the screen names.

Other sources, cheapest first:
1. **Goldens** — Android Roborazzi in `docs/design/**` (record on the Mac via `verify-ui` /
   `recordRoborazziDebug`); compare with `git diff` of the PNGs or side by side.
2. **Previews** — `@Preview` / `#Preview` matrices for components.
3. **Emulator / simulator, ad hoc** — `emulator-check` (Maestro `takeScreenshot`) and `ios/scripts/maestro-sim.sh`, for a state `design-shots.sh` has no flow for.
   Motion, predictive back, sheets and transitions need this or a recording.
4. **Device** — `android-device` / `ios-device` skills, for haptics, real colour, performance.

A full matrix is rarely needed: a critique of one screen needs phone light/dark, 200% text, and the
largest tier it supports. A redesign sign-off needs the full matrix for that screen.

Screenshots go to the owner (Read the PNGs so they render; see the show-screenshots memory). Workers
capture; the orchestrator or reviewer looks.

## Whole-app audit — four required passes

Auditing the whole app (not one screen) is four passes, and all four are required; an audit that ran
only the screenshot scorecard is incomplete. Findings from every pass get filed.

1. **Screenshot scorecard** — the evidence matrix above, `--matrix full`, judged by a fresh-context
   reviewer against the rubric below (cohesion row included).
2. **Foundations sweep** — the code, not the screenshots: hard-coded tokens (`N.dp`, `Color(0x…)`,
   `.padding(13)`, `RoundedCornerShape(n.dp)`, `Font.system(size:)`) in screens; components
   duplicated per screen instead of shared; missing a11y/adaptive usage. Judged against the
   ground-truth docs in SKILL.md's table — `docs/design/design-language.md`,
   `docs/design/ios-design-language.md`, `docs/architecture/parity-audit.md` and
   `redesign-inventory.md` — reporting every place code and docs disagree, in both directions.
3. **Native surfaces** — everything `design-shots.sh` never tours: media notification, Lock
   Screen / Dynamic Island, widgets, Auto/CarPlay, shortcuts / App Intents. Walk the table in
   [native-surfaces.md](native-surfaces.md) and capture or check each surface by hand (its
   "Checking surfaces in an audit" section); one this environment cannot capture is checked from
   code and marked *unverified-in-this-audit*.
4. **Feature expectations** — the **Feature expectations** checklist in
   [competitors.md](competitors.md): every feature a premium 2026 music player is expected to
   have, present / partial / absent, per platform.
   A missing expectation is a finding even when everything that exists is beautiful.

Pass 1 produces the rubric scores; passes 2–4 produce findings and the feature-gap list. All four go
in the audit report.

## Rubric (score 0–10 each, anchored)

| Category | Weight | 9–10 | 5–6 | 1–3 |
|---|---|---|---|---|
| Platform conformance | 0.15 | Indistinguishable from a first-party app of this platform | Mostly standard, a few borrowed idioms | Looks ported from the other platform or the web |
| Hierarchy & layout | 0.15 | One obvious focus; rhythm and alignment exact at every tier | Clear on phone, weak on large screens | Everything equal weight; stretched phone layout |
| Craft | 0.15 | Tokens only, optical details right, motion purposeful | Minor spacing/radius inconsistencies | Literals, misalignment, janky motion |
| Accessibility | 0.15 | Passes the craft-floor checks at 200% and with a screen reader | Small targets or contrast misses in secondary UI | Breaks at large text; unlabeled controls |
| Adaptivity | 0.10 | Deliberate layout per tier, posture-aware, state continuous | Works but under-uses space | Letterboxed, stretched or broken on resize |
| Music fitness | 0.10 | Artwork-led with art-derived colour at Shuttle's bar (music.md), playback always reachable, source/download state clear | Functional but generic; art colour flat or single-tone | Playback buried; art treated as decoration |
| Parity | 0.10 | Same intent and information as the other platform, native idioms | Minor capability gaps | Missing features or borrowed idioms |
| Cohesion | 0.05 | One app, not a collection of screens: same components, spacing rhythm and colour behaviour everywhere; screens differ only where the job differs | Core screens consistent; secondary ones drift (a component re-built per screen, one-off spacing) | Every screen its own dialect: duplicated components, mixed idioms, goldens disagree |
| Distinctiveness | 0.05 | Has one memorable, appropriate moment | Competent and anonymous | AI-slop defaults (craft-floor list) |

Weighted score ≥ 7.5 to ship a redesign; any category ≤ 4 blocks regardless of total; any accessibility
floor violation blocks. Cohesion is judged across the whole screen set (the tour or the goldens), not
one screen, and is scored in single-screen critiques too — against the app's other screens.

## Reviewer brief template

> You are a strict design reviewer for Shuttle Music (Android Compose M3 Expressive / iOS SwiftUI).
> Inputs: <plan or intent>, screenshots at <paths>, rubric at `.claude/skills/mobile-design/evaluate.md`,
> slop list at `craft-floor.md`, platform rules at `<android|ios>.md`. Assume the work is flawed until the
> screenshots prove otherwise. Score each category with one sentence of evidence citing a screenshot.
> List defects as `severity (blocker/major/minor) | screenshot or file:line | rule | fix`. Report only real
> problems; no praise section. Under 40 lines.

## Bounded loop

At most two fix → recapture → re-score rounds per change. If it still fails, file the remaining
findings with `/note` (label `design`) and surface them to the owner rather than looping.

## Filing findings (evidence in the issue)

Every non-trivial finding is filed with `/note` (label `design`) as the audit finds it; the
transcript is not a backlog. The evidence lives **in the issue**, embedded, not named:

- Crop each screenshot to the defect — a reviewer must not have to find it in a full-screen shot.
  Before/after pairs wherever a fix is proposed.
- Never point at the tool or the shot path ("the design-shots capture shows…"): `gh` cannot upload
  images, so push the PNGs to the orphan `design-evidence` branch and embed them via raw URLs.

```bash
# once: git worktree add --detach .claude/worktrees/design-evidence \
#   && git -C .claude/worktrees/design-evidence switch --orphan design-evidence \
#   && (commit one file, push -u origin design-evidence) && git worktree remove …
git fetch origin design-evidence
git worktree add .claude/worktrees/design-evidence design-evidence
cp <cropped>.png .claude/worktrees/design-evidence/<issue>-<slug>.png
git -C .claude/worktrees/design-evidence add . && git -C .claude/worktrees/design-evidence commit -m "#<issue> <slug>"
git -C .claude/worktrees/design-evidence push origin design-evidence
git worktree remove .claude/worktrees/design-evidence
```

Embed in the issue body as
`![before](https://raw.githubusercontent.com/timusus/Shuttle2/design-evidence/<issue>-<slug>.png)`.
