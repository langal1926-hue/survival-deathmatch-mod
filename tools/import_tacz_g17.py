#!/usr/bin/env python3
"""Local, opt-in import of a licensed TaCZ Glock 17 into Zombie Apocalypse SMP.

Keep the original third-party JAR and imported assets OUT of public Git.
Supports the user's TaCZ 1.20.1 1.1.8 hotfix2 archive; generates resource-only
models, 1P frame variants, original animation/rig JSON, original audio and skin.
This is NOT a port of the TaCZ Forge 1.20.1 runtime or a full bone renderer.
"""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
import zipfile

ROOT = "assets/tacz/custom/tacz_default_gun/assets/tacz/"
GEO = "geo_models/gun/glock_17_geo.json"
ANIM = "animations/glock_17.animation.json"
SOUNDS = {
    "g17_tacz_shoot": "glock17_shoot.ogg",
    "g17_tacz_shoot_3p": "glock17_shoot_3p.ogg",
    "g17_tacz_reload": "glock17_reload_tactical.ogg",
    "g17_tacz_magout": "glock17_reload_magout.ogg",
    "g17_tacz_magin": "glock17_reload_magin.ogg",
    "g17_tacz_draw": "glock17_draw.ogg",
    "g17_tacz_reload_empty": "glock17_reload_empty.ogg",
}
FRAMES = {
    "": ("static_idle", 0.0),
    "_slide_half": ("shoot", 0.0333),
    "_slide_back": ("shoot", 0.0667),
    "_magazine_half": ("reload_tactical", 0.3),
    "_magazine_out": ("reload_tactical", 0.75),
}
VISIBLE_BONES = {
    "deco", "group3", "grip_lower", "octagon2", "trigger",
    "barrel", "barrel2", "iron_sight", "slide_base", "group",
    "sight_illuminated", "latter", "mag"
}


def vec(value):
    if isinstance(value, list):
        return [float(x) if isinstance(x, (int, float)) else 0.0 for x in value]
    if isinstance(value, dict):
        return vec(value.get("post", [0, 0, 0]))
    return [0.0, 0.0, 0.0]


def sample(track, time):
    if isinstance(track, list):
        return vec(track)
    if not isinstance(track, dict) or not track:
        return [0.0, 0.0, 0.0]
    points = sorted((float(k), vec(v)) for k, v in track.items())
    if time <= points[0][0]:
        return points[0][1]
    if time >= points[-1][0]:
        return points[-1][1]
    for (t0, a), (t1, b) in zip(points, points[1:]):
        if t0 <= time <= t1:
            u = (time-t0)/(t1-t0)
            return [x*(1-u)+y*u for x, y in zip(a, b)]
    return points[-1][1]


def delta(animation, bone, time):
    value = animation.get("bones", {}).get(bone, {}).get("position")
    if value is None:
        return [0.0, 0.0, 0.0]
    a, b = sample(value, 0), sample(value, time)
    return [y-x for x, y in zip(a, b)]


def cube_to_element(cube, movement):
    origin, size = cube["origin"], cube["size"]
    start = [round(origin[i]+(8 if i != 1 else 0)+movement[i], 4)
             for i in range(3)]
    end = [round(start[i]+size[i], 4) for i in range(3)]
    if any(a >= b or a < -16 or b > 32 for a,b in zip(start,end)):
        return None
    faces = {}
    for side, face in cube.get("uv", {}).items():
        x,y = face.get("uv", [0,0])
        w,h = face.get("uv_size", [0,0])
        if x < 0 or y < 0 or not w or not h:
            continue
        uv = [round(float(v)/64,5) for v in (x,y,x+w,y+h)]
        if all(-0.01 <= v <= 16.01 for v in uv):
            faces[side] = {"uv": uv, "texture": "#gun"}
    if not faces:
        return None
    result = {"from": start, "to": end, "faces": faces}
    rotations = [(axis, angle) for axis, angle in zip("xyz", cube.get("rotation", [0,0,0]))
                 if abs(angle) > 0.001]
    if len(rotations) == 1 and rotations[0][1] in (-45,-22.5,22.5,45):
        axis, angle = rotations[0]
        pivot = cube.get("pivot", origin)
        result["rotation"] = {
            "axis": axis, "angle": angle,
            "origin": [round(pivot[i]+(8 if i != 1 else 0)+movement[i],4)
                       for i in range(3)],
        }
    return result


