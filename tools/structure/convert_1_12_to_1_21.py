"""Translate a 1.12.2 structure NBT into a 1.21.1 one for a loadout of vanilla + Twilight Forest
+ this repo's own dawnduskterminal blocks.

Anything without a 1.21.1 equivalent becomes minecraft:green_wool.

Every name and property mapping here was derived from the 1.21.1 jars themselves
(assets/*/blockstates, Blocks.java, block createBlockStateDefinition) and from Twilight Forest's
own 1.12.2 vs 1.21.1 shipped structures -- never from memory.

Deliberate replacements agreed with the project owner:
  * tconstruct:rack          -> twilightforest:twilight_oak_drying_rack (TF's new drying rack)
  * tconstruct:seared_tank   -> dawnduskterminal:wrought_iron_glass     (new repo block)
  * tconstruct:seared_glass  -> dawnduskterminal:wrought_iron_glass
"""
import sys, os, collections

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import nbtlib

GREEN_WOOL = "minecraft:green_wool"
WROUGHT_GLASS = "dawnduskterminal:wrought_iron_glass"
DRYING_RACK = "twilightforest:twilight_oak_drying_rack"

# 1.12.2 all-users -> 1.21.1 split blocks
STONE_VARIANTS = {"stone": "minecraft:stone", "andesite": "minecraft:andesite",
                  "diorite": "minecraft:diorite", "granite": "minecraft:granite"}

WOODS = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak"}

SLAB_TARGETS = {"brick": "minecraft:brick_slab",
                "stone_brick": "minecraft:stone_brick_slab",
                "stone": "minecraft:smooth_stone_slab",
                "sandstone": "minecraft:sandstone_slab",
                "cobblestone": "minecraft:cobblestone_slab",
                "nether_brick": "minecraft:nether_brick_slab",
                "quartz": "minecraft:quartz_slab"}

FLOWERS = {"poppy": "minecraft:poppy", "blue_orchid": "minecraft:blue_orchid",
           "allium": "minecraft:allium", "houstonia": "minecraft:azure_bluet",
           "red_tulip": "minecraft:red_tulip", "orange_tulip": "minecraft:orange_tulip",
           "white_tulip": "minecraft:white_tulip", "pink_tulip": "minecraft:pink_tulip",
           "oxeye_daisy": "minecraft:oxeye_daisy"}


def _type(half):
    return "top" if half == "top" else "bottom"


def _persistent(p):
    return "false" if p.get("decayable", "true") == "true" else "true"


def _stairs(p):
    return {"facing": p.get("facing", "north"), "half": p.get("half", "bottom"),
            "shape": p.get("shape", "straight"), "waterlogged": "false"}


