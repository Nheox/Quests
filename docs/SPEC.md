# Quests — Feature Specification v0.4

An open-source, local-first gamified quest tracker for **macOS and Linux desktop**,
inspired by the discontinued LifeRPG. This document is the independent
specification we build from. It describes *what* the app does and *why*, in our
own terms.

Changelog from v0.3: Linux packaging settled — **`.deb` plus Flathub**, Snap
declined (§1). Added **§8.3 Known risks**, documenting a serious problem with the
global hotkey on Wayland and the fallback plan of record.

Changelog from v0.2: name settled — "Quests"; core entity renamed `Task` →
`Quest`; identifiers fixed (§1.1).

Changelog from v0.1: Android and iOS dropped; desktop-only. Licence settled
(GPLv3). Map feature dropped. Sync architecture added (Logseq-style, no server).
Revenue model settled (donations only). Database deferred in favour of
event-log replay.

Status: draft. Items marked **[decide]** still need your input.

---

## 1. Decisions settled

| Question | Decision | Why |
|---|---|---|
| **Name** | **Quests** | Clear and easy to type. Known trade-off: poor discoverability against games and Meta Quest — accepted, since this is built primarily for its author. The original app called its items *Missions*, so there is no vocabulary overlap. |
| **Platforms** | macOS + Linux desktop. Windows unsupported-but-working via the same JVM target. | Mintal keeps the phone out of reach during focus time, so a phone-first tracker is the wrong shape for the actual workflow. |
| **Android / iOS** | Dropped. Architecture stays multiplatform-ready so adding `androidTarget()` later is a few lines, not a rewrite. | Direct consequence of the above. |
| **Stack** | Kotlin + Compose Multiplatform, desktop (JVM) target only for now. | Both Android and desktop JVM are Stable; keeps the door open; one language end to end. |
| **Licence** | **GPLv3** | Keeps improvements open. The original app vanished and nobody could rescue it; this licence stops that happening to ours. Sole copyright holder, so revisitable until outside contributors arrive. |
| **Revenue** | None. Donations only (Liberapay / GitHub Sponsors). | Removes the need for a server, accounts, a privacy policy, and any liability for user data. |
| **Distribution** | **Linux:** `.deb` on GitHub Releases *and* Flathub. **macOS:** signed, notarised `.dmg` on GitHub Releases. | `.deb` is free from the Compose plugin, unconfined, and covers the author's own Ubuntu machine. Flathub reaches every other distro with one package. |
| **Snap** | **Declined.** | Three packages to maintain solo for no benefit the author would feel; slower startup for a JVM app; strict confinement makes the global-hotkey workaround harder (§8.3). Revisitable if users ask. |
| **Sync** | Logseq-style: plain files in a user-chosen folder, bring your own sync. No server, ever. | Follows from donations-only. See §6. |
| **Storage** | Append-only JSONL event logs are the source of truth. No database in v1. | Makes conflict-free sync possible and gives undo and history for free. |

### 1.1 Identifiers

```
repo         github.com/<you>/quests
binary       quests
Flathub ID   io.github.<you>.Quests
package      cl.nheox.quests        # or io.github.<you>.quests
sync folder  ~/Quests/
```

The app ID must be reverse-DNS and backed by an installed `.desktop` file — not
cosmetic, see §8.3. Availability still to verify: GitHub repo name, Flathub app
ID, a brief trademark glance.

---

## 2. Provenance and licensing posture

**Derived from.** A factual inventory of the original app's resource *name table*
(extracted from `resources.arsc`), plus your own experience as a long-term user,
plus public write-ups.

**Deliberately not used.** No decompiled source (`smali`) was read. No layout XML,
drawable, icon, sound, or user-facing string from the original was copied or
consulted for wording. No XP, level, or cost formula from the original is known
to us, and none is reproduced.

**Rules for the build:**

- All strings written fresh. No phrase lifted from the original.
- All icons and artwork original or permissively licensed (e.g. Material Symbols,
  Apache-2.0 — attribution recorded in `NOTICE`).
- All balance numbers designed by us from first principles — see §5.
- Nothing from the original APK in the repository, **including commit history**.
- App name, icon and branding entirely our own.

---

