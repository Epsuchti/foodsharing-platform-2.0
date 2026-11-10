#!/usr/bin/env python3
"""Tests of gen-quiz-pool.py: python3 -m unittest discover -s server/scripts -p 'test_*.py'"""
import importlib.util
import os
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
SPEC = importlib.util.spec_from_file_location("gen_quiz_pool", os.path.join(HERE, "gen-quiz-pool.py"))
gen = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(gen)

GOOD = """questions:
  - n: 3
    chapter: "principles"
    anchor: "das-bedeutet"
    mandatory: true
    failurePoints: 2
    durationSeconds: 60
    text: "Frage?"
    answers:
      - rating: CORRECT
        text: "Ja"
        explanation: "Weil."
      - rating: WRONG
        text: "Nein"
        explanation: ""
"""


class QuizPoolTest(unittest.TestCase):

    def test_parses_and_renders_insert_if_missing_changesets(self):
        questions = gen.validate(gen.parse(GOOD))
        self.assertEqual(questions[0]["answers"][0], {"rating": "CORRECT", "text": "Ja", "explanation": "Weil."})
        out = gen.render(questions)
        self.assertIn("id: 197-0", out)
        self.assertIn("id: 197-3", out)
        self.assertIn("SELECT COUNT(*) FROM quiz_question WHERE id = '00000013-0003-4000-8000-000000001003'", out)
        self.assertIn("valueComputed: (SELECT id FROM quiz WHERE kind = 'FOODSAVER')", out)
        self.assertIn('value: "/learn/principles#das-bedeutet"', out)
        self.assertEqual(out.count("tableName: quiz_answer_i18n"), 2)
        self.assertEqual(out.count("name: explanation"), 1)

    def test_rejects_broken_questions(self):
        for broken in (GOOD.replace("rating: CORRECT", "rating: WRONG"),
                       GOOD.replace('anchor: "das-bedeutet"', 'anchor: "nope"'),
                       GOOD.replace("failurePoints: 2", "failurePoints: 7"),
                       GOOD.replace('chapter: "principles"', 'chapter: "unknown"'),
                       GOOD + GOOD.replace("questions:\n", "")):
            with self.assertRaises(gen.PoolError):
                gen.validate(gen.parse(broken))
        with self.assertRaises(gen.PoolError):
            gen.parse(GOOD.replace('text: "Frage?"', "text: Frage?"))

    def test_ids(self):
        self.assertEqual(gen.answer_id(64, 3), "00000013-0005-4000-8000-000000010643")
        self.assertEqual(gen.question_text_id(1), "00000013-0004-4000-8000-000000001001")

    def test_the_pool_is_valid(self):
        with open(gen.SOURCE, encoding="utf-8") as f:
            questions = gen.validate(gen.parse(f.read()))
        self.assertGreaterEqual(len(questions), 60)
        self.assertTrue(6 <= sum(q["mandatory"] for q in questions) <= 10)
        per_chapter = {}
        for question in questions:
            per_chapter[question["chapter"]] = per_chapter.get(question["chapter"], 0) + 1
        self.assertEqual(len(per_chapter), 7)
        self.assertTrue(all(count >= 8 for count in per_chapter.values()))


if __name__ == "__main__":
    unittest.main()
