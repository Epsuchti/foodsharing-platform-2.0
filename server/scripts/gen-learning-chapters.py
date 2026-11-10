#!/usr/bin/env python3
"""Learning wiki (C1, docs/tasks/2026-11-10-learning-wiki.md): foodsharing-Grundlagen from the owner's wiki texts.

Reads docs/content-import/wiki/*.wiki (MediaWiki markup, the owner's own texts) and writes
  - docs/content-import/learning/<key>.de.md          the converted German chapter ("# Title", blank line, body)
  - changes/195-content-learning-basics.yaml           non-seed changeset: chapters in five languages
  - changes/196-legal-agreement-wiki.yaml              non-seed changeset: the Rechtsvereinbarung as LEGAL_AGREEMENT
The translations docs/content-import/learning/<key>.<en|fr|it|gws>.md are written by hand from the German file;
the converter checks that they keep the anchors and links of the German text.

Every changeset inserts only when its fixed ids are missing (precondition, MARK_RAN), so CMS edits are never
overwritten. Applied changesets must not change: a newer wiki version needs a new changeset file (see README.md in
docs/content-import/learning/). Re-running gives identical files.

  python3 server/scripts/gen-learning-chapters.py            convert, check translations, write everything
  python3 server/scripts/gen-learning-chapters.py --de-only  only (re)write the German Markdown files
  ... --force   overwrite changesets with different content (only before they were ever released)
"""
import json
import math
import os
import re
import sys
import unicodedata
import urllib.parse

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.normpath(os.path.join(HERE, "..", ".."))
WIKI_DIR = os.path.join(REPO, "docs", "content-import", "wiki")
OUT_DIR = os.path.join(REPO, "docs", "content-import", "learning")
CHANGES = os.path.join(REPO, "server", "src", "main", "resources", "db", "changelog", "changes")
CHAPTERS_FILE = "195-content-learning-basics.yaml"
LEGAL_FILE = "196-legal-agreement-wiki.yaml"

LANGS = ["de", "en", "gws", "fr", "it"]
WIKI_BASE = "https://wiki.foodsharing.network/wiki/"
STAMP = "2026-11-10T00:00:00"
MAX_BODY = 32600
WORDS_PER_MINUTE = 200

# wiki file (without .wiki) -> (key, group, required, number); the sort order is the "foodsharing-Grundlagen N" number
# times ten. Rechtsvereinbarung: owner decision 2026-11-10 (required BASICS chapter and the binding legal agreement).
CHAPTERS = [
    ("Grundsätze", "principles", "BASICS", True, 1),
    ("Verhaltensregeln", "code-of-conduct", "BASICS", True, 2),
    ("Regelverletzungen - Konsequenzen und Bearbeitung", "rule-violations", "BASICS", True, 3),
    ("Regelverletzungen - Erläuterungen", "rule-violations-explained", "MORE", False, 8),
    ("Hygieneregeln", "hygiene-rules", "BASICS", True, 4),
    ("Voraussetzungen für Rollen in foodsharing", "role-requirements", "BASICS", True, 5),
    ("Rechtsvereinbarung", "legal-agreement", "BASICS", True, 6),
    ("Kontext und Selbstverständnis", "context-and-self-image", "BASICS", True, 7),
]
TITLES = {
    "Regelverletzungen - Konsequenzen und Bearbeitung": "Regelverletzungen – Konsequenzen und Bearbeitung",
    "Regelverletzungen - Erläuterungen": "Regelverletzungen – Erläuterungen",
}
SORT_OVERRIDE = {"rule-violations-explained": 35}
LEGAL_KEY = "legal-agreement"
LEGAL_VERSION = "2026-11"
LEGAL_PUBLISHED = "2026-11-01T00:00:00"
LEGAL_SUMMARY = {
    "de": "Verhaltenskodex, Sorgfaltspflichten und Haftungsausschluss für Foodsaver:innen und Betriebsverantwortliche.",
    "en": "Code of conduct, duties of care and disclaimer of liability for foodsavers and store managers.",
    "gws": "Verhaltenskodex, Sorgfaltspflichte und Haftigsusschluss für Foodsaver:innen und Betriebsverantwortlichi.",
    "fr": "Code de conduite, devoirs de diligence et exclusion de responsabilité pour foodsavers et responsables de"
          " commerce.",
    "it": "Codice di condotta, obblighi di diligenza ed esclusione di responsabilità per foodsaver e responsabili dei"
          " negozi.",
}
ORIGINAL = {"de": "Original im foodsharing-Wiki", "en": "Original in the foodsharing wiki",
            "gws": "Original im foodsharing-Wiki", "fr": "Original dans le wiki foodsharing",
            "it": "Originale nel wiki di foodsharing"}