# ---------------------------------------------------------------- vanilla 1.12.2 -> 1.21.1
def v_vanilla(name, p):
    if name == "minecraft:air":
        return "minecraft:air", {}
    if name == "minecraft:stone":
        t = STONE_VARIANTS.get(p.get("variant", "stone"))
        return (t, {}) if t else None

    # plain blocks: name unchanged, no state carried
    plain = {"minecraft:gravel", "minecraft:dirt", "minecraft:coal_ore", "minecraft:cobblestone",
             "minecraft:redstone_block", "minecraft:crafting_table", "minecraft:iron_ore",
             "minecraft:bookshelf", "minecraft:sand", "minecraft:netherrack", "minecraft:clay",
             "minecraft:sea_lantern", "minecraft:enchanting_table", "minecraft:redstone_lamp",
             "minecraft:cauldron", "minecraft:flower_pot"}
    if name in plain:
        if name == "minecraft:redstone_lamp":
            return "minecraft:redstone_lamp", {"lit": "false"}
        return name, {}

    if name == "minecraft:brick_block":
        return "minecraft:bricks", {}
    if name == "minecraft:water":
        return "minecraft:water", {"level": p.get("level", "0")}
    if name == "minecraft:lit_redstone_lamp":
        return "minecraft:redstone_lamp", {"lit": "true"}
    if name == "minecraft:grass":
        return "minecraft:grass_block", {"snowy": p.get("snowy", "false")}
    if name == "minecraft:farmland":
        return "minecraft:farmland", {"moisture": p.get("moisture", "0")}
    if name == "minecraft:stonebrick":
        t = {"stonebrick": "minecraft:stone_bricks",
             "chiseled_stonebrick": "minecraft:chiseled_stone_bricks",
             "mossy_stonebrick": "minecraft:mossy_stone_bricks",
             "cracked_stonebrick": "minecraft:cracked_stone_bricks"}.get(p.get("variant"))
        return (t, {}) if t else None

    # wood family
    if name == "minecraft:planks":
        v = p.get("variant")
        return (f"minecraft:{v}_planks", {}) if v in WOODS else None
    if name == "minecraft:log":
        v = p.get("variant")
        return (f"minecraft:{v}_log", {"axis": p.get("axis", "y")}) if v in WOODS else None
    if name == "minecraft:leaves":
        v = p.get("variant")
        if v not in WOODS:
            return None
        return f"minecraft:{v}_leaves", {"distance": "7", "persistent": _persistent(p),
                                         "waterlogged": "false"}
    if name == "minecraft:wooden_slab":
        v = p.get("variant")
        if v not in WOODS:
            return None
        return f"minecraft:{v}_slab", {"type": _type(p.get("half")), "waterlogged": "false"}
    if name.endswith("_stairs") and name[10:-len("_stairs")] in WOODS:
        return name, _stairs(p)
    if name.endswith("_fence") and name[10:-len("_fence")] in WOODS:
        out = {d: p.get(d, "false") for d in ("east", "north", "south", "west")}
        out["waterlogged"] = "false"
        return name, out
    if name.endswith("_fence_gate") and name[10:-len("_fence_gate")] in WOODS:
        return name, {"facing": p.get("facing", "north"), "in_wall": p.get("in_wall", "false"),
                      "open": p.get("open", "false"), "powered": p.get("powered", "false")}

    if name == "minecraft:stone_brick_stairs":
        return name, _stairs(p)
    if name == "minecraft:double_stone_slab":
        t = SLAB_TARGETS.get(p.get("variant"))
        return (t, {"type": "double", "waterlogged": "false"}) if t else None
    if name == "minecraft:stone_slab":
        t = SLAB_TARGETS.get(p.get("variant"))
        return (t, {"type": _type(p.get("half")), "waterlogged": "false"}) if t else None

    if name == "minecraft:wool":
        return (f"minecraft:{p.get('color')}_wool", {}) if p.get("color") else None
    if name == "minecraft:carpet":
        return (f"minecraft:{p.get('color')}_carpet", {}) if p.get("color") else None

    if name == "minecraft:red_flower":
        t = FLOWERS.get(p.get("type"))
        return (t, {}) if t else None
    if name == "minecraft:yellow_flower":
        return ("minecraft:dandelion", {}) if p.get("type", "dandelion") == "dandelion" else None
    if name == "minecraft:tallgrass":
        return "minecraft:short_grass", {}

    # crops
    if name in ("minecraft:wheat", "minecraft:potatoes", "minecraft:carrots",
                "minecraft:beetroots", "minecraft:cocoa"):
        out = {"age": p.get("age", "0")}
        if name == "minecraft:cocoa":
            out["facing"] = p.get("facing", "north")
        return name, out

    # containers / directional
    if name == "minecraft:chest":
        return name, {"facing": p.get("facing", "north"), "type": "single", "waterlogged": "false"}
    if name == "minecraft:furnace":
        return name, {"facing": p.get("facing", "north"), "lit": "false"}
    if name == "minecraft:lit_furnace":
        return "minecraft:furnace", {"facing": p.get("facing", "north"), "lit": "true"}
    if name == "minecraft:lit_pumpkin":
        return "minecraft:jack_o_lantern", {"facing": p.get("facing", "north")}
    if name == "minecraft:torch":
        f = p.get("facing", "up")
        return ("minecraft:torch", {}) if f == "up" else ("minecraft:wall_torch", {"facing": f})

    if name == "minecraft:wall_banner":
        return "minecraft:red_wall_banner", {"facing": p.get("facing", "north")}
    if name == "minecraft:hopper":
        return name, {"enabled": p.get("enabled", "true"), "facing": p.get("facing", "down")}
    if name == "minecraft:repeating_command_block":
        return name, {"conditional": p.get("conditional", "false"),
                      "facing": p.get("facing", "north")}
    if name == "minecraft:iron_bars":
        out = {d: p.get(d, "false") for d in ("east", "north", "south", "west")}
        out["waterlogged"] = "false"
        return name, out
    if name == "minecraft:iron_trapdoor":
        return name, {"facing": p.get("facing", "north"), "half": p.get("half", "bottom"),
                      "open": p.get("open", "false"), "powered": "false", "waterlogged": "false"}
    if name == "minecraft:lever":
        return name, {"face": "wall", "facing": p.get("facing", "north"),
                      "powered": p.get("powered", "false")}
    if name == "minecraft:cobblestone_wall":
        if p.get("variant") not in (None, "cobblestone"):
            return None
        out = {d: ("low" if p.get(d) == "true" else "none") for d in ("east", "north", "south", "west")}
        out["up"] = p.get("up", "true")
        out["waterlogged"] = "false"
        return "minecraft:cobblestone_wall", out
    if name == "minecraft:cake":
        return name, {"bites": p.get("bites", "0")}
    if name == "minecraft:anvil":
        return name, {"facing": p.get("facing", "north")}
    return None