## 3. Core concept

The user maintains a list of real-life quests. Each carries subjective ratings of
how hard, how frightening, and how urgent it is. Completing a quest grants
experience to the skills it is linked to, plus a spendable currency. Skills level
up over time, producing a profile of what the user actually did rather than what
they intended to do.

Four design commitments distinguish this from a generic to-do app:

1. **Fear is a first-class dimension**, separate from difficulty. A two-minute
   phone call can be terrifying; a four-hour spreadsheet can be trivial but dull.
   Tracking fear separately lets the app reward emotional difficulty, which is
   where procrastination actually lives.
2. **Failure is an explicit outcome.** A quest can be *failed*, not merely left
   undone. This keeps the record honest and lets the app reason about patterns.
3. **The user owns the files.** Plain text on disk, in a folder they choose,
   readable without the app. No account, no server, no telemetry.
4. **Keyboard first.** This is a desktop app, not a phone app with a bigger
   window. Everything reachable without the mouse.

---

## 4. Domain model

```
Quest
  id, title, notes
  difficulty   : enum  (7 levels, §5.1)
  fear         : enum  (5 levels)
  urgency      : enum  (5 levels)
  estimatedDuration : Duration?
  dueAt        : Instant?          // date, optionally with time
  repetition   : Repetition?
  parentId     : Quest.id?         // hierarchy
  manualOrder  : Int
  state        : OPEN | DONE | FAILED
  completedAt, lastWorkedAt : Instant?
  rewardPoints : Int               // payout on completion
  skillIds     : Set<Skill.id>     // many-to-many

Skill
  id, name, category?, icon, notes
  totalXp : Long                   // level is derived, never stored

Reward
  id, name, icon, cost : Int
  costIncrement : Int?             // escalating price...
  incrementEvery : Int?            // ...after every N claims
  claimCount, lastClaimedAt

InventoryItem
  id, name, icon, quantity : Int
  consumable : Bool
  lastUsedAt : Instant?

ScratchpadEntry                    // lightweight checklist
  id, questId?, text, done
  // promotable into a full Quest

Profile
  displayName, avatar
  rewardPointBalance : Int
  energyMode : MANUAL | SLEEP_SCHEDULE
  wakeTime, sleepTime
  manualEnergy : Int?
```

`location` is removed — the map feature is dropped (§7.3).

Total XP is the stored truth and level is computed, so curve changes are
non-destructive.

---

## 5. Balance design (ours, from scratch)

All numbers are our own invention and are starting points for play-testing.

### 5.1 Rating scales

**Difficulty** (7): Trivial, Very Low, Low, Moderate, High, Very High, Extreme.
**Fear** (5): None, Mild, Moderate, Strong, Severe.
**Urgency** (5): Whenever, Low, Normal, High, Critical.

### 5.2 XP formula

```
baseXp      = 10
difficultyM = [0.5, 0.75, 1.0, 1.5, 2.25, 3.5, 5.0]       // by difficulty index
fearBonus   = [1.00, 1.10, 1.25, 1.45, 1.70]              // by fear index
durationF   = clamp(sqrt(estimatedMinutes / 30), 0.5, 4.0) // 1.0 at 30 min

xp = round(baseXp × difficultyM × fearBonus × durationF)
```

Distributed evenly across linked skills; with none linked it counts toward
overall progress only.

**Urgency deliberately does not affect XP.** Paying more for urgent work rewards
leaving things until they are urgent. Urgency drives sorting and the suggester.

### 5.3 Level curve

```
xpForLevel(n) = round(100 × n^1.6)      // cumulative
// L2 at 303, L5 at 1210, L10 at 3981, L20 at 12_379, L50 at 56_234
```

### 5.4 Energy

- **Manual**: user sets 0–100.
- **Sleep schedule**: decays from 100 at wake time to 20 at sleep time on a
  gentle curve; near zero during sleeping hours.

Energy **never changes XP**. It feeds the suggester (§7.4) and the energy bar
only. Keeping it out of the reward maths avoids punishing someone for working
while tired.

### 5.5 Reward points

```
rewardPoints = round(2 × difficultyM × durationF)    // fear excluded
```

