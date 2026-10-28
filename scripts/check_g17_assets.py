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

    # Regression guard: muzzle is at model NEGATIVE Z (around -10), while
    # rear iron sight/handgrip are at POSITIVE Z (around +22). The camera
    # looks toward -Z, so a 180-degree first-person Y flip is wrong:
    # it was observed in-game as the muzzle facing back at the player.
    right = model["display"]["firstperson_righthand"]
    left = model["display"]["firstperson_lefthand"]
    for display in (right, left):
        assert display["rotation"] == [0, 0, 0], (
            "First-person G17 muzzle at -Z must face AWAY from the camera"
        )
        assert display["scale"] == [0.25, 0.25, 0.25], "Update ADS calibration if FPS scale changes"

    # Confirm geometry contains sights at both barrel ends, and that the
    # rear (+Z) is closer to the camera than the muzzle (-Z).
    assert any(e["from"][2] < -9 and e["to"][2] < -9 for e in elements)
    assert any(e["from"][2] > 19 and e["to"][2] > 21 for e in elements)
    # This is only a mathematical placement guard, not an in-game screenshot.
    import re
    pose_java = (
        ROOT / "src/main/java/com/zombieapocalypse/client/G17ClientExtensions.java"
    ).read_text(encoding="utf-8")
    def constant(name):
        match = re.search(r"\b" + name + r"\s*=\s*(-?[0-9.]+)F", pose_java)
        assert match is not None, f"Missing ADS calibration constant: {name}"
        return float(match.group(1))

    s = right["scale"][0]
    # No rotation. Item pivot at (8, 8, 8), 16 model units to one block.
    rear_x = constant("ADS_X") + (1 - 8) * s / 16
    rear_y = constant("ADS_Y") + (21 - 8) * s / 16
    rear_z = constant("ADS_Z") + (22 - 8) * s / 16
    muzzle_z = constant("ADS_Z") + (-9 - 8) * s / 16
    assert abs(rear_x) < 0.03 and abs(rear_y) < 0.03, (
        f"Rear iron sight not at screen center: {rear_x=}, {rear_y=}"
    )
    assert muzzle_z < rear_z < -0.2, (
        f"Barrel must point forward and rear sight must be visible: {muzzle_z=}, {rear_z=}"
    )
    assert "return true;" in pose_java, (
        "The G17 ADS pose must skip the vanilla duplicate item transforms"
    )


    # The original user-supplied G17 now has our own four visual animation
    # frames. All are baked vanilla JSON item models: no TaCZ dependency.
    expected_frames = {
        0.2: ("slide_half", 83),
        0.4: ("slide_back", 83),
        0.6: ("magazine_half", 84),
        0.8: ("magazine_out", 84),
    }
    overrides = model.get("overrides", [])
    assert len(overrides) == len(expected_frames)
    for value, (suffix, expected_count) in expected_frames.items():
        resource_name = "zombieapocalypse:item/g17_pistol_" + suffix
        assert any(
            o.get("model") == resource_name
            and o.get("predicate", {}).get("zombieapocalypse:g17_frame") == value
            for o in overrides
        ), f"Missing G17 model override for frame {value}"
        frame_file = ITEM.with_name("g17_pistol_" + suffix + ".json")
        variant = json.loads(frame_file.read_text(encoding="utf-8"))
        assert len(variant["elements"]) == expected_count, frame_file
        assert variant["textures"] == model["textures"], frame_file
        assert variant["display"] == model["display"], frame_file
        for j, cuboid in enumerate(variant["elements"]):
            assert all(-16 <= v <= 32 for v in cuboid["from"] + cuboid["to"]), (
                f"{suffix} element {j}: outside Minecraft item model bounds"
            )
            assert all(a < b for a, b in zip(cuboid["from"], cuboid["to"]))
        if suffix.startswith("slide"):
            # The slide and iron sights travel in +Z (rearward), while the
            # barrel and frame cuboids must remain in place.
            assert variant["elements"][36]["from"][2] > model["elements"][36]["from"][2]
            assert variant["elements"][0] == model["elements"][0]
            assert variant["elements"][43] == model["elements"][43]
        else:
            # Our newly authored detachable magazine geometry moves down;
            # the original pistol frame must remain unchanged.
            assert variant["elements"][0]["from"][1] < model["elements"][0]["from"][1]
            assert variant["elements"][36] == model["elements"][36]
    print("OK: animated slide and magazine models, geometry bounds and item overrides validated.")

    print("OK: FPS barrel points forward and rear sight ADS X/Y alignment calibrated.")
    print("OK: 83 3D G17 cubes, first/third-person displays, and 4 PNG textures validated.")


if __name__ == "__main__":
    check()
