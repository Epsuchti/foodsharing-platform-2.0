#!/usr/bin/env python3
"""Tests of gen-learning-chapters.py with tiny fixtures of our own: python3 -m unittest discover -s server/scripts -p 'test_*.py'"""
import importlib.util
import os
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
SPEC = importlib.util.spec_from_file_location("gen_learning_chapters", os.path.join(HERE, "gen-learning-chapters.py"))
gen = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(gen)

TITLES = {"Grundsätze": "principles", "Verhaltensregeln": "code-of-conduct"}


def convert(source, key="principles"):
    converter = gen.Converter(TITLES)
    number, body = converter.convert("Fixture", key, source)
    return converter, number, body


class ConverterTest(unittest.TestCase):

    def test_header_line_toc_and_pdf_sentences_are_dropped(self):
        _, number, body = convert("{{Wiki-Artikel-Typ|Typ=1 (Regel-Artikel)}}\n"
                                  "<b><font size = 3>[[foodsharing-Grundlagen|foodsharing-Grundlagen]] 2</font></b><br>\n"
                                  "Gültig ab 02.01.2020<br>\n\n<pdfexcludestart />\n"
                                  "Als {{PDFLink|page=X|label=PDF}} zum Download.\n<pdfexcludeend />\n__TOC__\n"
                                  "Text [[File:Bild.png|30px]] bleibt.\n")
        self.assertEqual(number, 2)
        self.assertEqual(body, "*Gültig ab 02.01.2020*\n\nText bleibt.\n")

    def test_headings_get_anchors_and_font_lines_become_h3(self):
        _, _, body = convert("==A) Allgemeines Verhalten==\n<font size=4>'''Präambel'''</font>\n===Das bedeutet:===\n"
                             "===Das bedeutet:===\n")
        self.assertIn('## <a id="a-allgemeines-verhalten"></a>A) Allgemeines Verhalten', body)
        self.assertIn('### <a id="praeambel"></a>Präambel', body)
        self.assertIn('### <a id="das-bedeutet-2"></a>Das bedeutet:', body)

    def test_emphasis_underline_and_literal_asterisks(self):
        _, _, body = convert("'''fett''' ''kursiv'' '''''beides''''' <u>unter</u> '''<u>Vertraulichkeit</u>:''' "
                             "''<u>Kühlwaren</u>'' Spender*innen\n")
        self.assertEqual(body, "**fett** *kursiv* ***beides*** *unter* **Vertraulichkeit:** *Kühlwaren* "
                               "Spender\\*innen\n")

    def test_lists_and_continuations(self):
        _, _, body = convert("# eins\n# zwei\n\n:a) erster Punkt\n::weiter\n::* unten\n::** ganz unten\n\n"
                             ":b) zweiter\n")
        self.assertEqual(body, "1. eins\n2. zwei\n\n- **a)** erster Punkt\n\n  weiter\n  - unten\n    - ganz unten\n"
                               "- **b)** zweiter\n")

    def test_layout_table_becomes_a_nested_list(self):
        _, _, body = convert('{|\n|- style="vertical-align:top;"\n| style="" |A1)\n| style="" |Freundlich.<br /> Immer.\n'
                             "|- style=\"vertical-align:top;\"\n|b)\n|Suspendierung\n  |-\n  |\n  |i) bei Gefahr\n"
                             "  |-\n  |\n  |Nach Ablauf.\n|}\n")
        self.assertEqual(body, "- **A1)** Freundlich.\\\n  Immer.\n- **b)** Suspendierung\n  - **i)** bei Gefahr\n\n"
                               "  Nach Ablauf.\n")

    def test_links_resolve_to_chapters_or_the_wiki(self):
        converter, _, body = convert("[[Verhaltensregeln#A.29_Allgemeines_Verhalten|Regeln A)]] "
                                     "[[Verhaltensregeln_-_Erl%C3%A4uterungen#Regel_A1.29|Erläuterung]] "
                                     "[[Grundsätze]] [https://foodsharing.de Website] [mailto:a@b.ch Kontakt]\n")
        self.assertEqual(body, "[Regeln A)](/learn/code-of-conduct#a-allgemeines-verhalten) "
                               "[Erläuterung](https://wiki.foodsharing.network/wiki/Verhaltensregeln_-_Erl%C3%A4uterungen"
                               "#Regel_A1.29) [Grundsätze](/learn/principles) [Website](https://foodsharing.de) "
                               "[Kontakt](mailto:a@b.ch)\n")
        self.assertIn(("principles", "code-of-conduct", "A) Allgemeines Verhalten"), converter.links)

    def test_paragraphs_starting_like_lists_are_escaped(self):
        _, _, body = convert("1) Saubere Hände.\n")
        self.assertEqual(body, "1\\) Saubere Hände.\n")

    def test_redirects_are_skipped(self):
        with self.assertRaises(gen.Redirect) as raised:
            convert("#WEITERLEITUNG [[Verhaltensregeln:Regelverletzungen - Erläuterungen]]")
        self.assertIn("redirect", str(raised.exception))

    def test_unknown_markup_fails_with_the_line(self):
        with self.assertRaises(gen.ConversionError) as raised:
            convert("gut\n<span>schlecht</span>\n")
        self.assertIn("Fixture.wiki:2", str(raised.exception))
        with self.assertRaises(gen.ConversionError):
            convert("{{Infobox|x}}\n")
        with self.assertRaises(gen.ConversionError):
            convert("{|\n! Kopf\n|}\n")

    def test_convert_all_checks_links_and_size(self):
        with tempfile.TemporaryDirectory() as wiki:
            for name, *_ in gen.CHAPTERS:
                with open(os.path.join(wiki, name.replace(" ", "_") + ".wiki"), "w", encoding="utf-8") as f:
                    if name == "Regelverletzungen - Erläuterungen":
                        f.write("#WEITERLEITUNG [[Somewhere]]")
                    else:
                        f.write("==Abschnitt==\nText [[Grundsätze#Abschnitt|hier]] und "
                                "[[Regelverletzungen - Erläuterungen#x|dort]] und [[Grundsätze#Fehlt|weg]].\n")
            converted, skipped, warnings = gen.convert_all(wiki)
        self.assertEqual(len(converted), 7)
        self.assertEqual(len(skipped), 1)
        body = converted["code-of-conduct"][2]
        self.assertIn("[hier](/learn/principles#abschnitt)", body)
        self.assertIn("[dort](https://wiki.foodsharing.network/wiki/Regelverletzungen_-_Erl%C3%A4uterungen#x)", body)
        self.assertIn("[weg](/learn/principles)", body)
        self.assertTrue(any("Fehlt" in w for w in warnings))
        self.assertIn("*Original im foodsharing-Wiki: [Verhaltensregeln]"
                      "(https://wiki.foodsharing.network/wiki/Verhaltensregeln)*", body)

    def test_summaries_must_be_plain_text(self):
        self.assertEqual(gen.summary_problems("Wofür foodsharing steht."), [])
        for broken in ('<a id="x"></a>Text', "Siehe [Wiki](https://x)", "Änderungen(https://drive.google.com/x)",
                       "Gültig ab 2020\\", "**fett**", ""):
            self.assertTrue(gen.summary_problems(broken), broken)
        with tempfile.TemporaryDirectory() as folder:
            path = os.path.join(folder, "summaries.json")
            with open(path, "w", encoding="utf-8") as f:
                f.write('{"principles": {"de": "a", "en": "b", "gws": "c", "fr": "d", "it": "Siehe [x](y)"}}')
            with self.assertRaises(gen.ConversionError) as raised:
                gen.read_summaries(["principles"], path)
            self.assertIn("principles/it", str(raised.exception))
            with self.assertRaises(gen.ConversionError):
                gen.read_summaries(["code-of-conduct"], path)

    def test_the_summaries_file_covers_every_chapter(self):
        keys = [key for name, key, *_ in gen.CHAPTERS if key != "rule-violations-explained"]
        summaries = gen.read_summaries(keys)
        self.assertTrue(all(len(summaries[key]) == 5 for key in keys))

    def test_bodies_without_visible_markup(self):
        good = ('## <a id="a-b"></a>A) B\n\nText mit [Link](/learn/x#a-b) und\\\nUmbruch, Spender\\*in, '
                '[mail](mailto:a@b.ch), 1\\) Punkt.\n')
        self.assertEqual(gen.body_problems(good), [])
        for broken in ('<a id="x"</aText\n', "Text <span>x</span>\n", "Siehe [Link] (https://x)\n",
                       "Änderungen(https://drive.google.com/x)\n", "Gültig ab\\ Zu den neuen\n",
                       "Ende\\\n\nNeuer Absatz\n"):
            self.assertTrue(gen.body_problems(broken), broken)

    def test_generated_chapters_have_no_visible_markup(self):
        folder = os.path.join(gen.OUT_DIR)
        for name in sorted(os.listdir(folder)):
            if name.endswith(".md") and name != "README.md":
                _, body = gen.read_markdown(os.path.join(folder, name))
                self.assertEqual(gen.body_problems(body), [], name)

    def test_minutes_and_ids(self):
        self.assertEqual(gen.minutes("wort " * 401), 3)
        self.assertEqual(gen.page_id(1), "00000012-0001-4000-8000-000000000101")
        self.assertEqual(gen.text_id(7, "it"), "00000012-0002-4000-8000-000000001075")


if __name__ == "__main__":
    unittest.main()