def convert_model(geometry, animation, clip_name, time, template):
    bones = geometry["minecraft:geometry"][0]["bones"]
    by_name = {bone["name"]: bone for bone in bones}
    clip = animation["animations"].get(clip_name, {})
    elements = []
    for bone in bones:
        if bone["name"] not in VISIBLE_BONES:
            continue
        movement = [0.0,0.0,0.0]
        parent = bone.get("parent")
        visited = set()
        while parent and parent not in visited:
            visited.add(parent)
            if parent == "slide" and clip_name == "shoot":
                movement = [a+b for a,b in zip(movement, delta(clip, "slide", time))]
            if parent == "mag_and_bullet" and clip_name.startswith("reload"):
                movement = [a+b for a,b in zip(movement, delta(clip, "mag_and_bullet", time))]
            parent = by_name.get(parent, {}).get("parent")
        for cube in bone.get("cubes", []):
            element = cube_to_element(cube, movement)
            if element:
                elements.append(element)
    if len(elements) < 110:
        raise ValueError("G17 model conversion unexpectedly incomplete")
    return {
        "parent": "minecraft:block/block", "gui_light": "front",
        "ambientocclusion": False,
        "textures": {"gun": "zombieapocalypse:item/tacz_g17_uv", "particle": "#gun"},
        "display": template.get("display", {}), "elements": elements
    }


def import_assets(jar_path, output, template_path):
    jar_path, output = Path(jar_path), Path(output)
    template = json.loads(Path(template_path).read_text(encoding="utf-8"))
    with zipfile.ZipFile(jar_path) as archive:
        for name in (GEO, ANIM, "textures/gun/uv/glock_17.png"):
            if ROOT+name not in archive.namelist():
                raise ValueError("TaCZ 1.1.8 G17 is missing: " + name)
        geo = json.loads(archive.read(ROOT+GEO))
        animations = json.loads(archive.read(ROOT+ANIM))
        asset_root = output/"assets"/"zombieapocalypse"
        model_dir = asset_root/"models"/"item"
        texture_dir = asset_root/"textures"/"item"
        rig_dir = asset_root/"licensed_tacz"/"g17"
        sound_dir = asset_root/"sounds"/"g17_tacz"
        for folder in (model_dir,texture_dir,rig_dir,sound_dir):
            folder.mkdir(parents=True,exist_ok=True)
        (rig_dir/"glock_17_geo.json").write_bytes(archive.read(ROOT+GEO))
        (rig_dir/"glock_17.animation.json").write_bytes(archive.read(ROOT+ANIM))
        (texture_dir/"tacz_g17_uv.png").write_bytes(
            archive.read(ROOT+"textures/gun/uv/glock_17.png"))
        for name in ("textures/gun/uv/glock_17_n.png",
                     "textures/gun/uv/glock_17_s.png",
                     "display/guns/glock_17_display.json",
                     "scripts/glock_17_state_machine.lua"):
            if ROOT+name in archive.namelist():
                (rig_dir/Path(name).name).write_bytes(archive.read(ROOT+name))
        for suffix,(clip,time) in FRAMES.items():
            model=convert_model(geo, animations, clip, time, template)
            if not suffix:
                model["overrides"]=[
                    {"predicate":{"zombieapocalypse:g17_frame":value},
                     "model":"zombieapocalypse:item/g17_pistol"+frame}
                    for value,frame in ((.2,"_slide_half"),(.4,"_slide_back"),
                                        (.6,"_magazine_half"),(.8,"_magazine_out"))]
            (model_dir/("g17_pistol"+suffix+".json")).write_text(
                json.dumps(model,separators=(",",":")),encoding="utf-8")
        sound_defs={}
        for event, filename in SOUNDS.items():
            (sound_dir/filename).write_bytes(
                archive.read(ROOT+"tacz_sounds/glock_17/"+filename))
            sound_defs[event]={"sounds":[{"name":"zombieapocalypse:g17_tacz/"+filename[:-4],
                                          "stream":False}]}
        (asset_root/"sounds.json").write_text(
            json.dumps(sound_defs,indent=2),encoding="utf-8")
        manifest={
            "vendor_jar_sha256":hashlib.sha256(jar_path.read_bytes()).hexdigest(),
            "converted_visible_cubes":len(convert_model(geo,animations,"static_idle",0,template)["elements"]),
            "animation_clips":list(animations["animations"]),
            "imported_sounds":list(sound_defs),
            "note":"Locally imported licensed TaCZ G17. Do not commit generated resources to public Git."
        }
        (rig_dir/"import_manifest.json").write_text(
            json.dumps(manifest,indent=2),encoding="utf-8")
        print(json.dumps(manifest,indent=2))
        return manifest


if __name__ == "__main__":
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("--jar",required=True)
    p.add_argument("--output",required=True)
    p.add_argument("--template",
        default="src/main/resources/assets/zombieapocalypse/models/item/g17_pistol.json")
    a=p.parse_args()
    import_assets(a.jar,a.output,a.template)
