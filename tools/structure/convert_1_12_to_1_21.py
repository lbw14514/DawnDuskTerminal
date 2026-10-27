"""Translate a 1.12.2 structure NBT into a 1.21.1 one, for a loadout of vanilla + Twilight Forest only.

Anything whose block does not exist in 1.21.1 vanilla or Twilight Forest 1.21.1 becomes
minecraft:green_wool.

Property-name/meaning changes handled here were derived from the 1.21.1 jars themselves
(assets/*/blockstates) and from TF's own 1.12.2 vs 1.21.1 shipped structures, not from memory.
"""
import sys, os, json, collections

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import nbtlib

GREEN_WOOL = ("minecraft:green_wool", {})

# ---------------------------------------------------------------- vanilla 1.12.2 -> 1.21.1
STONE_VARIANTS = {
    "stone": "minecraft:stone",
    "andesite": "minecraft:andesite",
    "diorite": "minecraft:diorite",
    "granite": "minecraft:granite",
}


def slab_type(half):
    return "top" if half == "top" else "bottom"


def v_vanilla(name, p):
    """Return (target_name, props) or None when unhandled."""
    if name == "minecraft:air":
        return "minecraft:air", {}
    if name == "minecraft:stone":
        t = STONE_VARIANTS.get(p.get("variant", "stone"))
        return (t, {}) if t else None
    if name in ("minecraft:gravel", "minecraft:dirt", "minecraft:coal_ore", "minecraft:cobblestone",
                "minecraft:redstone_block", "minecraft:crafting_table", "minecraft:iron_ore",
                "minecraft:bookshelf", "minecraft:sand", "minecraft:netherrack", "minecraft:clay",
                "minecraft:sea_lantern", "minecraft:enchanting_table", "minecraft:redstone_lamp",
                "minecraft:water"):
        if name == "minecraft:water":
            return "minecraft:water", {"level": p.get("level", "0")}
        if name == "minecraft:redstone_lamp":
            return "minecraft:redstone_lamp", {"lit": "false"}
        return name, {}
    if name == "minecraft:grass":
        return "minecraft:grass_block", {"snowy": p.get("snowy", "false")}
    if name == "minecraft:leaves":
        if p.get("variant") != "oak":
            return None
        return "minecraft:oak_leaves", {"distance": "7",
                                        "persistent": "false" if p.get("decayable") == "true" else "true",
                                        "waterlogged": "false"}
    if name == "minecraft:log":
        if p.get("variant") != "oak":
            return None
        return "minecraft:oak_log", {"axis": p.get("axis", "y")}
    if name == "minecraft:lit_redstone_lamp":
        return "minecraft:redstone_lamp", {"lit": "true"}
    if name == "minecraft:wool":
        if p.get("color") != "red":
            return None
        return "minecraft:red_wool", {}
    if name == "minecraft:carpet":
        if p.get("color") != "red":
            return None
        return "minecraft:red_carpet", {}
    if name == "minecraft:red_flower":
        if p.get("type") != "oxeye_daisy":
            return None
        return "minecraft:oxeye_daisy", {}
    if name == "minecraft:tallgrass":
        return "minecraft:short_grass", {}
    if name == "minecraft:double_stone_slab":
        v = p.get("variant")
        table = {"brick": "minecraft:brick_slab", "stone_brick": "minecraft:stone_brick_slab",
                 "stone": "minecraft:smooth_stone_slab", "sandstone": "minecraft:sandstone_slab",
                 "cobblestone": "minecraft:cobblestone_slab"}
        t = table.get(v)
        return (t, {"type": "double", "waterlogged": "false"}) if t else None
    if name == "minecraft:stone_slab":
        v = p.get("variant")
        table = {"brick": "minecraft:brick_slab", "stone_brick": "minecraft:stone_brick_slab",
                 "stone": "minecraft:smooth_stone_slab", "sandstone": "minecraft:sandstone_slab",
                 "cobblestone": "minecraft:cobblestone_slab"}
        t = table.get(v)
        return (t, {"type": slab_type(p.get("half")), "waterlogged": "false"}) if t else None
    if name == "minecraft:cauldron":
        return "minecraft:cauldron", {}
    if name == "minecraft:flower_pot":
        return "minecraft:flower_pot", {}
    if name == "minecraft:wall_banner":
        return "minecraft:red_wall_banner", {"facing": p.get("facing", "north")}
    if name == "minecraft:hopper":
        return "minecraft:hopper", {"enabled": p.get("enabled", "true"),
                                    "facing": p.get("facing", "down")}
    if name == "minecraft:repeating_command_block":
        return "minecraft:repeating_command_block", {"conditional": p.get("conditional", "false"),
                                                    "facing": p.get("facing", "north")}
    if name == "minecraft:iron_bars":
        return "minecraft:iron_bars", {d: p.get(d, "false") for d in ("east", "north", "south", "west")} | {
            "waterlogged": "false"}
    if name == "minecraft:iron_trapdoor":
        return "minecraft:iron_trapdoor", {"facing": p.get("facing", "north"),
                                           "half": p.get("half", "bottom"),
                                           "open": p.get("open", "false"),
                                           "powered": "false", "waterlogged": "false"}
    if name == "minecraft:lever":
        return "minecraft:lever", {"face": "wall", "facing": p.get("facing", "north"),
                                   "powered": p.get("powered", "false")}
    if name == "minecraft:cobblestone_wall":
        if p.get("variant") not in (None, "cobblestone"):
            return None
        out = {d: ("low" if p.get(d) == "true" else "none") for d in ("east", "north", "south", "west")}
        out["up"] = p.get("up", "true")
        out["waterlogged"] = "false"
        return "minecraft:cobblestone_wall", out
    if name == "minecraft:cake":
        return "minecraft:cake", {"bites": p.get("bites", "0")}
    if name == "minecraft:anvil":
        return "minecraft:anvil", {"facing": p.get("facing", "north")}
    return None


