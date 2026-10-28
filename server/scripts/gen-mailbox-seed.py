#!/usr/bin/env python3
"""Generates the mailbox seed (docs/seed-ids.md module 0042, upload 0040) deterministically.

Writes src/main/resources/db/seed/changes/133-mailbox-seed.yaml and the seeded attachment files under
src/main/resources/db/seed/upload/files/. Re-run after editing: python3 server/scripts/gen-mailbox-seed.py
"""
import hashlib
import json
import os
import struct
import zlib
from datetime import datetime, timedelta, timezone

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "db", "seed")
SEED_NOW = datetime(2026, 11, 2, 9, 0, tzinfo=timezone.utc)
DOMAIN = "local.test"

BOT = "00000001-0001-4000-8000-000000000011"
WELCOME = "00000001-0001-4000-8000-000000000012"
REPORTS = "00000001-0001-4000-8000-000000000013"
ZUERICH_STADT = "00000010-0001-4000-8000-000000000040"
WELCOME_GROUP = "00000011-0001-4000-8000-000000000020"


def mid(table, n):
    return "00000042-%04d-4000-8000-%012d" % (table, n)


def uid(n):
    return "00000040-0001-4000-8000-%012d" % n


def ts(dt):
    return dt.strftime("%Y-%m-%dT%H:%M:%S")


def q(value):
    """A YAML double-quoted scalar (JSON string syntax keeps newlines and quotes intact)."""
    return json.dumps(str(value), ensure_ascii=False)


# ----------------------------------------------------------------------------------------------- files

def make_pdf(text):
    content = ("BT /F1 18 Tf 72 720 Td (%s) Tj ET" % text).encode("latin-1")
    objects = [
        b"<< /Type /Catalog /Pages 2 0 R >>",
        b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R "
        b"/Resources << /Font << /F1 5 0 R >> >> >>",
        b"<< /Length %d >>\nstream\n" % len(content) + content + b"\nendstream",
        b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
    ]
    out = b"%PDF-1.4\n"
    offsets = []
    for i, obj in enumerate(objects, 1):
        offsets.append(len(out))
        out += b"%d 0 obj\n" % i + obj + b"\nendobj\n"
    xref = len(out)
    out += b"xref\n0 %d\n0000000000 65535 f \n" % (len(objects) + 1)
    for off in offsets:
        out += b"%010d 00000 n \n" % off
    out += b"trailer\n<< /Size %d /Root 1 0 R >>\nstartxref\n%d\n%%%%EOF\n" % (len(objects) + 1, xref)
    return out


def make_png(width=16, height=16):
    raw = b"".join(b"\x00" + bytes([40, 160, 70]) * width for _ in range(height))

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xffffffff)

    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b""))


FILES = [
    (1, "Hygienekonzept-Fairteiler.pdf", "application/pdf", make_pdf("Hygienekonzept Fairteiler Zuerich")),
    (2, "flyer-vorschau.png", "image/png", make_png()),
]

# ----------------------------------------------------------------------------------------------- mails

SENDERS = [
    ("info@coop-bahnhofbruecke.ch", "Coop Bahnhofbrücke"), ("anna.keller@example.ch", "Anna Keller"),
    ("stadt@zuerich.example.ch", "Stadt Zürich Umwelt"), ("presse@tagblatt.example.ch", "Tagblatt Redaktion"),
    ("lukas.meier@example.ch", "Lukas Meier"), ("hello@baeckerei-gut.example.ch", "Bäckerei Gut"),
    ("verein@quartierzentrum.example.ch", "Quartierzentrum Kreis 5"), ("sara.frei@example.ch", "Sara Frei"),
]
SUBJECTS = [
    "Abholung am Samstag", "Frage zum Fairteiler", "Neue Kooperation?", "Medienanfrage foodsharing",
    "Danke für euren Einsatz", "Brotüberschuss am Abend", "Raum für Infoabend", "Wie kann ich mitmachen?",
    "Lagerplatz gesucht", "Termin Stadtrundgang", "Rückfrage Hygienenachweis", "Spendenquittung",
    "Kühlschrank defekt", "Einladung Quartierfest", "Interview nächste Woche", "Kooperation Wochenmarkt",
    "Flyer für die Mensa", "Abholzeiten Winter", "Feedback zum Workshop", "Vernetzungstreffen",
]


class Mail:
    def __init__(self, **kw):
        self.__dict__.update(kw)


mails = []
attachments = []


def add_mail(n, mailbox, folder, received, sender, to, subject, text, html=None, read=True, answered=None,
             bounce=False, bounced=None, message_id=None, in_reply_to=None, previous=None, sent_by=None, files=()):
    mails.append(Mail(id=mid(3, n), mailbox=mailbox, folder=folder, received=received, sender=sender, to=to,
                      subject=subject, text=text, html=html, read=read, answered=answered, bounce=bounce,
                      bounced=bounced, message_id=message_id or "<seed-%03d@%s>" % (n, "example.ch"),
                      in_reply_to=in_reply_to, previous=previous, sent_by=sent_by, files=list(files)))


