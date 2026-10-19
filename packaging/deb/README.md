# Debian package

```sh
./gradlew packageDeb
sudo dpkg -i composeApp/build/compose/binaries/main/deb/quests_*.deb
```

Produced by `jpackage` via the Compose Desktop plugin, configured in
`composeApp/build.gradle.kts`. Requires `fakeroot` and `dpkg` on the build
machine — `jpackage` shells out to both, and the error when `fakeroot` is
missing is only visible in `build/compose/logs/packageDeb/`.

## What it installs

| Path | |
|---|---|
| `/opt/quests/` | the application image, including its own JRE |
| `/opt/quests/bin/Quests` | the launcher |
| `/usr/bin/quests` | symlink to the launcher |
| `/usr/share/applications/quests.desktop` | desktop entry |
| `/usr/share/icons/hicolor/.../quests.png` | icon |

The package is unconfined: no sandbox, no portal indirection, and `gsettings`
works, so Settings can install the global quick-capture keybinding directly.
That is the reason `.deb` is a first-class target alongside Flathub rather than
being replaced by it ([SPEC.md §1](../../docs/SPEC.md#1-decisions-settled)).

## `quests.desktop`

The `quests.desktop` in this directory is the desktop entry the `.deb` installs,
including a *Quick capture* desktop action. `jpackage` also generates an entry of
its own and registers it from `postinst`; `finalizeDebPackage` removes that
registration, because with both installed the application menu shows Quests
twice.

Packages built before that fix do show it twice. Installing a newer package
clears the extra entry; for the same version, reinstall explicitly:

```sh
sudo apt install --reinstall ./quests_*.deb
```

## The `quests` command

Global quick capture binds a desktop shortcut to `quests --capture`, so the
`quests` name must be on `PATH`. Check after installing:

```sh
quests --version
```

If the symlink is missing, the launcher is at `/opt/quests/bin/Quests`, and a
keybinding can call that path directly.

## Size

Around 56 MB. That is a bundled JRE, which is the price of shipping a JVM
desktop app that does not require the user to have Java. `jlink` already strips
it to the modules declared in the Gradle configuration.
