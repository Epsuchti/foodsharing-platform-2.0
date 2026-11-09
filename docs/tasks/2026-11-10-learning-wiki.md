# Learning wiki: relearn, "Wissen" menu, imported basics, own foodsaver quiz pool (C1, Q2)

## Goal
Make the learning chapters a knowledge section ("Wissen") that is always reachable, so anyone can reread after LEARN.
Fill it with the owner's foodsharing basics (`docs/content-import/wiki/*.wiki`). Then give the foodsaver quiz its own
uncommitted question pool, written from those chapters.
Why you can't go back today (checked): `shared/journey-stepper` (full mode) hides everything of DONE steps, so the
server's `/learn` action goes unused; `/learn` has no nav item and `onboarding` disappears once the journey is complete; a read
chapter offers "mark as read" again. `markChapterRead` is already idempotent (history only on the first read).

## Decisions (proposed; open points are the owner questions Q1–Q5)
1. The route stays `/learn` and `/learn/:key`, with no `/wiki` alias (journey actions, e2e tests and quiz links use it). The label is "Wissen" (`nav.learn`).
2. All chapters stay public. Visitors get side-menu section `about` (after `education`), members get `community` (after `guide`, `secondary`).
   The phone drawer and the visitor menu follow through `sidenavSections`/`drawerItems`.
3. Chapters get a `group` (BASICS, MORE, STORE_MANAGER, AMBASSADOR) and a `required` flag. Only published required chapters
   count for LEARN progress and for `learningDone` (verification queue). Optional chapters can still be marked read.
4. Rule change (amends decision 44): LEARN is DONE when all required chapters are read **or** the user's role/quiz role is
   ≥ FOODSAVER. Otherwise every foodsaver's journey reopens when the new chapters land.
5. Texts are the owner's own, so they may go into the public repo. A repeatable converter turns the `.wiki` files into
   Markdown and generates a **non-seed** Liquibase changeset. It inserts each chapter only if its fixed id is missing
   (precondition, `MARK_RAN`), so CMS edits are never overwritten. A newer wiki version needs a new generated changeset,
   made only when the owner asks.
6. The changeset holds German only. Other languages use the existing fallback to `de` with the `fallback` badge (Q3).
7. The quiz pool lives in gitignored `docs/content-import/quiz/foodsaver.local.yaml`. An importer script loads it through
   the **existing** quiz-editor endpoints: no contract change, permission `QuizEditorService.mayEdit`, history via the
   existing `QUESTION_CREATED`/`ANSWER_CREATED`. Seed keeps its demo questions.

