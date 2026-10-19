# Global quick capture

Quick capture is the highest-value desktop feature in Quests and the one with a
real technical obstacle. This document is the reasoning behind how it is
implemented, which is deliberately *not* the obvious way.

## Quests does not grab a hotkey

It cannot, reliably. Four facts compound:

1. **mutter on GNOME ≥ 49 no longer honours XWayland global key grabs.** A
   grabbed key only fires while the application's own window has focus, which
   defeats the entire purpose.
2. **The replacement — the XDG GlobalShortcuts portal — expects a native Wayland
   client**, plus a reverse-DNS app ID backed by an *installed* `.desktop` file.
   Portal 1.21 rejects a session created with an empty app ID.
3. **OpenJDK ships no Wayland backend.** Project Wakefield is unreleased, so
   Compose Desktop runs on AWT/X11 and is therefore always an XWayland client on
   Wayland sessions — on the wrong side of both points above.
4. **`xdg-desktop-portal-wlr` has no GlobalShortcuts backend at all**, so Sway,
   Hyprland and Niri users could not have a global shortcut by any route.

## The plan of record: the compositor owns the shortcut

Bind a key in your desktop environment's own keyboard settings to:

```
quests --capture
```

That starts a second, short-lived Quests process. It finds the running one
through a lock file and a Unix domain socket in `$XDG_RUNTIME_DIR/quests/`,
hands over the request, and exits. The running instance opens its capture window.

No portal, no key grab, no Wayland-client requirement. Works identically on X11
and Wayland, on GNOME, KDE, Sway and anything else that can bind a key to a
command — which is all of them.

It is also more robust than an in-app grab: if Quests is not running, the
keybinding still launches it, straight into capture.

## Installing the binding

### GNOME

Settings → Global quick capture → *Install GNOME shortcut* writes a custom
keybinding via `gsettings`, defaulting to `Super+Shift+Q`. It reads the existing
`custom-keybindings` list and appends to it, so your other shortcuts are left
alone.

Inside a Flatpak sandbox this will fail, because `gsettings` cannot reach the
host's dconf. Set it by hand instead; the result is identical.

### GNOME, by hand

Settings → Keyboard → View and Customise Shortcuts → Custom Shortcuts → `+`

- Name: `Quests quick capture`
- Command: `quests --capture`
- Shortcut: whatever you like

### KDE Plasma

System Settings → Shortcuts → Add New → Command/URL → `quests --capture`

### Sway / i3

```
bindsym $mod+Shift+q exec quests --capture
```

### Hyprland

```
bind = SUPER SHIFT, Q, exec, quests --capture
```

### macOS

macOS has working global hotkey APIs and is unaffected by any of the above. The
same `quests --capture` command works, and can be bound with any of the usual
tools; a native in-app hotkey would also be possible here and is not currently
implemented.

## The spike that has not been done

[SPEC.md §8.3](SPEC.md#83-known-risks) proposes a 30-minute spike early in M2:
test whether the GlobalShortcuts portal can in fact be driven over D-Bus from an
XWayland JVM client. The reading above may be too pessimistic, and if it is, the
portal route is nicer than the keybinding.

That spike has **not** been done. It does not block anything: the keybinding
fallback is implemented, works everywhere, and has to exist regardless for
wlroots users. If the portal does turn out to be drivable, it becomes a nicety
on GNOME and KDE rather than a necessity.

## Why the app ID matters anyway

The Flathub ID is `io.github.nheox.Quests` and the Linux package installs a
reverse-DNS `.desktop` file. That is not cosmetic even though the portal route
is unused: it is what makes the app addressable to the portal *if* the spike
succeeds, and what makes the window associate correctly with its icon and its
tray entry in the meantime.
