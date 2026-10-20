# Design quality bar

§7.6 of [SPEC.md](SPEC.md) says the app should feel "smoother" than the one it
replaces, and the standard set for that is: it should look like something Apple
would approve. That is not a style to imitate — the palette here is already its
own, and should stay that way. It is a level of finish, and finish is made of
checks that a thing either passes or doesn't.

So this document is a checklist, not a mood board. Each section states a
standard and, where the current code falls short of it, says exactly where. A
component that type-checks and passes its unit tests but fails a section here is
not finished; it is functional. That distinction is the whole point.

Everything below assumes the design system in `ui/theme/Tokens.kt` and
`ui/theme/Theme.kt` — the 4dp spacing rhythm, the semantic colour roles, the
three rating ramps. Those are good and are not relitigated here.

---

## 1. Type resolves to the font we think it does

**Current state:** the scale in `Theme.kt` is deliberate work — custom sizes,
real line heights, negative tracking on display sizes and positive tracking on
small labels. It is not Material's defaults wearing a new palette. Two things
are still loose.

First, every role sets `fontFamily = FontFamily.SansSerif`. The doc comment
explains the intent — use the platform's own UI font, the one the user's
accessibility settings already know how to scale — and that reasoning is sound.
But `SansSerif` is a *logical* alias, resolved by the JVM's own font mapping,
and what it resolves to is not necessarily the platform UI font. On macOS it
commonly lands on Helvetica Neue rather than SF Pro, which is precisely the
"close but not native" look that reads as a port. The intent is right; the
mechanism doesn't deliver it.

Second, `labelSmall` reaches for `FontFamily.Monospace` to get numbers that
don't jitter. That works, but it changes *face* to get a *figure style* — so XP
counts and timestamps render as code sitting inside a UI.

**Standard:**

- Resolve the real system UI font per platform and fall back to the alias, not
  the other way round. On macOS that means the system font by name
  (`.AppleSystemUIFont` / `SF Pro Text`); on Linux, the fontconfig UI font
  (`Cantarell`, `Inter`, `Noto Sans`, in whatever order the desktop reports)
  before falling through to `FontFamily.SansSerif`. One resolver, called once,
  logged at startup so it is debuggable when a machine disagrees.
- Get tabular figures from the font feature, not from a second typeface:
  `TextStyle(fontFeatureSettings = "tnum")` on every role that renders a number
  that updates — XP pills, level badges, streak counts, heatmap tooltips, the
  "3 of 12 done" summaries. Keep `Monospace` only where monospace is the point
  (debug surfaces, HLC values, the data-folder path).
- If a bundled face is ever adopted instead, the licence clears GPLv3 bundling
  the same way the icon sets in SPEC §7.3 were vetted — SIL OFL is the safe
  default — and it must still scale with the platform's text-size setting.
- The 13 Material role names (`bodyLarge` for a quest title) say nothing about
  what the text *is*. Not urgent, but a named scale (`questTitle`, `rowMeta`,
  `metricValue`) makes the call sites self-documenting and makes an unintended
  role change show up in review.

## 2. Nothing moves that wasn't asked to move

**Current state:** in `ui/components/QuestRow.kt`, the trailing cluster swaps
`XpPill` for a fail `IconAction` on hover — two controls of different width in
the same slot, so the whole right edge of the row shifts as the pointer crosses
it. Scanning a list by running the mouse down it produces a shiver the user
can't name but will feel.

**Standard:** any hover- or state-conditional content reserves its layout slot
at a fixed width up front and cross-fades inside it. The content changes; the
box does not. This applies everywhere a `when (hovered)` or `when (state)`
branch changes what is drawn, not only where it was first caught:

- `QuestRow`'s trailing cluster — reserve `XpPill`'s width, fade the fail icon
  in over it.
- `MarkDot` and the sub-quest chevron — already fixed-size boxes (18dp, 20dp).
  Correct; keep them as the pattern.
- `DueChip` — text length varies from "Today" to "Overdue by 12 days". Either
  cap the width with ellipsis or confirm the row tolerates the reflow. Don't
  leave it unexamined.
- Any control whose label changes with state (Complete ↔ Reopen) needs the same
  treatment.

A state that changes a control's **colour or opacity** may animate in place. A
state that changes its **content or width** reserves the space first.

## 3. A small, fixed set of icon sizes

**Current state:** `QuestRow.kt` alone uses 12dp, 13dp, 18dp, 20dp and 24dp.
Each is locally defensible; together they are not a system, they are five
decisions nobody compared.

