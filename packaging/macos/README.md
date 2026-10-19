# macOS package

```sh
./gradlew packageDmg
```

Produces an unsigned `.dmg` in
`composeApp/build/compose/binaries/main/dmg/`. Must be run on macOS —
`jpackage` cannot cross-build a `.dmg`.

An unsigned build will be blocked by Gatekeeper. Until signing is set up, a user
has to right-click → Open, or clear the quarantine attribute:

```sh
xattr -dr com.apple.quarantine /Applications/Quests.app
```

That is not an acceptable thing to ask of anyone, which is why
[SPEC.md §1](../../docs/SPEC.md#1-decisions-settled) commits to a signed,
notarised `.dmg`. It is **not yet configured**: it needs an Apple Developer
account, which costs money and is the author's call to make.

## What signing will need

Once a Developer ID Application certificate is in the login keychain, add to the
`macOS` block in `composeApp/build.gradle.kts`:

```kotlin
macOS {
    signing {
        sign.set(true)
        identity.set("Developer ID Application: Your Name (TEAMID)")
    }
    notarization {
        appleID.set(providers.environmentVariable("NOTARIZE_APPLE_ID"))
        password.set(providers.environmentVariable("NOTARIZE_PASSWORD"))
        teamID.set(providers.environmentVariable("NOTARIZE_TEAM_ID"))
    }
    entitlementsFile.set(project.file("../packaging/macos/entitlements.plist"))
}
```

`NOTARIZE_PASSWORD` is an app-specific password, not the Apple ID password. Then:

```sh
./gradlew notarizeDmg
```

Never commit the identity string or the credentials.

## Entitlements

`entitlements.plist` in this directory is the minimum a JVM app needs: the JIT
requires an unsigned-executable-memory entitlement, which is unavoidable rather
than careless. Notably absent is any network entitlement, because the
application contains no networking code.

## Bundle version

macOS will not accept a version whose first component is zero. `jpackage`
rejects it outright:

```
The first number in an app-version cannot be zero or negative.
```

That makes a pre-1.0 project unpackageable on macOS as-is, so
`composeApp/build.gradle.kts` maps it: while the project version is `0.x`, the
bundle is built as `1.0.0`. See `macAppVersion` there.

This affects only `CFBundleVersion` and `CFBundleShortVersionString`. Everything
a person actually reads still reports the real version — `quests --version`, the
About section in Settings, the calendar feed's `PRODID`, the `.deb`, and the
release artefacts. Once the project reaches 1.0.0 the mapping returns the real
version unchanged and can be deleted.

## Platform notes

- **Global hotkeys work properly on macOS**, unlike Wayland
  ([HOTKEY.md](../../docs/HOTKEY.md)). `quests --capture` works, and a native
  in-app hotkey would also be possible here; it is not implemented, since the
  keybinding route has to exist for Linux regardless.
- `Cmd` is accepted everywhere `Ctrl` is, so the shortcuts in the help screen
  read correctly on both platforms.
- The app writes to `~/Library/Application Support/quests` for its device name
  and vault pointer, and the vault itself defaults to `~/Quests`.
- Universal binaries are not built. The package targets the architecture of the
  machine that built it; Apple Silicon and Intel need separate runs.
