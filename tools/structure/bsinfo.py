"""Dump blockstate property info for blocks from the 1.21.1 vanilla jar, the Twilight Forest jar
and this repo's own assets."""
import zipfile, json, sys, collections

EXTRA = r"D:\DawnDuskTerminal-main\build\moddev\artifacts\neoforge-21.1.252-client-extra-aka-minecraft-resources.jar"
TF21 = r"C:\Users\34229\.gradle\caches\modules-2\files-2.1\curse.maven\the-twilight-forest-227639\7797302\9cfd4154928433e2f1f6cfc3deb48c284debb580\the-twilight-forest-227639-7797302.jar"
REPO_ASSETS = r"D:\DawnDuskTerminal-main\src\main\resources\assets"

JARS = {"minecraft": zipfile.ZipFile(EXTRA), "twilightforest": zipfile.ZipFile(TF21)}
BASES = {"minecraft": "assets/minecraft/blockstates/", "twilightforest": "assets/twilightforest/blockstates/"}


def get(block):
    ns, name = block.split(":")
    if ns in JARS:
        try:
            return json.loads(JARS[ns].read(BASES[ns] + name + ".json"))
        except KeyError:
            return None
    import os
    p = os.path.join(REPO_ASSETS, ns, "blockstates", name + ".json")
    if os.path.exists(p):
        return json.load(open(p, encoding="utf-8"))
    return None


def analyse(block):
    d = get(block)
    if d is None:
        return None
    props = collections.defaultdict(set)
    if "variants" in d:
        kind = "variants"
        for k in d["variants"]:
            if k:
                for part in k.split(","):
                    a, b = part.split("=")
                    props[a].add(b)
    else:
        kind = "multipart"
        for part in d.get("multipart", []):
            wh = part.get("when") or {}
            for key, val in wh.items():
                if key in ("OR", "AND"):
                    for sub in val:
                        for a, b in sub.items():
                            for v in (b.split("|") if isinstance(b, str) else b):
                                props[a].add(v)
                else:
                    for v in (val.split("|") if isinstance(val, str) else val):
                        props[key].add(v)
    return kind, {k: sorted(v) for k, v in props.items()}


if __name__ == "__main__":
    for b in sys.argv[1:]:
        r = analyse(b)
        if r is None:
            print(f"{b}: <<< NOT FOUND >>>")
            continue
        kind, props = r
        inside = ", ".join(f"{k}={v}" for k, v in sorted(props.items()))
        print(f"{b}: [{kind}] {inside}")