# ------------------------------------------------------- twilight forest 1.12.2 -> 1.21.1
SIMPLE_TF = {
    "twilightforest:trans_planks": ("twilightforest:transformation_planks", {}),
    "twilightforest:mine_planks": ("twilightforest:mining_planks", {}),
    "twilightforest:time_planks": ("twilightforest:time_planks", {}),
    "twilightforest:canopy_planks": ("twilightforest:canopy_planks", {}),
    "twilightforest:twilight_oak_planks": ("twilightforest:twilight_oak_planks", {}),
    "twilightforest:knightmetal_block": ("twilightforest:knightmetal_block", {}),
    "twilightforest:hedge": ("twilightforest:hedge", {}),
    "twilightforest:fluffy_cloud": ("twilightforest:fluffy_cloud", {}),
    "twilightforest:firefly_jar": ("twilightforest:firefly_jar", {}),
    "twilightforest:trollber": ("twilightforest:trollber", {}),
    "twilightforest:uncrafting_table": ("twilightforest:uncrafting_table", {"powered": "false"}),
    "twilightforest:aurora_block": ("twilightforest:aurora_block", {}),
    "twilightforest:moonworm": ("twilightforest:moonworm", {}),   # facing dropped in 1.21.1
    "twilightforest:firefly": ("twilightforest:firefly", {}),     # facing dropped in 1.21.1
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
             "twilightforest:twilight_oak_stairs": "twilightforest:twilight_oak_stairs",
             "twilightforest:dark_stairs": "twilightforest:dark_stairs",
             "twilightforest:mangrove_stairs": "twilightforest:mangrove_stairs"}

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

# 1.12.2 magic wood variants -> 1.21.1 per-variant log / log core blocks
MAGIC_LOG = {"sort": "twilightforest:sorting_log",
             "time": "twilightforest:time_log",
             "trans": "twilightforest:transformation_log"}
MAGIC_LOG_CORE = {"sort": "twilightforest:sorting_log_core",
                  "time": "twilightforest:time_log_core",
                  "trans": "twilightforest:transformation_log_core"}