# ------------------------------------------------------- twilight forest 1.12.2 -> 1.21.1
SIMPLE_TF = {
    # renamed / unchanged, no state properties worth carrying
    "twilightforest:trans_planks": ("twilightforest:transformation_planks", {}),
    "twilightforest:mine_planks": ("twilightforest:mining_planks", {}),
    "twilightforest:time_planks": ("twilightforest:time_planks", {}),
    "twilightforest:canopy_planks": ("twilightforest:canopy_planks", {}),
    "twilightforest:knightmetal_block": ("twilightforest:knightmetal_block", {}),
    "twilightforest:hedge": ("twilightforest:hedge", {}),
    "twilightforest:fluffy_cloud": ("twilightforest:fluffy_cloud", {}),
    "twilightforest:firefly_jar": ("twilightforest:firefly_jar", {}),
    "twilightforest:trollber": ("twilightforest:trollber", {}),
    "twilightforest:uncrafting_table": ("twilightforest:uncrafting_table", {"powered": "false"}),
    "twilightforest:aurora_block": ("twilightforest:aurora_block", {}),
    "twilightforest:moonworm": ("twilightforest:moonworm", {}),      # facing no longer a property
    "twilightforest:firefly": ("twilightforest:firefly", {}),        # facing no longer a property
}

MAZE_STONE = {"brick": "twilightforest:mazestone_brick",
              "decorative": "twilightforest:decorative_mazestone",
              "mosaic": "twilightforest:mazestone_mosaic"}

TOWER_DEVICE = {
    "reappearing_inactive": ("twilightforest:reappearing_block", {"active": "false", "vanished": "false"}),
    "builder_inactive": ("twilightforest:carminite_builder", {"state": "builder_inactive"}),
    "reactor_inactive": ("twilightforest:carminite_reactor", {"active": "false"}),
    "ghasttrap_inactive": ("twilightforest:ghast_trap", {"active": "false"}),
}

