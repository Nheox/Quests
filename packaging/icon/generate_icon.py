#!/usr/bin/env python3
"""Rasterises the Quests application icon.

The mark is a gold progress ring forming the bowl of a Q, with a teal tail
crossing in front of it at the lower right. Both ends of the ring stop under the
tail, so the tail appears to cut them off. It is the same ring the app draws
around a skill level, so the icon and the UI say one thing.

The artwork is original to this project (§2 of docs/SPEC.md) and lives as SVG
next to this script. This script only rasterises it: the geometry is never
re-derived here, and the rendered files are never edited by hand.

    quests.svg          the master, for every frame of 48 px and above
    quests-small.svg    optical version for 32 px and below: no track, heavier
                        strokes, because at 16 px a two-tone ring is a smudge
    quests-tray.svg     white template mark for the tray and the macOS menu bar

Outputs, into composeApp/src/desktopMain/resources/icons/ (the names are
referenced by composeApp/build.gradle.kts, packaging/ and Main.kt):

    quests.png, quests-{16,32,48,64,128,256}.png   window and Linux icons
    quests.ico                                     Windows
    quests.icns                                    macOS, on Apple's icon grid
    quests-tray-{16,22,32,44}.png                  tray, white, alpha only

Every file is opened again and checked before the script reports success.

    pip install cairosvg pillow
    python3 packaging/icon/generate_icon.py
"""

import io
import os
import struct
import sys
import xml.etree.ElementTree as ElementTree

try:
    import cairosvg
    from PIL import Image
except ImportError as missing:
    package = {"PIL": "Pillow"}.get(missing.name, missing.name)
    sys.exit(
        f"generate_icon.py needs {package} to render the icon. Install it with:\n\n"
        "    pip install cairosvg pillow\n"
    )
except OSError as error:
    # cairosvg itself is installed but cannot find the Cairo C library.
    sys.exit(
        f"cairosvg could not load the Cairo library ({error}).\n"
        "Install Cairo (macOS: brew install cairo; Debian/Ubuntu: apt install libcairo2), then retry."
    )

HERE = os.path.dirname(os.path.abspath(__file__))
OUT_DIR = os.path.normpath(os.path.join(HERE, "..", "..", "composeApp", "src", "desktopMain", "resources", "icons"))

MASTER_SVG = os.path.join(HERE, "quests.svg")
SMALL_SVG = os.path.join(HERE, "quests-small.svg")
TRAY_SVG = os.path.join(HERE, "quests-tray.svg")
SVG_NAMESPACE = "http://www.w3.org/2000/svg"

# Frames this many pixels or fewer are drawn from the optical small artwork.
# Counted in pixels, not points: the 32 px ic11 (16 pt @2x) uses it, the 64 px
# ic12 (32 pt @2x) does not.
SMALL_ART_MAX = 32

# Apple's icon grid: the artwork occupies 824 x 824 of a 1024 canvas, because
# the Dock composes its own shadow into the margin. A full-bleed icon looks
# oversized beside every other app there.
MACOS_GRID = 824 / 1024

PNG_SIZES = (16, 32, 48, 64, 128, 256)
MASTER_PNG_SIZE = 512
ICO_SIZES = (16, 32, 48, 64, 128, 256)
TRAY_SIZES = (16, 22, 32, 44)

# (type code, pixel size). The 1x codes, then the Retina ones: ic11-ic14 are
# 16, 32, 128 and 256 points at @2x. Without them the Dock upscales a 1x frame
# and the icon goes soft. ic10 is both 1024 at 1x and 512 at @2x.
ICNS_FRAMES = (
    (b"icp4", 16),
    (b"icp5", 32),
    (b"icp6", 64),
    (b"ic07", 128),
    (b"ic08", 256),
    (b"ic09", 512),
    (b"ic10", 1024),
    (b"ic11", 32),
    (b"ic12", 64),
    (b"ic13", 256),
    (b"ic14", 512),
)