Earned more flatly than XP so the economy stays legible. Failing a quest costs
nothing but records the failure; no point penalties. **[decide]**

---

## 6. Sync and storage architecture

### 6.1 The Logseq model, adapted

The user picks a folder. Everything lives there as plain text. The app holds no
authoritative state of its own — it replays the folder on launch.

```
~/Quests/                          # user-chosen location
  events-macbook.jsonl             # one log per device, append-only
  events-ubuntu.jsonl
  snapshots/
    macbook-00042.json             # periodic compaction checkpoints
  export/
    quests.ics                     # generated calendar feed (§7.5)
    quests.md                      # generated human-readable snapshot
```

The user syncs that folder with whatever they already use — Syncthing, Git,
Nextcloud, iCloud Drive, Dropbox. Quests never talks to a network.

### 6.2 Why one log per device

Logseq writes the same files from every device, which is why Logseq users hit
conflict files in practice. Here, **a file is only ever written by the device
that owns it**, so no sync tool ever sees a concurrent write. There is nothing
to merge at the file level.

Each device reads every log it can see and replays the union.

### 6.3 Event shape

```jsonc
{"ts":"...","hlc":"...","device":"macbook","type":"QuestCreated",
 "id":"q_01H…","title":"Write methods section","difficulty":"HIGH","fear":"STRONG"}
{"ts":"...","hlc":"...","device":"macbook","type":"QuestCompleted","id":"q_01H…"}
```

- **Hybrid logical clocks** order events across devices whose wall clocks
  disagree. Wall-clock timestamps alone are not trustworthy.
- **Last-write-wins per field** for genuine collisions, resolved by HLC.
- **Tombstones** for deletions; never a physical delete in the log.

### 6.4 Projection

No database in v1. On launch the app replays all logs into in-memory state. For
a personal quest list — thousands of quests, tens of thousands of events — this
is milliseconds. A persisted index (SQLDelight) gets added only if it measurably
stops being fast enough.

### 6.5 Compaction

Logs grow forever otherwise. Each device periodically writes a snapshot of its
own view and truncates its own log behind it. Because snapshots are per-device
too, compaction needs no coordination. Deferred until after M1.

---

## 7. Feature inventory

**Keep** (present in the original, worth having), **Change**, **Drop**, **New**.

### 7.1 Keep — quest management

Create, edit, duplicate, delete. Complete, fail, reopen. Difficulty / fear /
urgency. Estimated duration. Due date, optionally with a time. Free-text notes.
Many-to-many skill links, with batch assignment across a selection. Hierarchy:
break into sub-quests, re-parent, promote to top level, export a branch. Manual
ordering with move-to-top / move-to-bottom. Reschedule shortcuts (today,
tomorrow, +1 week, +1 month, next repetition, pick date, pick time, clear due
date). Bulk operations on a multi-selection. Search. Pick a random open quest.
Share a quest as text.

### 7.2 Keep — views, repetition, progression

**Views.** Today, Tomorrow, Overdue, Next, Plan (tree), All, plus completed and
last-seven-days. Sort by name, created, updated, due date, difficulty, fear,
urgency, duration, XP, XP-to-next-level, last worked, date completed, manual
order, random — each direction.

**Repetition.** Hourly, daily, weekly, monthly, yearly, and "every N" of each.

**Reminders.** Multiple per quest, as offsets before the due time or at an
absolute time. A dedicated pending-reminders list. See §7.5 for the delivery
problem.

**Progression.** XP on completion distributed to linked skills. Derived levels
with a level-up event. Reward points as a separate currency. Rewards with
optional escalating cost after every N claims. Inventory with quantities and a
consumable flag. Radar chart across selected skills. Skill categories and
per-skill notes.

**Scratchpad.** Quick checklist, per-quest and global, entries promotable into
real quests.

**Onboarding and help.** First-run introduction; in-app help.

### 7.3 Drop

- **Map and locations.** Was a paid unlock in the original, pulled in Google Maps
  and Places, and makes little sense on a desktop app. The `location` field goes
  with it.
- **Shop as a feature store.** Nothing gated behind purchase; no IAP.
- **Android home-screen widgets and launcher shortcuts.** No Android. Replaced by
  the desktop surfaces in §7.4.