BLOCK_STORAGE = {"fiery": "twilightforest:fiery_block",
                 "carminite": "twilightforest:carminite_block",
                 "ironwood": "twilightforest:ironwood_block"}

# 1.12.2 door/trapdoor/stairs style blocks that are a straight rename in 1.21.1
TF_SLAB = {"twilightforest:trans_slab": "twilightforest:transformation_slab",
           "twilightforest:mine_slab": "twilightforest:mining_slab",
           "twilightforest:sort_slab": "twilightforest:sorting_slab",
           "twilightforest:time_slab": "twilightforest:time_slab",
           "twilightforest:dark_slab": "twilightforest:dark_slab",
           "twilightforest:mangrove_slab": "twilightforest:mangrove_slab",
           "twilightforest:canopy_slab": "twilightforest:canopy_slab",
           "twilightforest:twilight_oak_slab": "twilightforest:twilight_oak_slab",
           "twilightforest:aurora_slab": "twilightforest:aurora_slab"}

TF_DOUBLESLAB = {"twilightforest:trans_doubleslab": "twilightforest:transformation_slab",
                 "twilightforest:mine_doubleslab": "twilightforest:mining_slab",
                 "twilightforest:twilight_oak_doubleslab": "twilightforest:twilight_oak_slab"}

TF_STAIRS = {"twilightforest:canopy_stairs": "twilightforest:canopy_stairs",
             "twilightforest:sort_stairs": "twilightforest:sorting_stairs",
             "twilightforest:trans_stairs": "twilightforest:transformation_stairs",
             "twilightforest:twilight_oak_stairs": "twilightforest:twilight_oak_stairs"}

TF_TRAPDOOR = {"twilightforest:canopy_trapdoor": "twilightforest:canopy_trapdoor",
               "twilightforest:mine_trapdoor": "twilightforest:mining_trapdoor",
               "twilightforest:sort_trapdoor": "twilightforest:sorting_trapdoor",
               "twilightforest:time_trapdoor": "twilightforest:time_trapdoor",
               "twilightforest:dark_trapdoor": "twilightforest:dark_trapdoor",
               "twilightforest:mangrove_trapdoor": "twilightforest:mangrove_trapdoor",
               "twilightforest:twilight_oak_trapdoor": "twilightforest:twilight_oak_trapdoor",
               "twilightforest:trans_trapdoor": "twilightforest:transformation_trapdoor"}

TF_DOOR = {"twilightforest:canopy_door", "twilightforest:time_door", "twilightforest:mangrove_door",
           "twilightforest:mining_door", "twilightforest:sorting_door", "twilightforest:twilight_oak_door",
           "twilightforest:transformation_door"}

TF_PILLAR = {"twilightforest:aurora_pillar": "twilightforest:aurora_pillar",
             "twilightforest:stone_twist": "twilightforest:twisted_stone"}