ZUERICH = mid(1, 1)
WELCOME_BOX = mid(1, 2)
PERSONAL = mid(1, 3)
ZUERICH_ADDR = ("zuerich@" + DOMAIN, "Zürich Stadt")

# zuerich INBOX: 25 mails (01–25), unread 23–25, attachments on 05 (PDF) and 12 (PNG), answered 10,
# HTML newsletter 15 (sanitised), bounce DSN 20 referencing SENT 26.
for i in range(1, 26):
    received = SEED_NOW - timedelta(days=26 - i, hours=(i * 5) % 11)
    sender = SENDERS[i % len(SENDERS)]
    subject = SUBJECTS[(i - 1) % len(SUBJECTS)]
    text = "Hallo foodsharing Zürich\n\n%s - wir würden uns über eine kurze Rückmeldung freuen.\n\nLiebe Grüsse\n%s" % (
        subject, sender[1])
    kw = {}
    if i == 5:
        kw["files"] = [1]
        subject = "Hygienekonzept für den Fairteiler"
    if i == 12:
        kw["files"] = [2]
        subject = "Flyer-Entwurf zur Ansicht"
    if i == 10:
        kw["answered"] = received + timedelta(hours=3)
    if i == 15:
        sender = ("newsletter@lebensmittelretten.example.ch", "Lebensmittelretten Newsletter")
        subject = "Newsletter Oktober: Aktionswoche"
        text = "Aktionswoche gegen Foodwaste\nAlle Termine findest du online."
        kw["html"] = ('<h2>Aktionswoche gegen Foodwaste</h2><p>Alle Termine findest du '
                      '<a href="https://lebensmittelretten.example.ch/termine" rel="noopener noreferrer" '
                      'target="_blank">online</a>.</p><p>Bis bald!</p>')
    if i == 20:
        sender = ("mailer-daemon@" + DOMAIN, "Mail Delivery System")
        subject = "Undelivered Mail Returned to Sender"
        text = ("This is the mail system at host %s.\n\nI am sorry to inform you that your message could not be "
                "delivered.\n\n<nobody@unknown-domain.example>: host not found\n\nOriginal-Message-ID: "
                "<seed-sent-026@%s>" % (DOMAIN, DOMAIN))
        kw["bounce"] = True
        kw["bounced"] = "<seed-sent-026@%s>" % DOMAIN
    add_mail(i, ZUERICH, "INBOX", received, sender, [ZUERICH_ADDR], subject, text, read=i < 23, **kw)

# zuerich SENT 26–31 (26 bounced), TRASH 32–33.
for k, n in enumerate(range(26, 32)):
    received = SEED_NOW - timedelta(days=20 - 3 * k, hours=2)
    recipient = ("nobody@unknown-domain.example", None) if n == 26 else SENDERS[k]
    subject = "Re: " + SUBJECTS[k]
    add_mail(n, ZUERICH, "SENT", received, ZUERICH_ADDR, [recipient], subject,
             "Hallo\n\nDanke für deine Nachricht, wir melden uns bald.\n\nfoodsharing Zürich", sent_by=BOT,
             message_id="<seed-sent-%03d@%s>" % (n, DOMAIN),
             in_reply_to="<seed-010@example.ch>" if n == 27 else None)
for n in (32, 33):
    add_mail(n, ZUERICH, "TRASH", SEED_NOW - timedelta(days=30 + n), ("werbung@shop.example.com", "Shop"),
             [ZUERICH_ADDR], "Sonderangebot nur heute" if n == 32 else "Gewinnspiel", "Jetzt zugreifen!",
             previous="INBOX")

# welcome-zuerich INBOX 34–37 (35 unread).
WELCOME_ADDR = ("welcome-zuerich@" + DOMAIN, "Begrüssungsteam Zürich")
for k, n in enumerate(range(34, 38)):
    add_mail(n, WELCOME_BOX, "INBOX", SEED_NOW - timedelta(days=8 - 2 * k), SENDERS[(k + 3) % len(SENDERS)],
             [WELCOME_ADDR], ["Einführungsabholung", "Termin für Infoabend", "Wie werde ich Foodsaver?",
                              "Danke für die Einführung"][k],
             "Hallo Begrüssungsteam\n\nIch habe eine Frage zur Einführung.\n\nViele Grüsse", read=n != 35)

# personal reto.baumann INBOX 38.
add_mail(38, PERSONAL, "INBOX", SEED_NOW - timedelta(days=1), ("kontakt@migros-city.example.ch", "Migros City"),
         [("reto.baumann@" + DOMAIN, "Reto Baumann")], "Kooperationsgespräch",
         "Guten Tag Herr Baumann\n\nGerne bestätigen wir den Termin am Donnerstag.\n\nFreundliche Grüsse", read=False)


# ----------------------------------------------------------------------------------------------- yaml

