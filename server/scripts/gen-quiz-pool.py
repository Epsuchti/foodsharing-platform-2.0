#!/usr/bin/env python3
"""Foodsaver quiz pool (C1, owner decision 2026-11-10): docs/content-import/quiz/foodsaver.de.yaml ->
changes/197-quiz-foodsaver-pool.yaml (non-seed, production data).

  python3 server/scripts/gen-quiz-pool.py           write the changeset (refuses to change an existing one)
  python3 server/scripts/gen-quiz-pool.py --force   overwrite a changeset that was never released

- 197-0 creates the FOODSAVER quiz (seed id and texts) where none exists, so the pool has a home in every environment.
- 197-<n> inserts question n with its German text and answers, only when its fixed id is free (MARK_RAN), so edits
  made later in the quiz editor are never overwritten. quiz_id is looked up by kind, so an existing FOODSAVER quiz
  with another id gets the questions too.
- Wiki links point at the learning chapters: /learn/<chapter>#<anchor>; the anchors must exist in the German chapter.
Ids (docs/seed-ids.md): question 00000013-0003-…-(1000+n), its text 0004-…-(1000+n), answer a (1-based)
0005-…-(10000+10n+a), its text 0006-…-(10000+10n+a).
"""
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.normpath(os.path.join(HERE, "..", ".."))
SOURCE = os.path.join(REPO, "docs", "content-import", "quiz", "foodsaver.de.yaml")
LEARNING = os.path.join(REPO, "docs", "content-import", "learning")
CHANGES = os.path.join(REPO, "server", "src", "main", "resources", "db", "changelog", "changes")
OUTPUT = "197-quiz-foodsaver-pool.yaml"
STAMP = "2026-11-10T00:00:00"
QUIZ_ID = "00000013-0001-4000-8000-000000000001"
# the seed's FOODSAVER quiz (quiz.csv / quiz_i18n.csv): same ids, so seeded databases keep one row per language
QUIZ_TEXTS = [
    ("00000013-0002-4000-8000-000000000001", "de", "Foodsaver-Quiz",
     "Teste dein Wissen über die Regeln beim Lebensmittelretten."),
    ("00000013-0002-4000-8000-000000000002", "en", "Foodsaver quiz", "Test your knowledge of the rules for saving food."),
    ("00000013-0002-4000-8000-000000000003", "gws", "Foodsaver-Quiz",
     "Teschte dis Wüsse über d Regle bim Läbensmittel rette."),
    ("00000013-0002-4000-8000-000000000004", "fr", "Quiz foodsaver",
     "Teste tes connaissances sur les règles du sauvetage alimentaire."),
    ("00000013-0002-4000-8000-000000000005", "it", "Quiz foodsaver",
     "Metti alla prova le tue conoscenze sulle regole per salvare il cibo."),
]
RATINGS = {"CORRECT", "WRONG", "NEUTRAL"}
QUESTION_KEYS = {"n", "chapter", "anchor", "mandatory", "failurePoints", "durationSeconds", "text", "answers"}
ANSWER_KEYS = {"rating", "text", "explanation"}


class PoolError(Exception):
    pass


def question_id(n):
    return "00000013-0003-4000-8000-%012d" % (1000 + n)


def question_text_id(n):
    return "00000013-0004-4000-8000-%012d" % (1000 + n)


def answer_id(n, a):
    return "00000013-0005-4000-8000-%012d" % (10000 + 10 * n + a)


def answer_text_id(n, a):
    return "00000013-0006-4000-8000-%012d" % (10000 + 10 * n + a)


def scalar(raw, where):
    raw = raw.strip()
    if raw.startswith('"'):
        try:
            return json.loads(raw)
        except json.JSONDecodeError as error:
            raise PoolError("%s: bad string %s (%s)" % (where, raw, error))
    if raw in ("true", "false"):
        return raw == "true"
    if re.fullmatch(r"-?\d+", raw):
        return int(raw)
    if re.fullmatch(r"[A-Z_]+", raw):
        return raw
    raise PoolError("%s: unquoted value %s" % (where, raw))


