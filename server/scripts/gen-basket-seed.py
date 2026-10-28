#!/usr/bin/env python3
"""Generates the food basket seed (M6 B5): db/seed/changes/163-basket-seed.yaml and four basket photos.

Run from anywhere: python3 server/scripts/gen-basket-seed.py
"""
import hashlib
import os
import struct
import zlib

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "db", "seed")
CHANGES = os.path.join(ROOT, "changes")
FILES = os.path.join(ROOT, "upload", "files")
SIZE = 320
CREATED = "2026-10-20T09:00:00"
# Far in the future so the seeded ACTIVE baskets stay open for local runs and e2e (the expiry job skips them).
OPEN_UNTIL = "2099-12-31T12:00:00"

USERS = {"foodsaver": 6, "jumper": 7, "bieb": 9, "guide": 10}
HOME = {"foodsaver": (47.389, 8.548), "jumper": (47.386, 8.525), "bieb": (47.38, 8.51), "guide": (47.382, 8.515)}


def user_id(key):
    return "00000001-0001-4000-8000-%012d" % USERS[key]


def seed_id(table, n):
    return "00000048-%04d-4000-8000-%012d" % (table, n)


def upload_id(n):
    return "00000040-0001-4000-8000-%012d" % (200 + n)


def grocery_id(n):
    return "00000020-0005-4000-8000-%012d" % n


# (owner, description, status, location, lat, lon, kinds, groceries, photo, contact (message, phone, mobile), weight)
BASKETS = [
    ("foodsaver", "Zwei Brote und Gipfeli vom Vortag", "ACTIVE", "HOME", None, None, ["BAKED_GOODS"], [1], 1,
     (True, False, False), 1500),
    ("foodsaver", "Gemüsekiste: Rüebli, Lauch, Kartoffeln", "ACTIVE", "CUSTOM", 47.3769, 8.5417,
     ["FRUIT_VEGETABLES"], [2], None, (True, False, True), 4000),
    ("bieb", "Joghurt und Milch, Ablaufdatum morgen", "ACTIVE", "HOME", None, None, ["DAIRY"], [3], 2,
     (True, True, False), 2000),
    ("bieb", "Äpfel aus dem Garten", "ACTIVE", "CUSTOM", 47.3667, 8.5500, ["FRUIT_VEGETABLES"], [], 3,
     (True, False, False), 3000),
    ("guide", "Gekochter Reis mit Gemüse (2 Portionen)", "ACTIVE", "HOME", None, None, ["COOKED"], [], None,
     (True, False, False), 800),
    ("guide", "Konserven: Tomaten und Bohnen", "ACTIVE", "CUSTOM", 47.3910, 8.5120, ["CANNED"], [4], None,
     (False, False, True), 2400),
    ("jumper", "Schokolade und Kekse", "ACTIVE", "HOME", None, None, ["SWEETS"], [], 4, (True, False, False), 600),
    ("jumper", "Getränke: Mineralwasser und Säfte", "ACTIVE", "CUSTOM", 47.3600, 8.5300, ["DRINKS"], [5], None,
     (True, False, False), 6000),
    ("bieb", "Salat und Kräuter (abgeholt)", "TAKEN", "CUSTOM", 47.3790, 8.5390, ["FRUIT_VEGETABLES"], [], None,
     (True, False, False), 500),
    ("foodsaver", "Tiefkühlpizza (abgelaufen)", "EXPIRED", "HOME", None, None, ["FROZEN"], [], None,
     (True, False, False), 900),
    ("guide", "Brot in Winterthur", "ACTIVE", "CUSTOM", 47.4997, 8.7241, ["BAKED_GOODS"], [1], None,
     (True, False, False), 1000),
    ("jumper", "Gemüse in Winterthur Töss", "ACTIVE", "CUSTOM", 47.4900, 8.7100, ["FRUIT_VEGETABLES", "OTHER"],
     [2], None, (True, False, False), 2000),
]
PHONES = {"foodsaver": ("+41441234506", "+41791234506"), "bieb": ("+41441234509", "+41791234509"),
          "guide": ("+41441234510", "+41791234510"), "jumper": ("+41441234507", "+41771234507")}
# (basket number, requester, status, message, answered)
REQUESTS = [
    (3, "foodsaver", "REQUESTED", "Hallo Nina, ich könnte heute Abend vorbeikommen.", False),
    (4, "foodsaver", "READ", None, False),
    (1, "guide", "REQUESTED", "Hätte gerne die Brote!", False),
    (5, "jumper", "DENIED", None, True),
    (9, "guide", "PICKED_UP", "Komme um 18 Uhr.", True),
    (9, "jumper", "NOT_PICKED_UP", None, True),
]
COLORS = [(0xd3, 0x8b, 0x2c), (0xf0, 0xf0, 0xe0), (0xc0, 0x39, 0x2b), (0x6d, 0x4c, 0x41)]


