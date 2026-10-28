#!/usr/bin/env python3
"""Generates the M4 B4 automation seed CSVs (deterministic, relative to SEED_NOW 2026-11-02T09:00Z).

Run from anywhere: python3 server/scripts/gen-automation-seed.py
Ids: module 0022 (docs/seed-ids.md).
"""
import datetime as dt
import json
import os

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "db", "seed", "automation")
SEED_NOW = dt.datetime(2026, 11, 2, 9, 0, tzinfo=dt.timezone.utc)
CREATED = "2026-10-01T10:00:00Z"
REGION = "00000010-0001-4000-8000-000000000040"


def sid(table, n):
    return "00000022-%04d-4000-8000-%012d" % (table, n)


def store(n):
    return "00000020-0001-4000-8000-%012d" % n


def user(n):
    return "00000001-0001-4000-8000-%012d" % n


def iso(t):
    return t.strftime("%Y-%m-%dT%H:%M:%SZ")


def q(value):
    if value is None:
        return "NULL"
    s = str(value)
    if any(c in s for c in ',"\n'):
        return '"' + s.replace('"', '""') + '"'
    return s


def write(name, header, rows):
    os.makedirs(OUT, exist_ok=True)
    with open(os.path.join(OUT, name), "w", encoding="utf-8", newline="\n") as f:
        f.write(",".join(header) + "\n")
        for row in rows:
            f.write(",".join(q(v) for v in row) + "\n")


BASE = ["id", "version", "created_at", "updated_at", "created_by", "updated_by"]

# pickup_policy (0001): S-06 live (no M3 scenario uses it), S-01 dry run, S-02 dry run (cleaning), S-04 dry run (linked to S-01), S-03 disabled
write("pickup_policy.csv", BASE + ["store_id", "enabled", "dry_run", "gap_rule_enabled", "minimum_gap_days",
                                   "cleaning_rule_enabled", "experience_rule_enabled", "public_holiday_rule_enabled"], [
    [sid(1, 1), 0, CREATED, CREATED, None, None, store(1), "true", "true", "true", 2, "false", "true", "true"],
    [sid(1, 5), 0, CREATED, CREATED, None, None, store(6), "true", "false", "true", 2, "false", "true", "true"],
    [sid(1, 2), 0, CREATED, CREATED, None, None, store(2), "true", "true", "false", 0, "true", "false", "false"],
    [sid(1, 3), 0, CREATED, CREATED, None, None, store(4), "true", "true", "false", 0, "false", "false", "false"],
    [sid(1, 4), 0, CREATED, CREATED, None, None, store(3), "false", "true", "false", 0, "false", "false", "false"],
])
# excluded date (0002): S-01 next Sunday (the extra pickup 2026-11-08)
write("pickup_policy_excluded_date.csv", BASE + ["policy_id", "excluded_date"], [
    [sid(2, 1), 0, CREATED, CREATED, None, None, sid(1, 1), "2026-11-08"],
])
# linked store (0003/0004): S-04 needs S-01 on the same day, 30 min grace (one-way, see decision in seed-ids.md)
write("pickup_policy_linked_store.csv", BASE + ["policy_id", "bidirectional", "window_mode", "window_days_before",
                                                "window_days_after", "grace_minutes", "message", "mirror_of"], [
    [sid(3, 1), 0, CREATED, CREATED, None, None, sid(1, 3), "false", "SAME_DAY", 0, 0, 30, None, None],
])
write("pickup_policy_linked_store_member.csv", BASE + ["link_id", "store_id"], [
    [sid(4, 1), 0, CREATED, CREATED, None, None, sid(3, 1), store(1)],
])
# cleaning exemption (0005): bieb in zuerich-stadt
write("cleaning_rule_exemption.csv", BASE + ["region_id", "user_id", "reason"], [
    [sid(5, 1), 0, CREATED, CREATED, None, None, REGION, user(9), "Koordiniert die Fairteiler-Reinigung"],
])