def page_id(number):
    return "00000012-0001-4000-8000-%012d" % (100 + number)


def text_id(number, lang):
    return "00000012-0002-4000-8000-%012d" % (1000 + number * 10 + LANGS.index(lang) + 1)


LEGAL_VERSION_ID = "00000012-0003-4000-8000-000000000101"


def legal_text_id(lang):
    return "00000012-0004-4000-8000-%012d" % (100 + LANGS.index(lang) + 1)


class ConversionError(Exception):
    pass


# ------------------------------------------------------------------------------------------------ helpers

ALLOWED_TAGS = {"b", "font", "u", "br", "pdfexcludestart", "pdfexcludeend"}
ALLOWED_TEMPLATES = {"Wiki-Artikel-Typ", "PDFLink"}
LABEL = re.compile(r"^(?:[A-Z]\d{1,2}|[a-z]|\d{1,2}|[ivx]{1,4})\)$")
ROMAN_ITEM = re.compile(r"^((?:i|ii|iii|iv|v|vi|vii|viii|ix|x)\))\s*(.*)$", re.S)
LEADING_LABEL = re.compile(r"^((?:[A-Z]\d{1,2}|[a-z]|\d{1,2}|[ivx]{1,4})\))\s+(.*)$", re.S)
BR = "\x00"


def slug(text):
    text = re.sub(r"<[^>]+>", "", text)
    text = re.sub(r"\[([^\]]*)\]\([^)]*\)", r"\1", text)
    text = text.replace("\\", "")
    for a, b in (("ä", "ae"), ("ö", "oe"), ("ü", "ue"), ("Ä", "ae"), ("Ö", "oe"), ("Ü", "ue"), ("ß", "ss")):
        text = text.replace(a, b)
    text = unicodedata.normalize("NFKD", text).encode("ascii", "ignore").decode("ascii").lower()
    return re.sub(r"[^a-z0-9]+", "-", text).strip("-") or "abschnitt"


def decode_anchor(anchor):
    """MediaWiki legacy anchors (.C3.A4) and percent escapes (%C3%A4) -> text with spaces."""
    anchor = urllib.parse.unquote(anchor)
    raw = re.sub(r"\.([0-9A-F]{2})", lambda m: "%" + m.group(1), anchor)
    try:
        anchor = urllib.parse.unquote(raw, errors="strict")
    except UnicodeDecodeError:
        pass
    return anchor.replace("_", " ").strip()


def normalise_title(title):
    return decode_anchor(title).replace("_", " ").strip()


def wiki_url(page, anchor=None):
    url = WIKI_BASE + urllib.parse.quote(page.strip().replace(" ", "_"), safe="/:_-.,'!*")
    if anchor:
        url += "#" + urllib.parse.quote(anchor.replace(" ", "_"), safe="_-.:")
    return url


def escape_text(text):
    """Literal Markdown characters of the wiki text (before emphasis turns into Markdown)."""
    return text.replace("\\", "\\\\").replace("*", "\\*").replace("_", "\\_").replace("`", "\\`")


def wrap(marker, inner):
    lead = inner[:len(inner) - len(inner.lstrip())]
    trail = inner[len(inner.rstrip()):]
    core = inner.strip()
    return lead + marker + core + marker + trail if core else inner


# ------------------------------------------------------------------------------------------------ converter