## Scope
In: relearn hook, nav item, grouped overview, read state on the chapter page, `group`/`required` (contract + Liquibase),
rule 4, converter + generated changeset, seed remap, quiz intro → `/learn` link, quiz importer + gitignore, e2e tests and screenshots.
Out: the journey page layout (designer's `docs/design/onboarding-journey-*`; we only add the hook), and the "Weitere
Artikel", store-manager and ambassador chapters (the groups exist; their files come later). Also out: search (C5),
role-gated chapters, translations, the Hygiene-Ratgeber, and fetching the wiki (agents never do).

## Chapter map (sort follows the "foodsharing-Grundlagen N" line)
| File | key | required | sort |
|---|---|---|---|
| Grundsätze | `principles` | yes | 10 |
| Verhaltensregeln | `code-of-conduct` | yes | 20 |
| Regelverletzungen_-_Konsequenzen_und_Bearbeitung | `rule-violations` | yes | 30 |
| Regelverletzungen_-_Erläuterungen (now only a redirect; skipped until the owner supplies it) | `rule-violations-explained` | Q2 | 35 |
| Hygieneregeln | `hygiene-rules` | yes | 40 |
| Voraussetzungen_für_Rollen_in_foodsharing | `role-requirements` | yes | 50 |
| Rechtsvereinbarung | `legal-agreement` | Q1 | 60 |
| Kontext_und_Selbstverständnis | `context-and-self-image` | yes | 70 |
All are group BASICS. Keys avoid the seed placeholders, because `uq_content_page_slug` is global.

## Changes
**Contract** (`openapi/schemas/content.yaml`, then regenerate): add the enum `LearningChapterGroup`. Add `group` and `required` to
`LearningChapter`, the chapter fields of `ContentPage`, `ContentPageSummary` and the editor input. No new paths.
**Liquibase**:
- `changes/194-content-learning-groups.yaml`: `content_page.chapter_group VARCHAR(30)` and
  `required BOOLEAN NOT NULL DEFAULT true`, with existing chapters set to BASICS.
- `changes/195-content-learning-basics.yaml` (**generated**): fixed UUIDs from a free `0012` range (add them to
  `docs/seed-ids.md`), `de` i18n, published, `editor_function='PR'`, `estimated_minutes` from words/200.
- `seed/changes/195-learning-basics-seed.yaml`: deletes the 4 placeholders (`rules`, `hygiene`,
  `how-a-pickup-works`, `etiquette`) with their i18n rows. It remaps `learning_progress` so each persona keeps its journey
  state: whoever read all 4 placeholders reads all new required chapters, partial readers stay partial.
**Converter** `server/scripts/gen-learning-chapters.py` (style of `gen-content-seed.py`; file→key map as above):
- Drop `{{Wiki-Artikel-Typ}}`, the Grundlagen header line, `{{PDFLink}}` sentences, `__TOC__`, `<pdfexclude*>` markers
  (keep their content), `[[File:…]]`, `<br>`, `<font>` (a bold font line becomes `###`). "Gültig ab"/"Stand" stays as an italic first line.
- `==` headings become `##` with `<a id="…"></a>` (MediaWiki anchor); `'''`/`''`/`<u>`/`<b>` become bold/italic; `#`/`*`
  become lists; `[url text]` becomes a link. Layout tables (no `!` header) become nested lists (`**a)** …`); header tables become GFM.
- `[[Page#anchor|text]]` goes to `/learn/<key>#anchor` if imported, else `https://wiki.foodsharing.network/wiki/Page#anchor`
  (decode `.C3.A4` and `%C3%A4` first). `#WEITERLEITUNG` files are reported and skipped. Each chapter ends with a link to its original.
- Fails on unknown markup (with the line number) or bodies over 32600 chars. Writes nothing but the changeset.
**Server**:
- `ContentPage` gets `chapterGroup`/`required` (mapped in `ContentModels`/`ContentMapper`; saved via the existing editor
  path, which records history). `ContentService.requiredChapterKeys()`; `markChapterRead` still accepts any published chapter.
- `JourneyService.inputs`/`learningDone` use the required keys; `JourneyCalculator.learn` gets a "quiz role ≥ FOODSAVER" input (rule 4).
**Client**:
- `layout/nav.config.ts`: add the `learn` item. For visitors it is a footer-area item with section `about`, for members
  a main item in `community`; both are added to the `NAV_SECTIONS` order.
- `learning-overview-page`: chapters under group headings. The progress card counts required chapters only, and
  optional chapters carry a tag. When LEARN is done, offer "Nochmals lesen" next to "Zum Einstieg".
- `learning-chapter-page`: signed-in users load the journey (`loadResource`, compact `fs-data-state`). An already-read
  chapter shows "Bereits gelesen" plus next/overview, without the mark button and without an API call.
- `learning-progress.ts`: count and `nextKey` cover required keys only. `content-editor-page`: group select + `required` toggle.
- `shared/journey-stepper` (minimal hook, aligned later with the designer's journey design): a DONE LEARN step shows a
  "Nochmals lesen" link to `/learn` (`<testId>.relearn.LEARN`).
- `quiz-intro-page` (FOODSAVER): "Vorbereitung: Grundlagen lesen" link to `/learn`. `question-dialog`: the wiki link input
  becomes `type="text"` so `/learn/<key>#anchor` is accepted (results and session pages already open it).
- i18n in de/en/gws/fr/it; `npm run i18n:check`.
**Quiz pool (after the chapter work is merged)**: `scripts/import-quiz.mjs` in the style of `scripts/import-csv.mjs`:
dry run by default (`--base-url --session <quiz editor fs_session> --kind FOODSAVER --confirm`); skips existing `de`
texts (`listQuestions`); `createQuestion`/`createAnswer` with `durationSeconds`, `wikiLink` (`/learn/<key>#anchor`),
`mandatory`, `failurePoints`, answers CORRECT/WRONG/NEUTRAL + explanation; never changes quiz settings. `.gitignore`
gets `*.local.yaml`; `docs/content-import/quiz/README.md` documents the format (no real questions). The main session
drafts the pool; the owner reviews it in the quiz editor on staging; then it is imported into production.

## UI
Yes (nav item, grouped overview, read state, relearn link). No mockup needed; the relearn link follows the journey redesign.

## Tests
- Server unit: `JourneyCalculatorTest` (only required chapters count; quiz role ≥ FOODSAVER means DONE; done keeps
  `/learn`; key list updated), `ContentService` (`requiredChapterKeys`, `nextChapterKey` across groups), `JourneyService`
  (an optional read doesn't change progress; a second read records no second history event).
- Client unit: `learning-progress.spec.ts`, nav config spec (visitor `about`, member `community`, drawer), stepper
  relearn link, `markdown.pipe.spec.ts` (`<a id>` survives DOMPurify). Gallery `content.section.ts` gets the new keys.
- Converter: `server/scripts/test_gen_learning_chapters.py`, with our own tiny fixtures (anchors, layout table, links, redirect, unknown markup).
- e2e `@C1`, changed on purpose: `c1-content.spec.ts` (new keys/groups, "Bereits gelesen", nav item for a visitor and a
  finished foodsaver); `d11-journey` and `personas/newcomer` (more chapters; raise budgets only with a reason);
  `e2e/screenshots/content.screen.ts` (grouped overview, read chapter, relearn link). Baselines via CI (decision 161).

## Done when
- [ ] The contract is regenerated; 194/195 run on H2 and PostgreSQL; seed personas keep their journey states
- [ ] A finished user reaches every chapter through "Wissen" and the journey; rereading makes no API call and keeps progress
- [ ] Verified foodsavers' journeys stay complete after the import
- [ ] Re-running the converter gives an identical changeset; it has no unknown markup and every internal link resolves
- [ ] i18n check, lint, unit, ArchUnit and targeted e2e tests are green; screenshots are refreshed in CI and reviewed
- [ ] The quiz importer dry run works on staging; no real questions are in git

Workflow: planner → owner approval → implementer → tester. Order: (1) contract, 194, server rules + tests; (2) converter,
195, seed remap; (3) client; (4) e2e/screenshots. Then a second implementer task (quiz importer), then the question drafts.

## Risks
- Long chapters (Regelverletzungen source 19 KB) vs. plan §11.1 "~2 min each": newcomer funnel and persona budgets change.
- The German Rechtsvereinbarung may contradict our Swiss legal agreement (AGREEMENT step).
- 195 is the first non-seed content changeset (an id precondition keeps it idempotent). ~100 links to the Erläuterungen pages go to the external wiki.

## Owner decisions (2026-11-10) – these override the proposals above
- **Q1 Rechtsvereinbarung:** there is no Swiss version on the wiki (only `Rechtsvereinbarung` (DE) and
  `Rechtsvereinbarung Österreich`). Import the German one as an **optional** chapter in group MORE, titled
  "Rechtsvereinbarung (Deutschland, Referenz)", not required. Our own legal agreement (AGREEMENT step) stays the binding
  one. Add a `todo-eric.md` item: Swiss Rechtsvereinbarung text still needed from the owner / foodsharing Schweiz.
- **Q2:** accepted (6 required chapters, Erläuterungen optional; skipped by the converter until its file is no longer a redirect).
- **Q3 (replaces decision 6):** the chapters are **translated** into `en`, `fr`, `it` and `gws` (Swiss German, as the
  existing gws UI texts are written). The implementer translates from the converted German Markdown; the translations live
  in versioned files next to the converter output (e.g. `docs/content-import/learning/<key>.<lang>.md`) and the
  generated changeset inserts all five languages. Mark the non-German rows as machine-translated in the changeset comment
  only (no UI badge). Keep anchors/links identical across languages.
- **Q4:** accepted (rule change to decision 44; record it in `docs/decisions.md`).
- **Q5:** accepted: ~60 questions, at least 8 per required chapter, 6–10 mandatory, German only for now.
- **Delivery (replaces decision 7 and the importer):** chapters **and** quiz questions ship as **non-seed Liquibase
  changesets** that run automatically on the next deployment in every environment. The owner accepts that the question
  pool is public in the repo. No gitignored file, no import script. Questions: fixed UUIDs (document the range in
  `docs/seed-ids.md`), insert-if-missing preconditions so later quiz-editor edits are never overwritten, `wiki_link` →
  `/learn/<key>` (+ `#anchor` where useful). The seed's demo questions stay for the seed context; check the foodsaver
  quiz in production ends up with the new pool and that demo and real questions don't mix in dev (decide with a seed
  changeset that removes the demo foodsaver questions once the real pool exists, and say so in the report).
- **Who writes the questions:** the implementer drafts the ~60 questions from the converted German chapters into a
  reviewable source file (e.g. `docs/content-import/quiz/foodsaver.de.yaml`) and generates the changeset from it with the
  same converter approach. The owner reviews them on staging afterwards and edits in the quiz editor.
- **Order:** contract + Liquibase 194 + server rules → converter + chapters (5 languages) + seed remap → client (menu,
  relearn hook, overview groups) → quiz question pool changeset → e2e/screenshots.