def png(color):
    """A wicker-coloured basket shape on a light background."""
    rows = []
    for y in range(SIZE):
        row = bytearray(b"\x00")
        for x in range(SIZE):
            in_basket = SIZE * 0.45 <= y <= SIZE * 0.85 and abs(x - SIZE / 2) <= SIZE * (0.38 - (y / SIZE - 0.45) * 0.2)
            in_food = (x - SIZE / 2) ** 2 + ((y - SIZE * 0.45) * 1.6) ** 2 <= (SIZE * 0.3) ** 2 and y < SIZE * 0.45
            row += bytes((0xa0, 0x6a, 0x2c) if in_basket else color if in_food else (0xf7, 0xf3, 0xe8))
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


def insert(table, values):
    lines = ["        - insert:", "            tableName: " + table, "            columns:"]
    base = [("version", 0), ("created_at", CREATED), ("updated_at", CREATED)]
    for name, value in [values[0]] + base + values[1:]:
        line = col(name, value)
        if line:
            lines.append(line)
    return "\n".join(lines)


def main():
    os.makedirs(FILES, exist_ok=True)
    uploads, baskets, groceries, kinds, requests = [], [], [], [], []
    request_counts = {}
    for number, _, _, _, _ in REQUESTS:
        request_counts[number] = request_counts.get(number, 0) + 1
    for i, (owner, text, status, location, lat, lon, kind_list, grocery_list, photo, contact, weight) in \
            enumerate(BASKETS, start=1):
        if location == "HOME":
            lat, lon = HOME[owner]
        photo_id = None
        if photo:
            data = png(COLORS[photo - 1])
            name = "basket-%d.png" % photo
            with open(os.path.join(FILES, name), "wb") as out:
                out.write(data)
            photo_id = upload_id(photo)
            uploads.append(insert("upload", [
                ("id", photo_id), ("owner_user_id", user_id(owner)), ("file_name", name),
                ("mime_type", "image/png"), ("size", len(data)), ("sha256", hashlib.sha256(data).hexdigest()),
                ("storage_key", "seed/" + name), ("usage_count", 1), ("usage_module", "BASKET_PHOTO"),
                ("usage_owner_id", seed_id(1, i)), ("width", SIZE), ("height", SIZE), ("is_public", True)]))
        phone, mobile = PHONES[owner]
        expires = OPEN_UNTIL if status in ("ACTIVE", "TAKEN") else "2026-10-27T09:00:00"
        baskets.append(insert("basket", [
            ("id", seed_id(1, i)), ("owner_id", user_id(owner)), ("description", text), ("status", status),
            ("weight_grams", weight), ("location_type", location), ("lat", lat), ("lon", lon),
            ("contact_message", contact[0]), ("contact_phone", contact[1]), ("contact_mobile", contact[2]),
            ("phone", phone if contact[1] else None), ("mobile", mobile if contact[2] else None),
            ("lifetime_days", 7), ("expires_at", expires),
            ("taken_at", "2026-10-21T18:00:00" if status == "TAKEN" else None),
            ("photo_upload_id", photo_id), ("request_count", request_counts.get(i, 0))]))
        for g in grocery_list:
            groceries.append(insert("basket_grocery", [
                ("id", seed_id(2, len(groceries) + 1)), ("basket_id", seed_id(1, i)), ("grocery_id", grocery_id(g))]))
        for k in kind_list:
            kinds.append(insert("basket_kind", [
                ("id", seed_id(3, len(kinds) + 1)), ("basket_id", seed_id(1, i)), ("kind", k)]))
    for n, (number, requester, status, message, answered) in enumerate(REQUESTS, start=1):
        requests.append(insert("basket_request", [
            ("id", seed_id(4, n)), ("basket_id", seed_id(1, number)), ("requester_id", user_id(requester)),
            ("status", status), ("message", message), ("requested_at", "2026-10-21T08:%02d:00" % n),
            ("answered_at", "2026-10-21T18:00:00" if answered else None)]))

    def change_set(n, comment, inserts):
        return ("  - changeSet:\n      id: seed-163-%d\n      author: platform\n      context: seed\n"
                "      comment: \"%s\"\n      changes:\n%s\n" % (n, comment, "\n".join(inserts)))

    with open(os.path.join(CHANGES, "163-basket-seed.yaml"), "w", encoding="utf-8") as out:
        out.write("# Generated by server/scripts/gen-basket-seed.py (edit the script, then re-run it). "
                  "Food baskets (module 0048).\ndatabaseChangeLog:\n")
        out.write(change_set(1, "Basket photos (public uploads).", uploads))
        out.write(change_set(2, "12 baskets: 8 open in Zürich, 1 taken, 1 expired, 2 in Winterthur.", baskets))
        out.write(change_set(3, "Groceries and kinds of the baskets.", groceries + kinds))
        out.write(change_set(4, "6 requests: foodsaver has 2 open ones on bieb's baskets, one picked up.", requests))


if __name__ == "__main__":
    main()