def parse(text):
    """The strict YAML subset of foodsaver.de.yaml: questions: / - key: value / answers: / - key: value."""
    questions, question, answer = [], None, None
    for number, line in enumerate(text.split("\n"), 1):
        where = "foodsaver.de.yaml:%d" % number
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        if line == "questions:":
            continue
        match = re.fullmatch(r"( *)(- )?([A-Za-z]+):(.*)", line)
        if not match:
            raise PoolError("%s: cannot read %r" % (where, line))
        indent, dash, key, value = len(match.group(1)), match.group(2), match.group(3), match.group(4)
        if indent == 2 and dash:
            question, answer = {}, None
            questions.append(question)
        elif indent == 6 and dash:
            if question is None or "answers" not in question:
                raise PoolError("%s: answer outside answers:" % where)
            answer = {}
            question["answers"].append(answer)
        elif not ((indent == 4 and not dash) or (indent == 8 and not dash)):
            raise PoolError("%s: unexpected indentation" % where)
        target = answer if indent >= 6 else question
        if target is None:
            raise PoolError("%s: value outside a question" % where)
        if indent >= 6 and answer is None:
            raise PoolError("%s: answer value without '- '" % where)
        if key == "answers" and indent == 4:
            question["answers"] = []
            continue
        if key in target:
            raise PoolError("%s: duplicate key %s" % (where, key))
        target[key] = scalar(value, where)
    return questions


def anchors(chapter):
    path = os.path.join(LEARNING, chapter + ".de.md")
    if not os.path.exists(path):
        raise PoolError("unknown chapter %s (no %s)" % (chapter, path))
    with open(path, encoding="utf-8") as f:
        return set(re.findall(r'<a id="([^"]+)"></a>', f.read()))


def validate(questions):
    seen = set()
    for question in questions:
        where = "question %s" % question.get("n")
        missing = QUESTION_KEYS - set(question) - {"anchor"}
        if missing or set(question) - QUESTION_KEYS:
            raise PoolError("%s: keys %s" % (where, sorted(set(question) ^ QUESTION_KEYS)))
        n = question["n"]
        if not isinstance(n, int) or n < 1 or n > 899 or n in seen:
            raise PoolError("%s: n must be a unique number 1-899" % where)
        seen.add(n)
        if question.get("anchor") and question["anchor"] not in anchors(question["chapter"]):
            raise PoolError("%s: anchor %s not in %s" % (where, question["anchor"], question["chapter"]))
        anchors(question["chapter"])
        if question["failurePoints"] not in (1, 2, 3) or not isinstance(question["mandatory"], bool):
            raise PoolError("%s: failurePoints 1-3 and mandatory true/false" % where)
        if not 10 <= question["durationSeconds"] <= 300:
            raise PoolError("%s: durationSeconds 10-300" % where)
        if not question["text"].strip() or len(question["text"]) > 4000:
            raise PoolError("%s: text" % where)
        answers = question["answers"]
        if not 2 <= len(answers) <= 9:
            raise PoolError("%s: 2-9 answers" % where)
        for answer in answers:
            if set(answer) != ANSWER_KEYS or answer["rating"] not in RATINGS or not answer["text"].strip():
                raise PoolError("%s: answer needs rating (CORRECT/WRONG/NEUTRAL), text and explanation" % where)
            if len(answer["text"]) > 2000 or len(answer["explanation"]) > 4000:
                raise PoolError("%s: answer too long" % where)
        if not any(answer["rating"] == "CORRECT" for answer in answers):
            raise PoolError("%s: no CORRECT answer" % where)
    return questions


def yaml_str(value):
    return json.dumps(value, ensure_ascii=False)


def column(name, value, kind="value"):
    return "              - column: {name: %s, %s: %s}" % (name, kind, value)


def insert(table, columns):
    return "\n".join(["        - insert:", "            tableName: " + table, "            columns:"] + columns)


def base(row_id):
    return [column("id", yaml_str(row_id)), column("version", 0, "valueNumeric"),
            column("created_at", yaml_str(STAMP), "valueDate"), column("updated_at", yaml_str(STAMP), "valueDate")]


QUIZ_LOOKUP = "(SELECT id FROM quiz WHERE kind = 'FOODSAVER')"