- **Play Store rating prompt.**
- **Per-event sound file pickers.** One sounds on/off toggle instead. **[decide]** —
  you may have valued the four separate slots.
- **Icon-coloring / show-icons settings.** Unnecessary with a coherent design
  system; revisit only if missed.

### 7.4 New — desktop-native surfaces

These replace what we lose by leaving Android, and are better than it.

- **Global quick capture.** A system-wide shortcut opens a single input anywhere,
  types a quest, closes. The highest-value desktop feature — and the one with a
  real technical obstacle on Linux. **See §8.3 before implementing.** The plan of
  record is a desktop-environment keybinding invoking `quests --capture`, not an
  in-app key grab.
- **Quick-add parsing.** `write methods tomorrow 3pm #thesis !! ~2h` sets due
  date, skill, urgency and duration in one line.
- **Command palette.** Ctrl/Cmd-K for every action.
- **Full keyboard navigation.** No action mouse-only.
- **Tray / menu-bar presence** showing today's count, so reminders can fire while
  the main window is closed.
- **"What now?" suggester.** Given current energy, time of day, minutes
  available, and the fear/urgency of open quests, propose three candidates. The
  feature the original's energy model gestured at but never delivered.
- **Fear log.** Completion rate by fear level over time, making avoidance
  patterns concrete ("you finish 80% of low-fear quests and 22% of high-fear ones").
- **Trend charts.** XP per week, completion rate, skill growth.
- **Per-skill streaks.**
- **Markdown in notes.**
- **Bulk reschedule of overdue items** with a review step.

### 7.5 New — the reminder problem

Desktop notifications only fire when the machine is awake and the user is near
it. Combined with Mintal deliberately removing the phone, a 3pm reminder has no
reliable way to reach anyone.

**Solution: generate an `.ics` calendar feed** in the sync folder containing all
quests with due times. The user subscribes their phone's or system calendar to
that file. Alerts arrive through a calendar app they already trust, and Quests
never needs to exist on the phone.

Plus, on the desktop side: a daily digest notification at a configured time, and
an overdue summary on launch.

### 7.6 Change from the original

| Area | Original | Quests |
|---|---|---|
| Aesthetics | Material 1/2-era, action-bar heavy | Custom design system, modern, dark-first, built for a desktop window rather than a scaled-up phone screen. The stated reason for the rewrite. |
| Data | Opaque SQLite blob export | Plain JSONL as the source of truth, documented and versioned, plus a generated Markdown snapshot. |
| Undo | Confirmation dialogs | Snackbar undo for complete, fail and delete. The event log makes this nearly free. |
| Energy | Opaque multiplier | Same two modes, explicit documented effect (§5.4). |
| Dates | Joda-Time | `java.time`. |
| Accessibility | Unclear | Keyboard-complete, screen-reader labelled, dynamic type, from the start. |
| Localisation | Several languages | English and Spanish from day one; structured for Weblate later. |

---

## 8. Technical architecture

### 8.1 Stack

- **Language/UI**: Kotlin, Compose Multiplatform, desktop (JVM) target.
- **Project shape**: Compose Multiplatform project with `commonMain` holding
  domain, event log and projection; only the desktop target enabled. Adding
  `androidTarget()` later is a target declaration, not a restructure.
- **Persistence**: JSONL event logs on disk (§6). `kotlinx.serialization`.
  No database in v1.
- **Background work**: in-process coroutine scheduler for reminders; tray
  presence keeps it alive with the window closed.
- **Single instance**: a lock in `$XDG_RUNTIME_DIR` plus a local socket, so
  `quests --capture` reaches the running process instead of starting a second
  one. Required by the quick-capture design (§8.3).
- **Charts**: Compose `Canvas`, hand-drawn radar and trend charts. No chart
  library unless it earns its place.
- **DI**: manual constructor injection. The project is too small for Hilt/Koin. **[decide]**
- **Packaging**: Compose Desktop `jpackage` → `packageDeb` (Linux) and signed,
  notarised `packageDmg` (macOS). Flatpak manifest wrapping the same app image
  for Flathub.
- **No** network code of any kind in v1. This is a feature.

### 8.2 Repository layout

