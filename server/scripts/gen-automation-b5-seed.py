#!/usr/bin/env python3
"""Generates the M4 B5 automation seed (CSVs + changelogs 107-110), relative to SEED_NOW 2026-11-02T09:00Z.

Decision 60: live automations only on S-06 (Babu's Bakery); S-01 adverts are dry runs. Table codes in module 0022:
open_slot_advert 0009, open_slot_advert_audit 0010, signout_alert_setting 0011, signout_cancellation 0012,
pickup_extra_slot 0013, metric_daily_count 0014.
"""
import datetime as dt
import json
import os

HERE = os.path.dirname(__file__)
CSV_OUT = os.path.join(HERE, "..", "src", "main", "resources", "db", "seed", "automation")
YAML_OUT = os.path.join(HERE, "..", "src", "main", "resources", "db", "seed", "changes")
SEED_NOW = dt.datetime(2026, 11, 2, 9, 0, tzinfo=dt.timezone.utc)
CREATED = "2026-10-01T10:00:00Z"
REGION = "00000010-0001-4000-8000-000000000040"
S01 = "00000020-0001-4000-8000-000000000001"
S06 = "00000020-0001-4000-8000-000000000006"


def sid(table, n):
    return "00000022-%04d-4000-8000-%012d" % (table, n)


def user(n):
    return "00000001-0001-4000-8000-%012d" % n


def iso(t):
    return None if t is None else t.strftime("%Y-%m-%dT%H:%M:%SZ")


def q(value):
    if value is None:
        return "NULL"
    if isinstance(value, bool):
        return "true" if value else "false"
    s = str(value)
    if any(c in s for c in ',"\n'):
        return '"' + s.replace('"', '""') + '"'
    return s


BASE = ["id", "version", "created_at", "updated_at", "created_by", "updated_by"]


def write(name, header, rows):
    with open(os.path.join(CSV_OUT, name + ".csv"), "w", encoding="utf-8", newline="\n") as f:
        f.write(",".join(BASE + header) + "\n")
        for row in rows:
            f.write(",".join(q(v) for v in row) + "\n")


def base(table, n, created=None):
    c = iso(created) if created else CREATED
    return [sid(table, n), 0, c, c, None, None]


LATE = ("Du hast gerade den Slot {{datetimeDe}} freigegeben. Du bist trotzdem verantwortlich für diesen Slot! "
        "Bitte sorge für Ersatz und notifiziere sowohl das Team als auch den Notfallchat.")
STORE_MSGS = ["Am {{datetimeDe}} ist bei {{storeName}} noch {{freeSlots}} Platz frei – wer kann? {{link}}",
              "Freier Slot bei {{storeName}}: {{weekday}}, {{dateDe}} um {{time}}. Bitte eintragen: {{link}}",
              "Hilfe gesucht! {{storeName}} am {{dateDe}} {{time}} hat noch freie Plätze."]
ADVERT_HEADER = ["store_id", "advert_number", "enabled", "dry_run", "trigger_hours_before", "channels",
                 "telegram_chat_id", "store_messages", "push_messages", "telegram_messages",
                 "send_late_cancellation_message", "late_cancellation_message", "late_cancellation_max_hours",
                 "resend_latest_advert_after_late_cancellation"]
write("open_slot_advert", ADVERT_HEADER, [
    base(9, 1) + [S01, 1, True, True, 24, "PUSH,STORE_CHAT", None, json.dumps(STORE_MSGS, ensure_ascii=False),
                  json.dumps(["Freier Platz: {{storeName}} {{datetimeDe}}"], ensure_ascii=False), None, True, LATE,
                  72, True],
    base(9, 2) + [S01, 2, True, True, 6, "TELEGRAM", "-1002", None, None,
                  json.dumps(["🍞 {{storeName}} {{datetimeDe}}: {{freeSlots}} Platz frei! {{link}}"],
                             ensure_ascii=False), False, LATE, 72, True],
])

AUDIT_HEADER = ["advert_id", "advert_number", "store_id", "region_id", "pickup_at", "trigger_hours_before", "user_id",
                "telegram_chat_id", "telegram_message_id", "telegram_deleted_at", "status", "dry_run", "message",
                "reason", "error", "sent_at", "filled_at"]


def advert_audit(n, advert, number, days_ago, status, hours, user_id=None, filled=False, message=None,
                 reason=None, telegram=None):
    created = SEED_NOW - dt.timedelta(days=days_ago, hours=3)
    pickup = created + dt.timedelta(hours=hours)
    return base(10, n, created) + [sid(9, advert), number, S01, REGION, iso(pickup), hours, user_id,
                                   "-1002" if telegram else None, telegram,
                                   iso(pickup) if telegram else None, status, False, message, reason, None,
                                   iso(created), iso(created + dt.timedelta(hours=2)) if filled else None]


