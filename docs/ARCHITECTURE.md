# Architecture

## Stack

- **Kotlin 2.4.21**, **Compose Multiplatform 1.12.2**, desktop (JVM) target only.
- **Persistence**: append-only JSONL event logs on disk. No database.
- **Serialization**: `kotlinx.serialization`.
- **Concurrency**: `kotlinx.coroutines`.
- **Dependency injection**: none. Manual constructor injection
  ([SPEC.md §8.1](SPEC.md#81-stack) marked this `[decide]`; decided: the project
  has exactly one of each long-lived object, and a container would be machinery
  in place of four lines in `Main.kt`).
- **Charts**: hand-drawn on Compose `Canvas`. No chart library — three charts at
  under a hundred lines each, against a dependency with its own theming,
  animation opinions and release cadence.
- **Network**: none. Not "disabled" — there is no networking code in the
  application at all.

## Project shape

```
composeApp/src/
  commonMain/kotlin/cl/nheox/quests/
    domain/      Quest, Skill, Reward, enums, XP and level maths, energy,
                 repetition, views, sorting, quick-add parser, suggester, stats
    events/      event types, hybrid logical clock, JSONL reader/writer,
                 device identity, compaction
    projection/  replay into in-memory state; the store (command surface)
    export/      iCalendar and Markdown generation
    i18n/        Strings interface + English and Spanish implementations
    ui/          design system, screens, command palette, keyboard map
  desktopMain/kotlin/cl/nheox/quests/
    Main.kt      entry point, CLI, windows, tray
    platform/    paths, single instance, notifications, reminder scheduler,
                 GNOME keybinding, file dialogs
  commonTest/    balance, HLC, replay determinism, quick-add, compaction,
                 export, suggester, stats, store
  desktopTest/   single instance, keybinding parsing, paths
```

Only the desktop target is enabled. Adding `androidTarget()` later is a target
declaration rather than a restructure: everything above `desktopMain` is
platform-neutral Kotlin.

> **One deviation worth naming.** `commonMain` uses `java.time` directly, which
> is not portable Kotlin. With a single JVM target this compiles and is by far
> the most readable option, and [SPEC.md §7.6](SPEC.md#76-change-from-the-original)
> specifically calls for `java.time`. On Android it works through core library
> desugaring. If a non-JVM target is ever added, these types are confined to
> `domain/Dates.kt`, the `Clock` interface and the model's field types, which is
> the smallest surface this could have been kept to.

## The event log is the only source of truth

Nothing in the app holds authoritative state. Every change — including every
settings toggle — is an event appended to this device's log; the in-memory state
is a projection, rebuilt by replaying the folder.

That choice pays for itself four times over:

1. **Sync has nothing to merge.** See [SYNC.md](SYNC.md).
2. **Undo is nearly free.** The inverse of an event is another event.
3. **History exists.** The fear log and trend charts need to know what a quest's
   fear rating *was when it was finished*, which a current-state store throws
   away.
4. **The user can read and repair their own data**, which is the promise in
   [SPEC.md §3.3](SPEC.md#3-core-concept).

### Last-write-wins, per field, for free

Changes travel as a map of changed fields rather than a replacement object, so
two devices that edit the title and the due date of the same quest while both
offline each keep their edit. An explicit JSON `null` means "clear this field";
an absent key means "leave it alone".

Because the projector sorts events by hybrid logical clock before folding, a
later edit simply overwrites an earlier one as it goes. There is no separate
per-field clock table to maintain and therefore no way for it to disagree with
the log.

The wire format is dynamic, but the code that produces and consumes it is not:
writers go through `FieldPatch`, whose setters are named and typed, and the
projector reads through an exhaustive `when` over the same constants.

### Payouts are recorded, not recomputed

`QuestCompleted` carries the XP and points it paid and the per-skill
distribution. Recomputing from the current formula at replay time would mean
retuning the balance silently rewrote history — a skill levelled last year could
un-level itself after an update.

## Threading

Writes are append-then-project, in that order: if the write fails (a full disk, a
sync folder on an unmounted drive) the exception propagates and in-memory state
is left alone, so the UI can never show a change that is not on disk.

The hybrid logical clock is an `AtomicReference`, because reminders fire from a
background coroutine while the UI writes from the main one.

## Performance

Replay is a fold over a sorted list. For a personal quest list — thousands of
quests, tens of thousands of events — this is milliseconds, and the whole state
is immutable so the UI can never observe a half-applied change. A persisted
index (SQLDelight) gets added only if replay measurably stops being fast enough.

The seeded 160-event test vault replays in under a millisecond; the largest
realistic case we have modelled, 50 000 events, replays in well under a second.

## What is and is not built

Implemented: M1 through M5 of [SPEC.md §9](SPEC.md#9-milestones) — event log and
projection, quest CRUD, the three ratings, complete/fail/reopen, skills and
derived levels, all views and sorts, keyboard navigation, single-instance
plumbing, `quests --capture`, quick capture, quick-add parsing, the command
palette, due dates, repetition, reminders, tray presence, `.ics` and Markdown
export, hierarchy and the Plan tree, scratchpad with promotion, search, bulk
operations, undo, reward points, rewards with escalating cost, inventory, stats,
radar and trend charts, the fear log, the suggester, compaction, `.deb`
packaging, and English and Spanish.

Not built:

- **macOS signing and notarisation.** Configured in Gradle; needs an Apple
  Developer certificate to actually run.
- **Flathub submission.** The manifest is in `packaging/flatpak`; submitting it
  needs a published release to point at.
- **The GlobalShortcuts portal spike** ([SPEC.md §8.3](SPEC.md#83-known-risks)).
  The keybinding fallback is implemented and is the plan of record; the spike to
  see whether the portal can be driven over D-Bus from an XWayland JVM client has
  not been done.
- **Markdown *rendering* in notes.** Notes are stored and exported as Markdown;
  the editor shows them as plain text.
- **Per-event completion sounds.** One on/off toggle, per
  [SPEC.md §7.3](SPEC.md#73-drop) — still marked `[decide]` there.