class Converter:
    def __init__(self, title_to_key):
        self.title_to_key = title_to_key
        self.links = []          # (key, target key, anchor text or None) for the resolution check
        self.warnings = []

    def check_markup(self, name, lines):
        for number, line in enumerate(lines, 1):
            for tag in re.findall(r"<\s*/?\s*([A-Za-z][A-Za-z0-9]*)", line):
                if tag.lower() not in ALLOWED_TAGS:
                    raise ConversionError("%s.wiki:%d: unknown markup <%s>" % (name, number, tag))
            for template in re.findall(r"\{\{\s*([^|}]+)", line):
                if template.strip() not in ALLOWED_TEMPLATES:
                    raise ConversionError("%s.wiki:%d: unknown template {{%s}}" % (name, number, template.strip()))
            if re.search(r"^\s*!|\|\|", line) and not line.strip().startswith("{|"):
                raise ConversionError("%s.wiki:%d: unknown table markup (header or inline cells)" % (name, number))
            if re.search(r"<(?:ref|nowiki|pre|code|math|gallery|span|div|small|big|center|s|i)\b", line):
                raise ConversionError("%s.wiki:%d: unknown markup" % (name, number))

    # ---------------------------------------------------------------------------------------- inline

    def link(self, key, target, text):
        page, _, anchor = target.partition("#")
        page_title = normalise_title(page)
        if re.match(r"^(File|Datei|Bild|Image):", page_title):
            return ""
        anchor_text = decode_anchor(anchor) if anchor else None
        target_key = self.title_to_key.get(page_title) if page_title else key
        label = text if text is not None else (page_title if page_title else anchor_text)
        if target_key:
            self.links.append((key, target_key, anchor_text))
            href = "/learn/" + target_key + ("#" + slug(anchor_text) if anchor_text else "")
            return "\x01" + label + "\x02" + href + "\x03"
        href = wiki_url(page.replace("_", " ").strip() and urllib.parse.unquote(page).replace("_", " "),
                        anchor or None)
        return "\x01" + label + "\x02" + href + "\x03"

    def inline(self, key, text):
        text = re.sub(r"\[\[(?:File|Datei|Bild|Image):[^\]]*\]\]", "", text)
        text = text.replace("&nbsp;", " ").replace("\u200b", "")
        text = re.sub(r"<br\s*/?>", BR, text)
        text = re.sub(r"</?pdfexclude(?:start|end)\s*/?>", "", text)
        # protect links first (their URLs must not be escaped)
        text = re.sub(r"\[\[([^\]|]+)(?:\|([^\]]*))?\]\]",
                      lambda m: self.link(key, m.group(1), m.group(2)), text)
        text = re.sub(r"\[((?:https?://|mailto:)[^\s\]]+)(?:\s+([^\]]*))?\]",
                      lambda m: "\x01" + (m.group(2) or m.group(1)).strip() + "\x02" + m.group(1) + "\x03", text)
        # links: label text is Markdown-escaped, the URL is kept apart
        parts = re.split(r"(\x01[^\x02]*\x02[^\x03]*\x03)", text)
        out = []
        for part in parts:
            if part.startswith("\x01"):
                label, href = part[1:-1].split("\x02")
                out.append("\x01" + escape_text(label) + "\x02" + href.replace(")", "%29").replace("(", "%28")
                           .replace(" ", "%20") + "\x03")
            else:
                out.append(escape_text(part))
        text = "".join(out)
        # underline: dropped next to or around wiki emphasis, else italic
        text = re.sub(r"(?<=')<u>(.*?)</u>", r"\1", text)
        text = re.sub(r"<u>(\s*'.*?)</u>", r"\1", text)
        text = re.sub(r"<u>(.*?)</u>", lambda m: wrap("''", m.group(1)), text)
        text = re.sub(r"<b>(.*?)</b>", r"'''\1'''", text)
        text = re.sub(r"<font[^>]*>(.*?)</font>", r"\1", text)
        text = re.sub(r"'''''(.+?)'''''", lambda m: wrap("***", m.group(1)), text)
        text = re.sub(r"'''(.+?)'''", lambda m: wrap("**", m.group(1)), text)
        text = re.sub(r"''(.+?)''", lambda m: wrap("*", m.group(1)), text)
        if "''" in text:
            self.warnings.append("%s: unbalanced '' removed: %s" % (key, re.sub(r"[\x00-\x03]", " ", text).strip()[:60]))
            text = text.replace("'''", "").replace("''", "")
        text = re.sub(r"\x01([^\x02]*)\x02([^\x03]*)\x03", r"[\1](\2)", text)
        text = re.sub(r"[ \t]+", " ", text)
        return text

    # ---------------------------------------------------------------------------------------- blocks

    def convert(self, name, key, source):
        if re.match(r"\s*#(?:WEITERLEITUNG|REDIRECT)", source, re.I):
            target = re.search(r"\[\[([^\]]+)\]\]", source)
            raise Redirect("%s.wiki is only a redirect to %s; skipped until the owner supplies the text"
                           % (name, target.group(1) if target else "?"))
        lines = source.replace("\r\n", "\n").split("\n")
        self.check_markup(name, lines)
        source = re.sub(r"<!--.*?-->", "", source, flags=re.S)
        source = re.sub(r"\{\{Wiki-Artikel-Typ[^}]*\}\}", "", source)
        number = None
        match = re.search(r"<b>\s*<font[^>]*>\s*\[\[foodsharing-Grundlagen[^\]]*\]\]\s*(\d+)\s*</font>\s*</b>(?:<br\s*/?>)?",
                          source)
        if match:
            number = int(match.group(1))
            source = source[:match.start()] + source[match.end():]
        lines = []
        for line in source.split("\n"):
            if "{{PDFLink" in line or "zum Download]" in line:
                continue
            line = line.replace("__TOC__", "")
            line = re.sub(r"<pdfexclude(?:start|end)\s*/?>", "", line)
            lines.append(line.rstrip())
        blocks = self.blocks(key, lines)
        body = self.render(blocks)
        body = self.first_line_italic(body)
        return number, body

    def blocks(self, key, lines):
        """Wiki lines -> blocks: ("h", level, text), ("p", text), ("list", items), ("hr",)."""
        blocks, para, items = [], [], None
        i = 0

        def flush():
            nonlocal para, items
            if para:
                blocks.append(("p", " ".join(para)))
                para = []
            if items is not None:
                blocks.append(("list", items))
                items = None

        while i < len(lines):
            line = lines[i]
            stripped = line.strip()
            if not stripped:
                # wiki authors indent with ':' lists across blank lines; such a list stays one list
                following = next((rest.strip() for rest in lines[i + 1:] if rest.strip()), "")
                if items and items[-1][0].startswith(":") and following.startswith(":"):
                    i += 1
                    continue
                flush()
                i += 1
                continue
            heading = re.match(r"^(={2,4})\s*(.*?)\s*\1\s*$", stripped)
            if heading:
                flush()
                blocks.append(("h", len(heading.group(1)), self.inline(key, heading.group(2))))
                i += 1
                continue
            font_heading = re.match(r"^<font[^>]*>\s*'''(.*?)'''\s*</font>$", stripped)
            if font_heading:
                flush()
                blocks.append(("h", 3, self.inline(key, font_heading.group(1))))
                i += 1
                continue
            if re.match(r"^-{4,}$", stripped):
                flush()
                blocks.append(("hr",))
                i += 1
                continue
            if stripped.startswith("{|"):
                flush()
                end = i + 1
                while end < len(lines) and not lines[end].strip().startswith("|}"):
                    end += 1
                blocks.append(("list", self.table(key, lines[i + 1:end])))
                i = end + 1
                continue
            prefix = re.match(r"^([#*:]+)\s*(.*)$", stripped)
            if prefix:
                if para:
                    blocks.append(("p", " ".join(para)))
                    para = []
                if items is None:
                    items = []
                items.append((prefix.group(1), self.inline(key, prefix.group(2))))
                i += 1
                continue
            if items is not None:
                flush()
            para.append(self.inline(key, stripped))
            i += 1
        flush()
        return blocks

    def table(self, key, lines):
        """Layout table rows -> list entries (prefix, text) in the same form as wiki list lines."""
        rows, row, cell = [], None, None
        for line in lines:
            stripped = line.strip()
            if stripped.startswith("|-"):
                row = []
                rows.append(row)
                cell = None
                continue
            if stripped.startswith("|"):
                if row is None:
                    row = []
                    rows.append(row)
                content = stripped[1:]
                content = re.sub(r'^\s*(?:[a-z-]+="[^"]*"\s*)+\|', "", content)
                cell = [content.strip()]
                row.append(cell)
                continue
            if cell is None:
                if stripped:
                    raise ConversionError("%s: text outside a table cell: %s" % (key, stripped[:60]))
                continue
            cell.append(stripped)
        items = []
        for row in rows:
            cells = []
            for cell in row:
                # blank lines inside a cell separate paragraphs
                text = "\n".join(cell).strip()
                paragraphs = [" ".join(p.split("\n")) for p in re.split(r"\n\s*\n", text)]
                cells.append(paragraphs)
            texts = [c for c in cells if any(p.strip() for p in c)]
            if not texts:
                continue
            first = " ".join(texts[0]).replace("&nbsp;", " ").strip()
            if LABEL.match(first) and len(texts) > 1:
                paragraphs = [p for c in texts[1:] for p in c]
                items.append(("*", "**%s** %s" % (first, self.inline(key, paragraphs[0]))))
                for extra in paragraphs[1:]:
                    items.append(("**:", self.inline(key, extra)))
                continue
            paragraphs = [p for c in texts for p in c]
            first_text = paragraphs[0].strip()
            roman = ROMAN_ITEM.match(first_text)
            if roman:
                items.append(("**", "**%s** %s" % (roman.group(1), self.inline(key, roman.group(2)))))
            elif first_text.startswith("●"):
                items.append(("**", self.inline(key, first_text[1:].strip())))
            else:
                items.append(("*:", self.inline(key, first_text)))
            for extra in paragraphs[1:]:
                items.append(("*:", self.inline(key, extra)))
        return items

    # ---------------------------------------------------------------------------------------- Markdown

    def render(self, blocks):
        out, anchors = [], {}
        for block in blocks:
            if block[0] == "h":
                text = block[2].replace(BR, " ").strip()
                anchor = slug(text)
                if anchor in anchors:
                    anchors[anchor] += 1
                    anchor = "%s-%d" % (anchor, anchors[anchor])
                else:
                    anchors[anchor] = 1
                out.append("%s <a id=\"%s\"></a>%s" % ("#" * block[1], anchor, text))
            elif block[0] == "hr":
                out.append("---")
            elif block[0] == "p":
                out.append(self.paragraph(block[1], ""))
            else:
                out.append(self.list(block[1]))
        return "\n\n".join(part for part in out if part.strip()) + "\n"

    @staticmethod
    def breaks(text, indent):
        parts = [p.strip() for p in text.split(BR)]
        while parts and not parts[-1]:
            parts.pop()
        parts = [p for p in parts if p] or [""]
        return ("\\\n" + indent).join(parts)

    def paragraph(self, text, indent):
        text = text.strip()
        text = re.sub(r"^(\d+)([.)])(\s)", r"\1\\\2\3", text)
        text = re.sub(r"^([#>+-])(\s)", r"\\\1\2", text)
        return indent + self.breaks(text, indent)

    def list(self, items):
        """(prefix, text) entries -> nested Markdown list. A prefix ending in ':' continues the innermost open item
        one level up (MediaWiki '*:' / '::'), or a bulleted item of the same level; else it starts a labelled item."""
        lines, stack = [], []          # stack entries: (prefix length, content column, bulleted, number)
        for prefix, text in items:
            length, kind = len(prefix), prefix[-1]
            if kind == ":":
                parents = [i for i, entry in enumerate(stack) if entry[0] <= length - 1]
                if parents or (stack and stack[-1][0] == length and stack[-1][2]):
                    del stack[(parents[-1] if parents else len(stack) - 1) + 1:]
                    lines.append("")
                    lines.append(self.paragraph(text, " " * stack[-1][1]))
                    continue
            previous = None
            while stack and stack[-1][0] >= length:
                previous = stack.pop()
            indent = stack[-1][1] if stack else 0
            number = previous[3] + 1 if kind == "#" and previous and previous[0] == length and previous[3] else 1
            marker = "%d. " % number if kind == "#" else "- "
            if kind == ":" and not text.startswith("**"):
                label = LEADING_LABEL.match(text)
                if label:
                    text = "**%s** %s" % (label.group(1), label.group(2))
            column = indent + len(marker)
            lines.append(" " * indent + marker + self.breaks(text.strip(), " " * column))
            stack.append((length, column, kind in "*#", number if kind == "#" else 0))
        return "\n".join(lines)

    @staticmethod
    def first_line_italic(body):
        """'Gültig ab …' / 'Stand …' at the top stays as an italic first line."""
        parts = body.split("\n\n")
        for index, part in enumerate(parts[:3]):
            first, _, rest = part.partition("\\\n")
            plain = first.replace("**", "").replace("*", "").strip()
            if re.match(r"^(Gültig ab|Stand)\b", plain):
                parts[index] = "*" + re.sub(r"\s+", " ", plain) + "*" + ("\\\n" + rest if rest else "")
                break
        return "\n\n".join(parts)


