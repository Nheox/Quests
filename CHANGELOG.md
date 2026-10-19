# Changelog

## 0.1.0 — unreleased

First working build. Milestones M1 through M5 of
[docs/SPEC.md](docs/SPEC.md#9-milestones), plus `.deb` packaging from M6.

### Core

- Append-only JSONL event log, one file per device, as the only source of truth.
- Hybrid logical clocks giving a total order, so replay is deterministic
  regardless of log read order.
- Last-write-wins per field; tombstones for deletions; additive values applied
  as deltas.
- In-memory projection, rebuilt by replaying the folder on launch.
- Compaction: a device rewrites its own log, dropping only superseded field
  edits, leaving a snapshot behind. Completion and failure history is never
  compacted away.

### Quests

- Difficulty (7), fear (5) and urgency (5), estimated duration, due date with
  optional time, notes, many-to-many skill links.
- Complete, fail, reopen, duplicate, delete, mark as worked on.
- Hierarchy with sub-quests, re-parenting, promotion to top level; the Plan tree.
- Repetition (hourly to yearly, and every N of each); completing a repeating
  quest spawns its next occurrence as a new quest, so the record of how often it
  was actually done survives.
- Reminders as offsets before the due time.
- Views: Today, Tomorrow, Overdue, Next, Plan, All, No due date, Completed,
  Failed, Last 7 days. Fourteen sort keys, both directions.
- Search, multi-select, bulk complete/fail/reschedule/skill-assignment/delete.
- Snackbar undo for complete, fail, delete and claim, replacing confirmation
  dialogs.

### Progression

- Skills with stored XP and derived levels, so retuning the curve is
  non-destructive.
- XP from difficulty × fear × duration. Urgency and energy deliberately excluded
  from every payout — see [docs/BALANCE.md](docs/BALANCE.md).
- Reward points, rewards with escalating cost after every N claims, inventory
  with consumable and permanent items.
- Energy, manual or from a sleep schedule, feeding the suggester and the bar and
  nothing else.

### Insight

- The fear log: completion rate by fear level, measured against everything ever
  raised at that level rather than only what was resolved, because avoidance
  shows up as never resolving a quest at all.
- XP per week, completion rate over time, per-skill growth and streaks, and a
  radar of where effort actually goes.
- "What now?" — three candidates given current energy, minutes available and the
  fear and urgency of what is open, each with the reasons it was chosen.

### Desktop

- Global quick capture via `quests --capture` handed to the running instance
  over a Unix domain socket, rather than an in-app key grab, which cannot work
  reliably on Wayland. See [docs/HOTKEY.md](docs/HOTKEY.md).
- Quick-add parsing: `write methods tomorrow 3pm #thesis !! ~2h`, in English and
  Spanish.
- Command palette (`Ctrl+K`) built from the same action list as the keyboard map.
- Full keyboard navigation with a visible focus ring.
- Tray presence with today's count, reminder notifications, daily digest and an
  overdue summary on launch.
- Generated `export/quests.ics` calendar feed and `export/quests.md` snapshot,
  written atomically after every change.
- A custom dark-first design system: original icon set drawn in code,
  hand-drawn charts, no icon font and no chart library.
- English and Spanish.

### Packaging

- `.deb` via jpackage, including the `/usr/bin/quests` symlink that global quick
  capture depends on.
- One application-menu entry, not two: the `.deb` no longer also registers the
  entry jpackage generates, and neither installing nor removing the package can
  fail over a menu entry.
- Flatpak manifest and AppStream metainfo, not yet submitted to Flathub.
- macOS `.dmg`, unsigned; signing and notarisation configured but not enabled.

### Known gaps

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md#what-is-and-is-not-built).