def v_tf(name, p):
    if name in SIMPLE_TF:
        return SIMPLE_TF[name]
    if name == "twilightforest:castle_pillar":
        variant = p.get("variant", "bold")
        target = {"bold": "twilightforest:bold_castle_brick_pillar",
                  "encased": "twilightforest:encased_castle_brick_pillar"}.get(variant)
        if not target:
            return None
        axis = p.get("axis", "y")
        if axis == "none":          # 1.12.2 cap/joint form; 1.21.1 has axis pillars only
            axis = "y"
        return target, {"axis": axis}
    if name == "twilightforest:castle_stairs":
        if p.get("variant") != "encased":
            return None
        return "twilightforest:encased_castle_brick_stairs", {
            "facing": p.get("facing", "north"), "half": p.get("half", "bottom"),
            "shape": p.get("shape", "straight"), "waterlogged": "false"}
    if name == "twilightforest:maze_stone":
        t = MAZE_STONE.get(p.get("variant"))
        return (t, {}) if t else None
    if name == "twilightforest:tower_wood":
        t = {"plain": "twilightforest:towerwood", "encased": "twilightforest:encased_towerwood"}.get(p.get("variant"))
        return (t, {}) if t else None
    if name == "twilightforest:twilight_log":
        variant = p.get("variant")
        t = {"oak": "twilightforest:twilight_oak_log", "canopy": "twilightforest:canopy_log"}.get(variant)
        return (t, {"axis": p.get("axis", "y")}) if t else None
    if name == "twilightforest:magic_log":
        if p.get("variant") == "sort":
            return "twilightforest:sorting_log", {"axis": p.get("axis", "y")}
        return None
    if name == "twilightforest:magic_leaves":
        if p.get("variant") != "trans":
            return None
        return "twilightforest:transformation_leaves", {
            "distance": "7", "persistent": "false" if p.get("decayable") == "true" else "true",
            "waterlogged": "false"}
    if name in TF_SLAB:
        return TF_SLAB[name], {"type": slab_type(p.get("half")), "waterlogged": "false"}
    if name in TF_DOUBLESLAB:
        return TF_DOUBLESLAB[name], {"type": "double", "waterlogged": "false"}
    if name in TF_STAIRS:
        return TF_STAIRS[name], {"facing": p.get("facing", "north"), "half": p.get("half", "bottom"),
                                 "shape": p.get("shape", "straight"), "waterlogged": "false"}
    if name == "twilightforest:nagastone_stairs":
        t = {"left": "twilightforest:nagastone_stairs_left",
             "right": "twilightforest:nagastone_stairs_right"}.get(p.get("direction"))
        if not t:
            return None
        return t, {"facing": p.get("facing", "north"), "half": p.get("half", "bottom"),
                   "shape": p.get("shape", "straight"), "waterlogged": "false"}
    if name in TF_TRAPDOOR:
        return TF_TRAPDOOR[name], {"facing": p.get("facing", "north"), "half": p.get("half", "bottom"),
                                   "open": p.get("open", "false"), "waterlogged": "false"}
    if name in TF_DOOR:
        return name, {"facing": p.get("facing", "north"), "half": p.get("half", "lower"),
                      "hinge": p.get("hinge", "left"), "open": p.get("open", "false")}
    if name in TF_PILLAR:
        return TF_PILLAR[name], {"axis": p.get("axis", "y")}
    if name == "twilightforest:stone_twist_thin":
        out = {d: p.get(d, "false") for d in ("down", "east", "north", "south", "up", "west")}
        out["axis"] = p.get("axis", "y")
        out["waterlogged"] = "false"
        return "twilightforest:twisted_stone_pillar", out
    if name == "twilightforest:naga_stone":
        variant = p.get("variant", "solid")
        if variant.endswith("_head"):
            return "twilightforest:nagastone_head", {"facing": variant[:-len("_head")]}
        return "twilightforest:nagastone", {"variant": variant}
    if name == "twilightforest:etched_nagastone":
        return "twilightforest:etched_nagastone", {"facing": p.get("facing", "north")}
    if name == "twilightforest:etched_nagastone_weathered":
        # 1.21.1 renders the old "weathered" textures on the cracked_* family
        return "twilightforest:cracked_etched_nagastone", {"facing": p.get("facing", "north")}
    if name == "twilightforest:nagastone_pillar_weathered":
        return "twilightforest:cracked_nagastone_pillar", {"axis": p.get("axis", "y"),
                                                          "reversed": p.get("reversed", "false")}
    if name == "twilightforest:block_storage":
        t = BLOCK_STORAGE.get(p.get("variant"))
        return (t, {}) if t else None
    if name == "twilightforest:tower_device":
        return TOWER_DEVICE.get(p.get("variant"))
    if name == "twilightforest:huge_mushgloom":
        if p.get("variant") != "all_outside":
            return None
        # 1.12.2 "all_outside" == every face shows the outside texture == all six connected
        out = {d: "true" for d in ("down", "east", "north", "south", "up", "west")}
        return "twilightforest:huge_mushgloom", out
    if name == "twilightforest:root":
        v = p.get("variant")
        if v == "root":
            return "twilightforest:root", {}
        if v == "liveroot":
            return "twilightforest:liveroot_block", {}
        return None
    if name == "twilightforest:experiment_115":
        return "twilightforest:experiment_115", {"omnomnom": p.get("omnomnom", "0"),
                                                 "regenerate": p.get("regenerate", "true")}
    if name == "twilightforest:iron_ladder":
        return "twilightforest:iron_ladder", {"facing": p.get("facing", "north"),
                                              "left": p.get("left", "false"),
                                              "right": p.get("right", "false"),
                                              "waterlogged": "false"}
    if name == "twilightforest:trophy_pedestal":
        return "twilightforest:trophy_pedestal", {
            "active": "false" if p.get("latent") == "true" else "true",
            "facing": p.get("facing", "north")}
    if name == "twilightforest:underbrick":
        v = p.get("variant")
        if v == "normal":
            return "twilightforest:underbrick", {}
        if v == "floor":
            return "twilightforest:underbrick_floor", {}
        return None
    if name == "twilightforest:twilight_oak_plate":
        return "twilightforest:twilight_oak_pressure_plate", {"powered": p.get("powered", "false")}
    if name == "twilightforest:mangrove_plate":
        return "twilightforest:mangrove_pressure_plate", {"powered": p.get("powered", "false")}
    if name == "twilightforest:mine_gate":
        return "twilightforest:mining_fence_gate", {"facing": p.get("facing", "north"),
                                                    "in_wall": p.get("in_wall", "false"),
                                                    "open": p.get("open", "false"),
                                                    "powered": p.get("powered", "false")}
    if name == "twilightforest:twilight_portal":
        return "twilightforest:twilight_portal", {"is_one_way": p.get("is_one_way", "false")}
    return None