class Redirect(Exception):
    pass


# ------------------------------------------------------------------------------------------------ files

def read_markdown(path):
    with open(path, encoding="utf-8") as f:
        content = f.read()
    match = re.match(r"# (.+)\n\n", content)
    if not match:
        raise ConversionError("%s: must start with '# Title' and a blank line" % path)
    return match.group(1).strip(), content[match.end():]


def write_markdown(path, title, body):
    with open(path, "w", encoding="utf-8") as f:
        f.write("# " + title + "\n\n" + body)


def anchors_of(body):
    return sorted(re.findall(r'<a id="([^"]+)"></a>', body))


def links_of(body):
    return sorted(re.findall(r"\]\(([^)\s]+)\)", body))


def minutes(body):
    words = len(re.findall(r"\w+", re.sub(r"\([^)]*\)", "", body)))
    return max(1, math.ceil(words / WORDS_PER_MINUTE))


def yaml_str(value):
    return '"' + value.replace("\\", "\\\\").replace('"', '\\"').replace("\n", "\\n") + '"'


def column(name, value, kind="value"):
    return "              - column: {name: %s, %s: %s}" % (name, kind, value)


def insert(table, columns):
    return "\n".join(["        - insert:", "            tableName: " + table, "            columns:"] + columns)


def base_columns(row_id):
    return [column("id", yaml_str(row_id)), column("version", 0, "valueNumeric"),
            column("created_at", yaml_str(STAMP), "valueDate"), column("updated_at", yaml_str(STAMP), "valueDate")]


