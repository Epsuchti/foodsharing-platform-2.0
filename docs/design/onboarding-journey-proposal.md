# Onboarding journey page (`/onboarding`): horizontal stepper with a detail panel — design proposal

Status: round 1, no product code changed. Mockup: `onboarding-journey-mockup.html` next to this file (bar: Ansicht = Schritt 4 aktuell / Einführungsabholungen aktuell / Wartet auf Verifizierung / Abgeschlossen / Skeleton / Fehler / Offline / Heute; Fenster = 1280 / 1024 Sidebar / 1024 Rail / 768 Sidebar / 412 Phone; Hell/Dunkel; Bezirk mit/ohne Zusatzschritt; Journey-Bar auf dem Phone aus/an). Resizing the browser window shows the label density levels, because they follow the content width (`@container content`), not the viewport.

## 1. Problem

- Today the page stacks three things that say the same step: hero card ("Schritt 4 von 9 · Foodsaver-Quiz · Quiz starten"), the intro-pickups card, then a long vertical stepper whose current item repeats title, description and duration. On desktop the nine steps need two screens; the overview ("where am I on the way in") is never visible at once.
- The intro-pickups progress is a separate card even when that step is weeks away (locked), and it is not tied visually to its step.
- The landing page (decision 160) shows the way in as one horizontal line with phases Online / Vor Ort / Dabei. The logged-in page looks like a different product.
- Done steps are dead ends: "Grundlagen lernen" cannot be reached again from here (owner: it must).
- On phones the sticky journey bar repeats "Schritt 4 von 9 · Foodsaver-Quiz" directly above a page whose first card says exactly that.

## 2. Layout

**Desktop (content ≥ 52 rem, i.e. from ~1150 px viewport with the sidebar, ~1000 px with the rail).** One card (`onboarding.journey.state`) with three rows: (1) phase labels Online · Vor Ort · Dabei · Dein Bezirk (the landing's rule style, `--fs-primary-200` underline, each spanning its steps' columns); (2) the rail: an `<ol>` of 9 equal columns, each a button with a 2.5 rem marker, a two-line short label and, for Einführungsabholungen, a 3-segment mini meter; plus the landing's end node "Du holst selbst ab" (bag icon) in a narrow last column; (3) the detail panel below a hairline, with a caret under the selected marker. The panel carries eyebrow "Schritt 4 von 9 · Online", title + status tag, description, duration, notes (blocked reason, "kommt dran, sobald …"), and on the right the one action: primary button (`onboarding.journey.cta`), or the waiting block (hourglass, "dauert meist 5 Tage", contact chips), or the intro-pickups meter with its legend. The separate hero and intro-meter cards go away; "Weitere Wege" stays below unchanged.

