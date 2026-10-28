#!/usr/bin/env python3
"""Content seed (M6 B11): public CMS pages + FAQ (CSV of seed-035), blog posts with pictures, release notes and the
team-page flags (changes/170-content-seed.yaml), support tickets (changes/171-support-seed.yaml).

Texts live in content_seed_texts.py. Re-run after editing: python3 server/scripts/gen-content-seed.py
"""
import csv
import hashlib
import io
import os
import struct
import sys
import zlib

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from content_seed_texts import BLOG, DRAFT, EXISTING, FAQ, LEGAL, PAGES, RELEASE_NOTES  # noqa: E402

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "db", "seed")
CONTENT = os.path.join(ROOT, "content")
CHANGES = os.path.join(ROOT, "changes")
FILES = os.path.join(ROOT, "upload", "files")
LANGS = ["de", "en", "gws", "fr", "it"]
STAMP = "2026-10-25T10:00:00Z"
CREATED = "2026-10-25T10:00:00"
USERS = {"foodsaver": 6, "bot": 11, "welcome": 12, "orga": 14, "newbie": 1}
REGIONS = {"zuerich": "00000010-0001-4000-8000-000000000040",
           "welcome_group": "00000011-0001-4000-8000-000000000020"}
TEAM_GROUPS = ["00000011-0001-4000-8000-0000000000%02d" % n for n in (2, 4, 5, 20, 23)]
COLORS = [(0x25, 0x83, 0x4a), (0x2c, 0x6d, 0xd3), (0xd3, 0x8b, 0x2c), (0xb0, 0x3a, 0x48), (0x6a, 0x4c, 0x9c),
          (0x2a, 0x9d, 0x8f)]


def user_id(key):
    return "00000001-0001-4000-8000-%012d" % USERS[key]


def cid(table, n):
    return "00000012-%04d-4000-8000-%012d" % (table, n)


def upload_id(n):
    return "00000040-0001-4000-8000-%012d" % (400 + n)


def png(color, width=640, height=360):
    """A plate with a loaf on a coloured background."""
    rows = []
    cx, cy = width / 2, height / 2
    for y in range(height):
        row = bytearray(b"\x00")
        for x in range(width):
            d = ((x - cx) / 150) ** 2 + ((y - cy) / 110) ** 2
            loaf = ((x - cx) / 80) ** 2 + ((y - cy) / 40) ** 2 <= 1
            row += bytes((0xc8, 0x8a, 0x3c) if loaf else (0xfa, 0xfa, 0xf5) if d <= 1 else color)
        rows.append(bytes(row))
    raw = zlib.compress(b"".join(rows), 9)

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", raw) + chunk(b"IEND", b""))


def read(name):
    with open(os.path.join(CONTENT, name), newline="", encoding="utf-8") as f:
        return list(csv.DictReader(f))


def write(name, fields, rows):
    out = io.StringIO()
    writer = csv.DictWriter(out, fieldnames=fields, lineterminator="\n")
    writer.writeheader()
    writer.writerows(rows)
    with open(os.path.join(CONTENT, name), "w", encoding="utf-8") as f:
        f.write(out.getvalue())


def body_of(slug, lang, body):
    return DRAFT[lang] + body if slug in LEGAL else body