```
quests/
  composeApp/
    src/commonMain/kotlin/
      domain/        # Quest, Skill, Reward, enums, XP + level maths
      events/        # event types, HLC, log reader/writer
      projection/    # replay into in-memory state
      ui/            # Compose screens, design system
    src/desktopMain/kotlin/   # tray, single instance, file dialogs, notifications
    src/commonTest/kotlin/    # XP curve, HLC ordering, replay determinism
  docs/{SPEC.md,BALANCE.md,ARCHITECTURE.md,SYNC.md,CONTRIBUTING.md}
  packaging/{deb,flatpak,macos}/
  LICENSE            # GPLv3
  NOTICE             # third-party attributions
```

### 8.3 Known risks

**Global hotkeys on Linux are the biggest technical risk in the project.** Three
facts compound:

1. **mutter on GNOME ≥ 49 no longer honours XWayland global key grabs.** The
   traditional approach — grab a key via X11 and let XWayland forward it — now
   only fires while the app's own window has focus, which defeats the purpose.
2. **The replacement, the XDG GlobalShortcuts portal, expects a native Wayland
   client**, plus a reverse-DNS app ID backed by an *installed* `.desktop` file.
   Portal 1.21 rejects a session created with an empty app ID.
3. **OpenJDK has no shipped Wayland backend** (Project Wakefield is still
   unreleased), so Compose Desktop runs on AWT/X11 and is therefore always an
   XWayland client on Ubuntu. That puts it on the wrong side of both points above.

Additionally, `xdg-desktop-portal-wlr` has **no GlobalShortcuts backend at all**,
so Sway, Hyprland and Niri users cannot have a global shortcut by any route.

**Plan of record: don't grab keys in-app.** Register a desktop-environment
keybinding that runs `quests --capture`, and have the running instance open the
capture window over the single-instance socket (§8.1). The compositor owns the
shortcut, so no portal, no key grab, and no Wayland-client requirement. Works on
X11, Wayland, GNOME and KDE alike.

- On first run, offer to install the GNOME binding via `gsettings`
  (`org.gnome.settings-daemon.plugins.media-keys custom-keybindings`).
- If that write fails — likely inside the Flatpak sandbox — fall back to
  documented manual setup in Settings → Keyboard.
- macOS is unaffected; it has working global hotkey APIs.

**Spike, early M2, ~30 minutes:** test whether the GlobalShortcuts portal can in
fact be driven over D-Bus from an XWayland JVM client. The reading above may be
too pessimistic, and if it is, the portal route is nicer than the keybinding.
Either way the keybinding fallback must exist for wlroots users.

### 8.4 Testing from the start

- XP and level maths: pure functions, table-driven unit tests.
- HLC ordering: property tests that replay is deterministic regardless of
  log-read order.
- Replay: given a fixed set of logs, projection is byte-identical every time.
  This is the test that protects sync correctness.

---

## 9. Milestones

**M1 — Core loop.** Event log read/write, in-memory projection, quest CRUD,
difficulty/fear/urgency, complete and fail, skills, XP, derived levels, Today
and All views, sorting. Keyboard navigation from the first screen. Useful here.

**M2 — Capture and time.** Hotkey spike (§8.3), single-instance plumbing, quick
capture, quick-add parsing, command palette, due dates, repetition, reminders,
tray presence, Overdue and Tomorrow views, `.ics` export.

**M3 — Structure.** Hierarchy and the Plan tree, scratchpad with promotion,
search, bulk operations, undo.

**M4 — Economy.** Reward points, rewards with escalating cost, inventory.

**M5 — Insight.** Stats, radar chart, trend charts, fear log, the suggester.

**M6 — Ship it.** Compaction, `.deb` packaging, Flatpak manifest and Flathub
submission, macOS signing and notarisation, docs, Spanish localisation.

**Later (unscheduled).** Snap, if users ask. Android target if the workflow ever
changes. Persisted index if replay gets slow. Nothing else.

---

## 10. Open questions

1. Were the four separate completion sounds something you valued, or noise?
2. Failing a quest: purely recorded, or should it cost reward points?
3. Which sync tool do you actually use — Syncthing, Git, a cloud drive? It does
   not change the architecture, but it decides what the setup documentation says.
