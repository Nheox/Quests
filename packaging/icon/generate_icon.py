#!/usr/bin/env python3
"""Generates the Quests application icon.

The artwork is original to this project (see §2 of docs/SPEC.md: all icons and
artwork original or permissively licensed). It is generated from this script
rather than drawn in an editor so that it is reproducible, reviewable as a diff,
and obviously ours.

The mark: a gold target ring enclosing a rising arrow. A target because a quest
is something aimed at; the arrow because the point of the app is progress rather
than a static list. The arrow sits *inside* the ring rather than across it — a
line crossing a circle reads as a prohibition sign, which is the opposite of the
intended meaning. Legible down to 16px, which rules out anything more detailed.

Outputs PNG (Linux, tray), ICO (Windows) and ICNS (macOS).

    python3 packaging/icon/generate_icon.py
"""

import math
import os
import struct
from PIL import Image, ImageDraw

INK = (14, 17, 22, 255)
INK_EDGE = (42, 52, 64, 255)
GOLD = (224, 169, 74, 255)
TEAL = (63, 184, 175, 255)

SIZES = (16, 32, 48, 64, 128, 256, 512, 1024)
OUT_DIR = os.path.join(os.path.dirname(__file__), "..", "..", "composeApp", "src", "desktopMain", "resources", "icons")


def draw(size: int) -> Image.Image:
    # Supersample and downscale: hand-drawn arcs alias badly at 16px otherwise.
    scale = 8 if size <= 64 else 4
    s = size * scale
    image = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    canvas = ImageDraw.Draw(image)

    radius = s * 0.22
    canvas.rounded_rectangle([0, 0, s - 1, s - 1], radius=radius, fill=INK, outline=INK_EDGE,
                             width=max(1, int(s * 0.012)))

    centre = s / 2
    ring_outer = s * 0.325
    ring_width = max(1, int(s * 0.070))

    canvas.ellipse(
        [centre - ring_outer, centre - ring_outer, centre + ring_outer, centre + ring_outer],
        outline=GOLD, width=ring_width,
    )

    inner = s * 0.205
    canvas.ellipse(
        [centre - inner, centre - inner, centre + inner, centre + inner],
        outline=TEAL, width=max(1, int(s * 0.034)),
    )

    # The rising arrow, wholly inside the inner ring.
    thickness = max(2, int(s * 0.062))
    half = thickness / 2
    stem_bottom = centre + s * 0.112
    stem_top = centre - s * 0.112
    canvas.line([(centre, stem_bottom), (centre, stem_top)], fill=GOLD, width=thickness)

    wing = s * 0.082
    head = [
        (centre - wing, stem_top + wing * 0.92),
        (centre, stem_top),
        (centre + wing, stem_top + wing * 0.92),
    ]
    canvas.line(head, fill=GOLD, width=thickness, joint="curve")
    for point in (head[0], head[-1], (centre, stem_bottom), (centre, stem_top)):
        canvas.ellipse([point[0] - half, point[1] - half, point[0] + half, point[1] + half], fill=GOLD)

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
