#!/usr/bin/env python3
"""Rasterises the Quests application icon.

The artwork is original to this project (see §2 of docs/SPEC.md: all icons and
artwork original or permissively licensed).

**The SVG files beside this script are the master.** Edit `quests.svg` — never
the PNGs, and never this script's geometry, because it has none. This script
only rasterises. Keeping a single vector master is the reason the icon stays
sharp at 1024 and still resolves at 16px; a raster-only icon cannot be fixed
once it is wrong.

The mark: a gold progress ring that *is* the bowl of a Q, broken at the lower
right by a teal tail crossing through the gap. One form, not a letter sitting
next to a circle — which is what keeps it legible at 16px, where two competing
strokes merge into a blob. The ring is the same progress ring the app draws
around a skill level, so the icon and the UI say the same thing. The dim
remainder of the ring (`inkBorder`) disappears at small sizes, leaving a gold
arc and a teal tail: the silhouette degrades on purpose.

One master, two framings. `quests.svg` is full-bleed: artwork to the edges of
a 1024 canvas, which is what Linux hicolor, Flathub and the in-app window icon
want. macOS wants Apple's Big Sur grid instead — an 824x824 rounded square
centred in a 1024 canvas — because the Dock composes its own shadow around that
margin, and a full-bleed icon simply looks oversized beside every other icon in
it. Rather than keep a second file in sync by hand, this script derives the
macOS framing by wrapping the master in that transform at render time.

Outputs PNG (Linux, window icon), ICO (Windows, unsupported-but-working) and
ICNS (macOS).

    python3 packaging/icon/generate_icon.py

Needs one SVG rasteriser. Any of these, in order of preference:

    pip install cairosvg          # no system packages beyond libcairo
    apt install librsvg2-bin      # provides rsvg-convert
    apt install inkscape
"""

import os
import re
import shutil
import struct
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
MASTER = os.path.join(HERE, "quests.svg")
MASTER_SMALL = os.path.join(HERE, "quests-small.svg")

# Below this, the full mark stops resolving: the dim track muddies into the
# gold, and the casing that separates the tail from the ring eats more than a
# pixel of a ring that is only a pixel or two wide. Optical sizing is not
# duplication — it is the same mark drawn for a different amount of room.
SMALL_ABOVE = 32

# Apple's macOS icon grid: 824x824 of artwork centred in a 1024 canvas.
MACOS_INSET = 100
MACOS_SCALE = (1024 - 2 * MACOS_INSET) / 1024
OUT_DIR = os.path.normpath(
    os.path.join(HERE, "..", "..", "composeApp", "src", "desktopMain", "resources", "icons")
)

# Linux hicolor's standard set, plus 1024 for the macOS retina slot.
SIZES = (16, 24, 32, 48, 64, 128, 256, 512, 1024)


def _render_cairosvg(svg: str, size: int, destination: str) -> bool:
    try:
        import cairosvg
    except ImportError:
        return False
    cairosvg.svg2png(url=svg, write_to=destination, output_width=size, output_height=size)
    return True


def _render_rsvg(svg: str, size: int, destination: str) -> bool:
    if shutil.which("rsvg-convert") is None:
        return False
    subprocess.run(
        ["rsvg-convert", "-w", str(size), "-h", str(size), "-o", destination, svg],
        check=True,
    )
    return True


def _render_inkscape(svg: str, size: int, destination: str) -> bool:
    if shutil.which("inkscape") is None:
        return False
    subprocess.run(
        ["inkscape", svg, "-w", str(size), "-h", str(size), "-o", destination],
        check=True,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    return True


RENDERERS = (_render_cairosvg, _render_rsvg, _render_inkscape)


def render(svg: str, size: int, destination: str) -> None:
    for renderer in RENDERERS:
        if renderer(svg, size, destination):
            return
    sys.exit(
        "No SVG rasteriser found. Install one of:\n"
        "  pip install cairosvg\n"
        "  apt install librsvg2-bin\n"
        "  apt install inkscape"
    )


def macos_master(scratch: str, source_path: str, name: str) -> str:
    """The master wrapped in Apple's icon-grid transform.

    Derived rather than stored, so there is exactly one place the artwork can
    be edited. A gradient declared in userSpaceOnUse resolves inside the
    wrapping group's coordinate space, so the tile and the tail's casing go on
    matching each other without any change to the master.
    """
    with open(source_path, encoding="utf-8") as handle:
        source = handle.read()

    opening = re.search(r"<svg\b[^>]*>", source).group(0)
    body = source[source.index(opening) + len(opening) : source.rindex("</svg>")]

    wrapped = (
        opening
        + f'<g transform="translate({MACOS_INSET},{MACOS_INSET}) scale({MACOS_SCALE})">'
        + body
        + "</g></svg>"
    )

    path = os.path.join(scratch, name)
    with open(path, "w", encoding="utf-8") as handle:
        handle.write(wrapped)
    return path


def write_icns(path: str, frames: dict) -> None:
    """A minimal but valid ICNS: PNG payloads under the modern type codes.

    The @2x codes matter. Without ic11/ic12/ic13/ic14 the Dock upscales the 1x
    slot on a retina display, and a soft icon there is the single most visible
    sign of an app that was packaged rather than designed.
    """
    one_x = {16: b"icp4", 32: b"icp5", 128: b"ic07", 256: b"ic08", 512: b"ic09", 1024: b"ic10"}
    two_x = {32: b"ic11", 64: b"ic12", 256: b"ic13", 512: b"ic14"}

    chunks = b""
    for table in (one_x, two_x):
        for size, code in table.items():
            if size not in frames:
                continue
            payload = frames[size]
            chunks += code + struct.pack(">I", len(payload) + 8) + payload

    with open(path, "wb") as handle:
        handle.write(b"icns" + struct.pack(">I", len(chunks) + 8) + chunks)


def main() -> None:
    os.makedirs(OUT_DIR, exist_ok=True)

    for size in SIZES:
        master = MASTER_SMALL if size <= SMALL_ABOVE else MASTER
        render(master, size, os.path.join(OUT_DIR, f"quests-{size}.png"))

    # The window icon and the Linux .deb icon.
    shutil.copyfile(os.path.join(OUT_DIR, "quests-512.png"), os.path.join(OUT_DIR, "quests.png"))

    # The scalable icon, shipped as-is for Flathub and hicolor/scalable.
    shutil.copyfile(MASTER, os.path.join(OUT_DIR, "quests.svg"))

    try:
        from PIL import Image
    except ImportError:
        sys.exit("Pillow is required for the .ico: pip install pillow")

    Image.open(os.path.join(OUT_DIR, "quests-256.png")).save(
        os.path.join(OUT_DIR, "quests.ico"),
        sizes=[(16, 16), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)],
    )

    # macOS gets its own master, with Apple's margin built in.
    payloads = {}
    with tempfile.TemporaryDirectory() as scratch:
        full = macos_master(scratch, MASTER, "quests-macos.svg")
        small = macos_master(scratch, MASTER_SMALL, "quests-macos-small.svg")
        for size in (16, 32, 64, 128, 256, 512, 1024):
            frame = os.path.join(scratch, f"{size}.png")
            render(small if size <= SMALL_ABOVE else full, size, frame)
            with open(frame, "rb") as handle:
                payloads[size] = handle.read()
    write_icns(os.path.join(OUT_DIR, "quests.icns"), payloads)

    print("wrote icons to", OUT_DIR)


if __name__ == "__main__":
    main()