def v_tf(name, p):
    if name in SIMPLE_TF:
        return SIMPLE_TF[name]
    if name == "twilightforest:castle_pillar":
        target = {"bold": "twilightforest:bold_castle_brick_pillar",
                  "encased": "twilightforest:encased_castle_brick_pillar"}.get(p.get("variant", "bold"))
        if not target:
            return None
        axis = p.get("axis", "y")
        return target, {"axis": "y" if axis == "none" else axis}
    if name == "twilightforest:castle_stairs":
        if p.get("variant") != "encased":
            return None
        return "twilightforest:encased_castle_brick_stairs", _stairs(p)
    if name == "twilightforest:maze_stone":
        t = MAZE_STONE.get(p.get("variant"))
        return (t, {}) if t else None
    if name == "twilightforest:tower_wood":
        t = {"plain": "twilightforest:towerwood",
             "encased": "twilightforest:encased_towerwood"}.get(p.get("variant"))
        return (t, {}) if t else None
    if name == "twilightforest:twilight_log":
        t = {"oak": "twilightforest:twilight_oak_log",
             "canopy": "twilightforest:canopy_log"}.get(p.get("variant"))
        return (t, {"axis": p.get("axis", "y")}) if t else None
    if name == "twilightforest:magic_log":
        t = MAGIC_LOG.get(p.get("variant"))
        return (t, {"axis": p.get("axis", "y")}) if t else None
    if name == "twilightforest:magic_log_core":
        t = MAGIC_LOG_CORE.get(p.get("variant"))
        return (t, {"axis": p.get("axis", "y"), "active": "false"}) if t else None
    if name == "twilightforest:magic_leaves":
        if p.get("variant") != "trans":
            return None
        return "twilightforest:transformation_leaves", {"distance": "7", "persistent": _persistent(p),
                                                        "waterlogged": "false"}
    if name in TF_SLAB:
        return TF_SLAB[name], {"type": _type(p.get("half")), "waterlogged": "false"}
    if name in TF_DOUBLESLAB:
        return TF_DOUBLESLAB[name], {"type": "double", "waterlogged": "false"}
    if name in TF_STAIRS:
        return TF_STAIRS[name], _stairs(p)
    if name == "twilightforest:nagastone_stairs":
        t = {"left": "twilightforest:nagastone_stairs_left",
             "right": "twilightforest:nagastone_stairs_right"}.get(p.get("direction"))
        return (t, _stairs(p)) if t else None
    if name in TF_TRAPDOOR:
        return TF_TRAPDOOR[name], {"facing": p.get("facing", "north"), "half": p.get("half", "bottom"),
                                   "open": p.get("open", "false"), "waterlogged": "false"}
    if name in TF_DOOR:
        return name, {"facing": p.get("facing", "north"), "half": p.get("half", "lower"),
                      "hinge": p.get("hinge", "left"), "open": p.get("open", "false")}
    if name == "twilightforest:aurora_pillar":
        return name, {"axis": p.get("axis", "y")}
    if name == "twilightforest:stone_twist":
        return "twilightforest:twisted_stone", {"axis": p.get("axis", "y")}
    if name == "twilightforest:stone_twist_thin":
        out = {d: p.get(d, "false") for d in ("down", "east", "north", "south", "up", "west")}
        out["axis"] = p.get("axis", "y")
        out["waterlogged"] = "false"
        return "twilightforest:twisted_stone_pillar", out
    if name == "twilightforest:spiral_bricks":
        return name, {"axis": p.get("axis", "y"), "diagonal": p.get("diagonal", "top_left"),
                      "waterlogged": "false"}
    if name == "twilightforest:nagastone_pillar":
        return name, {"axis": p.get("axis", "y"), "reversed": p.get("reversed", "false")}
    if name == "twilightforest:nagastone_pillar_weathered":
        return "twilightforest:cracked_nagastone_pillar", {"axis": p.get("axis", "y"),
                                                            "reversed": p.get("reversed", "false")}
    if name == "twilightforest:naga_stone":
        v = p.get("variant", "solid")
        if v.endswith("_head"):
            return "twilightforest:nagastone_head", {"facing": v[:-len("_head")]}
        return "twilightforest:nagastone", {"variant": v}
    if name == "twilightforest:etched_nagastone":
        return name, {"facing": p.get("facing", "north")}
    if name == "twilightforest:etched_nagastone_weathered":
        # 1.21.1 renders the old *_weathered textures on the cracked_* family
        return "twilightforest:cracked_etched_nagastone", {"facing": p.get("facing", "north")}
    if name == "twilightforest:block_storage":
        t = BLOCK_STORAGE.get(p.get("variant"))
        return (t, {}) if t else None
    if name == "twilightforest:tower_device":
        return TOWER_DEVICE.get(p.get("variant"))
    if name == "twilightforest:huge_mushgloom":
        if p.get("variant") != "all_outside":
            return None
        return name, {d: "true" for d in ("down", "east", "north", "south", "up", "west")}
    if name == "twilightforest:root":
        return {"root": ("twilightforest:root", {}),
                "liveroot": ("twilightforest:liveroot_block", {})}.get(p.get("variant"))
    if name == "twilightforest:experiment_115":
        return name, {"omnomnom": p.get("omnomnom", "0"), "regenerate": p.get("regenerate", "true")}
    if name == "twilightforest:iron_ladder":
        return name, {"facing": p.get("facing", "north"), "left": p.get("left", "false"),
                      "right": p.get("right", "false"), "waterlogged": "false"}
    if name == "twilightforest:trophy_pedestal":
        return name, {"active": "false" if p.get("latent") == "true" else "true",
                      "facing": p.get("facing", "north")}
    if name == "twilightforest:underbrick":
        return {"normal": ("twilightforest:underbrick", {}),
                "floor": ("twilightforest:underbrick_floor", {})}.get(p.get("variant"))
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
        return name, {"is_one_way": p.get("is_one_way", "false")}
    return None


