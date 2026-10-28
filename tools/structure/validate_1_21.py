"""Validate a converted structure NBT against the real 1.21.1 vanilla + Twilight Forest jars.

Checks, for every palette entry:
  * the block exists (blockstates json present)
  * every property name is one the block either renders (blockstates) or is a known non-model property
  * every property value is one the blockstates json demonstrates
  * every `blocks[].state` index is in range and every pos is inside `size`
Also reports the DataVersion and the block count.
"""
import sys, os, json, glob, zipfile, collections

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import nbtlib

REPO = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
GRADLE = os.path.join(os.path.expanduser("~"), ".gradle", "caches")


def _first(patterns):
    for pat in patterns:
        hits = sorted(glob.glob(pat))
        if hits:
            return hits[-1]
    return None


# 1.21.1 vanilla resources, produced by the ModDevGradle/NeoForm pipeline of this repo
EXTRA = (os.environ.get("DDT_MC_EXTRA_JAR")
         or _first([os.path.join(REPO, "build", "moddev", "artifacts", "*client-extra*.jar"),
                    os.path.join(GRADLE, "neoformruntime", "**", "minecraft_1.21.1_client.jar")]))
# Twilight Forest 1.21.1, resolved by gradle from CurseMaven
TF21 = (os.environ.get("DDT_TF_JAR")
        or _first([os.path.join(GRADLE, "modules-2", "files-2.1", "curse.maven",
                                "the-twilight-forest-227639", "*", "*", "*.jar")]))

for label, path in (("1.21.1 client resources", EXTRA), ("Twilight Forest 1.21.1", TF21)):
    if not path or not os.path.exists(path):
        sys.exit(f"cannot locate {label}; run `gradlew build` once or set the env var")
    print(f"using {label}: {path}")

JARS = {"minecraft": zipfile.ZipFile(EXTRA), "twilightforest": zipfile.ZipFile(TF21)}
BASES = {"minecraft": "assets/minecraft/blockstates/",
         "twilightforest": "assets/twilightforest/blockstates/"}
# this repo's own blocks live in src/main/resources, not in a jar
REPO_ASSETS = os.path.join(REPO, "src", "main", "resources", "assets")

# properties that are real BlockState fields but never appear in a blockstates json
NON_MODEL = {
    "waterlogged": {"false", "true"},
    "powered": {"false", "true"},
    "enabled": {"false", "true"},
    "distance": {"1", "2", "3", "4", "5", "6", "7"},
    "persistent": {"false", "true"},
    "conditional": {"false", "true"},
    "level": {str(i) for i in range(16)},
    "facing": {"north", "south", "east", "west", "up", "down"},
    "up": {"false", "true"},
    "down": {"false", "true"},
    "east": {"false", "true", "none", "low", "tall"},
    "west": {"false", "true", "none", "low", "tall"},
    "north": {"false", "true", "none", "low", "tall"},
    "south": {"false", "true", "none", "low", "tall"},
    "is_one_way": {"false", "true"},
    # chest/double-chest shape; chest's blockstates json is a single static model
    "type": {"single", "left", "right"},
}


def props_of(block):
    ns, name = block.split(":")
    if ns in JARS:
        try:
            d = json.loads(JARS[ns].read(BASES[ns] + name + ".json"))
        except KeyError:
            return None
    else:
        path = os.path.join(REPO_ASSETS, ns, "blockstates", name + ".json")
        if not os.path.exists(path):
            return None
        d = json.load(open(path, encoding="utf-8"))
    props = collections.defaultdict(set)
    if "variants" in d:
        for k in d["variants"]:
            if k:
                for part in k.split(","):
                    a, b = part.split("=")
                    props[a].add(b)
    else:
        for part in d.get("multipart", []):
            wh = part.get("when") or {}
            for key, val in wh.items():
                if key in ("OR", "AND"):
                    for sub in val:
                        for a, b in sub.items():
                            for v in b.split("|"):
                                props[a].add(v)
                else:
                    for v in (val.split("|") if isinstance(val, str) else val):
                        props[key].add(v)
    return props


def main(path):
    name, root, _ = nbtlib.load(path)
    top = root.value
    size = [int(v) for v in top["size"].value]
    dv = top["DataVersion"].value if "DataVersion" in top else None
    pal = top["palette"].value
    blocks = top["blocks"].value
    print(f"file: {path}  root={name!r}  size={size}  DataVersion={dv}  palette={len(pal)}  blocks={len(blocks)}")

    problems = 0
    if dv != 3955:
        print(f"  !! DataVersion is {dv}, expected 3955 (1.21.1)")
        problems += 1

    for i, e in enumerate(pal):
        nm = e["Name"].value
        p = {k: v.value for k, v in e["Properties"].value.items()} if "Properties" in e else {}
        real = props_of(nm)
        if real is None:
            print(f"  !! [{i}] {nm} DOES NOT EXIST in this 1.21.1 loadout")
            problems += 1
            continue
        for k, v in p.items():
            allowed = real.get(k) or NON_MODEL.get(k)
            if allowed is None:
                print(f"  ?? [{i}] {nm}: unknown property {k}={v}")
                problems += 1
                continue
            allowed = set(allowed)
            # blockstates JSON only lists the values that pick a model, so close the domain:
            # a boolean shows up as just "true"/"false", a wall side as just low/tall
            if allowed <= {"true"} or allowed <= {"false"}:
                allowed |= {"true", "false"}
            if allowed <= {"low", "tall"}:
                allowed.add("none")
            if v not in allowed:
                print(f"  !! [{i}] {nm}: {k}={v} not allowed (allowed: {sorted(allowed)})")
                problems += 1
        # warn when a model property is missing entirely (harmless, defaults are used)
        for k in real:
            if k not in p:
                print(f"  -- [{i}] {nm}: model property {k} omitted, default will be used")

    seen_states = set()
    for b in blocks:
        s = b["state"].value
        if s < 0 or s >= len(pal):
            print(f"  !! bad state index {s}")
            problems += 1
        seen_states.add(s)
        pos = [int(v) for v in b["pos"].value]
        if any(x < 0 or x >= size[i] for i, x in enumerate(pos)):
            print(f"  !! pos {pos} outside size {size}")
            problems += 1

    unused = sorted(set(range(len(pal))) - seen_states)
    if unused:
        print(f"  -- unused palette entries: {unused}")
    be = sum(1 for b in blocks if "nbt" in b)
    print(f"  block entities: {be}")
    for b in blocks:
        if "nbt" in b:
            print(f"    nbt at {[int(v) for v in b['pos'].value]}: "
                  f"{ {k: v.value for k, v in b['nbt'].value.items()} }")
    print("RESULT:", "OK, no problems" if problems == 0 else f"{problems} problem(s)")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1]))