def content_csv():
    # rows up to page 11 / text 55 come from M2 (T14); everything after is generated here (idempotent re-runs)
    pages = [p for p in read("content_page.csv") if int(p["id"][-12:]) <= 11]
    texts = [t for t in read("content_page_i18n.csv") if int(t["id"][-12:]) <= 55]
    by_slug = {p["slug"]: p for p in pages}
    for page in pages:
        page.setdefault("editor_function", "PR")
        page["editor_function"] = page.get("editor_function") or "PR"
    # existing pages get fuller texts; faq becomes the intro page of the FAQ list
    for slug, (_, function, langs) in EXISTING.items():
        page = by_slug[slug]
        page["editor_function"] = function
        page["kind"] = "PAGE"
        existing = {t["language"]: t for t in texts if t["owner_id"] == page["id"]}
        for lang in LANGS:
            title, body = langs[lang]
            existing[lang]["title"] = title
            existing[lang]["body"] = body_of(slug, lang, body)
    next_page = max(int(p["id"][-12:]) for p in pages) + 1
    next_text = max(int(t["id"][-12:]) for t in texts) + 1
    new = [(slug, "PAGE", sort, function, langs) for slug, (sort, function, langs) in PAGES.items()]
    new += [("faq-%02d" % (i + 1), "FAQ", i + 1, "PR", entry) for i, entry in enumerate(FAQ)]
    for slug, kind, sort, function, langs in new:
        if slug in by_slug:
            raise SystemExit("duplicate slug " + slug)
        page_id = cid(1, next_page)
        next_page += 1
        pages.append({"id": page_id, "version": "0", "created_at": STAMP, "updated_at": STAMP, "created_by": "NULL",
                      "updated_by": "NULL", "slug": slug, "kind": kind, "sort_order": str(sort),
                      "estimated_minutes": "NULL", "published": "true", "editor_function": function})
        for lang in LANGS:
            title, body = langs[lang]
            texts.append({"id": cid(2, next_text), "version": "0", "created_at": STAMP, "updated_at": STAMP,
                          "created_by": "NULL", "updated_by": "NULL", "owner_id": page_id, "language": lang,
                          "title": title, "body": body_of(slug, lang, body)})
            next_text += 1
    write("content_page.csv", list(pages[0].keys()), pages)
    write("content_page_i18n.csv", list(texts[0].keys()), texts)
    return len(new)


def col(name, value):
    if value is None:
        return None
    if isinstance(value, bool):
        return "              - column: {name: %s, valueBoolean: %s}" % (name, "true" if value else "false")
    if isinstance(value, (int, float)):
        return "              - column: {name: %s, valueNumeric: %s}" % (name, value)
    if name.endswith("_at") and name != "released_at":
        return '              - column: {name: %s, valueDate: "%s"}' % (name, value)
    return "              - column: {name: %s, value: %s}" % (name, yaml_str(value))


def yaml_str(value):
    return '"' + value.replace("\\", "\\\\").replace('"', '\\"').replace("\n", "\\n") + '"'


def insert(table, values, created=CREATED):
    lines = ["        - insert:", "            tableName: " + table, "            columns:"]
    base = [("version", 0), ("created_at", created), ("updated_at", created)]
    for name, value in [values[0]] + base + values[1:]:
        line = col(name, value)
        if line:
            lines.append(line)
    return "\n".join(lines)


def change_set(prefix, n, comment, changes):
    return "\n".join(["  - changeSet:", "      id: seed-%s-%d" % (prefix, n), "      author: platform",
                      "      context: seed", "      comment: %s" % yaml_str(comment), "      changes:"] + changes)


