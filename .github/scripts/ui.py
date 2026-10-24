#!/usr/bin/env python3
"""Finds a view on screen from a uiautomator dump (stdin) and prints its center as "x y", or its bounds with --bounds.
Match by --id (resource id, short or full), --text or --desc (exact, or *contains*). --nth picks among matches."""
import re, sys, argparse, xml.etree.ElementTree as ET
ap = argparse.ArgumentParser()
ap.add_argument("--id"); ap.add_argument("--text"); ap.add_argument("--desc"); ap.add_argument("--cls"); ap.add_argument("--longclick", action="store_true") ap.add_argument("--nth", type=int, default=0)
ap.add_argument("--bounds", action="store_true"); ap.add_argument("--near", nargs=2, type=int)
a = ap.parse_args()
raw = sys.stdin.read()
raw = raw[raw.find("<?xml"):raw.rfind("</hierarchy>") + len("</hierarchy>")]
def ok(v, want):
    if want is None: return True
    if want.startswith("*") and want.endswith("*"): return want[1:-1].lower() in (v or "").lower()
    return (v or "") == want
hits = []
for n in ET.fromstring(raw).iter("node"):
    rid = n.get("resource-id", "")
    if a.id and not (rid == a.id or rid.endswith(":id/" + a.id)): continue
    if a.cls and a.cls not in n.get("class", ""): continue
    if a.longclick and n.get("long-clickable") != "true": continue
    if not ok(n.get("text"), a.text) or not ok(n.get("content-desc"), a.desc): continue
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
    if x2 > x1 and y2 > y1: hits.append((x1, y1, x2, y2))
if a.near: hits.sort(key=lambda b: ((b[0] + b[2]) / 2 - a.near[0]) ** 2 + ((b[1] + b[3]) / 2 - a.near[1]) ** 2)
if len(hits) <= a.nth: sys.exit(1)
x1, y1, x2, y2 = hits[a.nth]
print(f"{x1} {y1} {x2} {y2}" if a.bounds else f"{(x1 + x2) // 2} {(y1 + y2) // 2}")
