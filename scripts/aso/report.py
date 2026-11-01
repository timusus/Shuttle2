#!/usr/bin/env python3
"""Rank movement per country vs N days ago, from ranks.csv."""
import argparse
import csv
import datetime
import os
from collections import defaultdict

DATA = os.environ.get("ASO_DATA_DIR", os.path.expanduser("~/.local/share/shuttle-music-aso"))


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--days", type=int, default=7)
    days = p.parse_args().days
    by_date = defaultdict(dict)  # date -> (country, lang, keyword) -> rank or None
    for r in csv.DictReader(open(os.path.join(DATA, "ranks.csv"))):
        by_date[r["date"]][(r["country"], r["lang"], r["keyword"])] = int(r["rank"]) if r["rank"] else None
    dates = sorted(by_date)
    if not dates:
        print("no data")
        return
    now = dates[-1]
    target = (datetime.date.fromisoformat(now) - datetime.timedelta(days=days)).isoformat()
    earlier = [d for d in dates if d <= target] or [d for d in dates if d < now]
    if not earlier:
        print(f"{now}: only one snapshot so far, nothing to compare")
        return
    then = earlier[-1]
    cur, old = by_date[now], by_date[then]
    print(f"Rank changes {then} -> {now} (lower is better; - = absent)")
    for country in ("us", "gb", "au", "ca", "in"):
        all_keys = [k for k in cur if k[0] == country and k[1] == "en"]
        # Keywords added since the earlier snapshot would otherwise count as climbs from absent.
        keys = [k for k in all_keys if k in old]
        added = [k for k in all_keys if k not in old]
        top = lambda snap, n, ks=keys: sum(1 for k in ks if snap.get(k) and snap[k] <= n)
        print(f"{country.upper()}: top10 {top(old, 10)}->{top(cur, 10)}  top30 {top(old, 30)}->{top(cur, 30)}")
        if added:
            print(f"  {len(added)} new keywords: {top(cur, 10, added)} in top10, {top(cur, 30, added)} in top30")
        moved = [(k[2], old.get(k), cur[k]) for k in keys if k in old and old.get(k) != cur[k]]
        for kw, a, b in sorted(moved)[:5]:
            print(f"  {kw}: {a or '-'} -> {b or '-'}")
        if len(moved) > 5:
            print(f"  ... {len(moved) - 5} more moved")


if __name__ == "__main__":
    main()