# pickup_policy_audit (0006): 40 decisions over the past 7 days
TEXT = {
    "ALL_RULES_PASSED": "Alle aktiven Abholungs-Automationsregeln sind erfüllt.",
    "RULES_SKIPPED_NEAR_PICKUP": "Die Abholung findet in zwei Kalendertagen oder weniger statt; die Automationsregeln werden übersprungen.",
    "GAP": "Zwischen den Abholungen liegt nur 1 Tag Abstand; erforderlich sind mindestens 2 Tage.",
    "EXPERIENCE": "Diese Abholung benötigt Erfahrung in diesem Betrieb. Weder du noch eine allfällige begleitende Person haben diese.",
    "MANUAL_DATE": "Die Abholung ist für diesen Betrieb als manuell zu prüfendes Datum hinterlegt und wird nicht automatisch bestätigt.",
    "PUBLIC_HOLIDAY": "Die Abholung ist am Feiertag Allerheiligen; sie wird nicht automatisch bestätigt.",
    "CLEANING_REQUIRED": "Bei diesem Betrieb gilt die allgemein geltende Reinigungspflicht: Jedes Mitglied soll alle 6 Monate einen Fairteiler reinigen.",
    "CLEANING_REMINDER": "Du hast in den letzten 12 Monaten keinen Fairteiler gereinigt; da nur 2 freie Reinigungsslots verfügbar sind, bleibst du dieses Mal eingetragen.",
    "LINKED_STORE": "Innerhalb von 30 Minuten nach deiner Anmeldung haben wir keine passende Abholung bei Coop Bahnhofbrücke gefunden.",
}
FOOTER = "Diese Aktion wurde automatisch ausgeführt. Bei Fragen oder Problemen wende dich an die Betriebsverantwortlichen."
PLAN = [  # (store, policy, decision, dry_run, rules)
    (1, 1, "CONFIRMED", False, ["ALL_RULES_PASSED"]),
    (1, 1, "CONFIRMED", False, ["RULES_SKIPPED_NEAR_PICKUP"]),
    (1, 1, "DECLINED", False, ["GAP"]),
    (1, 1, "DECLINED", False, ["EXPERIENCE"]),
    (1, 1, "SKIPPED", False, ["MANUAL_DATE"]),
    (1, 1, "SKIPPED", False, ["PUBLIC_HOLIDAY"]),
    (1, 1, "DECLINED", False, ["GAP", "EXPERIENCE"]),
    (1, 1, "FAILED", False, []),
    (2, 2, "WOULD_CONFIRM", True, ["ALL_RULES_PASSED"]),
    (2, 2, "WOULD_DECLINE", True, ["CLEANING_REQUIRED"]),
    (2, 2, "WOULD_CONFIRM", True, ["CLEANING_REMINDER"]),
    (4, 3, "WOULD_DECLINE", True, ["LINKED_STORE"]),
    (4, 3, "WOULD_CONFIRM", True, ["ALL_RULES_PASSED"]),
]
USERS = [6, 7, 8, 10, 11, 12, 9]
rows = []
for i in range(40):
    s, policy, decision, dry, rules = PLAN[i % len(PLAN)]
    created = SEED_NOW - dt.timedelta(hours=4 + i * 4)
    pickup = (created + dt.timedelta(days=3 + i % 5)).replace(hour=18, minute=0, second=0)
    u = USERS[i % len(USERS)]
    reasons = [{"rule": r, "text": TEXT[r]} for r in rules]
    declined = decision in ("DECLINED", "WOULD_DECLINE")
    reminder = "CLEANING_REMINDER" in rules
    message = ("\n".join(TEXT[r] for r in rules) + "\n\n" + FOOTER) if declined or reminder else None
    error = "Timeout beim Bestätigen" if decision == "FAILED" else None
    rows.append([sid(6, i + 1), 0, iso(created), iso(created), None, None, sid(1, policy), store(s), REGION, user(u),
                 iso(pickup), "true" if dry else "false", decision,
                 json.dumps(reasons, ensure_ascii=False, separators=(",", ":")), message, error])
write("pickup_policy_audit.csv", BASE + ["policy_id", "store_id", "region_id", "user_id", "pickup_at", "dry_run",
                                         "decision", "reasons", "user_message", "error"], rows)

# request_policy (0007): S-07, distance rule 5 km, dry run (default)
write("request_policy.csv", BASE + ["store_id", "enabled", "dry_run", "distance_rule_enabled", "maximum_distance_km"], [
    [sid(7, 1), 0, CREATED, CREATED, None, None, store(7), "true", "true", "true", 5.0],
])
write("request_policy_audit.csv", BASE + ["policy_id", "store_id", "region_id", "user_id", "dry_run", "status",
                                          "reason", "message", "error"], [
    [sid(8, 1), 0, iso(SEED_NOW - dt.timedelta(days=1)), iso(SEED_NOW - dt.timedelta(days=1)), None, None, sid(7, 1),
     store(7), REGION, user(10), "true", "WOULD_APPROVE", "ALL_RULES_PASSED",
     "Die Anfrage wurde automatisch angenommen.", None],
    [sid(8, 2), 0, iso(SEED_NOW - dt.timedelta(days=2)), iso(SEED_NOW - dt.timedelta(days=2)), None, None, sid(7, 1),
     store(7), REGION, user(11), "true", "WOULD_DECLINE", "DISTANCE",
     "Die Anfrage wurde automatisch abgelehnt, weil die Entfernung von 12.4 km das Maximum von 5.0 km überschreitet.",
     None],
    [sid(8, 3), 0, iso(SEED_NOW - dt.timedelta(days=3)), iso(SEED_NOW - dt.timedelta(days=3)), None, None, sid(7, 1),
     store(7), REGION, user(5), "true", "SKIPPED", "NO_ADDRESS",
     "Für die Person oder den Betrieb ist keine Adresse mit Koordinaten hinterlegt; die Anfrage wird nicht automatisch bearbeitet.",
     None],
])

# holiday_calendar (0015): CH national 2026-2028 + canton ZH
EASTER = {2026: dt.date(2026, 4, 5), 2027: dt.date(2027, 3, 28), 2028: dt.date(2028, 4, 16)}
rows = []
n = 0
for year in (2026, 2027, 2028):
    e = EASTER[year]
    national = [(dt.date(year, 1, 1), "Neujahr"), (e + dt.timedelta(days=39), "Auffahrt"),
                (dt.date(year, 8, 1), "Bundesfeier"), (dt.date(year, 12, 25), "Weihnachten")]
    zh = [(dt.date(year, 1, 2), "Berchtoldstag"), (e - dt.timedelta(days=2), "Karfreitag"),
          (e + dt.timedelta(days=1), "Ostermontag"), (dt.date(year, 5, 1), "Tag der Arbeit"),
          (e + dt.timedelta(days=50), "Pfingstmontag"), (dt.date(year, 12, 26), "Stephanstag")]
    for date, name in national:
        n += 1
        rows.append([sid(15, n), 0, CREATED, CREATED, None, None, "CH", None, date.isoformat(), name])
    for date, name in zh:
        n += 1
        rows.append([sid(15, n), 0, CREATED, CREATED, None, None, "CH", "ZH", date.isoformat(), name])
write("holiday_calendar.csv", BASE + ["country", "canton", "holiday_date", "name"], rows)
print("ok")