write("open_slot_advert_audit", AUDIT_HEADER, [
    advert_audit(1, 1, 1, 1, "SENT", 24, filled=True, message="Am Sonntag, 01.11.2026 um 19:00 ist bei Coop Bahnhofbrücke noch 1 Platz frei – wer kann?",
                 reason="Advert sent."),
    advert_audit(2, 1, 1, 2, "SENT", 24, message="Freier Slot bei Coop Bahnhofbrücke.", reason="Advert sent."),
    advert_audit(3, 2, 2, 3, "SENT", 6, message="Coop Bahnhofbrücke: 1 Platz frei!", reason="Advert sent.",
                 telegram=4711),
    advert_audit(4, 1, 1, 4, "OCCUPIED_AT_TRIGGER", 24, user_id=user(6),
                 reason="Pickup was occupied when this advert became due."),
    advert_audit(5, 1, 1, 5, "LATE_CANCELLATION_REMINDER_AND_ADVERTISEMENT_SENT", 24, user_id=user(7),
                 message="Du hast gerade den Slot freigegeben. Bitte sorge für Ersatz.",
                 reason="Pickup was released after this advert time."),
    advert_audit(6, 1, 1, 6, "LATE_CANCELLATION_NO_ACTION", 24, user_id=user(8),
                 reason="No late-cancellation action is enabled or applicable for this pickup."),
])

write("signout_alert_setting", ["store_id", "enabled", "cutoff_enabled", "maximum_hours_before_pickup", "channels",
                                "telegram_chat_ids", "active_since", "last_checked_at"], [
    base(11, 1) + [S06, True, True, 72, "BELL,TELEGRAM", None, CREATED, iso(SEED_NOW - dt.timedelta(hours=6))],
])

CANCEL_HEADER = ["store_id", "region_id", "pickup_at", "user_id", "cancelled_at", "total_slots", "occupied_slots",
                 "canceller_rejoined", "status", "immediate_notified_at", "final_checked_at", "final_notified_at",
                 "dry_run", "message", "error"]


def cancellation(n, user_n, days, status, hours_before, total, occupied, rejoined=False):
    pickup = (SEED_NOW + dt.timedelta(days=days)).replace(hour=16, minute=30)
    cancelled = pickup - dt.timedelta(hours=hours_before)
    passed = days < 0
    final = pickup + dt.timedelta(hours=12) if passed else None
    return base(12, n, cancelled) + [S06, REGION, iso(pickup), user(user_n), iso(cancelled), total, occupied,
                                     rejoined, status, iso(cancelled), iso(final),
                                     iso(final) if status == "NOT_REPLACED" else None, False,
                                     "Kurzfristige Austragung %d h vor der Abholung." % hours_before, None]


write("signout_cancellation", CANCEL_HEADER, [
    cancellation(1, 8, 2, "OPEN", 20, 2, 1),
    cancellation(2, 6, -2, "REPLACED", 30, 2, 2),
    cancellation(3, 7, -4, "NOT_REPLACED", 10, 2, 1),
    cancellation(4, 8, -6, "REJOINED", 40, 2, 2, rejoined=True),
    cancellation(5, 10, -9, "UNKNOWN", 5, None, None),
])


def extra(n, days_ago):
    pickup = (SEED_NOW - dt.timedelta(days=days_ago)).replace(hour=16, minute=30)
    created = pickup - dt.timedelta(days=1)
    return base(13, n, created) + [S06, REGION, iso(pickup), user(9), 2, 3]


write("pickup_extra_slot", ["store_id", "region_id", "pickup_at", "user_id", "previous_total_slots",
                            "new_total_slots"], [extra(1, 3), extra(2, 10)])

KEYS = [("SLOT_APPROVAL.CONFIRMED", 7), ("SLOT_APPROVAL.DECLINED", 3), ("SLOT_DECLINE_RULE.GAP", 2),
        ("REQUEST.APPROVED", 2), ("ADVERT.SENT", 5), ("ADVERT.FILLED_AFTER_ADVERT", 3),
        ("CANCELLATION.NOT_REPLACED", 2), ("EXTRA_SLOT.ADDED", 2)]
rows = []
n = 0
for d in range(60):
    day = (SEED_NOW - dt.timedelta(days=8 + d)).date()
    for i, (key, top) in enumerate(KEYS):
        value = (d * 7 + i * 3) % (top + 1)
        if value == 0:
            continue
        n += 1
        rows.append(base(14, n) + [REGION, day.isoformat(), key, value])