def convert_state(name, props):
    """(target_name, target_props, note)"""
    if name.startswith("minecraft:"):
        r = v_vanilla(name, props)
        if r:
            return r[0], r[1], ""
        return GREEN_WOOL[0], {}, "vanilla block removed in 1.21.1"
    if name.startswith("twilightforest:"):
        r = v_tf(name, props)
        if r:
            return r[0], r[1], ""
        return GREEN_WOOL[0], {}, "no 1.21.1 Twilight Forest equivalent"
    return GREEN_WOOL[0], {}, "mod not present in 1.21.1"


# 1.12.2 tile-entity ids whose block survives; everything else gets its NBT dropped
def build_be(name, old_nbt):
    if name == "minecraft:repeating_command_block":
        return {
            "id": nbtlib.Tag(nbtlib.TAG_STRING, "minecraft:command_block"),
            "Command": nbtlib.Tag(nbtlib.TAG_STRING,
                                  "item replace block ~ ~-1 ~ container.0 with twilightforest:experiment_115 5"),
            "auto": nbtlib.Tag(nbtlib.TAG_BYTE, 0),
            "powered": nbtlib.Tag(nbtlib.TAG_BYTE, 0),
            "conditionMet": nbtlib.Tag(nbtlib.TAG_BYTE, 0),
            "UpdateLastExecution": nbtlib.Tag(nbtlib.TAG_BYTE, 1),
            "TrackOutput": nbtlib.Tag(nbtlib.TAG_BYTE, 0),
            "SuccessCount": nbtlib.Tag(nbtlib.TAG_INT, 0),
            "LastExecution": nbtlib.Tag(nbtlib.TAG_LONG, 0),
        }
    return None


