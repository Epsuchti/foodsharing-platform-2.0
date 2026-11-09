#!/usr/bin/env python3
"""Tests of gen-learning-chapters.py with tiny fixtures of our own: python3 -m unittest server/scripts/test_gen_learning_chapters.py"""
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

    def test_minutes_and_ids(self):
        self.assertEqual(gen.minutes("wort " * 401), 3)
        self.assertEqual(gen.page_id(1), "00000012-0001-4000-8000-000000000101")
        self.assertEqual(gen.text_id(7, "it"), "00000012-0002-4000-8000-000000001075")


if __name__ == "__main__":
    unittest.main()
