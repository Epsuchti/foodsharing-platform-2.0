#!/usr/bin/env python3
"""Generates the statistics_daily seed CSV (M6 B12, seed module 0054): 400 days ending the day before SEED_NOW_DATE
(2026-11-02); deterministic, slowly growing platform totals. Run from the repository root."""
import datetime
import os

OUT = os.path.join("server", "src", "main", "resources", "db", "seed", "statistics", "statistics_daily.csv")
SEED_NOW = datetime.date(2026, 11, 2)
DAYS = 400

lines = ["id,version,created_at,updated_at,stat_day,kg_saved,pickups,foodsavers,stores,cooperations,food_share_points,baskets"]
for n in range(1, DAYS + 1):
    day = SEED_NOW - datetime.timedelta(days=DAYS - n + 1)
    i = n - 1
    pickups = 180000 + i * 95 + (i * 37) % 23
    kg = round(pickups * 6.8 + (i * 13) % 50, 1)
    foodsavers = 9800 + i * 3 + (i % 7)
    stores = 1450 + i // 4
    cooperations = 1020 + i // 6
    fsps = 160 + i // 40
    baskets = 52000 + i * 21
    ts = f"{day.isoformat()}T01:30:00"
    lines.append(f"00000054-0001-4000-8000-{n:012d},0,{ts},{ts},{day.isoformat()},{kg},{pickups},{foodsavers},"
                 f"{stores},{cooperations},{fsps},{baskets}")

os.makedirs(os.path.dirname(OUT), exist_ok=True)
with open(OUT, "w", encoding="utf-8") as f:
    f.write("\n".join(lines) + "\n")
print(f"wrote {DAYS} rows to {OUT}")
