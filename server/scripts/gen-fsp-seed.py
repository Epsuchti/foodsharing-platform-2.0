#!/usr/bin/env python3
"""Generates the food share point seed (M6 B6): db/seed/changes/164-fsp-seed.yaml and three FSP photos.

Run from anywhere: python3 server/scripts/gen-fsp-seed.py
"""
import hashlib
import os
import struct
import zlib

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "db", "seed")
CHANGES = os.path.join(ROOT, "changes")
FILES = os.path.join(ROOT, "upload", "files")
SIZE = 320
CREATED = "2026-10-10T09:00:00"

USERS = {"foodsaver": 6, "jumper": 7, "bieb": 9, "guide": 10, "bot": 11, "welcome": 12}
ZUERICH = "00000010-0001-4000-8000-000000000040"
WINTERTHUR = "00000010-0001-4000-8000-000000000041"
FSP_GROUP = "00000011-0001-4000-8000-000000000022"


def user_id(key):
    return "00000001-0001-4000-8000-%012d" % USERS[key]


def seed_id(table, n):
    return "00000049-%04d-4000-8000-%012d" % (table, n)


def upload_id(n):
    return "00000040-0001-4000-8000-%012d" % (300 + n)


# (name, street, postcode, city, lat, lon, status, region, creator, managers, photo, deleted, description)
FSPS = [
    ("Fairteiler Wiedikon", "Schlossgasse 10", "8003", "Zürich", 47.3721, 8.5172, "ACTIVE", ZUERICH, "bot",
     ["bieb"], 1, False, "Kühlschrank und Regal im Hinterhof des Quartiertreffs. Täglich von 8 bis 22 Uhr offen."),
    ("Fairteiler Langstrasse", "Langstrasse 120", "8004", "Zürich", 47.3783, 8.5262, "ACTIVE", ZUERICH, "bot",
     ["bieb"], 2, False, "Regal vor dem Café, nur ungekühlte Lebensmittel."),
    ("Fairteiler Oerlikon", "Schaffhauserstrasse 350", "8050", "Zürich", 47.4106, 8.5446, "ACTIVE", ZUERICH, "bot",
     ["guide"], 3, False, "Kühlschrank beim Gemeinschaftszentrum, rund um die Uhr zugänglich."),
    ("Fairteiler Hottingen", "Gemeindestrasse 48", "8032", "Zürich", 47.3700, 8.5600, "ACTIVE", ZUERICH, "bot",
     ["bot"], None, False, None),
    ("Fairteiler Altstetten", "Badenerstrasse 600", "8048", "Zürich", 47.3880, 8.4880, "INACTIVE", ZUERICH,
     "foodsaver", ["foodsaver"], None, False, "Vorschlag: Regal im Treppenhaus der Siedlung, wartet auf Freigabe."),
    ("Fairteiler Enge", "Seestrasse 50", "8002", "Zürich", 47.3620, 8.5320, "ACTIVE", ZUERICH, "bot", ["bot"],
     None, True, "Aufgelöst."),
    ("Fairteiler Winterthur Bahnhof", "Bahnhofplatz 5", "8400", "Winterthur", 47.5000, 8.7240, "ACTIVE",
     WINTERTHUR, "bot", ["bot"], None, False, "Regal in der Velostation."),
]
# (fsp number, user, bell, mail)
FOLLOWERS = [(1, "foodsaver", True, True), (2, "foodsaver", True, False), (1, "jumper", True, False),
             (3, "jumper", True, False)]
# (fsp number, author, body, day)
POSTS = [
    (1, "bieb", "Kühlschrank ist geputzt, bitte nur verpackte Lebensmittel hineinlegen.", 12),
    (1, "foodsaver", "Habe gerade 5 kg Brot vom Beck gebracht.", 14),
    (1, "jumper", "Brot ist schon weg, danke!", 14),
    (1, "bot", "Am Samstag ist Putzaktion, wer hilft mit?", 20),
    (2, "bieb", "Regal ist wieder aufgefüllt mit Gemüse.", 15),
    (2, "foodsaver", "Achtung: Die Tür klemmt etwas.", 18),
    (3, "guide", "Neues Thermometer im Kühlschrank montiert.", 16),
    (3, "jumper", "Joghurt mit Ablaufdatum heute hingestellt.", 21),
    (7, "bot", "Der Fairteiler in Winterthur ist eröffnet!", 11),
]
COLORS = [(0x25, 0x83, 0x4a), (0x2c, 0x6d, 0xd3), (0xd3, 0x8b, 0x2c)]


def png(color):
    """A fridge on a light background."""
    rows = []
    for y in range(SIZE):
        row = bytearray(b"\x00")
        for x in range(SIZE):
            in_fridge = SIZE * 0.3 <= x <= SIZE * 0.7 and SIZE * 0.1 <= y <= SIZE * 0.9
            door_line = in_fridge and abs(y - SIZE * 0.4) < 3
            handle = SIZE * 0.62 <= x <= SIZE * 0.65 and (SIZE * 0.2 <= y <= SIZE * 0.32 or
                                                           SIZE * 0.48 <= y <= SIZE * 0.62)
            row += bytes((0x40, 0x40, 0x40) if door_line or handle else color if in_fridge else (0xf7, 0xf3, 0xe8))
        rows.append(bytes(row))
    raw = zlib.compress(b"".join(rows), 9)

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", SIZE, SIZE, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", raw) + chunk(b"IEND", b""))


