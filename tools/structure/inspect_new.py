import sys, collections
sys.path.insert(0, r"D:\DawnDuskTerminal-main\tools\structure")
import nbtlib

for f in ("ghastfarmhouse.nbt", "ghastfarmhouse2.nbt"):
    name, root, _ = nbtlib.load(r"D:\MCmaidtest\.minecraft\saves\新的世界\structures\\" + f)
    top = root.value
    pal = top["palette"].value
    cnt = collections.Counter(b["state"].value for b in top["blocks"].value)
    groups = collections.defaultdict(list)
    for i, e in enumerate(pal):
        nm = e["Name"].value
        props = {k: v.value for k, v in e["Properties"].value.items()} if "Properties" in e else {}
        groups[nm].append((i, props, cnt.get(i, 0)))
    print("=" * 78)
    print(f, "palette", len(pal))
    for nm in sorted(groups, key=lambda n: -sum(c for _, _, c in groups[n])):
        tot = sum(c for _, _, c in groups[nm])
        print(f"  {nm}   (states {len(groups[nm])}, blocks {tot})")
        for i, p, c in sorted(groups[nm], key=lambda x: -x[2]):
            print(f"      #{i:3d} x{c:5d}  {p}")
    # tile entities
    ids = collections.Counter()
    for b in top["blocks"].value:
        if "nbt" in b:
            ids[b["nbt"].value.get("id", nbtlib.Tag(8, "<none>")).value] += 1
    print("  tile entities:", dict(ids))
