#!/usr/bin/env python3
"""Generates the forum seed (docs/seed-ids.md module 0043) deterministically.

Writes src/main/resources/db/seed/changes/135-forum-seed.yaml. Re-run after editing:
python3 server/scripts/gen-forum-seed.py
"""
import json
import os
from datetime import datetime, timedelta, timezone

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "db", "seed")
SEED_NOW = datetime(2026, 11, 2, 9, 0, tzinfo=timezone.utc)


def user(n):
    return "00000001-0001-4000-8000-%012d" % n


QUIZ, EINAB, PENDING, FOODSAVER, JUMPER, SLEEPY, BIEB, GUIDE, BOT, WELCOME, REPORTS, ORGA = (
    user(3), user(4), user(5), user(6), user(7), user(8), user(9), user(10), user(11), user(12), user(13), user(14))
ZUERICH = "00000010-0001-4000-8000-000000000040"
KOCH = "00000011-0001-4000-8000-000000000033"
NAMES = {FOODSAVER: "Sarah Müller", JUMPER: "Jonas Weber", BIEB: "Nina Schneider", GUIDE: "Julien Favre",
         BOT: "Reto Baumann", WELCOME: "Priya Sharma", REPORTS: "Céline Rochat", ORGA: "Daniel Huber"}


def mid(table, n):
    return "00000043-%04d-4000-8000-%012d" % (table, n)


def ts(dt):
    return dt.strftime("%Y-%m-%dT%H:%M:%S")


def q(value):
    return json.dumps(str(value), ensure_ascii=False)


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


def base(id_, created, by=None):
    return [column("id", id_), column("version", 0, "num"), column("created_at", created, "date"),
            column("updated_at", created, "date"), column("created_by", by)]


def changeset(n, comment, inserts):
    return "\n".join(["  - changeSet:", "      id: seed-135-%d" % n, "      author: platform", "      context: seed",
                      "      comment: %s" % q(comment), "      changes:"] + inserts)


def mention(uid):
    return "@[%s](user:%s)" % (NAMES[uid], uid)


