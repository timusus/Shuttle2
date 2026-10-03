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
support/scripts/longjob.sh start design-shots -- support/scripts/design-shots.sh --platform both [--screens home,now-playing] [--matrix quick|full] [--contact-sheet]
support/scripts/longjob.sh wait design-shots
```

`quick` is phone only, light + dark, default text; `full` adds Android tablet/foldable (`wm size` overrides,
not AVDs), iPad and large text (font scale 2.0 / AX5). Output lands in `shots/<run>/<platform>/<screen>__<device>__<theme>__<text>.png`
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

## Rubric (score 0–10 each, anchored)

| Category | Weight | 9–10 | 5–6 | 1–3 |
|---|---|---|---|---|
| Platform conformance | 0.20 | Indistinguishable from a first-party app of this platform | Mostly standard, a few borrowed idioms | Looks ported from the other platform or the web |
| Hierarchy & layout | 0.15 | One obvious focus; rhythm and alignment exact at every tier | Clear on phone, weak on large screens | Everything equal weight; stretched phone layout |
| Craft | 0.15 | Tokens only, optical details right, motion purposeful | Minor spacing/radius inconsistencies | Literals, misalignment, janky motion |
| Accessibility | 0.15 | Passes the craft-floor checks at 200% and with a screen reader | Small targets or contrast misses in secondary UI | Breaks at large text; unlabeled controls |
| Adaptivity | 0.10 | Deliberate layout per tier, posture-aware, state continuous | Works but under-uses space | Letterboxed, stretched or broken on resize |
| Music fitness | 0.10 | Artwork-led with art-derived colour at Shuttle's bar (music.md), playback always reachable, source/download state clear | Functional but generic; art colour flat or single-tone | Playback buried; art treated as decoration |
| Parity | 0.10 | Same intent and information as the other platform, native idioms | Minor capability gaps | Missing features or borrowed idioms |
| Distinctiveness | 0.05 | Has one memorable, appropriate moment | Competent and anonymous | AI-slop defaults (craft-floor list) |

Weighted score ≥ 7.5 to ship a redesign; any category ≤ 4 blocks regardless of total; any accessibility
floor violation blocks.

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
