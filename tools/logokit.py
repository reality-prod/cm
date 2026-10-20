#!/usr/bin/env python3
# SPDX-License-Identifier: AGPL-3.0-or-later
"""Reads the official Codeberg logo kit (brand/logo-kit/*.svg) and returns drawing data for the apps.

The kit files are used as published. This module only reads them: it resolves transforms, fills and gradients into
plain values that the Kotlin and icon generators can use. It refuses anything it cannot represent exactly
(rotation, skew, opacity other than 1), so a logo can never be drawn subtly wrong.
"""

import math
import re
import xml.etree.ElementTree as ET

SVG_NS = "{http://www.w3.org/2000/svg}"
XLINK_HREF = "{http://www.w3.org/1999/xlink}href"


def mul(m, n):
    """Multiplies two 2D affine matrices given as (a, b, c, d, e, f). Applies n first, then m."""
    a, b, c, d, e, f = m
    A, B, C, D, E, F = n
    return (a * A + c * B, b * A + d * B, a * C + c * D, b * C + d * D, a * E + c * F + e, b * E + d * F + f)


def parse_transform(text):
    matrix = (1.0, 0.0, 0.0, 1.0, 0.0, 0.0)
    if not text:
        return matrix
    for name, args in re.findall(r"(matrix|translate|scale|rotate|skewX|skewY)\(([^)]*)\)", text):
        values = [float(v) for v in re.split(r"[\s,]+", args.strip()) if v]
        if name == "matrix":
            step = tuple(values)
        elif name == "translate":
            step = (1.0, 0.0, 0.0, 1.0, values[0], values[1] if len(values) > 1 else 0.0)
        elif name == "scale":
            sy = values[1] if len(values) > 1 else values[0]
            step = (values[0], 0.0, 0.0, sy, 0.0, 0.0)
        else:
            raise ValueError(f"transform {name} is not supported by the logo kit reader")
        matrix = mul(matrix, step)
    return matrix


def style_map(element):
    result = {}
    for item in (element.get("style") or "").split(";"):
        if ":" in item:
            key, value = item.split(":", 1)
            result[key.strip()] = value.strip()
    return result


def style_value(element, key):
    return style_map(element).get(key, element.get(key))


def stop_color_and_opacity(stop):
    color = style_value(stop, "stop-color") or "#000000"
    opacity = float(style_value(stop, "stop-opacity") or "1")
    return color.lower(), opacity


def gradient_stops(ids, gradient):
    """Stops live on the gradient or on the gradient it links to (xlink:href)."""
    element = gradient
    seen = set()
    while element is not None and not list(element.iter(SVG_NS + "stop")):
        href = element.get(XLINK_HREF) or element.get("href")
        if not href or href in seen:
            return []
        seen.add(href)
        element = ids.get(href.lstrip("#"))
    if element is None:
        return []
    stops = []
    for stop in element.iter(SVG_NS + "stop"):
        offset = stop.get("offset", "0")
        offset = float(offset[:-1]) / 100.0 if offset.endswith("%") else float(offset)
        color, opacity = stop_color_and_opacity(stop)
        stops.append((offset, color, opacity))
    return stops


def read_logo(path):
    """Returns {'viewBox': (w, h), 'parts': [...]} for one kit file. Each part is one filled path, drawn in order."""
    root = ET.parse(path).getroot()
    parent = {child: p for p in root.iter() for child in p}
    ids = {el.get("id"): el for el in root.iter() if el.get("id")}

    view = [float(v) for v in re.split(r"[\s,]+", root.get("viewBox").strip())]
    parts = []
    for element in root.iter(SVG_NS + "path"):
        d = element.get("d")
        if not d:
            continue
        opacity = style_value(element, "opacity")
        if opacity not in (None, "1", "1.0"):
            raise ValueError(f"{path}: opacity {opacity} is not supported by the logo kit reader")

        # Compose transforms from the root down to this path.
        chain = []
        node = element
        while node is not None:
            chain.append(node)
            node = parent.get(node)
        matrix = (1.0, 0.0, 0.0, 1.0, 0.0, 0.0)
        for ancestor in reversed(chain):
            matrix = mul(matrix, parse_transform(ancestor.get("transform")))
        a, b, c, dd, e, f = matrix
        if abs(b) > 1e-9 or abs(c) > 1e-9:
            raise ValueError(f"{path}: rotation or skew is not supported")

        fill = style_value(element, "fill") or "#000000"
        if fill == "none":
            continue
        if fill.startswith("url("):
            gradient = ids.get(fill[len("url(#"):-1])
            if gradient is None or gradient.tag != SVG_NS + "linearGradient":
                raise ValueError(f"{path}: only linear gradients are supported")
            stops = gradient_stops(ids, gradient)
            fill_data = {
                "type": "linear",
                "stops": stops,
                "x1": float(gradient.get("x1")),
                "y1": float(gradient.get("y1")),
                "x2": float(gradient.get("x2")),
                "y2": float(gradient.get("y2")),
            }
            if gradient.get("gradientUnits", "objectBoundingBox") != "userSpaceOnUse":
                raise ValueError(f"{path}: only userSpaceOnUse gradients are supported")
        else:
            fill_data = {"type": "solid", "color": fill.lower()}

        parts.append({
            "id": element.get("id"),
            "d": d,
            "scaleX": a,
            "scaleY": dd,
            "translateX": e,
            "translateY": f,
            "fill": fill_data,
        })
    return {"viewBox": (view[2], view[3]), "parts": parts}


def to_hex_argb(color, opacity=1.0):
    color = color.lstrip("#")
    alpha = round(opacity * 255)
    return f"{alpha:02X}{color.upper()}"


def preview_svg(logo, background="#000000"):
    """A standalone SVG rebuilt from the extracted parts. Used to check the extraction against the original."""
    w, h = logo["viewBox"]
    defs = []
    body = []
    for index, part in enumerate(logo["parts"]):
        fill = part["fill"]
        if fill["type"] == "linear":
            gid = f"g{index}"
            stops = "".join(
                f'<stop offset="{o}" stop-color="{c}" stop-opacity="{op}"/>' for o, c, op in fill["stops"]
            )
            defs.append(
                f'<linearGradient id="{gid}" gradientUnits="userSpaceOnUse" '
                f'x1="{fill["x1"]}" y1="{fill["y1"]}" x2="{fill["x2"]}" y2="{fill["y2"]}">{stops}</linearGradient>'
            )
            paint = f"url(#{gid})"
        else:
            paint = fill["color"]
        body.append(
            f'<path d="{part["d"]}" fill="{paint}" '
            f'transform="matrix({part["scaleX"]} 0 0 {part["scaleY"]} {part["translateX"]} {part["translateY"]})"/>'
        )
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w} {h}">'
        f'<rect width="{w}" height="{h}" fill="{background}"/>'
        f"<defs>{''.join(defs)}</defs>{''.join(body)}</svg>"
    )


if __name__ == "__main__":
    import sys

    logo = read_logo(sys.argv[1])
    sys.stdout.write(preview_svg(logo))
