#!/usr/bin/env python3
"""Generates the achievement seed (M6 B9): db/seed/changes/168-achievement-seed.yaml.

Run from anywhere: python3 server/scripts/gen-achievement-seed.py
"""
import os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "db", "seed")
OUT = os.path.join(ROOT, "changes", "168-achievement-seed.yaml")

USERS = {"foodsaver": 6, "jumper": 7, "sleepy": 8, "bieb": 9, "guide": 10, "bot": 11, "welcome": 12,
         "reports": 13, "orga": 14}
ZUERICH = "00000010-0001-4000-8000-000000000040"


def user_id(key):
    return "00000001-0001-4000-8000-%012d" % USERS[key]


def seed_id(table, n):
    return "00000051-%04d-4000-8000-%012d" % (table, n)


# (n, region, system_key, icon, validity, visibility, mode, created_by, de, {lang: (name, description)})
ACHIEVEMENTS = [
    (1, None, "HYGIENE_CERTIFICATE", "shield", 365, "GLOBAL", "OVERRIDE", "orga",
     ("Hygiene-Zertifikat", "Hat das Hygiene-Quiz bestanden und kennt die Regeln für den sicheren Umgang mit "
                            "Lebensmitteln."),
     {"en": ("Hygiene certificate", "Passed the hygiene quiz and knows the rules for handling food safely."),
      "gws": ("Hygiene-Zertifikat", "Hät s Hygiene-Quiz bestande und kännt d Regle für de sicheri Umgang mit "
                                    "Läbesmittel."),
      "fr": ("Certificat d'hygiène", "A réussi le quiz d'hygiène et connaît les règles pour manipuler les "
                                     "aliments en toute sécurité."),
      "it": ("Certificato di igiene", "Ha superato il quiz sull'igiene e conosce le regole per maneggiare gli "
                                      "alimenti in sicurezza.")}),
    (2, None, "KAM_CERTIFICATE", "briefcase", None, "GLOBAL", "OVERRIDE", "orga",
     ("Key Account Manager:in", "Betreut eine Handelskette als Key Account Manager:in."),
     {"en": ("Key account manager", "Looks after a store chain as key account manager."),
      "gws": ("Key Account Manager:in", "Betreut e Handelskette als Key Account Manager:in."),
      "fr": ("Key account manager", "S'occupe d'une chaîne de magasins en tant que key account manager."),
      "it": ("Key account manager", "Segue una catena di negozi come key account manager.")}),
    (3, ZUERICH, None, "sparkles", None, "SCOPE", "MULTIPLE", "bot",
     ("Fairteiler-Putzheld", "Hat beim grossen Fairteiler-Putztag mitgeholfen."),
     {"en": ("Food share point cleaning hero", "Helped at the big food share point cleaning day."),
      "gws": ("Fairteiler-Putzheld", "Hät bim grosse Fairteiler-Putztag mitghulfe."),
      "fr": ("Héros du nettoyage des frigos partagés", "A aidé lors de la grande journée de nettoyage des "
                                                       "frigos partagés."),
      "it": ("Eroe delle pulizie dei frigo solidali", "Ha aiutato nella grande giornata di pulizia dei frigo "
                                                      "solidali.")}),
    (4, ZUERICH, None, "sun", 180, "STORE_MANAGERS", "OVERRIDE", "bot",
     ("Sommerfest-Helfer 2026", "Hat beim Sommerfest 2026 der Zürcher Foodsaver mitgeholfen."),
     {"en": ("Summer party helper 2026", "Helped at the 2026 summer party of the Zurich foodsavers."),
      "gws": ("Sommerfäscht-Hälfer 2026", "Hät bim Sommerfäscht 2026 vo de Zürcher Foodsaver mitghulfe."),
      "fr": ("Bénévole de la fête d'été 2026", "A aidé lors de la fête d'été 2026 des foodsavers zurichois."),
      "it": ("Aiutante della festa d'estate 2026", "Ha aiutato alla festa d'estate 2026 dei foodsaver "
                                                    "zurighesi.")}),
    (5, ZUERICH, None, "handshake", None, "HIDDEN", "OVERRIDE", "bot",
     ("Vertrauensperson", "Ansprechperson des Bezirks für vertrauliche Anliegen."),
     {"en": ("Person of trust", "The region's contact for confidential matters."),
      "gws": ("Vertrauensperson", "Aasprächperson vom Bezirk für vertraulichi Aaliege."),
      "fr": ("Personne de confiance", "Interlocuteur du district pour les questions confidentielles."),
      "it": ("Persona di fiducia", "Referente del distretto per le questioni confidenziali.")}),
]