**Tablet (48–75 rem viewport, side menu takes 16.5 rem or 4.5 rem).** Same DOM, denser by container width: 34–52 rem content → markers 2.25 rem, labels only for the current step ± 1 and the selected step (the others keep their name in visually hidden text, hover underlines, the panel names any selected step); below 34 rem content (768 px with the sidebar ≈ 28 rem) → numbers/glyphs only, the phase row hidden (the phase is in the panel's eyebrow), end node shrinks to its icon. Nothing scrolls sideways; all nine markers are always visible.

**Phone (< 48 rem viewport).** Native-feeling single column, no card chrome: (1) a progress strip — nine slim segments (done filled, current 45 % mix, waiting `--fs-warning`, locked `--fs-border-strong`) with the caption "Schritt 4 von 9 · Online" and "3 erledigt"; (2) the current step as a card with a 4 px primary left edge (today's hero look), description, duration and a full-width action; (3) "Alle Schritte": rows grouped under small phase headings (Online / Vor Ort / Dabei / Dein Bezirk), each row = marker, title, status word, chevron; tapping a done or locked row expands it inline (one open at a time); the current row is tinted `--fs-primary-soft`, labelled "dein nächster Schritt" and tapping it scrolls back to the card (no second CTA); the end row "Ziel: Du holst selbst ab" closes the list. The sticky journey bar (`journey-bar.component.ts`) is hidden on `/onboarding` on phones (Q1) — it stays on every other page.

## 3. Interaction

- Click/tap on any step shows its detail; the current step is selected on load. Done → read-only (status tag "erledigt", description, duration) plus a revisit link where one exists: LEARN "Nochmal lesen" → `/learn` (owner requirement), PROFILE_REGION "Profil bearbeiten" → `/onboarding/profile`, INTRO_PICKUPS "Meine Termine" → `/intro-pickups` (both routes exist). ACCOUNT, QUIZ, AGREEMENT, ID_CHECK, PASSPORT and region steps link nowhere (Q3). Revisit links are outlined buttons, never the page's primary action. Locked → tag "noch gesperrt" and "Kommt dran, sobald „Foodsaver-Quiz“ erledigt ist." (the step right after the current one) or "Kommt dran, sobald die Schritte davor erledigt sind." Waiting → the waiting block. A selected non-current step gets a text-coloured ring and the panel shows "Zum aktuellen Schritt". Finished journey: the panel is the completion card; selecting a done step shows it with "Zur Zusammenfassung".
- Phone: the same rules inline in the row body (description, duration, lock note, meter, revisit link).
- Keyboard: every marker is a real `<button>` (Tab reaches all nine; Arrow Left/Right, Home/End move along the rail), Enter/Space selects, focus stays on the marker; the panel is `aria-controls`'d. Reduced motion: the 150 ms panel fade and the smooth scroll on the phone are off.

## 4. States (DataState, `onboarding.journey.state`)

| State | Desktop | Phone |
|---|---|---|
| skeleton | card: phase bar, 9 circles with label lines, panel lines and a button block (custom `fsSkeleton`, same height as the real card → no shift) | strip + card + 4 row lines |
| success | §2 | §2 |
| error | `shared.errorState.title.error` "Das hat nicht geklappt" + "Nochmals versuchen" (`onboarding.journey.state.retry`) | same |
| offline | "Du bist offline" + "Sobald du wieder online bist, laden wir die Daten automatisch." | same |
| empty / forbidden / not-found | not reachable for the own journey (`isEmpty: () => false`); `fs-data-state` defaults | same |
| complete | all markers filled, line fully primary, end node filled; completion card; tracks below | strip "Alle Schritte erledigt", completion card, list, tracks |
Mutations: none on this page.

## 5. Visual spec (tokens)

- Card `--fs-surface`, `--fs-border`, `--fs-radius-lg`, `--fs-shadow-sm`. Phase labels 0.72 rem/700/0.06em uppercase `--fs-primary-strong` on a 2 px `--fs-primary-200` rule. Rail line 2 px `--fs-border-strong`, `--fs-primary` after a done step.
- Marker 2.5 rem (2.25 rem dense), 2 px border. Done: `--fs-primary` fill, check, `--fs-on-primary`. Current: `--fs-primary` border, number in `--fs-primary-strong`, 4 px `--fs-primary-soft` halo. Waiting: `--fs-warning` border, hourglass in `--fs-warning-text` on `--fs-warning-soft`. Locked: lock glyph, `--fs-surface-muted` fill, `--fs-text-muted`. Selected: `0 0 0 2px --fs-surface, 0 0 0 4px --fs-text` ring + caret (0.9 rem square, `--fs-border` edges).
- Labels 0.78 rem/650, two lines max, `--fs-text` (locked `--fs-text-muted`, current `--fs-primary-strong`), soft hyphen in "Einführungs­abholungen". Mini meter 2.2 rem × 4 px, three segments in the meter colours.
- Panel: eyebrow 0.8 rem uppercase muted, title 1.45 rem, status tags (`tag--ok` primary-soft, `tag--warn` warning-soft, `tag--now` primary fill), duration row 0.85 rem muted with clock, notes `--fs-surface-muted` / warning-soft at `--fs-radius-sm`. Button `fs-btn fs-btn--lg`; revisit `fs-btn--outlined`; back `fs-btn--text fs-btn--sm`.
- Phone strip segments 0.45 rem tall; rows min 3 rem, `--fs-radius-md` hover `--fs-surface-muted`; row body `--fs-surface` card 1 px `--fs-border`; vertical connector 2 px like today's stepper.
- Dark mode: same tokens; the selected ring uses `--fs-text` (#ecf2ea) and the current halo `--fs-primary-soft` (14 % green), both ≥ 3:1 against `--fs-surface`; status is never colour alone (glyph + word).

## 6. Labels and i18n keys (de shown; en, gws, fr, it at implementation)

Keep: `onboarding.journey.title/subtitle/stepOf/introTitle/introDone/introBooked/introOpen/allSteps/completeTitle/completeText/toDashboard/tracks/tracksText`, `onboarding.step.<KEY>.title/.description`, `onboarding.action.*`, `onboarding.waiting.*`, `onboarding.blocked.*`, `shared.journey.status.*`, `shared.journey.duration.*`, `shared.journey.typicalDays`, `shared.journey.progressDetail`.
New: `onboarding.step.<KEY>.short` (Konto · Profil & Bezirk · Grundlagen · Quiz · Vereinbarung · Einführungs­abholungen · Verifizierung · Dein Ausweis; region steps use their server title, clamped), `onboarding.journey.phase.online/onsite/in/region` ("Online", "Vor Ort", "Dabei", "Dein Bezirk"), `onboarding.journey.end` "Du holst selbst ab", `onboarding.journey.endGoal` "Ziel: Du holst selbst ab", `onboarding.journey.nextStepTag` "Dein nächster Schritt", `onboarding.journey.unlocksAfter` "Kommt dran, sobald „{step}“ erledigt ist.", `onboarding.journey.unlocksLater` "Kommt dran, sobald die Schritte davor erledigt sind.", `onboarding.journey.backToCurrent` "Zum aktuellen Schritt", `onboarding.journey.backToSummary` "Zur Zusammenfassung", `onboarding.journey.regionStep` "Zusatzschritt deines Bezirks {region}", `onboarding.journey.stepName` "{title}, Schritt {n} von {total}, {status}" (accessible name), `onboarding.journey.doneCount` "{count} erledigt", `onboarding.journey.allDone` "Alle Schritte erledigt", `onboarding.journey.learnHint` "Die Kapitel bleiben jederzeit offen – auch nach dem Einstieg.", `onboarding.action.readAgain` "Nochmal lesen", `onboarding.action.editProfile` "Profil bearbeiten" (`onboarding.action.myIntroPickups` exists).
Removed from this page: none (the intro meter keeps its keys inside the panel).

## 7. Accessibility

- Rail is an `<ol aria-label="Alle Schritte">`; phase labels and the end node are list items with text. Each step `<li data-status aria-current="step">` holds one `<button aria-expanded aria-controls="journey-detail">` whose name is "Grundlagen, Schritt 3 von 9, erledigt" (visible short label + hidden suffix). Panel `<section id="journey-detail">` with an `<h2 tabindex="-1">`.
- Status always as glyph + word (check/lock/hourglass/number, "erledigt"/"noch gesperrt"/"wartet auf andere"); selection as ring + caret + the panel's eyebrow/title; progress as meter `role="meter"` with a spoken "1 von 3 erledigt, 1 gebucht" plus the text legend; phone strip `role="progressbar"`.
- Focus ring: the global 3 px outline; buttons ≥ 44 px on touch (`--fs-target-sm`). Reduced motion respected (§3).

## 8. Mobile summary

412 px: strip → current card (full-width action) → grouped accordion list → end row → tracks; sits above the tab bar (`--fs-tabbar-h`), no sticky CTA, no journey bar on this route. Also the layout at 768 px with the sidebar open is the dense desktop rail (numbers only), not the phone layout.

## 9. Test ids

Keep: `onboarding.journey.state`, `.state.retry`, `.current` (now the panel/phone card when it shows the current step, with `data-step`, `data-status`), `.cta`, `.waiting`, `.complete`, `.toDashboard`, `.introMeter` (`data-done`, `data-required`), `.stepper.step.<KEY>` (the `<li>`, `data-status`, `aria-current`), `.track`, `.trackBlocked`, `.trackWaiting`.
New: `.stepper.select.<KEY>` (button), `.stepper.mini.INTRO_PICKUPS`, `.detail` (panel showing a non-current step, `data-step`), `.detail.back`, `.revisit`, `.phase.<online|onsite|in|region>`, `.end`, `.strip`, `.list`.
Tests to adapt: `d11-journey.spec.ts` D11.5.4 lines 103–105 (bar hidden on `/onboarding` on phones → `onboarding.bar.stepper` count 0 there); `onboarding.screen.ts` baselines regenerate in CI (decision 161).

## 10. What changes where

- `journey-page.component.ts`: new template (rail + panel on desktop, strip + card + list on phones via the shell's 48 rem viewport signal), `selected` signal, intro meter moves into the panel, hero and meter sections removed; custom skeleton template.
- `shared/journey-stepper`: new mode `rail` (phases, buttons, mini meter, end node, `selected` input, `select` output, arrow keys) and mode `list` (phone accordion); `full` mode stays until the dashboard or others no longer need it; `compact` untouched.
- `journey-bar.component.ts`: `visible` also false while `isJourneyPage(url)` (phones).
- Contract: `JourneyStep.revisit` (optional `JourneyNextAction`) in `openapi/schemas/onboarding.yaml`, filled by `JourneyCalculator` for LEARN, PROFILE_REGION, INTRO_PICKUPS when DONE; phase grouping is client-side by key (extra steps → `region`), no contract change.
- i18n: §6 in all five languages; `npm run i18n:check`.

## 11. Rejected alternatives

- **Two columns on desktop (vertical stepper left, detail right).** Keeps today's component, but wastes the width at 1000–1150 px, puts the detail beside step 1 instead of under the current step, and does not match the landing's horizontal line.
- **Horizontally scrolling rail with full labels (overflow-x).** Readable labels everywhere, but steps beyond the viewport disappear, which defeats "du siehst immer, was als Nächstes kommt"; scroll affordance is weak on desktop and the e2e overflow check (D11.5.4) would need exceptions. Density levels keep all nine visible instead.

## 12. Questions (with recommendation)

- **Q1 Journey bar on `/onboarding` (phones):** hide it there (recommended; the strip and the current card replace it, ~44 px gained above the fold) or keep it as today? The mockup toggle shows both.
- **Q2 Region extra steps:** the server appends them after PASSPORT, so they form a fourth group "Dein Bezirk" at the end (as mocked). Recommended: accept now; add a `todo.md` item to order region steps before ID_CHECK on the server if verification depends on them — the layout handles either order.
- **Q3 Revisit links:** LEARN, PROFILE_REGION and INTRO_PICKUPS as mocked (recommended, all routes exist); should AGREEMENT offer "Vereinbarung ansehen" too? Only if a read-only agreement page exists; otherwise leave it.
- **Q4 Tablet density 34–52 rem:** labels for current ± 1 (recommended, mocked at 1024 Sidebar) or numbers only?
- **Q5 End node "Du holst selbst ab":** keep (recommended; it is the landing's end node and makes the finished state read as "arrived") or drop to gain one column?