# (region, ambassador forum, title, sticky, status, active, [(author, body, hours after start)...])
THREADS = [
    (ZUERICH, False, "Willkommen im Forum Zürich Stadt – bitte zuerst lesen", True, "OPEN", True, [
        (BOT, "Hier besprechen wir alles rund um foodsharing in der Stadt Zürich.\n\n**Regeln:** freundlich bleiben, "
              "keine Werbung, Abholungen bitte über die Betriebe koordinieren.", 0),
        (FOODSAVER, "Danke für die Übersicht! Gibt es auch eine Liste der Fairteiler?", 5),
        (BOT, "Ja, die Fairteiler findest du auf der Karte. Ich verlinke sie bald hier.", 7)]),
    (ZUERICH, False, "Termine Einführungsabholungen November", True, "OPEN", True, [
        (GUIDE, "Die nächsten Einführungsabholungen sind im Kalender eingetragen. Bitte meldet euch frühzeitig an.", 0),
        (EINAB, "Ich hätte gern noch einen Termin am Wochenende – geht das?", 3),
        (GUIDE, "Samstag beim Bäcker in Wiedikon ist noch frei.", 4)]),
    (ZUERICH, False, "Fairteiler Wiedikon: Kühlschrank defekt", False, "OPEN", True, [
        (JUMPER, "Der Kühlschrank im Fairteiler Wiedikon kühlt nicht mehr. Bitte keine Milchprodukte hinbringen!", 0),
        (BIEB, "Ich habe den Vermieter informiert, ein Techniker kommt am Donnerstag.", 2),
        (FOODSAVER, "Danke! Ich hänge einen Zettel auf.", 3),
        (BOT, "Update: der Kühlschrank läuft wieder.", 50)]),
    (ZUERICH, False, "Brot übrig? Tauschbörse für Abholungen", False, "OPEN", True, [
        (FOODSAVER, "Manchmal habe ich nach der Abholung zu viel Brot. Wer möchte etwas?", 0),
        (JUMPER, "Ich nehme gern etwas für die WG!", 1),
        (QUIZ, "Ich auch, falls noch etwas übrig ist.", 2),
        (FOODSAVER, "Super, ich melde mich nach der nächsten Abholung.", 3)]),
    (ZUERICH, False, "Velo-Anhänger für grosse Abholungen gesucht", False, "OPEN", True, [
        (BIEB, "Für die Abholung beim Grossverteiler bräuchten wir einen Velo-Anhänger. Hat jemand einen?", 0),
        (GUIDE, "Ich kann meinen ausleihen, er steht in Oerlikon.", 6),
        (BIEB, "Perfekt, ich melde mich per Nachricht.", 7),
        (GUIDE, "Gern, er ist ab Freitag frei.", 9)]),
    (ZUERICH, False, "Stammtisch im Dezember", False, "CLOSED", True, [
        (WELCOME, "Wir treffen uns am 12. Dezember im Quartiertreff. Wer kommt?", 0),
        (FOODSAVER, "Ich bin dabei!", 2),
        (JUMPER, "Ich auch.", 3),
        (WELCOME, "Danke für die Rückmeldungen – das Thema ist geschlossen, Details folgen per Mail.", 30)]),
    (ZUERICH, False, "Hygieneregeln für Milchprodukte", False, "OPEN", True, [
        (REPORTS, "Kurze Erinnerung: Milchprodukte dürfen nur gekühlt transportiert werden.", 0),
        (FOODSAVER, "Gilt das auch für Joghurt mit abgelaufenem Datum?", 4),
        (REPORTS, "Ja, das Mindesthaltbarkeitsdatum ist kein Verfallsdatum, aber die Kühlkette muss stimmen.", 6)]),
    (ZUERICH, False, "Neue Betriebe in Altstetten", False, "OPEN", True, [
        (BIEB, "Wir verhandeln gerade mit zwei Bäckereien in Altstetten. " + mention(FOODSAVER)
               + ", hättest du Zeit für die Testabholungen?", 0),
        (FOODSAVER, "Klar, schreib mir die Daten.", 3),
        (BIEB, "Mache ich, danke!", 4)]),
    (ZUERICH, False, "Streit um Abholzeiten", False, "OPEN", True, [
        (JUMPER, "Warum bekommen immer dieselben Leute die guten Abholzeiten?", 0),
        (SLEEPY, "Das ist doch völlig unfair, ihr seid alle Idioten!", 1),
        (BOT, "Bitte bleibt sachlich. Die Slots werden nach dem Prinzip «wer zuerst kommt» vergeben.", 3),
        (JUMPER, "Okay, danke für die Erklärung.", 4)]),
    (ZUERICH, False, "Kisten und Taschen: Wer hat zu viele?", False, "OPEN", True, [
        (GUIDE, "Für die Einführungen brauche ich noch ein paar stabile Kisten.", 0),
        (FOODSAVER, "Ich habe drei übrig, bringe sie zum Stammtisch mit.", 2),
        (GUIDE, "Danke dir!", 3)]),
    (ZUERICH, False, "Erfahrungen mit dem Foodsaver-Quiz", False, "OPEN", True, [
        (QUIZ, "Ich mache gerade das Quiz. Habt ihr Tipps?", 0),
        (FOODSAVER, "Lies die Hygieneregeln gut durch, da kommen viele Fragen.", 1),
        (GUIDE, "Und keine Angst, man kann es wiederholen.", 2)]),
    (ZUERICH, False, "Lebensmittel für die Gassenküche", False, "OPEN", True, [
        (WELCOME, "Die Gassenküche freut sich über Gemüse. Wer kann am Montag etwas vorbeibringen?", 0),
        (JUMPER, "Ich kann am Montagabend.", 5),
        (WELCOME, "Merci! " + mention(JUMPER) + " übernimmt den Montag.", 6)]),
    (ZUERICH, False, "Weihnachtsmarkt-Stand 2026", False, "OPEN", True, [
        (BOT, "Wir haben einen Stand am Weihnachtsmarkt. Wer hilft mit?", 0),
        (FOODSAVER, "Ich kann am ersten Wochenende.", 4),
        (BIEB, "Ich bringe Flyer mit.", 8),
        (JUMPER, "Ich kann am Sonntag aushelfen.", 9),
        (BOT, "Danke euch, ich mache einen Plan.", 12)]),
    (ZUERICH, False, "Abholungen in den Ferien abgeben", False, "OPEN", False, [
        (JUMPER, "Ich bin zwei Wochen in den Ferien. Kann jemand meine Abholungen übernehmen?", 0)]),
    (ZUERICH, True, "Botschafter-Treffen Q4", True, "OPEN", True, [
        (BOT, "Agenda für das Botschafter-Treffen: Betriebsstand, neue Foodsaver, Stammtisch.", 0),
        (ORGA, "Ich nehme online teil.", 3),
        (BOT, "Danke, ich schicke den Link.", 4)]),
    (ZUERICH, True, "Umgang mit inaktiven Foodsavern", False, "OPEN", True, [
        (BOT, "Wie gehen wir mit Foodsavern um, die seit Monaten nicht mehr abholen?", 0),
        (ORGA, "Zuerst freundlich nachfragen, nach drei Monaten aus den Teams nehmen.", 6),
        (BOT, "Einverstanden, so machen wir es.", 8)]),
    (ZUERICH, True, "Budget Weihnachtsmarkt", False, "OPEN", True, [
        (BOT, "Für den Stand brauchen wir rund 200 CHF. Einwände?", 0),
        (ORGA, "Passt.", 1),
        (BOT, "Gerne auch etwas für Flyer einplanen.", 1.5),
        (BOT, "Super, dann bestelle ich das Material.", 2)]),
    (KOCH, False, "Rezept: Brotsuppe aus Resten", False, "OPEN", True, [
        (FOODSAVER, "Altes Brot, Zwiebeln, Bouillon – fertig ist die Brotsuppe!", 0),
        (JUMPER, "Mit etwas Käse obendrauf noch besser.", 2),
        (FOODSAVER, "Stimmt!", 3)]),
    (KOCH, False, "Gemeinsames Kochen im Januar", False, "OPEN", True, [
        (JUMPER, "Wer hat Lust, im Januar zusammen zu kochen?", 0),
        (FOODSAVER, "Ich! Bei mir in der Küche hat es Platz für sechs.", 3),
        (JUMPER, "Toll, dann machen wir das so.", 4)]),
    (KOCH, False, "Bananen retten: Bananenbrot", False, "OPEN", True, [
        (FOODSAVER, "Überreife Bananen sind perfekt für Bananenbrot.", 0),
        (JUMPER, "Hast du ein Rezept ohne Zucker?", 1),
        (FOODSAVER, "Ja, die Bananen sind süss genug.", 2)]),
    (KOCH, False, "Einmachen von Tomaten", False, "OPEN", True, [
        (JUMPER, "Wie lange halten eingemachte Tomaten?", 0),
        (FOODSAVER, "Gut verschlossen ein Jahr.", 2),
        (JUMPER, "Danke!", 3),
        (FOODSAVER, "Gern geschehen.", 4)]),
]
HIDDEN = (8, 1)  # thread index, post index: the insult in "Streit um Abholzeiten"
ACTIVATED = [2, 12]  # threads that were activated by bot (moderated region)