**Standard:** three sizes, named in `Tokens.kt` beside `Space` and `Radii`:

```kotlin
public object IconSize {
    public val sm: Dp = 14.dp   // inline with 12sp text — due chip, repeat and bell glyphs
    public val md: Dp = 18.dp   // inline with 14sp text — row actions, nav rail
    public val lg: Dp = 24.dp   // standalone — toolbar buttons, empty states
}
```

Every call site migrates to one of the three. A *container* may be a bespoke
hit-target size (the 20dp `CompleteBox`, the 18dp `MarkDot`); the *glyph inside
it* still comes from `IconSize`.

## 4. Optical, not geometric

This is the section that separates "tidy" from "designed". Every rule below
says the same thing: the eye, not the bounding box, decides when something is
centred, aligned or the same size.

**Standard:**

- **Glyphs are centred by eye.** A check mark's visual mass sits low and left of
  its box; a right-pointing chevron sits left of centre; a play triangle is the
  textbook case. `CompleteBox` centres its check geometrically today. Each glyph
  that needs one carries a fixed nudge recorded next to it in `Icons.kt`, not
  re-derived per call site.
- **Round things need to be bigger than square things to look equal.** A 9dp
  circle beside a 20dp rounded square does not read as a smaller sibling, it
  reads as a dot that got lost. When a circle and a square must feel the same
  size, the circle is drawn roughly 4–6% larger.
- **Text aligns on baselines, not on box edges.** In a row, the quest title and
  anything beside it share a baseline. Compose will happily centre two text
  boxes of different sizes and leave their baselines 2dp apart; that is the kind
  of wrongness nobody can point at.
- **Optical padding beats symmetric padding.** A pill containing a glyph then a
  label needs less padding on the glyph side than the text side, because the
  glyph's own bounding box already carries air. `Space` gives the rhythm; a
  component may deviate from it for an optical reason, recorded in a comment.
- **Corner radii nest.** An inner radius equals the outer radius minus the gap
  between them, or the two curves fight. A 12dp card holding an 8dp control with
  8dp padding is right; the same card holding a 12dp control is not.
- **Stroke weights stay in a family.** Borders, dividers, focus rings and chart
  axes should look like they were drawn by one pen. Pick 1dp hairlines and
  1.5dp emphasis and stop there.

The app icon is the worked example: the tail is separated from the ring by a
casing stroke rather than left to butt against it, and below 32px it is drawn
from different artwork entirely. Both are optical corrections, and neither shows
up in any measurement of the file.

## 5. The states nobody specs by default

**Current state:** the app has been built against a reasonable middle case — a
normal list, a normal window. The edges haven't been looked at on purpose.

**Standard:** every screen that lists or edits is checked against all of these
before it is called done:

- **Empty.** Zero quests in the view. Not a blank area — a short line and the
  one action that would fix it, placed where the first row would have been.
- **One item.** Density-tuned layouts often look broken with a single row.
  Check vertical rhythm and whether summary counts degrade sensibly.
- **Overflowing.** A 300-character title, 200 open quests in one view, a
  40-character skill name in the radar legend on `SkillsScreen`.
- **Narrow window.** `Space.railWidth` (216dp) plus `Space.detailWidth` (380dp)
  leave almost nothing for the list below roughly 900dp of window. Decide and
  implement what happens — detail collapses to an overlay, rail collapses to
  icons, or a declared minimum window size. Don't leave it to whatever Compose
  does by default.
- **Compound state.** A row that is selected *and* hovered. `QuestRow`'s
  background is a `when` with `selected` and `hovered` as sibling branches, so a
  selected row stops responding to the pointer entirely — which reads as a bug
  the first time it is noticed. Compound states layer: selection as a base
  tint, hover as a highlight on top.
- **First frame.** The gap between the folder picker (SPEC §1.2) answering and
  the initial replay (SPEC §6.5) finishing. Even at "milliseconds", an
  undrawn frame reads as broken; a deliberate empty frame does not.
- **Failure.** The data folder is gone, read-only, or holds a log the parser
  rejects. A dialog that says what happened and offers the folder picker, never
  a stack trace and never a silent empty list that looks like data loss.

## 6. Contrast is measured, not eyeballed

