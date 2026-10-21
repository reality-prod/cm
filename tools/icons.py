#!/usr/bin/env python3
# SPDX-License-Identifier: AGPL-3.0-or-later
"""Renders the app icons from the official pure mountain mark (brand/logo-kit/codeberg-logo_icon_*.svg).

  Android: adaptive icon. The mark sits inside the 66% safe zone; the background is white (colors.xml), and the
           monochrome layer uses the white mark so the launcher can tint it.
  iOS:     one 1024 px image, flattened on white (the App Store requires an opaque icon).

Needs rsvg-convert (librsvg) and Python 3. This writes binary PNGs, so it is run with `make icons`, not by the
drift check: renderer versions differ slightly in antialiasing.
"""

import os
import shutil
import struct
import subprocess
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MARK = os.path.join(ROOT, "brand", "logo-kit", "codeberg-logo_icon_blue.svg")
MARK_MONO = os.path.join(ROOT, "brand", "logo-kit", "codeberg-logo_icon_white.svg")
ANDROID_RES = os.path.join(ROOT, "mobile", "androidApp", "src", "main", "res")
IOS_ICON = os.path.join(ROOT, "mobile", "iosApp", "Assets.xcassets", "AppIcon.appiconset")

# Android adaptive foreground & monochrome: 108 dp at each density, mark drawn at the 66% safe zone.
ANDROID_DENSITIES = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}
SAFE_ZONE = 0.66

IOS_SIZE = 1024
IOS_MARK = 0.80


def render_centred(svg, canvas_size, mark_fraction, out_path, background=None):
    mark_size = round(canvas_size * mark_fraction)
    offset = (canvas_size - mark_size) // 2
    cmd = [
        "rsvg-convert",
        "-w", str(mark_size),
        "-h", str(mark_size),
        "--page-width", str(canvas_size),
        "--page-height", str(canvas_size),
        "--left", str(offset),
        "--top", str(offset),
    ]
    if background:
        cmd += ["-b", background]
    cmd += [svg, "-o", out_path]
    subprocess.run(cmd, check=True)


def strip_png_alpha(path):
    """Converts an 8-bit RGBA PNG (already composited on an opaque background) to an 8-bit RGB PNG."""
    with open(path, "rb") as f:
        data = f.read()
    assert data[:8] == b"\x89PNG\r\n\x1a\n"
    pos = 8
    width = height = 0
    idat = bytearray()
    while pos < len(data):
        length = struct.unpack(">I", data[pos:pos + 4])[0]
        ctype = data[pos + 4:pos + 8]
        chunk = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if ctype == b"IHDR":
            width, height, depth, color_type, comp, filt, interlace = struct.unpack(">IIBBBBB", chunk)
            if color_type == 2:
                return
            assert depth == 8 and color_type == 6 and interlace == 0
        elif ctype == b"IDAT":
            idat.extend(chunk)

    raw = zlib.decompress(bytes(idat))
    stride = width * 4
    rgb_rows = bytearray()
    prev = bytearray(width * 4)
    idx = 0
    for _ in range(height):
        ftype = raw[idx]
        row = bytearray(raw[idx + 1:idx + 1 + stride])
        idx += 1 + stride
        if ftype == 1:
            for x in range(4, stride):
                row[x] = (row[x] + row[x - 4]) & 0xFF
        elif ftype == 2:
            for x in range(stride):
                row[x] = (row[x] + prev[x]) & 0xFF
        elif ftype == 3:
            for x in range(stride):
                a = row[x - 4] if x >= 4 else 0
                row[x] = (row[x] + ((a + prev[x]) >> 1)) & 0xFF
        elif ftype == 4:
            for x in range(stride):
                a = row[x - 4] if x >= 4 else 0
                b = prev[x]
                c = prev[x - 4] if x >= 4 else 0
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                row[x] = (row[x] + pr) & 0xFF
        prev = row
        rgb_rows.append(0)
        for x in range(0, stride, 4):
            rgb_rows.extend(row[x:x + 3])

    def make_chunk(ctype, body):
        crc = zlib.crc32(ctype + body) & 0xFFFFFFFF
        return struct.pack(">I", len(body)) + ctype + body + struct.pack(">I", crc)

    ihdr = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    compressed = zlib.compress(bytes(rgb_rows), 9)
    out = b"\x89PNG\r\n\x1a\n" + make_chunk(b"IHDR", ihdr) + make_chunk(b"IDAT", compressed) + make_chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(out)


def main():
    if shutil.which("rsvg-convert") is None:
        raise SystemExit("rsvg-convert is needed (apt install librsvg2-bin / brew install librsvg)")

    # Android foreground (blue mark) and monochrome layer (white mark) in the 66% safe zone.
    for density, size in ANDROID_DENSITIES.items():
        out_dir = os.path.join(ANDROID_RES, f"mipmap-{density}")
        os.makedirs(out_dir, exist_ok=True)
        render_centred(MARK, size, SAFE_ZONE, os.path.join(out_dir, "ic_launcher_foreground.png"))
        render_centred(MARK_MONO, size, SAFE_ZONE, os.path.join(out_dir, "ic_launcher_monochrome.png"))
        print("android", density, size)

    LINUX_ICON_DIR = os.path.join(ROOT, "mobile", "desktopApp", "src", "jvmMain", "resources")
    os.makedirs(LINUX_ICON_DIR, exist_ok=True)
    render_centred(MARK, 512, SAFE_ZONE, os.path.join(LINUX_ICON_DIR, "app_icon.png"))
    print("linux jvm icon generated (512x512)")

    # iOS: one opaque 1024 px icon, mark at 80%, white background.
    os.makedirs(IOS_ICON, exist_ok=True)
    ios_out = os.path.join(IOS_ICON, "AppIcon-1024.png")
    render_centred(MARK, IOS_SIZE, IOS_MARK, ios_out, background="#ffffff")
    strip_png_alpha(ios_out)
    print("ios 1024")


if __name__ == "__main__":
    main()

