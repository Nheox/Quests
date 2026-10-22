# Balance design

Every number in this document is ours, designed from first principles. No
formula from the app that inspired Quests is known to us, and none is
reproduced ([SPEC.md §2](SPEC.md#2-provenance-and-licensing-posture)).

Implemented in
`composeApp/src/commonMain/kotlin/cl/nheox/quests/domain/Balance.kt`, with a
table-driven test per rule in `BalanceTest.kt`.

## Rating scales

**Difficulty** (7): Trivial, Very low, Low, Moderate, High, Very high, Extreme.
**Fear** (5): None, Mild, Moderate, Strong, Severe.
**Urgency** (5): Whenever, Low, Normal, High, Critical.

## Experience

```
baseXp      = 10
difficultyM = [0.5, 0.75, 1.0, 1.5, 2.25, 3.5, 5.0]
fearBonus   = [1.00, 1.10, 1.25, 1.45, 1.70]
durationF   = clamp(sqrt(estimatedMinutes / 30), 0.5, 4.0)

xp = round(baseXp × difficultyM × fearBonus × durationF)
```

Distributed as evenly as whole numbers allow across the linked skills, with the
remainder going to the first skills in a stable alphabetical order. The parts
always sum back to the total — a test asserts it — because a distribution that
leaked XP would make replay non-reproducible.

Three deliberate absences:

- **Urgency does not affect XP.** Paying more for urgent work rewards leaving
  things until they are urgent. Urgency drives sorting and the suggester only.
- **Energy does not affect XP.** Someone who does the work while tired has
  earned more, not less.
- **A missing duration estimate is treated as the 30-minute reference**, not as
  zero. Otherwise the app quietly punishes capturing a quest quickly, which is
  the behaviour it most wants to encourage.

The difficulty ramp is deliberately super-linear: an Extreme quest is worth ten
Trivial ones, so breaking a hard thing into small pieces never pays better than
doing the hard thing.

## Reward points

```
rewardPoints = round(2 × difficultyM × durationF) + (rewardBonus ?: 0)
```

Fear is excluded, so the spendable economy stays legible and does not track the
XP curve. Earned far more flatly than XP: a quest paying 65 XP pays 9 points.

`rewardBonus` is an optional, hand-set premium for a quest the formula
undervalues — usually empty. It adds points and never XP: points are openly
fictional and exist to be spent, while XP is the record of what was actually
done. A negative bonus (only a hand-edited log can produce one) counts as none,
because completing a quest never costs anything.

## Level curve

```
xpForLevel(n) = round(100 × n^1.6)      // cumulative
```

| Level | Total XP | Level | Total XP |
|---:|---:|---:|---:|
| 1 | 0 | 10 | 3 981 |
| 2 | 303 | 20 | 12 068 |
| 3 | 580 | 50 | 52 282 |
| 4 | 919 | 100 | 158 489 |
| 5 | 1 313 | | |

Total XP is the stored truth and the level is always derived, so retuning this
curve is non-destructive: it changes what a given XP total is called, never the
total itself.

> **Note on SPEC.md §5.3.** The specification prints this formula and then a set
> of worked examples beside it. Its figures for levels 2 and 10 (303 and 3 981)
> match the formula; its figures for levels 5, 20 and 50 (1 210, 12 379 and
> 56 234) do not — the formula gives 1 313, 12 068 and 52 282. The formula is
> the normative part and is what is implemented. The table above is what it
> actually produces. Worth a decision at some point: either correct the examples,
> or retune the exponent to around 1.62, which is roughly what those three
> figures imply.

## Priority (automatic ordering)

The default order of every list of open quests. Recomputed on read, never
stored, so retuning a weight reorders every list without touching a log line.
Implemented in `domain/Priority.kt`, with every weight and bucket boundary
pinned in `PriorityTest.kt`.

```
urgencyW = [0, 1, 2, 4, 7]              // Whenever … Critical
fearW    = [0, 0.4, 0.9, 1.5, 2.2]      // None … Severe
dueW:
  overdue       → 6 + min(daysOverdue, 10) × 0.4
  due today     → 5
  due tomorrow  → 3
  due ≤ 3 days  → 2
  due ≤ 7 days  → 1
  later or none → 0
ageW     = min(daysSinceCreated × 0.05, 3.0)

priority = urgencyW + dueW + fearW + ageW
```

Days are whole calendar days in the device's time zone. "Overdue" means what it
means everywhere else in the app: a quest due today at 09:00 is overdue at
10:00, by zero days, so it scores 6 and outranks one merely due today.

- **Fear pushes a quest up, not down.** Surfacing the avoided thing is the
  point; the suggester is where fear meets energy.
- **Age is a slow ramp with a ceiling.** Something untouched for months drifts
  upward, but 3.0 can never outrank a genuine emergency.
- **Difficulty is absent.** It only breaks ties: among equal scores the shorter
  estimate comes first (no estimate counts as the 30-minute reference), then
  the title ignoring case, then the id, so the order is total and two devices
  always agree on it.

**Pins** override the score within a sibling group. `manualOrder` is null for
almost every quest. "Pin to top" sets it to `min(0, lowest sibling pin) − 1`,
"pin to bottom" to `max(0, highest sibling pin) + 1`, and "clear pin" back to
null, so the automatic block sits at 0 between the two: top pins ascending,
then everything else by score, then bottom pins ascending. Reversing the sort
direction mirrors the whole list, pins included. Before 0.5 every quest carried
a creation-order value at creation; that value is ignored, because it never
meant "pinned". Old explicit moves still read sensibly: values at or below 0 are
top pins, values above 0 bottom pins.

## Energy

- **Auto** (the default): `20 + 80 × (1 − t)^1.25`, where `t` is the fraction
  of the day elapsed between the wake-up time and bedtime — 100 at wake-up, 20 at
  bedtime, on a gentle curve that falls faster late in the day than early.
  Between bedtime and wake-up it sits at 5. (Stored as `SLEEP_SCHEDULE`, its
  name in older logs.)
- **Manual**: the user sets 0–100.

A day that crosses midnight — a bedtime after 00:00, or a night shift — is
handled, because plenty of people have one, and the suggester would otherwise
be useless for exactly the people most likely to need it.

Energy feeds the energy bar and the suggester, and nothing else.

## Failure

Failing a quest is recorded and, by default, costs nothing
([SPEC.md §10 Q2](SPEC.md#10-open-questions)). There is a setting —
*Failing a quest costs its reward points* — for anyone who wants stakes. The
default is off because the point of an explicit failure outcome is an honest
record, and a punishment attached to honesty buys dishonesty.

## The suggester

Not a formula so much as a set of weights, in
`domain/Suggester.kt`. The design claim it encodes:

> Fear-heavy work should be pushed into high-energy windows and kept out of
> low-energy ones. Avoidance is not a willpower problem to be nagged about; it
> is a scheduling problem, and the app knows the schedule.

So at high energy a frightening quest scores *up*, and at spent energy it scores
sharply *down* while a gentle one scores up. Work that does not fit the minutes
available is penalised heavily. Every suggestion carries the reasons it was
chosen, because a suggester that cannot explain itself gets ignored.

## Play-testing notes

These are starting points, not settled values. The things most likely to need
adjusting after real use:

- `fearBonus` for Severe (1.70) may be too low. If the fear log shows
  high-fear quests still being avoided after a few months of use, the reward for
  facing them is not yet doing its job.
- The duration clamp at 4.0 means a full day of work pays the same as eight
  hours. Possibly correct — a quest that takes longer than a day probably wants
  breaking up — but it is an assumption.
- The point economy has no sink other than rewards the user invents. Escalating
  cost is the only brake.