def col(name, value):
    if value is None:
        return None
    if isinstance(value, bool):
        return "              - column: {name: %s, valueBoolean: %s}" % (name, "true" if value else "false")
    if isinstance(value, (int, float)):
        return "              - column: {name: %s, valueNumeric: %s}" % (name, value)
    if name.endswith("_at"):
        return '              - column: {name: %s, valueDate: "%s"}' % (name, value)
    return '              - column: {name: %s, value: "%s"}' % (name, value)


def insert(table, values, created=CREATED):
    lines = ["        - insert:", "            tableName: " + table, "            columns:"]
    base = [("version", 0), ("created_at", created), ("updated_at", created)]
    for name, value in [values[0]] + base + values[1:]:
        line = col(name, value)
        if line:
            lines.append(line)
    return "\n".join(lines)


def main():
    os.makedirs(FILES, exist_ok=True)
    uploads, fsps, followers, posts = [], [], [], []
    for i, (name, street, postcode, city, lat, lon, status, region, creator, managers, photo, deleted, text) in \
            enumerate(FSPS, start=1):
        photo_id = None
        if photo:
            data = png(COLORS[photo - 1])
            file_name = "fsp-%d.png" % photo
            with open(os.path.join(FILES, file_name), "wb") as out:
                out.write(data)
            photo_id = upload_id(photo)
            uploads.append(insert("upload", [
                ("id", photo_id), ("owner_user_id", user_id(creator)), ("file_name", file_name),
                ("mime_type", "image/png"), ("size", len(data)), ("sha256", hashlib.sha256(data).hexdigest()),
                ("storage_key", "seed/" + file_name), ("usage_count", 1), ("usage_module", "FSP_PHOTO"),
                ("usage_owner_id", seed_id(1, i)), ("width", SIZE), ("height", SIZE), ("is_public", True)]))
        fsps.append(insert("food_share_point", [
            ("id", seed_id(1, i)), ("region_id", region), ("name", name), ("description", text),
            ("street", street), ("postcode", postcode), ("city", city), ("lat", lat), ("lon", lon),
            ("status", status), ("photo_upload_id", photo_id), ("created_by_user_id", user_id(creator)),
            ("deleted_at", "2026-10-15T09:00:00" if deleted else None)]))
        for manager in managers:
            followers.append(insert("fsp_follower", [
                ("id", seed_id(2, len(followers) + 1)), ("fsp_id", seed_id(1, i)), ("user_id", user_id(manager)),
                ("role", "MANAGER"), ("bell", True), ("mail", False)]))
    for number, user, bell, mail in FOLLOWERS:
        followers.append(insert("fsp_follower", [
            ("id", seed_id(2, len(followers) + 1)), ("fsp_id", seed_id(1, number)), ("user_id", user_id(user)),
            ("role", "FOLLOWER"), ("bell", bell), ("mail", mail)]))
    for n, (number, author, body, day) in enumerate(POSTS, start=1):
        posts.append(insert("fsp_wall_post", [
            ("id", seed_id(3, n)), ("fsp_id", seed_id(1, number)), ("author_id", user_id(author)),
            ("body", body)], created="2026-10-%02dT%02d:00:00" % (day, 8 + n)))
    admin = [
        insert("region_admin", [("id", "00000010-0003-4000-8000-000000000011"), ("user_id", user_id("bot")),
                                ("region_id", FSP_GROUP)]),
        insert("region_membership", [("id", "00000010-0002-4000-8000-000000000062"), ("user_id", user_id("bot")),
                                     ("region_id", FSP_GROUP), ("active", True), ("joined_at", CREATED)]),
    ]

    def change_set(n, comment, inserts):
        return ("  - changeSet:\n      id: seed-164-%d\n      author: platform\n      context: seed\n"
                "      comment: \"%s\"\n      changes:\n%s\n" % (n, comment, "\n".join(inserts)))

    with open(os.path.join(CHANGES, "164-fsp-seed.yaml"), "w", encoding="utf-8") as out:
        out.write("# Generated by server/scripts/gen-fsp-seed.py (edit the script, then re-run it). "
                  "Food share points (module 0049).\ndatabaseChangeLog:\n")
        out.write(change_set(1, "bot administers the FSP group of Zürich Stadt (activates FSPs).", admin))
        out.write(change_set(2, "FSP photos (public uploads).", uploads))
        out.write(change_set(3, "7 FSPs: 4 active + 1 inactive + 1 deleted in Zürich Stadt, 1 in Winterthur.", fsps))
        out.write(change_set(4, "Managers (bieb 2, guide, bot, foodsaver) and followers (foodsaver, jumper).",
                             followers))
        out.write(change_set(5, "9 wall posts.", posts))


if __name__ == "__main__":
    main()