SUMMARIES_FILE = os.path.join(OUT_DIR, "summaries.json")
MAX_SUMMARY = 300


def summary_problems(text):
    """Markup that must not appear in a hand-written summary (it is shown as plain text)."""
    problems = []
    for pattern, label in ((r"<", "'<'"), (r"\]\(", "'](' (link)"), (r"\(https?:", "'(http' (raw link)"),
                           (r"\\\s*$", "trailing backslash"), (r"[*_`#]", "Markdown marker")):
        if re.search(pattern, text):
            problems.append(label)
    if not text.strip() or len(text) > MAX_SUMMARY:
        problems.append("empty or longer than %d characters" % MAX_SUMMARY)
    return problems


def read_summaries(keys, path=SUMMARIES_FILE):
    """summaries.json: {key: {language: one sentence}} for every chapter and language, plain text only."""
    with open(path, encoding="utf-8") as f:
        summaries = json.load(f)
    for key in keys:
        for lang in LANGS:
            text = summaries.get(key, {}).get(lang)
            if text is None:
                raise ConversionError("%s: no summary for %s/%s" % (path, key, lang))
            problems = summary_problems(text)
            if problems:
                raise ConversionError("%s: summary %s/%s has %s" % (path, key, lang, ", ".join(problems)))
    return summaries


