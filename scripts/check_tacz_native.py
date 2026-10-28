#!/usr/bin/env python3
"""Check that native TaCZ integration does not revive the prototype G17.

This is a static regression check. Real client/server mod loading and
in-game visual/network behavior must additionally be tested by the user.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "src/main/java/com/zombieapocalypse"


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


def main() -> None:
    gradle = read("build.gradle")
    mod = read("src/main/templates/META-INF/neoforge.mods.toml")
    bridge = read(
        "src/main/java/com/zombieapocalypse/compat/TaCZGunEvents.java")
    crafting = read(
        "src/main/java/com/zombieapocalypse/compat/TaCZCraftingRestrictions.java")
    main_mod = read("src/main/java/com/zombieapocalypse/ZombieApocalypseMod.java")
    client_renderers = read("src/main/java/com/zombieapocalypse/client/ClientModEvents.java")

    assert "maven.modrinth:OypNE65K:62q6mB5Q" in gradle
    assert "compileOnly 'maven.modrinth:" in gradle
    assert "localRuntime 'maven.modrinth:" in gradle
    assert "local/licensed_tacz_g17" not in gradle, (
        "Old per-G17 resource overlays must not affect native TaCZ weapons")
    assert 'modId="tacz"' in mod
    assert 'type="required"' in mod and 'side="BOTH"' in mod

    assert "GunFireEvent" in bridge
    assert "LogicalSide.SERVER" in bridge
    assert "event.isCanceled()" in bridge
    assert "NoiseSystem.alertNearbyInfected(" in bridge

    for block in ("GUN_SMITH_TABLE", "WORKBENCH_111",
                  "WORKBENCH_211", "WORKBENCH_121"):
        assert "ModBlocks." + block + ".get()" in crafting
    assert "event.setCanceled(true)" in crafting
    assert "GunNetwork::register" not in main_mod
    assert "G17LicensedSounds.SOUNDS.register(" not in main_mod
    assert "registerG17AnimationProperty" not in client_renderers

    for path in (
        "ClientGunInput.java", "ClientGunKeys.java", "ClientGunHud.java",
        "G17ViewRenderer.java", "G17ClientExtensions.java"
    ):
        client = read("src/main/java/com/zombieapocalypse/client/" + path)
        assert "@EventBusSubscriber(" not in client, (
            path + " reactivated legacy input/render/HUD over native TaCZ")

    # Keep legacy item IDs to avoid breaking existing saves on upgrade.
    items = read("src/main/java/com/zombieapocalypse/item/ModItems.java")
    assert 'registerItem("g17_pistol"' in items
    assert 'registerSimpleItem("pistol_ammo"' in items
    print("OK: separate TaCZ mod required; legacy G17 handlers disabled.")
    print("OK: real TaCZ gunfire hooks infected hearing; gunsmithing blocked.")


if __name__ == "__main__":
    main()