def content_yaml():
    uploads, posts = [], []
    for n, (title, teaser, body, region, author, state, picture, published_at, created_at) in enumerate(BLOG, 1):
        post_id = cid(6, n)
        picture_id = None
        if picture:
            data = png(COLORS[picture - 1])
            file_name = "blog-%d.png" % picture
            with open(os.path.join(FILES, file_name), "wb") as f:
                f.write(data)
            picture_id = upload_id(picture)
            uploads.append(insert("upload", [
                ("id", picture_id), ("owner_user_id", user_id(author)), ("file_name", file_name),
                ("mime_type", "image/png"), ("size", len(data)), ("sha256", hashlib.sha256(data).hexdigest()),
                ("storage_key", "seed/" + file_name), ("usage_count", 1), ("usage_module", "BLOG_PICTURE"),
                ("usage_owner_id", post_id), ("width", 640), ("height", 360), ("is_public", True)], created_at))
        posts.append(insert("blog_post", [
            ("id", post_id), ("region_id", REGIONS.get(region)), ("author_id", user_id(author)), ("title", title),
            ("teaser", teaser), ("body", body), ("picture_upload_id", picture_id),
            ("published", state == "published"), ("awaiting_check", state == "awaiting"),
            ("published_at", published_at)], created_at))
    notes = []
    for n, (version, released, langs) in enumerate(RELEASE_NOTES, 1):
        note_id = cid(7, n)
        notes.append(insert("release_note", [("id", note_id), ("release_version", version),
                                             ("released_at", released)]))
        for i, lang in enumerate(LANGS):
            title, body = langs[lang]
            notes.append(insert("release_note_i18n", [("id", cid(8, n * 10 + i)), ("owner_id", note_id),
                                                      ("language", lang), ("title", title), ("body", body)]))
    team = ["        - update:", "            tableName: region", "            columns:",
            "              - column: {name: team_page, valueBoolean: true}",
            "            where: \"id in (%s)\"" % ", ".join("'%s'" % g for g in TEAM_GROUPS)]
    text = "\n".join([
        "# Generated by server/scripts/gen-content-seed.py (edit the script, then re-run it). Blog, release notes,",
        "# team page (M6 B11). Pages and FAQ live in db/seed/content/*.csv (seed-035).",
        "databaseChangeLog:",
        change_set("170", 1, "Blog pictures (public uploads).", uploads),
        change_set("170", 2, "Blog posts: 6 published, 1 awaiting check (welcome), 1 unpublished.", posts),
        change_set("170", 3, "Release notes M1-M5 in five languages.", notes),
        change_set("170", 4, "Working groups on the public team page.", team),
        change_set("170", 5, "Donation campaign pages of the seed (169 seeded a placeholder slug).", [
            "        - update:", "            tableName: configuration", "            columns:",
            "              - column: {name: config_value, value: \"donation-campaign-1,donation-campaign-2\"}",
            "            where: \"config_key = 'donation.campaign-page-slugs'\""]),
    ]) + "\n"
    with open(os.path.join(CHANGES, "170-content-seed.yaml"), "w", encoding="utf-8") as f:
        f.write(text)


def support_yaml():
    tickets = [
        (user_id("foodsaver"), "foodsaver@local", "Sarah", "Kalender-Abo zeigt alte Abholungen",
         "Mein Kalender-Abo zeigt noch Abholungen, aus denen ich mich ausgetragen habe.", "/calendar",
         "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_5) AppleWebKit/605.1.15 Safari/605.1.15", "2026-10-21T09:15:00"),
        (user_id("newbie"), "newbie@local", "Lea", "Quiz startet nicht",
         "Wenn ich auf Quiz starten klicke, passiert nichts.", "/onboarding/quiz",
         "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/128.0 Mobile Safari/537.36",
         "2026-10-23T18:40:00"),
        (None, "baeckerei.sonne@example.ch", "Martin", "Brot spenden",
         "Wir sind eine Bäckerei in Winterthur und haben täglich 5-10 kg Brot übrig. Wie können wir mitmachen?",
         "/content/for-companies", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Firefox/131.0", "2026-10-24T07:05:00"),
    ]
    rows = [insert("support_ticket", [("id", "00000053-0001-4000-8000-%012d" % n), ("user_id", u), ("email", e),
                                      ("first_name", fn), ("subject", s), ("body", b), ("page", p),
                                      ("user_agent", ua)], c)
            for n, (u, e, fn, s, b, p, ua, c) in enumerate(tickets, 1)]
    text = "\n".join([
        "# Generated by server/scripts/gen-content-seed.py. Support tickets (M6 B11).",
        "databaseChangeLog:",
        change_set("171", 1, "Support tickets (2 logged in, 1 public).", rows),
    ]) + "\n"
    with open(os.path.join(CHANGES, "171-support-seed.yaml"), "w", encoding="utf-8") as f:
        f.write(text)


if __name__ == "__main__":
    added = content_csv()
    content_yaml()
    support_yaml()
    print("pages/faq added:", added)
