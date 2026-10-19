# Sync and storage

Quests has no server and never will. The sync model is Logseq's, with one
change that removes the failure mode Logseq users actually hit.

## The folder

```
~/Quests/                        a folder you choose
  events-macbook.jsonl           one append-only log per device
  events-ubuntu.jsonl
  snapshots/
    macbook-00001.json           compaction checkpoints
  export/
    quests.ics                   generated calendar feed
    quests.md                    generated human-readable snapshot
```

Sync it with Syncthing, Git, Nextcloud, iCloud Drive, Dropbox — whatever you
already run. Quests itself contains no networking code.

## One writer per file

This is the whole idea.

Logseq writes the same files from every device, which is why Logseq users
collect `.conflict` and `.sync-conflict` files. Here, **a log file is only ever
written by the device that owns it.** No sync tool ever observes a concurrent
write to one file, so there is nothing to merge at the file level — only a union
to replay.

The cost is that a device's name has to be stable for the life of the machine,
which is why it is written to `~/.config/quests/device` rather than derived from
the hostname at each launch. A test asserts that appending never touches another
device's file.

## Ordering

Each line carries a hybrid logical clock: a wall-clock reading for a human
reading the file, a logical counter that breaks ties inside a millisecond, and
the device id.

Wall clocks alone are not trustworthy across devices — laptops sleep, NTP
corrects backwards, time zones change. The HLC gives a *total* order (the device
id is the final tie-break), which is what makes replay deterministic regardless
of which log happened to be read first. That is the property sync correctness
rests on, and `ReplayTest` asserts it directly by shuffling the event list
twenty-five times and comparing the projections.

When a device reads logs, it folds the highest clock it saw into its own, so
everything it writes afterwards sorts after everything it has already read. The
encoding (`0001760000000000:00042:macbook`) is designed so lexicographic string
order matches logical order, and `HlcTest` checks the two agree across hundreds
of random pairs.

## Conflict resolution

- **Last write wins, per field**, resolved by HLC. Two devices editing different
  fields of the same quest while offline both keep their edit.
- **Tombstones for deletions**, never a physical delete. A tombstone beats a
  late-arriving creation, so an old log syncing in cannot resurrect a deleted
  quest. The tombstone keeps the quest's last known contents, so undoing a
  delete restores it exactly.
- **Additive values** — skill XP, the point balance, item quantities, claim
  counts — are applied as deltas, so two devices both contributing is a sum
  rather than a race.

## Event shape

One JSON object per line, with `type` as the discriminator:

```jsonc
{"type":"QuestCreated","ts":"2026-10-18T09:14:02.511Z","hlc":"0001792347242511:00000:macbook",
 "device":"macbook","id":"q_01M58MKCHJCCD9QMQSBPEVEP2A","title":"Write the methods section",
 "difficulty":"HIGH","fear":"STRONG","urgency":"HIGH","estimatedMinutes":120,"dueDate":"2026-10-18",
 "skillIds":["s_thesis"]}
{"type":"QuestChanged","ts":"...","hlc":"...","device":"macbook","id":"q_01M58...","fields":{"dueDate":null}}
{"type":"QuestCompleted","ts":"...","hlc":"...","device":"macbook","id":"q_01M58...","at":"...",
 "xp":65,"points":9,"skillXp":{"s_thesis":65}}
```

Defaults are omitted to keep lines readable, and unknown fields are ignored on
read, so a folder synced between machines running different versions stays
readable by both.

`SPEC.md` §6.3 sketches the key order as `ts, hlc, device, type, …`;
`kotlinx.serialization` writes the discriminator first. Same keys, same meaning.

## Editing the files by hand

Supported, within reason. Reads are deliberately forgiving: a line that will not
parse, or whose clock is unreadable, is skipped and reported in Settings rather
than taken as a reason to abandon the history. One bad line costs one line.

Things that will not work: inventing a clock that sorts before an existing
event's and expecting it to win; deleting a line and expecting the effect to
disappear (the log is a history, not a state file — append a compensating event
instead).

## Compaction

Logs would otherwise grow forever. Each device periodically rewrites **its own**
log, dropping field edits that its own later edits have superseded, and leaves a
snapshot behind first. Because every file has one writer, this needs no
coordination with any other device.

What is dropped is narrow on purpose. Only superseded field edits go.
Completions, failures, claims and tombstones all stay, because that history is
the data behind the fear log and the trend charts — a tracker that compacted
away the record of what you did would be compacting away the point.

The safety argument, which `CompactionTest` asserts as an executable claim:
suppose this device set field F at clock 5 and again at clock 20, and another
device set F at clock 12. Full replay applies 5, then 12, then 20, so 20 wins.
Compacted replay applies 12 and 20, and 20 still wins. Any edit that lost to a
later edit *from the same device* loses to that same edit in every interleaving,
so removing it cannot change the outcome.

The snapshot is written before the log is replaced, so a crash between the two
loses nothing: load unions both and deduplicates by clock.

## Backups

The folder is the backup. Copy it anywhere; it is text. `export/quests.md` is a
readable snapshot of the current state if you want something to skim without the
app.