def body_problems(body):
    """Markup that would show as text once the chapter body is rendered: stray HTML, broken links, raw URLs in
    parentheses and backslashes that are neither a hard line break nor an escape."""
    problems = []
    text = re.sub(r'<a id="[a-z0-9-]+"></a>', "", body)
    if "<" in re.sub(r"\\<", "", text):
        problems.append("HTML other than heading anchors")
    links = re.sub(r"\[[^\]\n]*\]\(<?[^)\s]+>?\)", "", text)
    if "](" in links:
        problems.append("a broken link")
    if re.search(r"\(https?:", links):
        problems.append("a raw (http...) link")
    for line in text.split("\n"):
        stripped = re.sub(r"\\[\\`*_{}\[\]()#+\-.!>|~]", "", line)
        if "\\" in stripped and not stripped.endswith("\\"):
            problems.append("a stray backslash: " + line.strip()[:60])
            break
    if re.search(r"\\\n\s*(\n|$)", text) or text.rstrip().endswith("\\"):
        problems.append("a hard line break at the end of a paragraph")
    return problems


def chapter_change_set(chapter, texts, summaries):
    name, key, group, required, number, sort, body_minutes = chapter
    changes = [insert("content_page", base_columns(page_id(number)) + [
        column("slug", yaml_str(key)), column("kind", yaml_str("LEARNING_CHAPTER")),
        column("sort_order", sort, "valueNumeric"), column("estimated_minutes", body_minutes, "valueNumeric"),
        column("published", "true", "valueBoolean"), column("editor_function", yaml_str("PR")),
        column("chapter_group", yaml_str(group)), column("required", "true" if required else "false", "valueBoolean")])]
    for lang in LANGS:
        title, body = texts[lang]
        changes.append(insert("content_page_i18n", base_columns(text_id(number, lang)) + [
            column("owner_id", yaml_str(page_id(number))), column("language", yaml_str(lang)),
            column("title", yaml_str(title)), column("body", yaml_str(body)),
            column("summary", yaml_str(summaries[key][lang]))]))
    return "\n".join([
        "  - changeSet:",
        "      id: 195-%d" % number,
        "      author: platform",
        "      comment: %s" % yaml_str("C1: learning chapter %s (foodsharing-Grundlagen %d); de from the wiki, "
                                       "en/gws/fr/it machine-translated." % (key, number)),
        "      preConditions:",
        "        - onFail: MARK_RAN",
        "        - sqlCheck:",
        "            expectedResult: 0",
        "            sql: %s" % yaml_str("SELECT COUNT(*) FROM content_page WHERE id = '%s' OR slug = '%s'"
                                         % (page_id(number), key)),
        "      changes:"] + changes)


