# Flatpak

```sh
./gradlew createDistributable
flatpak-builder --user --install --force-clean build-flatpak \
  packaging/flatpak/io.github.nheox.Quests.yml
```

The manifest wraps the app image rather than building Kotlin inside the sandbox.
Flathub builders have no network access during a build, and a Gradle build
without network access means vendoring every dependency by hand — so the release
workflow builds the image first and Flatpak packages it.

## Permissions

Granted: a window (X11, Wayland, DRI), IPC, PulseAudio for the completion
chime, and `--filesystem=home` for the sync folder.

Not granted, deliberately:

- **No network.** There is no networking code in the application, so the
  permission would be a lie about what it can do.
- **No dconf access.** This is the one real functional loss versus the `.deb`:
  Settings cannot install the GNOME quick-capture keybinding from inside the
  sandbox, because `gsettings` cannot reach the host's dconf. The fallback —
  adding the shortcut by hand in GNOME's keyboard settings — produces an
  identical result, and is documented in [HOTKEY.md](../../docs/HOTKEY.md).
  Granting dconf write access in order to set one shortcut is not a trade worth
  making.

`--filesystem=home` is broader than ideal. A narrower `--filesystem=~/Quests`
would cover the default location, but the folder is user-chosen by design, and
the document portal has no way to hold a folder grant across launches without
re-prompting. If that changes, this should narrow.

## Submitting to Flathub

Not yet done. It needs a tagged release for the manifest to point at, at which
point the `sources` entry becomes an archive URL with a checksum rather than a
local directory.

Check the metainfo before submitting:

```sh
flatpak run org.freedesktop.appstream-glib validate \
  packaging/flatpak/io.github.nheox.Quests.metainfo.xml
```
