# Contributing

Quests is GPLv3 and currently has a sole copyright holder. Contributions are
welcome; by sending one you agree it is licensed under the GPLv3.

## Building

Requires **JDK 21**. Everything else comes from the Gradle wrapper.

```sh
./gradlew :composeApp:run                 # run the app
./gradlew :composeApp:desktopTest         # the test suite
./gradlew packageDeb                      # a .deb (needs fakeroot, dpkg)
./gradlew createDistributable             # an app image, no packaging tools needed
```

The app writes to `~/Quests` by default. To run against a throwaway folder
without touching your real one:

```sh
./gradlew createDistributable
HOME=/tmp/quests-sandbox \
  ./composeApp/build/compose/binaries/main/app/Quests/bin/Quests
```

## House rules

**The event log is the only way anything changes.** If you find yourself
mutating `QuestsState` outside `Projector`, or writing a settings file beside
the logs, stop — there is a reason it is one mechanism, and a second source of
truth is a second thing that can disagree with the first.

**New state means a new event type, not a new field on an existing one** unless
the field genuinely belongs to that event. Add the field name to the relevant
`*Fields` object, a typed setter to `FieldPatch` if needed, and a branch to the
projector's `when`. The `when` is exhaustive on purpose.

**Payouts are recorded in events, never recomputed on replay.** Changing a
balance number must not rewrite what somebody already earned.

**Anything reachable by mouse must be reachable by keyboard.** Add it to
`Shortcuts`, to the command palette (they are built from the same list), or
both. Single-letter shortcuts must be suspended while `AppState.typing` is set.

**Never call `theme` or `strings` inside a non-composable lambda.** Capture them
at the top of the composable instead; the compiler error is unhelpful.

**Strings go in `i18n/Strings.kt` and `i18n/StringsEn.kt`.** Only English
ships, but no user-facing text is written inline: an interface of named
properties means a future translation's missing string is a compile error
rather than a blank label a user discovers.

## Tests

The suite is 193 tests and should stay green. Four areas carry more weight than
the rest, and a change that touches them wants a test with it:

- **`BalanceTest`** — the numbers a user's whole history is denominated in.
  Expected values are written out by hand from the published formula, not
  generated from the implementation.
- **`HlcTest`** — the total order that replay determinism rests on.
- **`ReplayTest`** — projection must be identical regardless of log read order.
  This is the test that protects sync correctness.
- **`IcsExportTest`** — tested against the letter of RFC 5545, because a feed
  that fails to import fails silently and takes the whole reminder story with it.

Run a single class with:

```sh
./gradlew :composeApp:desktopTest --tests '*ReplayTest*'
```

## Documentation

`docs/SPEC.md` is the specification Quests is built from and is treated as
normative. Where the implementation departs from it, the departure is documented
rather than silent — see the notes in `BALANCE.md` on the level curve and in
`ARCHITECTURE.md` on `java.time`.

## Translations

Quests ships in English only, to avoid a half-translated interface with one
maintainer. The structure is still ready for a translation: one new
implementation of `Strings`, returned from `Translations.of`. Keep the
`Strings` interface as the single list of keys.
