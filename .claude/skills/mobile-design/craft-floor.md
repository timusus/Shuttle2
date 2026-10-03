# Craft floor

Read before any UI edit. These are non-negotiable; a screen that breaks one is not ready for critique.

## Slop list — refuse these

Generic AI and "ported" tells. If you catch yourself producing one, stop and redesign.

**Both platforms**
- Cards nested in cards; every section boxed in a card "for structure".
- A phone layout stretched to tablet width (lines of text > ~80 characters, lists edge to edge on a
  1200dp window, artwork at 1000dp).
- Invented navigation: hamburger drawers on phone, custom tab bars, back buttons drawn by hand.
- Decorative gradients or glows unrelated to the artwork; "glassmorphism" in the content layer.
- ALL-CAPS eyebrow labels over every section; emoji as icons; icon + label + chevron on every row.
- Centred everything; grey-on-grey low-contrast secondary text "for elegance".
- Toast/snackbar for every action; confirmation dialogs for reversible actions (use undo).
- Settings that expose implementation ("Use ExoPlayer decoder"), or a setting instead of a decision.
- Loading spinners in the middle of content that could show a skeleton or cached state.
- Placeholder grey squares for missing artwork (use generated artwork).

**Android-specific**
- iOS idioms in Material clothing: centred titles everywhere, chevrons on list rows, iOS-style segmented
  controls, swipe-to-reveal as the only way to an action, bottom-sheet "action sheets" styled like iOS.
- Raw `Color(0x…)`, `N.dp`, `n.sp`, `RoundedCornerShape(n.dp)` in `app` — use `MaterialTheme` roles,
  `S2Spacing`, `MaterialTheme.shapes`/`S2Shapes`, typography roles.
- Drawing behind system bars without insets (content under the gesture bar, FAB under the nav bar,
  list last item unreachable). Missing `contentPadding` from `WindowInsets`.
- Ripple-less custom clickables; touch targets below 48dp on custom components (`minimumInteractiveComponentSize`).
- Ignoring the motion scheme: hard-coded `tween(300)` instead of `MaterialTheme.motionScheme` springs.
- Dynamic colour/accent applied to album-art screens (art-derived scheme wins there).

**iOS-specific**
- Material idioms: FABs, snackbars, ripple, overflow "⋮" menus, outlined text fields, top-left back arrows
  that aren't the system back button.
- Liquid Glass on content (rows, cards, artwork) instead of the floating control layer; glass on glass.
- Fixed font sizes (`.font(.system(size: 15))`) instead of text styles; `UIScreen.main.bounds` or
  `GeometryReader` for layout that `ViewThatFits`/`containerRelativeFrame` handles.
- Custom sheets instead of `.sheet` with detents; custom alerts instead of `.alert`/`.confirmationDialog`.

## Checks before handing over a UI change

1. **Tokens.** `rg -n 'Color\(0x|[0-9]+\.dp|[0-9]+\.sp|RoundedCornerShape\(' <changed .kt in app>` and
   `rg -n 'size: [0-9]|padding\([0-9]|cornerRadius: [0-9]|Color\(red' <changed .swift>` return nothing new.
   Exceptions need a comment saying why.
2. **Targets.** Every tappable thing ≥ 48dp / 44pt, including icon buttons in dense rows.
3. **Contrast.** Text ≥ 4.5:1 (≥ 3:1 at ≥ 18sp/pt bold or 24 regular), icons and focus rings ≥ 3:1 —
   including over artwork-derived colours in both light and dark.
4. **Text scale.** Layout survives 200% (Android font scale 2.0, iOS AX5): no clipping, truncation only
   where intended, rows grow vertically, toolbars collapse to overflow.
5. **Screen reader.** Meaningful content descriptions / accessibility labels; decorative images hidden;
   merged semantics for rows; custom actions for swipe/long-press actions; heading traits on section headers.
6. **Motion.** Uses the platform motion tokens; respects "Remove animations" / Reduce Motion (crossfade
   instead of movement) and Reduce Transparency.
7. **States.** Loading, empty, error, offline source, long titles (and RTL), no artwork, 10k+ items.
8. **Insets and cut-outs.** Edge-to-edge, display cut-out, gesture bar, IME, foldable hinge.
9. **Dark and light** both checked; dynamic colour and each accent don't break it (Android).
10. **Dead code.** Superseded components and tokens deleted in the same change.

## Optical details (the polish layer)

- **Concentric radii:** outer radius = inner radius + padding (artwork inside a card, buttons in a group).
- **Optical centring:** play triangles nudge right ~1/12 of the glyph width; check visually.
- **Tabular numbers** for elapsed/remaining time, track numbers, durations, counts that tick.
- **Type rhythm:** at most 3 sizes and 2 weights per screen region; secondary text via colour role
  (on-surface-variant / `.secondary`), not a smaller size.
- **Spacing rhythm:** one spacing step between related items, two between groups; align to the grid
  (Android 4dp grid via `S2Spacing`; iOS `Spacing`).
- **Artwork:** consistent corner radius per size class of artwork; a hairline or subtle shadow on light
  covers so white art doesn't bleed into a white surface.
- **Truncation:** titles truncate tail; artist/album lines may marquee only on Now Playing.