created = datetime(2026, 9, 1, 8, 0, tzinfo=timezone.utc)
out = ["# Generated by server/scripts/gen-forum-seed.py (edit the script, then re-run it). Forum threads, posts,",
       "# reactions and follows (docs/seed-ids.md module 0043); zuerich-stadt becomes a moderated region.",
       "databaseChangeLog:"]

ins = ["        - update:\n            tableName: region\n            columns:\n"
       "              - column: {name: moderated, valueBoolean: true}\n            where: \"id = '%s'\"" % ZUERICH,
       insert("region_option", base(mid(7, 1), created) + [column("region_id", ZUERICH),
                                                           column("option_key", "ALLOW_HIDING_IN_FORUM"),
                                                           column("option_value", "true")])]
out.append(changeset(1, "zuerich-stadt: moderated forum, hiding posts allowed.", ins))

threads, posts, post_ids = [], [], []
pn = 0
for i, (region, amb, title, sticky, status, active, items) in enumerate(THREADS, start=1):
    start = SEED_NOW - timedelta(days=60 - i * 2.5, hours=i)
    ids = []
    last = None
    for j, (author, body, hours) in enumerate(items):
        pn += 1
        at = start + timedelta(hours=hours)
        hidden = (i - 1, j) == HIDDEN
        cols = base(mid(2, pn), at, author) + [
            column("thread_id", mid(1, i)), column("author_id", author), column("body", body),
            column("hidden_at", at + timedelta(hours=2), "date") if hidden else None,
            column("hidden_by", BOT if hidden else None),
            column("hidden_reason", "Beleidigung" if hidden else None)]
        if i == 3 and j == 0:
            cols += [column("edited_at", at + timedelta(minutes=20), "date"), column("edited_by", author)]
        posts.append(insert("forum_post", cols))
        ids.append(mid(2, pn))
        last = (at, author)
    post_ids.append(ids)
    threads.append(insert("forum_thread", base(mid(1, i), start, items[0][0]) + [
        column("region_id", region), column("ambassador_forum", amb, "bool"), column("title", title),
        column("author_id", items[0][0]), column("status", status), column("sticky", sticky, "bool"),
        column("active", active, "bool"), column("post_count", len(items), "num"),
        column("last_post_at", last[0], "date"), column("last_post_author_id", last[1])]))
