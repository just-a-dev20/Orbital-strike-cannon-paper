#!/usr/bin/env python3
"""Converts the GeckoLib (Bedrock geometry) model of the original mod into a vanilla Java item model.

Usage: convert_geckolib_model.py <orbital_railgun.geo.json> <original item model json> <output json>

The transformation mirrors what GeckoLib 4 does at render time
(BakedModelFactory.DEFAULT_FACTORY + GeoItemRenderer#preRender):
  * x is mirrored: java_from_x = -(origin_x + size_x), pivot_x = -pivot_x
  * rotations around x and y are negated, z is kept
  * the model origin sits at the item centre, offset by +0.01 blocks (0.16 px) on y
Box UVs are laid out like Blockbench does for box-UV cubes, using floored sizes like GeckoLib.
Minecraft 26.x accepts arbitrary single-axis rotation angles, so no rounding is needed.
"""
import json
import math
import sys

TEXTURE = "orbital_railgun:item/orbital_railgun"
CENTER_OFFSET = (8.0, 8.16, 8.0)


def box_uv_faces(uv, size):
    u, v = uv
    sx, sy, sz = (math.floor(s) for s in size)
    return {
        "north": [u + sz, v + sz, u + sz + sx, v + sz + sy],
        "east": [u, v + sz, u + sz, v + sz + sy],
        "south": [u + 2 * sz + sx, v + sz, u + 2 * sz + 2 * sx, v + sz + sy],
        "west": [u + sz + sx, v + sz, u + 2 * sz + sx, v + sz + sy],
        "up": [u + sz + sx, v + sz, u + sz, v],
        "down": [u + sz + 2 * sx, v, u + sz + sx, v + sz],
    }


def face_has_area(face, size):
    sx, sy, sz = size
    return {
        "north": sx > 0 and sy > 0, "south": sx > 0 and sy > 0,
        "east": sz > 0 and sy > 0, "west": sz > 0 and sy > 0,
        "up": sx > 0 and sz > 0, "down": sx > 0 and sz > 0,
    }[face]


def r(value):
    return round(value, 5)


def convert_cube(cube, tex_w, tex_h):
    ox, oy, oz = cube["origin"]
    sx, sy, sz = cube["size"]
    cx, cy, cz = CENTER_OFFSET
    element = {
        "from": [r(cx - (ox + sx)), r(cy + oy), r(cz + oz)],
        "to": [r(cx - ox), r(cy + oy + sy), r(cz + oz + sz)],
    }

    rotation = cube.get("rotation", [0, 0, 0])
    if any(rotation):
        axes = [(axis, angle) for axis, angle in zip("xyz", (-rotation[0], -rotation[1], rotation[2])) if angle]
        if len(axes) != 1:
            raise SystemExit(f"multi-axis rotation not supported: {cube}")
        px, py, pz = cube.get("pivot", [0, 0, 0])
        element["rotation"] = {
            "angle": axes[0][1],
            "axis": axes[0][0],
            "origin": [r(cx - px), r(cy + py), r(cz + pz)],
        }

    if not isinstance(cube["uv"], list):
        raise SystemExit(f"per-face UV not supported: {cube}")
    scale_u, scale_v = 16.0 / tex_w, 16.0 / tex_h
    faces = {}
    for name, (u1, v1, u2, v2) in box_uv_faces(cube["uv"], cube["size"]).items():
        if face_has_area(name, cube["size"]):
            faces[name] = {"uv": [r(u1 * scale_u), r(v1 * scale_v), r(u2 * scale_u), r(v2 * scale_v)], "texture": "#0"}
    element["faces"] = faces
    return element


def main(geo_path, original_model_path, out_path):
    with open(geo_path) as f:
        geometry = json.load(f)["minecraft:geometry"][0]
    with open(original_model_path) as f:
        original = json.load(f)

    tex_w = geometry["description"]["texture_width"]
    tex_h = geometry["description"]["texture_height"]
    elements = []
    for bone in geometry["bones"]:
        if any(bone.get("rotation", [0, 0, 0])) or any(bone.get("pivot", [0, 0, 0])):
            raise SystemExit("bone transforms are not supported")
        elements += [convert_cube(cube, tex_w, tex_h) for cube in bone.get("cubes", [])]

    model = {
        "credit": "Converted from Orbital Railgun by Mishkis (MIT)",
        "textures": {"0": TEXTURE, "particle": TEXTURE},
        "elements": elements,
        "display": original["display"],
    }
    with open(out_path, "w") as f:
        json.dump(model, f, indent=2)
        f.write("\n")


if __name__ == "__main__":
    if len(sys.argv) != 4:
        raise SystemExit(__doc__)
    main(*sys.argv[1:])