# (n, achievement, user, awarded_by, awarded_at, valid_until, notice)
AWARDS = [(i + 1, 1, user, None, "2026-0%d-1%dT10:00:00" % (2 + i % 7, i % 9), "2027-0%d-1%d" % (2 + i % 7, i % 9),
           None) for i, user in enumerate(["foodsaver", "jumper", "bieb", "guide", "bot", "welcome", "reports",
                                           "orga"])]
# foodsaver's certificate mirrors the seeded hygiene quiz pass (2026-01-06, quiz session 00000013-0008-…-05)
AWARDS[0] = (1, 1, "foodsaver", None, "2026-01-06T19:20:00", "2027-01-06", None)
AWARDS += [
    (9, 1, "sleepy", None, "2025-02-20T10:00:00", "2026-02-20", None),
    (10, 2, "bieb", None, "2026-03-01T09:00:00", None, None),
    (11, 3, "foodsaver", "bot", "2026-05-09T16:00:00", None, "Kühlschrank Wiedikon geschrubbt"),
    (12, 4, "jumper", "bot", "2026-07-05T20:00:00", "2027-01-01", "Grill und Getränke"),
    (13, 5, "guide", "bot", "2026-04-01T09:00:00", None, None),
]


def col(name, value):
    if value is None:
        return None
    return '              - column: {name: %s, value: "%s"}' % (name, str(value).replace('"', '\\"'))


def num(name, value):
    if value is None:
        return None
    return "              - column: {name: %s, valueNumeric: %d}" % (name, value)


def date(name, value):
    if value is None:
        return None
    return "              - column: {name: %s, valueDate: '%s'}" % (name, value)


def insert(table, cols):
    lines = ["        - insert:", "            tableName: %s" % table, "            columns:"]
    lines += [c for c in cols if c is not None]
    return "\n".join(lines)


def base(id_, created, created_by=None):
    return [col("id", id_), "              - column: {name: version, valueNumeric: 0}", date("created_at", created),
            date("updated_at", created), col("created_by", created_by)]


def changeset(n, comment, inserts):
    head = ["  - changeSet:", "      id: seed-168-%d" % n, "      author: platform", "      context: seed",
            "      comment: \"%s\"" % comment, "      changes:"]
    return "\n".join(head + inserts)


def main():
    out = ["# Generated by server/scripts/gen-achievement-seed.py (edit the script, then re-run it). "
           "Achievements (module 0051).", "databaseChangeLog:"]
    definitions, texts = [], []
    t = 0
    for n, region, key, icon, validity, visibility, mode, by, de, translations in ACHIEVEMENTS:
        created = "2026-01-15T10:00:00"
        definitions.append(insert("achievement", base(seed_id(1, n), created, user_id(by)) + [
            col("region_id", region), col("name", de[0]), col("description", de[1]), col("icon", icon),
            num("validity_days", validity), col("visibility", visibility), col("duplicate_mode", mode),
            col("system_key", key)]))
        for language, (name, description) in translations.items():
            t += 1
            texts.append(insert("achievement_i18n", base(seed_id(2, t), created, user_id(by)) + [
                col("owner_id", seed_id(1, n)), col("language", language), col("name", name),
                col("description", description)]))
    awards = []
    for n, achievement, user, by, at, until, notice in AWARDS:
        awards.append(insert("awarded_achievement", base(seed_id(3, n), at, user_id(by) if by else None) + [
            col("achievement_id", seed_id(1, achievement)), col("user_id", user_id(user)),
            col("awarded_by", user_id(by) if by else None), date("awarded_at", at), date("valid_until", until),
            col("notice", notice)]))
    out.append(changeset(1, "Global hygiene and KAM certificates, three Zuerich Stadt achievements.", definitions))
    out.append(changeset(2, "Translations en/gws/fr/it (A14).", texts))
    out.append(changeset(3, "Hygiene certificates for the verified users (sleepy expired), KAM for bieb, "
                            "three regional awards.", awards))
    with open(OUT, "w", encoding="utf-8") as f:
        f.write("\n".join(out) + "\n")
    print("wrote", OUT, len(AWARDS), "awards", t, "translations")


if __name__ == "__main__":
    main()
