"""Integrity check: the converted structure must cover the same volume with the same positions.

usage: integrity_check.py <source 1.12.2 nbt> <converted 1.21.1 nbt>
"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import nbtlib


def main(src, dst):
    _, a, _ = nbtlib.load(src)
    _, b, _ = nbtlib.load(dst)
    ta, tb = a.value, b.value
    la = {tuple(int(v) for v in x["pos"].value) for x in ta["blocks"].value}
    lb = {tuple(int(v) for v in x["pos"].value) for x in tb["blocks"].value}
    sa = [int(v) for v in ta["size"].value]
    sb = [int(v) for v in tb["size"].value]
    full = {(x, y, z) for x in range(sa[0]) for y in range(sa[1]) for z in range(sa[2])}
    print(os.path.basename(dst))
    print("  size", sa, "->", sb, "| same:", sa == sb)
    print("  blocks", len(ta["blocks"].value), "->", len(tb["blocks"].value))
    print("  unique positions", len(la), "->", len(lb), "| identical:", la == lb)
    print("  covers full volume:", la == full)
    print("  entities", len(ta["entities"].value), "->", len(tb["entities"].value))
    print("  DataVersion", ta["DataVersion"].value, "->", tb["DataVersion"].value)
    print("  size on disk", os.path.getsize(src), "->", os.path.getsize(dst))
    return 0 if (sa == sb and la == lb and tb["DataVersion"].value == 3955) else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1], sys.argv[2]))