**Current state:** mostly good, and deliberately so — `LightColors` does not
reuse the dark accents, it defines darker ones (`#17847C` teal, `#A9761C` gold),
which is exactly right and easy to get wrong. One value misses:
`textOnPaperFaint` (`#74828F`) on `paper` measures **3.71:1**, under the 4.5:1
needed for body text, and it is used for row subtext — real content, not
decoration. Its dark-mode counterpart clears the bar at 4.65:1.

**Standard:**

- Any text carrying meaning meets 4.5:1 against the surface it sits on; large
  or bold text meets 3:1. Decorative rules and inactive borders are exempt.
- The rating ramps (difficulty, fear, urgency) must stay distinguishable in
  greyscale — `Tokens.kt` already claims this as the colour-blindness check, so
  it should be asserted by a test rather than trusted.
- Colour never carries meaning alone. A fear level is a colour *and* a position
  *and* a label in the tooltip.
- Both themes are checked, every time. A value that works on ink rarely works on
  paper, and the light theme is the one that gets tested less.
- Add this as a unit test over the `QuestColors` instances, not a thing someone
  remembers to do. It is a pure function over two colours; there is no excuse
  for it being manual.

## 7. Separation without shadow soup

**Standard:** surfaces separate by value, which is what `Tokens.kt` already
sets up (`ink` → `inkRaised` → `inkCard` → `inkHover`). Hold that line:

- One elevation device per boundary — a value shift **or** a hairline border,
  not both. Both is the single most common way a dark UI starts to look cheap.
- No drop shadows in dark mode. They do not read on a dark field; they only
  muddy the edge. Depth comes from the value ramp.
- In light mode, a shadow may replace the border, but there is exactly one
  shadow recipe in the app, defined once.
- Transparency over a scrolling list needs a real backdrop treatment or none at
  all. Half-opaque white over moving text is noise.

## 8. The platform's conventions are not ours to reinvent

An app earns "native" by doing the boring things the way the OS does them.

**Standard:**

- **Reduce Motion is honoured.** SPEC §7.6 specifies animation on nearly every
  state change. Every one of those has a reduced path — cross-fade or instant,
  never a half-speed version of the same movement. This is an accessibility
  requirement on macOS, not a preference.
- **Increase Contrast is honoured**, and so is the system text size.
- **Standard shortcuts do standard things.** `Cmd-W` closes the window, `Cmd-M`
  minimises, `Cmd-,` opens Settings, `Cmd-Q` quits. The command palette does not
  get to own a key the OS already owns.
- **The window is the system's.** No custom title bar, no reimplemented traffic
  lights, no inventing a close button. The menu bar sits in the system bar on
  macOS and in the window on Linux, as SPEC §7.4 already specifies.
- **Focus is always visible.** `focusRing()` exists and is used; the standard is
  that *every* interactive element gets it, including custom-drawn ones, and
  that it is visible against every surface it can land on.
- **System appearance is followed by default** — `isSystemInDarkTheme()` already
  does this — with an explicit override available, not the other way round.
- **Text behaves like text.** Selectable where it is content, with the platform's
  own selection colour and context menu.

## 9. The app icon

The mark is a gold progress ring forming the bowl of a Q, crossed at the lower
right by a teal tail: the same ring the app draws around a skill level, so the
icon and the UI say one thing. Rules, now that the asset exists:

- **`packaging/icon/quests.svg` is the master.** Never edit the PNGs, never
  re-derive the geometry in code. `generate_icon.py` only rasterises.
- **Optical sizing below 32px.** `quests-small.svg` drops the dim track and
  thickens the strokes, because at 16px the two-tone ring merges into a smudge.
  Same mark, different amount of room.
- **macOS gets Apple's grid** — 824×824 of artwork centred in a 1024 canvas —
  derived at render time from the same master, because the Dock composes its own
  shadow into that margin and a full-bleed icon looks oversized beside
  everything else in it.
- **Retina slots are filled.** The `.icns` carries `ic11`–`ic14` as well as the
  1x codes; without them the Dock upscales and a soft icon there is the clearest
  possible sign of an app that was packaged rather than designed.
- The icon is checked at 16, 32 and 256 on both a light and a black background
  before any change to it is committed.

---

## How this gets used

When a screen or component is built or revised, it is checked against every
section above before it is called done — the same way a change is checked
against the testing rules in SPEC §8.7. Sections 1, 3 and 6 can largely become
tests; the rest are read with the running app in front of you.

Nothing here is exhaustive. When a fourth icon size or a seventh colour role
turns out to be genuinely necessary, that is a deliberate addition to this
document, made once and applied everywhere — not a one-off override quietly
left in a single file.
