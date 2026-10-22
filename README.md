# Quests

A local-first, gamified quest tracker for real life, for **macOS and Linux
desktop**. Rate things by how hard they are, how *frightening* they are, and how
urgent — then watch what you actually did, instead of what you meant to do.

Inspired by the discontinued LifeRPG, built fresh from an independent
specification ([docs/SPEC.md](docs/SPEC.md)). Nothing in this repository is
derived from that app's code, assets, strings or balance numbers
([§2](docs/SPEC.md#2-provenance-and-licensing-posture)).

## What makes it different from a to-do list

- **Fear is a first-class rating**, separate from difficulty. A two-minute phone
  call can be terrifying; a four-hour spreadsheet can be trivial but dull.
  Tracking the two separately lets the app reward emotional difficulty, which is
  where procrastination actually lives — and lets it tell you, later, that you
  finish 88% of your low-fear quests and 25% of your high-fear ones.
- **Failure is an explicit outcome.** A quest can be *failed*, not merely left
  undone. By default that costs nothing; it just keeps the record honest.
- **The list orders itself.** Rate a quest once and the app decides what rises:
  urgency, how soon or how late it is due, fear and age add up to a priority
  score that every quest shows itemised. Disagree, and `[` or `]` pins a quest
  to the top or bottom of its list; `\` lets it go again.
- **You own the files.** Plain JSONL in a folder you choose, readable without
  the app. No account, no server, no telemetry, and no network code at all.
- **Keyboard first.** Every action has a key, and `Ctrl+K` lists them.

## Status

Early but genuinely usable. Milestones M1–M5 of the specification are
implemented: the event log and projection, quest CRUD with the three ratings,
complete/fail/reopen, skills and derived levels, all the views and sorts,
hierarchy, repetition, reminders, the `.ics` calendar feed, quick capture and
quick-add parsing, the command palette, undo, rewards and inventory, and the
insight screens including the fear log. See
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md#what-is-and-is-not-built) for what is
not built yet.

## Install

### Linux

Download the `.deb` from the releases page, or build it yourself:

```sh
./gradlew packageDeb
sudo dpkg -i composeApp/build/compose/binaries/main/deb/quests_*.deb
```

Needs `fakeroot` and `dpkg` present to build the package. A Flatpak manifest
lives in [`packaging/flatpak`](packaging/flatpak).

### macOS

```sh
./gradlew packageDmg
```

Signing and notarisation are not yet configured; see
[`packaging/macos`](packaging/macos).

### From source

```sh
./gradlew :composeApp:run     # run it
./gradlew :composeApp:desktopTest    # 193 tests
```

Requires JDK 21.

## Your data

Everything lives in one folder. The first launch asks where, offering `~/Quests`
or a folder of your own; Settings can change it later:

```
~/Quests/
  events-macbook.jsonl     one append-only log per device
  events-ubuntu.jsonl
  snapshots/               periodic compaction checkpoints
  export/
    quests.ics             generated calendar feed
    quests.md              generated human-readable snapshot
```

Sync that folder with whatever you already use — Syncthing, Git, Nextcloud,
iCloud Drive, Dropbox. **A log file is only ever written by the device that owns
it**, so no sync tool ever sees a concurrent write and there is nothing to
merge. Each device reads every log it can see and replays the union, ordered by
a hybrid logical clock. See [docs/SYNC.md](docs/SYNC.md).

## Reminders that actually reach you

Desktop notifications only fire when the machine is awake and you are sitting at
it. So Quests writes `export/quests.ics` into your folder, containing every
quest with a due date and its reminders. Subscribe your phone or system calendar
to that file and alerts arrive through an app you already trust to interrupt
you — without Quests ever running on your phone or touching a network.

## Global quick capture

Quests does not grab a global hotkey, because on Wayland an application cannot
reliably do so. Instead, bind a key in your desktop's own keyboard settings to:

```
quests --capture
```

The running instance opens its capture window. This works identically on X11 and
Wayland, on GNOME, KDE and wlroots compositors. Settings can install the binding
for you on GNOME. The reasoning is in [docs/HOTKEY.md](docs/HOTKEY.md).

## Quick-add syntax

```
write methods tomorrow 3pm #thesis !! ~2h
```

| Token | Meaning |
|---|---|
| `#thesis`, `#"lab work"` | link a skill, creating it if new |
| `*` … `*******` | difficulty, trivial to extreme |
| `^` … `^^^^` | fear, mild to severe |
| `!`, `!!` | urgency: high, critical |
| `d:high` `f:strong` `u:low` | the same three, spelled out |
| `~2h` `~90m` `~1h30m` `~45` | estimated duration |
| `today` `tomorrow` `mon`…`sun` | due date |
| `next week` `+3d` `+2w` | relative due date |
| `2026-10-20` `20/10` | absolute due date, day first |
| `3pm` `15:00` `at 9:30am` | due time |
| `every 2 weeks` `daily` | repetition |

## Licence

[GPLv3](LICENSE). Third-party attributions in [NOTICE](NOTICE).

Donations only — no paid features, ever. That is what keeps the server, the
accounts, the privacy policy and the liability out of the design.
