"""Integrity check: the converted structure must cover the same volume with the same positions."""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import nbtlib

SRC = r"D:\MCmaidtest\.minecraft\saves\新的世界\structures\cakehouse.nbt"
DST = r"D:\DawnDuskTerminal-main\run\client\saves\新的世界\structures\cakehouse.nbt"

_, a, _ = nbtlib.load(SRC)
_, b, _ = nbtlib.load(DST)
ta, tb = a.value, b.value
la = {tuple(int(v) for v in x["pos"].value) for x in ta["blocks"].value}
lb = {tuple(int(v) for v in x["pos"].value) for x in tb["blocks"].value}
sa = [int(v) for v in ta["size"].value]
sb = [int(v) for v in tb["size"].value]
print("size", sa, "->", sb, "| same:", sa == sb)
print("block entries", len(ta["blocks"].value), "->", len(tb["blocks"].value))
print("unique positions", len(la), "->", len(lb), "| identical:", la == lb)
full = {(x, y, z) for x in range(sa[0]) for y in range(sa[1]) for z in range(sa[2])}
print("covers full volume:", la == full)
print("entities", len(ta["entities"].value), "->", len(tb["entities"].value))
print("src DataVersion", ta["DataVersion"].value, "-> dst", tb["DataVersion"].value)
print("dst keys:", sorted(tb.keys()))
print("file size:", os.path.getsize(SRC), "->", os.path.getsize(DST))