def quiz_change_set():
    changes = [insert("quiz", base(QUIZ_ID) + [
        column("kind", yaml_str("FOODSAVER")), column("max_failure_points", 3, "valueNumeric"),
        column("question_count_timed", 8, "valueNumeric"), column("question_count_untimed", 10, "valueNumeric")])]
    for text_id, language, name, description in QUIZ_TEXTS:
        changes.append(insert("quiz_i18n", base(text_id) + [
            column("owner_id", yaml_str(QUIZ_ID)), column("language", yaml_str(language)),
            column("name", yaml_str(name)), column("description", yaml_str(description))]))
    return "\n".join([
        "  - changeSet:",
        "      id: 197-0",
        "      author: platform",
        "      comment: 'C1: the FOODSAVER quiz where none exists (seed id and texts; production had none).'",
        "      preConditions:",
        "        - onFail: MARK_RAN",
        "        - sqlCheck:",
        "            expectedResult: 0",
        "            sql: \"SELECT COUNT(*) FROM quiz WHERE kind = 'FOODSAVER'\"",
        "      changes:"] + changes)


def question_change_set(question):
    n = question["n"]
    link = "/learn/" + question["chapter"] + ("#" + question["anchor"] if question.get("anchor") else "")
    changes = [
        insert("quiz_question", base(question_id(n)) + [
            column("quiz_id", QUIZ_LOOKUP, "valueComputed"),
            column("duration_seconds", question["durationSeconds"], "valueNumeric"),
            column("wiki_link", yaml_str(link)),
            column("mandatory", "true" if question["mandatory"] else "false", "valueBoolean"),
            column("failure_points", question["failurePoints"], "valueNumeric")]),
        insert("quiz_question_i18n", base(question_text_id(n)) + [
            column("owner_id", yaml_str(question_id(n))), column("language", yaml_str("de")),
            column("text", yaml_str(question["text"].strip()))]),
    ]
    for index, answer in enumerate(question["answers"], 1):
        changes.append(insert("quiz_answer", base(answer_id(n, index)) + [
            column("question_id", yaml_str(question_id(n))), column("rating", yaml_str(answer["rating"]))]))
        texts = [column("owner_id", yaml_str(answer_id(n, index))), column("language", yaml_str("de")),
                 column("text", yaml_str(answer["text"].strip()))]
        if answer["explanation"].strip():
            texts.append(column("explanation", yaml_str(answer["explanation"].strip())))
        changes.append(insert("quiz_answer_i18n", base(answer_text_id(n, index)) + texts))
    return "\n".join([
        "  - changeSet:",
        "      id: 197-%d" % n,
        "      author: platform",
        "      comment: %s" % yaml_str("C1: foodsaver quiz question %d (%s)." % (n, question["chapter"])),
        "      preConditions:",
        "        - onFail: MARK_RAN",
        "        - sqlCheck:",
        "            expectedResult: 0",
        "            sql: %s" % yaml_str("SELECT COUNT(*) FROM quiz_question WHERE id = '%s'" % question_id(n)),
        "      changes:"] + changes)


def render(questions):
    header = ("# GENERATED by server/scripts/gen-quiz-pool.py from docs/content-import/quiz/foodsaver.de.yaml - do not edit.\n"
              "# C1 (owner decision 2026-11-10): the foodsaver quiz pool, production data (not seed), German only.\n"
              "# Each question is inserted only when its fixed id is free, so quiz-editor edits stay.\n")
    return header + "databaseChangeLog:\n" + "\n".join([quiz_change_set()] + [
        question_change_set(question) for question in sorted(questions, key=lambda q: q["n"])]) + "\n"


def main(argv):
    with open(SOURCE, encoding="utf-8") as f:
        questions = validate(parse(f.read()))
    content = render(questions)
    path = os.path.join(CHANGES, OUTPUT)
    if os.path.exists(path) and "--force" not in argv:
        with open(path, encoding="utf-8") as f:
            if f.read() != content:
                raise PoolError("%s exists with different content and may already be applied; add new questions with "
                                "new numbers in a new changeset, or pass --force if it was never released" % OUTPUT)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    per_chapter = {}
    for question in questions:
        per_chapter[question["chapter"]] = per_chapter.get(question["chapter"], 0) + 1
    print("wrote %d questions (%d mandatory): %s" % (len(questions), sum(q["mandatory"] for q in questions),
                                                     ", ".join("%s %d" % item for item in sorted(per_chapter.items()))))
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main(sys.argv[1:]))
    except PoolError as error:
        print("error: %s" % error, file=sys.stderr)
        sys.exit(1)