def legal_change_set(texts):
    changes = [insert("legal_document_version", base_columns(LEGAL_VERSION_ID) + [
        column("kind", yaml_str("LEGAL_AGREEMENT")), column("document_version", yaml_str(LEGAL_VERSION)),
        column("published_at", yaml_str(LEGAL_PUBLISHED), "valueDate"),
        column("require_reconsent", "false", "valueBoolean")])]
    for lang in LANGS:
        title, body = texts[lang]
        changes.append(insert("legal_document_version_i18n", base_columns(legal_text_id(lang)) + [
            column("owner_id", yaml_str(LEGAL_VERSION_ID)), column("language", yaml_str(lang)),
            column("summary", yaml_str(LEGAL_SUMMARY[lang])), column("body", yaml_str("# " + title + "\n\n" + body))]))
    return "\n".join([
        "  - changeSet:",
        "      id: 196-1",
        "      author: platform",
        "      comment: %s" % yaml_str("Owner decision 2026-11-10: the Rechtsvereinbarung (foodsharing-Grundlagen 6) is "
                                       "the binding LEGAL_AGREEMENT, version %s; de from the wiki, en/gws/fr/it "
                                       "machine-translated. No re-consent: earlier consents stay current."
                                       % LEGAL_VERSION),
        "      preConditions:",
        "        - onFail: MARK_RAN",
        "        - sqlCheck:",
        "            expectedResult: 0",
        "            sql: %s" % yaml_str("SELECT COUNT(*) FROM legal_document_version WHERE id = '%s' OR "
                                         "(kind = 'LEGAL_AGREEMENT' AND document_version = '%s')"
                                         % (LEGAL_VERSION_ID, LEGAL_VERSION)),
        "      changes:"] + changes)


def footer(lang, name):
    return "\n---\n\n*%s: [%s](%s)*\n" % (ORIGINAL[lang], escape_text(TITLES.get(name, name)), wiki_url(name))


def convert_all(wiki_dir=WIKI_DIR):
    def is_redirect(name):
        path = os.path.join(wiki_dir, name.replace(" ", "_") + ".wiki")
        with open(path, encoding="utf-8") as f:
            return bool(re.match(r"\s*#(?:WEITERLEITUNG|REDIRECT)", f.read(), re.I))

    # links to skipped (redirect) chapters go to the wiki
    title_to_key = {name: key for name, key, *_ in CHAPTERS
                    if os.path.exists(os.path.join(wiki_dir, name.replace(" ", "_") + ".wiki")) and not is_redirect(name)}
    converter = Converter(title_to_key)
    converted, skipped = {}, []
    for name, key, group, required, number in CHAPTERS:
        path = os.path.join(wiki_dir, name.replace(" ", "_") + ".wiki")
        if not os.path.exists(path):
            raise ConversionError("missing %s" % path)
        with open(path, encoding="utf-8") as f:
            source = f.read()
        try:
            found, body = converter.convert(name, key, source)
        except Redirect as redirect:
            skipped.append(str(redirect))
            continue
        if found is not None and found != number:
            raise ConversionError("%s: foodsharing-Grundlagen %d, expected %d" % (name, found, number))
        body = body.rstrip("\n") + "\n" + footer("de", name)
        leftovers = re.findall(r"\{\{|\}\}|\[\[|'''|''|<(?!a id=|/a>|br>)[a-zA-Z/][^>]*>|\{\||\|\}|&[a-z]+;", body)
        if leftovers:
            raise ConversionError("%s: unknown markup left after conversion: %s" % (name, ", ".join(sorted(set(leftovers)))))
        converted[key] = (name, TITLES.get(name, name), body, group, required, number)
    # every internal link must resolve to an imported chapter; unknown anchors are dropped (reported)
    anchors = {key: set(anchors_of(value[2])) for key, value in converted.items()}
    for key, value in list(converted.items()):
        body = value[2]
        for target_key, anchor in {(t, a) for k, t, a in converter.links if k == key}:
            if target_key not in converted:
                raise ConversionError("%s: link to %s, which is not imported" % (key, target_key))
            if anchor and slug(anchor) not in anchors[target_key]:
                converter.warnings.append("%s: anchor '%s' not in %s, linking the chapter" % (key, anchor, target_key))
                body = body.replace("/learn/%s#%s)" % (target_key, slug(anchor)), "/learn/%s)" % target_key)
        if len(body) > MAX_BODY:
            raise ConversionError("%s: body has %d characters (max %d)" % (key, len(body), MAX_BODY))
        if body_problems(body):
            raise ConversionError("%s: %s" % (key, "; ".join(body_problems(body))))
        converted[key] = (value[0], value[1], body) + value[3:]
    return converted, skipped, converter.warnings