out.append(changeset(2, "21 threads (zuerich-stadt 14 incl. 2 sticky, 1 closed, 1 awaiting activation; "
                        "ambassador forum 3; kochgruppe 4) with %d posts." % pn, threads + posts))

# 25 reactions on active zuerich/koch posts
REACTORS = [FOODSAVER, JUMPER, BIEB, GUIDE, BOT]
KEYS = ["THUMBSUP", "HEART", "TADA", "LAUGHING", "PRAY"]
reactions = []
n = 0
for t in [0, 1, 2, 3, 6, 11, 12, 17, 19, 4, 18]:
    for k in range(3 if t in (0, 2, 3, 12) else 2):
        n += 1
        if n > 25:
            break
        reactions.append(insert("forum_post_reaction", base(mid(3, n), created) + [
            column("post_id", post_ids[t][k % len(post_ids[t])]), column("user_id", REACTORS[(t + k) % 5]),
            column("reaction_key", KEYS[(t + 2 * k) % 5])]))
follows = []
for k, t in enumerate([2, 3, 6], start=1):
    follows.append(insert("forum_thread_follow", base(mid(4, k), created) + [
        column("thread_id", mid(1, t + 1)), column("user_id", FOODSAVER), column("bell", True, "bool"),
        column("mail", True, "bool")]))
for k, (t, uid) in enumerate([(4, BIEB), (13, JUMPER), (8, JUMPER), (14, BOT)], start=4):
    follows.append(insert("forum_thread_follow", base(mid(4, k), created) + [
        column("thread_id", mid(1, t + 1)), column("user_id", uid), column("bell", True, "bool"),
        column("mail", False, "bool")]))
follows.append(insert("forum_follow", base(mid(5, 1), created) + [
    column("region_id", ZUERICH), column("ambassador_forum", False, "bool"), column("user_id", FOODSAVER)]))
out.append(changeset(3, "%d reactions; foodsaver follows 3 threads (bell+mail) and the zuerich-stadt forum." % len(reactions),
                     reactions + follows))

hist = []
h = 0
for t in ACTIVATED:
    h += 1
    at = SEED_NOW - timedelta(days=60 - (t + 1) * 2.5, hours=t + 1) + timedelta(hours=1)
    hist.append(insert("history_entry", base(mid(6, h), at, BOT) + [
        column("occurred_at", at, "date"), column("actor_id", BOT), column("actor_type", "USER"),
        column("subject_type", "THREAD"), column("subject_id", mid(1, t + 1)), column("action", "THREAD_ACTIVATED"),
        column("region_id", ZUERICH)]))
h += 1
ti, pi = HIDDEN
at = SEED_NOW - timedelta(days=60 - (ti + 1) * 2.5, hours=ti + 1) + timedelta(hours=3)
hist.append(insert("history_entry", base(mid(6, h), at, BOT) + [
    column("occurred_at", at, "date"), column("actor_id", BOT), column("actor_type", "USER"),
    column("subject_type", "THREAD"), column("subject_id", mid(1, ti + 1)), column("action", "POST_HIDDEN"),
    column("reason", "Beleidigung"), column("metadata", json.dumps({"postId": post_ids[ti][pi]})),
    column("region_id", ZUERICH)]))
out.append(changeset(4, "Moderation log: 2 activations and the hidden post (bot).", hist))

with open(os.path.join(ROOT, "changes", "135-forum-seed.yaml"), "w", encoding="utf-8") as fh:
    fh.write("\n".join(out) + "\n")
print("threads:", len(THREADS), "posts:", pn, "reactions:", len(reactions))
