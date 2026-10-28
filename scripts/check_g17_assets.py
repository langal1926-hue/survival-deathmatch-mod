#!/usr/bin/env python3
"""Validate the contributed G17 model and mod-local PNG resources before building.

Only Python's standard library is required. In-game positioning and rendering
still need manual verification after a successful CI build.
"""
import json
from pathlib import Path
import struct

ROOT = Path(__file__).resolve().parents[1]
ITEM = ROOT / "src/main/resources/assets/zombieapocalypse/models/item/g17_pistol.json"
ASSETS = ROOT / "src/main/resources/assets/zombieapocalypse/textures/item"


def check():
    model = json.loads(ITEM.read_text(encoding="utf-8"))
    elements = model["elements"]
    assert len(elements) == 83, f"Expected 83 original cubes; got {len(elements)}"
    assert model["parent"] == "minecraft:block/block"
    assert "firstperson_righthand" in model["display"]
    assert "thirdperson_righthand" in model["display"]
    textures = model["textures"]
    assert set(textures) == {"m0", "m1", "m2", "m3", "particle"}
    assert textures["particle"] == "#m0"

    for alias in ("m0", "m1", "m2", "m3"):
        location = textures[alias]
        assert location.startswith("zombieapocalypse:item/g17_")
        path = ASSETS / (location.split("/", 1)[1] + ".png")
        data = path.read_bytes()
        assert data[:8] == b"\x89PNG\r\n\x1a\n", f"{path}: not a PNG"
        assert len(data) > 24 and data[12:16] == b"IHDR"
        width, height = struct.unpack_from(">II", data, 16)
        assert (width, height) == (16, 16), f"{path}: expected 16x16 texture"

    for i, element in enumerate(elements):
        bounds = element["from"] + element["to"]
        assert all(-16 <= n <= 32 for n in bounds), f"Element {i} outside model limits"
        assert all(a < b for a, b in zip(element["from"], element["to"])), f"Element {i} inverted"
        assert set(element["faces"]) == {"north", "east", "south", "west", "up", "down"}
        if "rotation" in element:
            rotation = element["rotation"]
            assert rotation["axis"] in ("x", "y", "z")
            assert rotation["angle"] in (-45, -22.5, 0, 22.5, 45)
            assert len(rotation["origin"]) == 3
        for side, face in element["faces"].items():
            assert face["texture"] in ("#m0", "#m1", "#m2", "#m3")
            u0, v0, u1, v1 = face["uv"]
            assert 0 <= u0 <= u1 <= 16 and 0 <= v0 <= v1 <= 16, (
                f"Element {i}/{side} invalid UV: {face['uv']}"
            )

    # Regression check for the original gun's axis: Blockbench +Z is the
    # muzzle direction, but first person looks down camera -Z.
    # 180 degrees about model-center Y reverses the barrel direction.
    right = model["display"]["firstperson_righthand"]
    left = model["display"]["firstperson_lefthand"]
    for display in (right, left):
        assert display["rotation"] == [0, 180, 0], "FPS barrel must point away from camera"
        assert display["scale"] == [0.25, 0.25, 0.25], "Update ADS calibration if FPS scale changes"

    # Coarse camera-space check using rear sight geometry at x=1, y=21, z=-9
    # and front sight at x=1, y=21, z=+22 (original Blockbench units).
    # This doesn't render Minecraft; it just guards against disappearing sights.
    import re
    pose_java = (
        ROOT / "src/main/java/com/zombieapocalypse/client/G17ClientExtensions.java"
    ).read_text(encoding="utf-8")
    def constant(name):
        match = re.search(r"\b" + name + r"\s*=\s*(-?[0-9.]+)F", pose_java)
        assert match is not None, f"Missing ADS calibration constant: {name}"
        return float(match.group(1))

    s = right["scale"][0]
    rear_x = constant("ADS_X") + (16 - 1 - 8) * s / 16
    rear_y = constant("ADS_Y") + (21 - 8) * s / 16
    rear_z = constant("ADS_Z") + (16 - (-9) - 8) * s / 16
    front_z = constant("ADS_Z") + (16 - 22 - 8) * s / 16
    assert abs(rear_x) < 0.03 and abs(rear_y) < 0.03, (
        f"Rear iron sight not at screen center: {rear_x=}, {rear_y=}"
    )
    assert front_z < rear_z < -0.2, (
        f"Barrel must point forward and rear sight must be visible: {front_z=}, {rear_z=}"
    )
    assert "return true;" in pose_java, (
        "The G17 ADS pose must skip the vanilla duplicate item transforms"
    )

    print("OK: FPS barrel points forward and rear sight ADS X/Y alignment calibrated.")
    print("OK: 83 3D G17 cubes, first/third-person displays, and 4 PNG textures validated.")


if __name__ == "__main__":
    check()
