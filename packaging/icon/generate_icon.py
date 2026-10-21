#!/usr/bin/env python3
"""Generates the Quests application icon.

The artwork is original to this project (see §2 of docs/SPEC.md: all icons and
artwork original or permissively licensed). It is generated from this script
rather than drawn in an editor so that it is reproducible, reviewable as a diff,
and obviously ours. `reference.png` beside this file is the design the script
reproduces; the numbers below were measured off it.

The mark: a **Q** for Quests, drawn as a progress dial. The ring is split in two
— gold for the distance covered, slate for what is left — and the teal bar is
the Q's tail, cutting the ring at the lower right. A dial rather than a static
letter because the point of the app is progress rather than a list.

The gold starts at the tail and runs **clockwise**: tail → bottom → left → top,
so the dial fills away from its own origin and the eye is handed off to the
slate remainder at twelve o'clock. The tail's break in the ring is also what
keeps the mark from reading as a prohibition sign, which a bar laid across an
unbroken circle always does.

Geometry, as fractions of the tile `s` (PIL angles: 0° east, 90° south, and
increasing clockwise, because y points down):

    tile corner radius      0.22 s      vertical gradient, hairline edge
    ring centre radius      0.29 s      stroke 0.08 s, round caps
      gold arc               73° → 261° starts at the tail, clockwise
      slate arc             278° → 377° the remainder, through twelve o'clock
    tail (teal)             along 45°, centres at 0.180 s and 0.385 s

Legible down to 16px, which rules out anything more detailed.

Outputs PNG (Linux, tray), ICO (Windows) and ICNS (macOS).

    python3 packaging/icon/generate_icon.py
"""

import math
import os
import struct
from PIL import Image, ImageDraw

INK_TOP = (30, 36, 47, 255)
INK_BOTTOM = (14, 18, 24, 255)
GOLD = (224, 169, 74, 255)
SLATE = (43, 52, 64, 255)  # the dial's remainder, and the tile's hairline edge
TEAL = (63, 184, 175, 255)

RING_RADIUS = 0.29
RING_WIDTH = 0.08
GOLD_ARC = (73, 261)
SLATE_ARC = (278, 377)
TAIL_ANGLE = 45
TAIL_INNER = 0.180
TAIL_OUTER = 0.385

SIZES = (16, 32, 48, 64, 128, 256, 512, 1024)
OUT_DIR = os.path.join(os.path.dirname(__file__), "..", "..", "composeApp", "src", "desktopMain", "resources", "icons")


def tile(s: int) -> Image.Image:
    """The rounded slab: a vertical gradient, so the mark has something to sit on."""
    column = Image.new("RGBA", (1, s))
    pixels = column.load()
    for y in range(s):
        t = y / max(1, s - 1)
        pixels[0, y] = tuple(round(a + (b - a) * t) for a, b in zip(INK_TOP, INK_BOTTOM))

    mask = Image.new("L", (s, s), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, s - 1, s - 1], radius=s * 0.22, fill=255)

    image = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    image.paste(column.resize((s, s)), (0, 0), mask)
    ImageDraw.Draw(image).rounded_rectangle(
        [0, 0, s - 1, s - 1], radius=s * 0.22, outline=SLATE, width=max(1, int(s * 0.010)),
    )
    return image


def cap(canvas: ImageDraw.ImageDraw, point: tuple, half: float, colour: tuple) -> None:
    x, y = point
    canvas.ellipse([x - half, y - half, x + half, y + half], fill=colour)


def arc(canvas: ImageDraw.ImageDraw, centre: float, radius: float, width: float,
        start: float, end: float, colour: tuple) -> None:
    """An arc on `radius` with round caps. PIL grows `width` inward from the box."""
    half = width / 2
    outer = radius + half
    canvas.arc(
        [centre - outer, centre - outer, centre + outer, centre + outer],
        start, end, fill=colour, width=max(1, int(round(width))),
    )
    for angle in (start, end):
        radians = math.radians(angle)
        cap(canvas, (centre + radius * math.cos(radians), centre + radius * math.sin(radians)), half, colour)


def draw(size: int) -> Image.Image:
    # Supersample and downscale: hand-drawn arcs alias badly at 16px otherwise.
    scale = 8 if size <= 64 else 4
    s = size * scale
    image = tile(s)
    canvas = ImageDraw.Draw(image)

    centre = s / 2
    radius = s * RING_RADIUS
    width = s * RING_WIDTH

    arc(canvas, centre, radius, width, *SLATE_ARC, SLATE)
    arc(canvas, centre, radius, width, *GOLD_ARC, GOLD)

    # The tail: the same weight as the ring, crossing it at the lower right.
    radians = math.radians(TAIL_ANGLE)
    half = width / 2
    inner = (centre + s * TAIL_INNER * math.cos(radians), centre + s * TAIL_INNER * math.sin(radians))
    outer = (centre + s * TAIL_OUTER * math.cos(radians), centre + s * TAIL_OUTER * math.sin(radians))
    canvas.line([inner, outer], fill=TEAL, width=max(1, int(round(width))))
    cap(canvas, inner, half, TEAL)
    cap(canvas, outer, half, TEAL)

    return image.resize((size, size), Image.LANCZOS)


def write_icns(path: str, frames: dict) -> None:
    """A minimal but valid ICNS: PNG payloads under the modern type codes."""
    types = {16: b"icp4", 32: b"icp5", 64: b"icp6", 128: b"ic07", 256: b"ic08", 512: b"ic09", 1024: b"ic10"}
    chunks = b""
    for size, code in types.items():
        if size not in frames:
            continue
        payload = frames[size]
        chunks += code + struct.pack(">I", len(payload) + 8) + payload
    with open(path, "wb") as handle:
        handle.write(b"icns" + struct.pack(">I", len(chunks) + 8) + chunks)


def main() -> None:
    out = os.path.normpath(OUT_DIR)
    os.makedirs(out, exist_ok=True)

    images = {size: draw(size) for size in SIZES}

    images[512].save(os.path.join(out, "quests.png"))
    for size in (16, 32, 48, 64, 128, 256):
        images[size].save(os.path.join(out, f"quests-{size}.png"))

    images[256].save(
        os.path.join(out, "quests.ico"),
        sizes=[(16, 16), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)],
    )

    png_bytes = {}
    for size, image in images.items():
        temporary = os.path.join(out, f".icns-{size}.png")
        image.save(temporary)
        with open(temporary, "rb") as handle:
            png_bytes[size] = handle.read()
        os.remove(temporary)
    write_icns(os.path.join(out, "quests.icns"), png_bytes)

    print("wrote icons to", out)


if __name__ == "__main__":
    main()