# ---------------------------------------------------- project-owner replacements for absent mods
# Tinkers' racks sit on walls or lie flat; TF's drying rack only knows the four horizontal facings,
# so the flat forms keep their long axis as a wall direction
RACK_FACING = {"east": "east", "west": "west", "north": "north", "south": "south",
               "down_x": "north", "up_x": "north", "down_z": "east", "up_z": "east"}


def convert_state(name, props):
    if name.startswith("minecraft:"):
        r = v_vanilla(name, props)
        return (r[0], r[1]) if r else (GREEN_WOOL, {})
    if name.startswith("twilightforest:"):
        r = v_tf(name, props)
        return (r[0], r[1]) if r else (GREEN_WOOL, {})
    # agreed replacements for mods absent from 1.21.1
    if name == "tconstruct:rack":
        return DRYING_RACK, {"facing": RACK_FACING.get(props.get("facing"), "north")}
    if name in ("tconstruct:seared_tank", "tconstruct:seared_glass"):
        return WROUGHT_GLASS, {}
    return GREEN_WOOL, {}


def build_be(name):
    """Only the command block carries data that still means anything in 1.21.1."""
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

    new_pal, pal_index, remap = [], {}, {}
    green_from = collections.Counter()
    usage = collections.Counter(b["state"].value for b in old_blocks)
    tally = collections.Counter()

    def label(nm, pr):
        extra = pr.get("variant") or pr.get("type")
        return f"{nm}[{extra}]" if extra else nm

    for i, e in enumerate(old_pal):
        nm = e["Name"].value
        pr = {k: v.value for k, v in e["Properties"].value.items()} if "Properties" in e else {}
        tgt, tprops = convert_state(nm, pr)
        key = (tgt, tuple(sorted(tprops.items())))
        if key not in pal_index:
            pal_index[key] = len(new_pal)
            new_pal.append((tgt, tprops))
        remap[i] = pal_index[key]
        tally[tgt] += usage.get(i, 0)
        if tgt == GREEN_WOOL:
            green_from[label(nm, pr)] += usage.get(i, 0)

    new_blocks = []
    kept = dropped = 0
    for b in old_blocks:
        entry = {"pos": nbtlib.Tag(nbtlib.TAG_LIST, [int(v) for v in b["pos"].value]),
                 "state": nbtlib.Tag(nbtlib.TAG_INT, remap[b["state"].value])}
        if "nbt" in b:
            be = build_be(new_pal[entry["state"].value][0])
            if be is not None:
                entry["nbt"] = nbtlib.Tag(nbtlib.TAG_COMPOUND, be)
                kept += 1
            else:
                dropped += 1
        new_blocks.append(entry)

    out_pal = []
    for nm, pr in new_pal:
        comp = {"Name": nbtlib.Tag(nbtlib.TAG_STRING, nm)}
        if pr:
            comp["Properties"] = nbtlib.Tag(nbtlib.TAG_COMPOUND,
                                            {k: nbtlib.Tag(nbtlib.TAG_STRING, v) for k, v in pr.items()})
        out_pal.append(comp)

    out = {"size": nbtlib.Tag(nbtlib.TAG_LIST, size),
           "entities": nbtlib.Tag(nbtlib.TAG_LIST, top["entities"].value),
           "blocks": nbtlib.Tag(nbtlib.TAG_LIST, new_blocks),
           "palette": nbtlib.Tag(nbtlib.TAG_LIST, out_pal),
           "DataVersion": nbtlib.Tag(nbtlib.TAG_INT, 3955)}
    if "author" in top:
        out["author"] = nbtlib.Tag(nbtlib.TAG_STRING, top["author"].value)

    os.makedirs(os.path.dirname(dst), exist_ok=True)
    nbtlib.save(dst, root_name, nbtlib.Tag(nbtlib.TAG_COMPOUND, out))

    print(f"{os.path.basename(src)}: size {size}  palette {len(old_pal)} -> {len(new_pal)}  "
          f"blocks {len(old_blocks)}  tile-entity nbt kept {kept} / dropped {dropped}")
    print(f"  green wool {tally[GREEN_WOOL]}   wrought iron glass {tally[WROUGHT_GLASS]}   "
          f"drying rack {tally[DRYING_RACK]}")
    if green_from:
        print("  green wool comes from:")
        for k, c in green_from.most_common():
            print(f"    {c:6d}  {k}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1], sys.argv[2]))