# Render this many times larger, then average down. Cairo antialiases each shape
# against what is already painted, so where the tail covers a ring end, the
# end's edge pixels leak through the tail's edge pixels as a gold fringe: the
# very ends that are meant to be hidden. Averaging a finer render measures true
# coverage instead. Capped so that the 1024 frame stays a reasonable bitmap.
SUPERSAMPLE = 8
MAX_RENDER_PX = 4096

PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"


def svg_source(path: str, scale: float) -> bytes:
    """The SVG at `path`, its artwork shrunk about the centre by `scale`.

    Widening the viewBox leaves the drawing itself untouched, so the macOS
    frames are derived from the same file as every other frame.
    """
    with open(path, "rb") as handle:
        source = handle.read()
    if scale == 1.0:
        return source
    ElementTree.register_namespace("", SVG_NAMESPACE)
    root = ElementTree.fromstring(source)
    x, y, width, height = (float(value) for value in root.get("viewBox").split())
    wide, tall = width / scale, height / scale
    root.set("viewBox", f"{x - (wide - width) / 2} {y - (tall - height) / 2} {wide} {tall}")
    return ElementTree.tostring(root)


def render(path: str, px: int, scale: float = 1.0) -> Image.Image:
    factor = max(1, min(SUPERSAMPLE, MAX_RENDER_PX // px))
    png = cairosvg.svg2png(bytestring=svg_source(path, scale), output_width=px * factor, output_height=px * factor)
    image = Image.open(io.BytesIO(png)).convert("RGBA")
    # Pillow premultiplies alpha before resampling, so edges stay clean.
    return image.resize((px, px), Image.Resampling.BOX) if factor > 1 else image


def app_icon(px: int, scale: float = 1.0) -> Image.Image:
    return render(SMALL_SVG if px <= SMALL_ART_MAX else MASTER_SVG, px, scale)


def tray_icon(px: int) -> Image.Image:
    """A template image: white everywhere, the drawing carried by alpha alone."""
    alpha = render(TRAY_SVG, px).getchannel("A")
    white = Image.new("L", alpha.size, 255)
    return Image.merge("RGBA", (white, white, white, alpha))


def png_bytes(image: Image.Image) -> bytes:
    buffer = io.BytesIO()
    image.save(buffer, "PNG", optimize=True)
    return buffer.getvalue()


def write_icns(path: str, frames: dict) -> None:
    """A minimal ICNS: one PNG payload per type code, no table of contents."""
    chunks = b"".join(code + struct.pack(">I", len(frames[px]) + 8) + frames[px] for code, px in ICNS_FRAMES)
    with open(path, "wb") as handle:
        handle.write(b"icns" + struct.pack(">I", len(chunks) + 8) + chunks)


def read_icns(path: str) -> list:
    """Parses an ICNS back into [(type code, PNG width, PNG height, PNG bytes)]."""
    with open(path, "rb") as handle:
        data = handle.read()
    check(data[:4] == b"icns", f"{path}: not an ICNS file")
    check(struct.unpack(">I", data[4:8])[0] == len(data), f"{path}: header length does not match the file")
    entries = []
    offset = 8
    while offset < len(data):
        check(offset + 8 <= len(data), f"{path}: truncated chunk header at byte {offset}")
        code = data[offset:offset + 4]
        length = struct.unpack(">I", data[offset + 4:offset + 8])[0]
        check(length > 8 and offset + length <= len(data), f"{path}: chunk {code!r} overruns the file")
        payload = data[offset + 8:offset + length]
        check(payload[:8] == PNG_SIGNATURE and payload[12:16] == b"IHDR", f"{path}: chunk {code!r} is not a PNG")
        width, height = struct.unpack(">II", payload[16:24])
        entries.append((code, width, height, payload))
        offset += length
    return entries


def check(condition: bool, message: str) -> None:
    if not condition:
        sys.exit(f"generate_icon.py: {message}")


def check_image(image: Image.Image, px: int, label: str) -> None:
    check(image.size == (px, px), f"{label}: {image.size}, expected {px}x{px}")
    check(image.mode == "RGBA", f"{label}: mode {image.mode}, expected RGBA")
    check(image.getchannel("A").getextrema()[1] > 0, f"{label}: fully transparent")


def verify(out: str, app: dict, mac: dict) -> None:
    """Opens every written file again and checks it against what was rendered."""
    for name, px in [("quests.png", MASTER_PNG_SIZE)] + [(f"quests-{px}.png", px) for px in PNG_SIZES]:
        with Image.open(os.path.join(out, name)) as image:
            check_image(image, px, name)

    for px in TRAY_SIZES:
        name = f"quests-tray-{px}.png"
        with Image.open(os.path.join(out, name)) as image:
            check_image(image, px, name)
            red, green, blue, _ = image.split()
            check(all(band.getextrema() == (255, 255) for band in (red, green, blue)), f"{name}: not pure white")

    with Image.open(os.path.join(out, "quests.ico")) as ico:
        check(sorted(ico.ico.sizes()) == [(px, px) for px in ICO_SIZES], f"quests.ico: sizes {sorted(ico.ico.sizes())}")
        for px in ICO_SIZES:
            frame = ico.ico.getimage((px, px)).convert("RGBA")
            check_image(frame, px, f"quests.ico {px}")
            check(frame.tobytes() == app[px].tobytes(), f"quests.ico {px}: differs from the rendered frame")

    entries = read_icns(os.path.join(out, "quests.icns"))
    check([code for code, *_ in entries] == [code for code, _ in ICNS_FRAMES], "quests.icns: type codes out of order")
    for (code, width, height, payload), (_, px) in zip(entries, ICNS_FRAMES):
        label = f"quests.icns {code.decode()}"
        check((width, height) == (px, px), f"{label}: PNG is {width}x{height}, expected {px}x{px}")
        frame = Image.open(io.BytesIO(payload))
        check_image(frame, px, label)
        check(frame.tobytes() == mac[px].tobytes(), f"{label}: differs from the rendered frame")
        # The artwork must sit inside Apple's grid, leaving the margin clear.
        margin = px * (1 - MACOS_GRID) / 2
        left, top, right, bottom = frame.getchannel("A").getbbox()
        check(min(left, top, px - right, px - bottom) >= int(margin), f"{label}: artwork reaches into the margin")


def main() -> None:
    os.makedirs(OUT_DIR, exist_ok=True)

    app = {px: app_icon(px) for px in PNG_SIZES + (MASTER_PNG_SIZE,)}
    with open(os.path.join(OUT_DIR, "quests.png"), "wb") as handle:
        handle.write(png_bytes(app[MASTER_PNG_SIZE]))
    for px in PNG_SIZES:
        with open(os.path.join(OUT_DIR, f"quests-{px}.png"), "wb") as handle:
            handle.write(png_bytes(app[px]))

    # The largest frame goes first: Pillow skips any requested size larger than
    # it, and takes every other size from append_images rather than resizing.
    largest = max(ICO_SIZES)
    app[largest].save(
        os.path.join(OUT_DIR, "quests.ico"),
        format="ICO",
        sizes=[(px, px) for px in ICO_SIZES],
        append_images=[app[px] for px in ICO_SIZES if px != largest],
    )

    mac = {px: app_icon(px, MACOS_GRID) for px in sorted({px for _, px in ICNS_FRAMES})}
    write_icns(os.path.join(OUT_DIR, "quests.icns"), {px: png_bytes(image) for px, image in mac.items()})

    for px in TRAY_SIZES:
        with open(os.path.join(OUT_DIR, f"quests-tray-{px}.png"), "wb") as handle:
            handle.write(png_bytes(tray_icon(px)))

    verify(OUT_DIR, app, mac)
    print("wrote and verified icons in", OUT_DIR)


if __name__ == "__main__":
    main()
