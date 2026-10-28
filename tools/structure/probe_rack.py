import zipfile, gzip, sys, collections
sys.path.insert(0, r"D:\DawnDuskTerminal-main\tools\structure")
import nbtlib

TF = r"C:\Users\34229\.gradle\caches\modules-2\files-2.1\curse.maven\the-twilight-forest-227639\7797302\9cfd4154928433e2f1f6cfc3deb48c284debb580\the-twilight-forest-227639-7797302.jar"
z = zipfile.ZipFile(TF)
seen = collections.Counter()
for n in z.namelist():
    if not n.endswith(".nbt"):
        continue
    raw = z.read(n)
    try:
        if raw[:2] == b"\x1f\x8b":
            raw = gzip.decompress(raw)
        r = nbtlib._Reader(raw)
        if r.u1() != 10:
            continue
        r.string()
        top = nbtlib._read_payload(r, 10)
    except Exception:
        continue
    if "palette" not in top:
        continue
    pal = top["palette"].value
    cnt = collections.Counter(b["state"].value for b in top["blocks"].value if "state" in b)
    for i, c in cnt.items():
        e = pal[i]
        nm = e["Name"].value
        if "drying_rack" in nm or "log_core" in nm:
            props = tuple(sorted((k, v.value) for k, v in e["Properties"].value.items())) if "Properties" in e else ()
            seen[(nm, props)] += c
for k, v in sorted(seen.items(), key=lambda x: -x[1]):
    print(v, k[0], dict(k[1]))