def main(src, dst):
    root_name, root, _ = nbtlib.load(src)
    top = root.value
    size = [int(v) for v in top["size"].value]
    old_pal = top["palette"].value
    old_blocks = top["blocks"].value
    old_entities = top["entities"].value

    # old palette index -> new palette index
    new_pal = []          # list of (name, props dict)
    pal_index = {}
    remap = {}
    notes = collections.Counter()
    per_source = collections.Counter()
    usage = collections.Counter(b["state"].value for b in old_blocks)

    for i, e in enumerate(old_pal):
        src_name = e["Name"].value
        src_props = ({k: v.value for k, v in e["Properties"].value.items()} | {}) if "Properties" in e else {}
        tgt_name, tgt_props, note = convert_state(src_name, src_props)
        key = (tgt_name, tuple(sorted(tgt_props.items())))
        if key not in pal_index:
            pal_index[key] = len(new_pal)
            new_pal.append((tgt_name, tgt_props))
        remap[i] = pal_index[key]
        per_source[(src_name, src_props.get("variant"))] += usage.get(i, 0)
        if tgt_name == GREEN_WOOL[0]:
            notes[f"{src_name}" + (f"[{src_props.get('variant')}]" if "variant" in src_props else "")] += usage.get(i, 0)

    new_blocks = []
    dropped_nbt = 0
    kept_nbt = 0
    for b in old_blocks:
        entry = {
            "pos": nbtlib.Tag(nbtlib.TAG_LIST, [int(v) for v in b["pos"].value]),
            "state": nbtlib.Tag(nbtlib.TAG_INT, remap[b["state"].value]),
        }
        tgt_name = new_pal[entry["state"].value][0]
        if "nbt" in b:
            be = build_be(tgt_name, b["nbt"])
            if be is not None:
                entry["nbt"] = nbtlib.Tag(nbtlib.TAG_COMPOUND, be)
                kept_nbt += 1
            else:
                dropped_nbt += 1
        new_blocks.append(entry)

    out_pal = []
    for name, props in new_pal:
        comp = {"Name": nbtlib.Tag(nbtlib.TAG_STRING, name)}
        if props:
            comp["Properties"] = nbtlib.Tag(nbtlib.TAG_COMPOUND, {
                k: nbtlib.Tag(nbtlib.TAG_STRING, v) for k, v in props.items()})
        out_pal.append(comp)

    out = {
        "size": nbtlib.Tag(nbtlib.TAG_LIST, size),
        "entities": nbtlib.Tag(nbtlib.TAG_LIST, old_entities),
        "blocks": nbtlib.Tag(nbtlib.TAG_LIST, new_blocks),
        "palette": nbtlib.Tag(nbtlib.TAG_LIST, out_pal),
        "DataVersion": nbtlib.Tag(nbtlib.TAG_INT, 3955),
    }
    if "author" in top:
        out["author"] = nbtlib.Tag(nbtlib.TAG_STRING, top["author"].value)

    os.makedirs(os.path.dirname(dst), exist_ok=True)
    nbtlib.save(dst, root_name, nbtlib.Tag(nbtlib.TAG_COMPOUND, out))

    print(f"size {size}  palette {len(old_pal)} -> {len(new_pal)}  blocks {len(old_blocks)}")
    print(f"tile-entity nbt: kept {kept_nbt}, dropped {dropped_nbt}")
    green = sum(1 for n, _ in new_pal if n == GREEN_WOOL[0])
    if green:
        print(f"green wool palette entries: {green}, blocks: {sum(c for i, c in usage.items() if remap[i] == pal_index[(GREEN_WOOL[0], ())])}")
    print("\nreplaced with green wool:")
    for k, c in notes.most_common():
        print(f"  {c:6d}  {k}")
    print("\nfinal palette:")
    for i, (n, p) in enumerate(new_pal):
        print(f"  {i:3d}  {n}  {p}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1], sys.argv[2]))