def write_changeset(name, content, force):
    """Applied changesets must not change (Liquibase checksums): refuse to overwrite different content."""
    path = os.path.join(CHANGES, name)
    if os.path.exists(path) and not force:
        with open(path, encoding="utf-8") as f:
            if f.read() != content:
                raise ConversionError("%s exists with different content and may already be applied; write a new "
                                      "changeset file (see docs/content-import/learning/README.md) or pass --force "
                                      "if it was never released" % name)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)


def main(argv):
    de_only = "--de-only" in argv
    force = "--force" in argv
    converted, skipped, warnings = convert_all()
    for message in skipped:
        print("skipped: " + message)
    for message in warnings:
        print("warning: " + message)
    os.makedirs(OUT_DIR, exist_ok=True)
    for key, (name, title, body, *_rest) in converted.items():
        write_markdown(os.path.join(OUT_DIR, key + ".de.md"), title, body)
    if de_only:
        print("wrote %d German chapters to %s" % (len(converted), OUT_DIR))
        return 0
    chapter_sets, legal_texts = [], None
    summaries = read_summaries(list(converted))
    for name, key, group, required, number in CHAPTERS:
        if key not in converted:
            continue
        _, title, body, *_rest = converted[key]
        texts = {"de": (title, body)}
        for lang in LANGS[1:]:
            path = os.path.join(OUT_DIR, "%s.%s.md" % (key, lang))
            if not os.path.exists(path):
                raise ConversionError("missing translation %s" % path)
            t_title, t_body = read_markdown(path)
            if anchors_of(t_body) != anchors_of(body):
                raise ConversionError("%s: anchors differ from the German text" % path)
            if links_of(t_body) != links_of(body):
                raise ConversionError("%s: links differ from the German text" % path)
            if len(t_body) > MAX_BODY or len(t_title) > 200:
                raise ConversionError("%s: too long" % path)
            if body_problems(t_body):
                raise ConversionError("%s: %s" % (path, "; ".join(body_problems(t_body))))
            texts[lang] = (t_title, t_body)
        sort = SORT_OVERRIDE.get(key, number * 10)
        chapter_sets.append(chapter_change_set((name, key, group, required, number, sort, minutes(body)), texts,
                                               summaries))
        if key == LEGAL_KEY:
            legal_texts = texts
    header = ("# GENERATED by server/scripts/gen-learning-chapters.py from docs/content-import/ - do not edit.\n"
              "# C1 learning wiki (docs/tasks/2026-11-10-learning-wiki.md): the foodsharing-Grundlagen as learning\n"
              "# chapters, production data (not seed). Each chapter is inserted only when its id and key are free, so\n"
              "# edits made in the CMS stay. A newer wiki version needs a new changeset file.\n")
    write_changeset(CHAPTERS_FILE, header + "databaseChangeLog:\n" + "\n".join(chapter_sets) + "\n", force)
    if legal_texts:
        legal_header = ("# GENERATED by server/scripts/gen-learning-chapters.py - do not edit.\n"
                        "# Owner decision 2026-11-10: the wiki Rechtsvereinbarung is the binding legal agreement\n"
                        "# (AGREEMENT step). require_reconsent=false: consents to earlier versions stay current\n"
                        "# (LegalService.isCurrent); users without a consent accept this version.\n")
        write_changeset(LEGAL_FILE, legal_header + "databaseChangeLog:\n" + legal_change_set(legal_texts) + "\n", force)
    print("wrote %d chapters (%s) and the legal agreement" % (len(chapter_sets), ", ".join(converted)))
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main(sys.argv[1:]))
    except ConversionError as error:
        print("error: %s" % error, file=sys.stderr)
        sys.exit(1)