write("metric_daily_count", ["region_id", "metric_day", "metric_key", "metric_value"], rows)


def load(cs_id, comment, table, columns):
    lines = ["  - changeSet:", "      id: %s" % cs_id, "      author: platform", "      context: seed",
             "      runOnChange: true", "      comment: '%s'" % comment, "      changes:",
             "        - loadUpdateData:", "            tableName: %s" % table,
             "            file: ../automation/%s.csv" % table, "            relativeToChangelogFile: true",
             "            primaryKey: id", "            encoding: UTF-8", "            separator: ','",
             "            quotchar: '\"'", "            columns:"]
    for name, kind in [("id", "UUID"), ("version", "NUMERIC"), ("created_at", "STRING"), ("updated_at", "STRING"),
                       ("created_by", "UUID"), ("updated_by", "UUID")] + columns:
        lines.append("              - column: {name: %s, type: %s}" % (name, kind))
    return "\n".join(lines) + "\n"


def changelog(name, header, body):
    with open(os.path.join(YAML_OUT, name), "w", encoding="utf-8", newline="\n") as f:
        f.write(header + "# Generated by server/scripts/gen-automation-b5-seed.py; ids in docs/seed-ids.md (0022).\n"
                "databaseChangeLog:\n" + body)


S, U, B, N = "STRING", "UUID", "BOOLEAN", "NUMERIC"
changelog("107-automation-adverts-seed.yaml", "# Seed (M4 B5): S-01 open-slot adverts (dry run) and their audits.\n",
          load("seed-107-1", "Open-slot adverts.", "open_slot_advert",
               [("store_id", U), ("advert_number", N), ("enabled", B), ("dry_run", B), ("trigger_hours_before", N),
                ("channels", S), ("telegram_chat_id", S), ("store_messages", S), ("push_messages", S),
                ("telegram_messages", S), ("send_late_cancellation_message", B), ("late_cancellation_message", S),
                ("late_cancellation_max_hours", N), ("resend_latest_advert_after_late_cancellation", B)])
          + load("seed-107-2", "Open-slot advert audits.", "open_slot_advert_audit",
                 [("advert_id", U), ("advert_number", N), ("store_id", U), ("region_id", U), ("pickup_at", S),
                  ("trigger_hours_before", N), ("user_id", U), ("telegram_chat_id", S),
                  ("telegram_message_id", N), ("telegram_deleted_at", S), ("status", S), ("dry_run", B),
                  ("message", S), ("reason", S), ("error", S), ("sent_at", S), ("filled_at", S)]))
changelog("108-automation-signout-seed.yaml", "# Seed (M4 B5): S-06 sign-out alert (live) with 5 cancellations.\n",
          load("seed-108-1", "Sign-out alert setting.", "signout_alert_setting",
               [("store_id", U), ("enabled", B), ("cutoff_enabled", B), ("maximum_hours_before_pickup", N),
                ("channels", S), ("telegram_chat_ids", S), ("active_since", S), ("last_checked_at", S)])
          + load("seed-108-2", "Cancellations (all statuses).", "signout_cancellation",
                 [("store_id", U), ("region_id", U), ("pickup_at", S), ("user_id", U), ("cancelled_at", S),
                  ("total_slots", N), ("occupied_slots", N), ("canceller_rejoined", B), ("status", S),
                  ("immediate_notified_at", S), ("final_checked_at", S), ("final_notified_at", S),
                  ("dry_run", B), ("message", S), ("error", S)]))
changelog("109-automation-extra-slot-seed.yaml", "# Seed (M4 B5): S-06 allows the extra slot; two added extra slots.\n",
          "  - changeSet:\n      id: seed-109-1\n      author: platform\n      context: seed\n"
          "      comment: 'S-06 allows an extra slot for the first sign-up.'\n      changes:\n"
          "        - update:\n            tableName: store\n            columns:\n"
          "              - column: {name: extra_slot_allowed, valueBoolean: true}\n"
          "            where: \"id = '" + S06 + "'\"\n"
          + load("seed-109-2", "Extra slots.", "pickup_extra_slot",
                 [("store_id", U), ("region_id", U), ("pickup_at", S), ("user_id", U),
                  ("previous_total_slots", N), ("new_total_slots", N)]))
changelog("110-automation-metrics-seed.yaml", "# Seed (M4 B5): 60 days of archived metrics for zuerich-stadt.\n",
          load("seed-110-1", "Daily metric counts.", "metric_daily_count",
               [("region_id", U), ("metric_day", "DATE"), ("metric_key", S), ("metric_value", N)]))