def column(name, value, kind="value"):
    if value is None:
        return None
    if kind == "bool":
        return "              - column: {name: %s, valueBoolean: %s}" % (name, "true" if value else "false")
    if kind == "num":
        return "              - column: {name: %s, valueNumeric: %s}" % (name, value)
    if kind == "date":
        return "              - column: {name: %s, valueDate: '%s'}" % (name, ts(value))
    return "              - column: {name: %s, value: %s}" % (name, q(value))


def insert(table, cols):
    lines = ["        - insert:", "            tableName: " + table, "            columns:"]
    lines += [c for c in cols if c]
    return "\n".join(lines)


def base(id_, created):
    return [column("id", id_), column("version", 0, "num"), column("created_at", created, "date"),
            column("updated_at", created, "date")]


def changeset(n, comment, inserts):
    return "\n".join(["  - changeSet:", "      id: seed-133-%d" % n, "      author: platform", "      context: seed",
                      "      comment: %s" % q(comment), "      changes:"] + inserts)


created = datetime(2026, 9, 1, 8, 0, tzinfo=timezone.utc)
out = ["# Generated by server/scripts/gen-mailbox-seed.py (edit the script, then re-run it). Mailboxes, members,",
       "# mails and attachments (docs/seed-ids.md modules 0040/0042). Attachment bytes: ../upload/files/.",
       "databaseChangeLog:"]

boxes = [(ZUERICH, "Zürich Stadt", "zuerich", "REGION", ZUERICH_STADT),
         (WELCOME_BOX, "Begrüssungsteam Zürich", "welcome-zuerich", "REGION", WELCOME_GROUP),
         (PERSONAL, "Reto Baumann", "reto.baumann", "USER", BOT)]
ins = [insert("mailbox", base(b[0], created) + [column("name", b[1]), column("email_name", b[2]),
                                                 column("owner_type", b[3]), column("owner_id", b[4])])
       for b in boxes]
ins.append("        - update:\n            tableName: user_account\n            columns:\n"
           "              - column: {name: mailbox_id, value: '%s'}\n            where: \"id = '%s'\"" % (PERSONAL, BOT))
for k, user in enumerate([WELCOME, REPORTS], 1):
    ins.append(insert("mailbox_member", base(mid(2, k), created) + [column("mailbox_id", ZUERICH),
                                                                    column("user_id", user)]))
out.append(changeset(1, "Mailboxes zuerich (members welcome, reports), welcome-zuerich, personal reto.baumann.", ins))

ins = []
for f in FILES:
    n, name, mime, data = f
    ins.append(insert("upload", base(uid(n), created) + [
        column("owner_user_id", BOT), column("file_name", name), column("mime_type", mime),
        column("size", len(data), "num"), column("sha256", hashlib.sha256(data).hexdigest()),
        column("storage_key", "seed/" + name), column("usage_count", 1, "num"), column("usage_module", "mailbox"),
        column("usage_owner_id", mid(3, 5 if n == 1 else 12))]))
out.append(changeset(2, "Seeded attachment uploads (bytes written by UploadSeedFiles).", ins))

ins = []
att = 0
for m in mails:
    sender = m.sender
    cols = base(m.id, m.received) + [
        column("mailbox_id", m.mailbox), column("folder", m.folder), column("previous_folder", m.previous),
        column("message_id", m.message_id), column("in_reply_to", m.in_reply_to),
        column("from_address", sender[0]), column("from_name", sender[1]),
        column("to_json", json.dumps([{"address": a, "name": nm} for a, nm in m.to], ensure_ascii=False)),
        column("subject", m.subject), column("body_text", m.text), column("body_html", m.html),
        column("received_at", m.received, "date"), column("read_at", m.received if m.read else None, "date"),
        column("answered_at", m.answered, "date"), column("bounce", m.bounce, "bool"),
        column("bounced_message_id", m.bounced), column("raw_size", 1200 + len(m.text), "num"),
        column("sent_by_user_id", m.sent_by), column("attachment_count", len(m.files), "num")]
    ins.append(insert("mail_message", cols))
    for f in m.files:
        att += 1
        n, name, mime, data = FILES[f - 1]
        ins.append(insert("mail_attachment", base(mid(4, att), m.received) + [
            column("mail_id", m.id), column("upload_id", uid(n)), column("file_name", name),
            column("mime_type", mime), column("size", len(data), "num")]))
# the answered original (10) and the SENT reply (27) point at each other via In-Reply-To
out.append(changeset(3, "Mails: zuerich 25 INBOX (3 unread, 2 attachments, 1 answered, 1 HTML, 1 bounce), "
                        "6 SENT, 2 TRASH; welcome-zuerich 4; reto.baumann 1.", ins))

with open(os.path.join(ROOT, "changes", "133-mailbox-seed.yaml"), "w", encoding="utf-8") as fh:
    fh.write("\n".join(out) + "\n")
os.makedirs(os.path.join(ROOT, "upload", "files"), exist_ok=True)
for n, name, mime, data in FILES:
    with open(os.path.join(ROOT, "upload", "files", name), "wb") as fh:
        fh.write(data)
print("mails:", len(mails), "attachments:", att)
