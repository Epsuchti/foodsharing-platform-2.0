# Date inputs follow the app, not the browser locale (resume item 4)

## Goal
Native `type="date"`, `datetime-local` and `time` inputs format by the browser/OS locale. In a German or Swiss-German UI on an en-US browser they show `mm/dd/yyyy` and AM/PM. Replace all of them with shared Optimus-based inputs that always show `dd.mm.yyyy` and 24h `HH:mm`, with calendar names in the UI language. Form values keep the same ISO strings as today.

## Decisions
- Owner: option 1 for all 26 date/datetime-local inputs plus the one `type="time"` input (`store-pickups.tab.ts`).
- Assumed, confirm at plan review:
  - A2: Typing is allowed (no `readonlyInput`). The field accepts `dd.mm.yyyy`, `d.m.yyyy`, `d.m.yy` and ISO `yyyy-mm-dd`. Text it can't read shows an inline error and keeps the last valid value (no silent clear). The calendar opens from the icon. No `touchUI`.
  - A3: `dd.mm.yyyy` and 24h time for every UI language (en too), as `OptimusLocaleService` already records.
  - A4: e2e helpers `fillDate(locator, iso)` / `fillDateTime(locator, isoLocal)` / `fillTime(locator, 'HH:mm')` type the display format and blur. All existing `.fill('2026-…')` calls on these fields change on purpose.
  - A5: The two existing `p-datepicker` uses (history-timeline range, pickup-policy multiple) stay as they are, except history-timeline drops its redundant `dateFormat="dd.mm.yy"` and `[firstDayOfWeek]="1"` (set globally).
- Value contract of the wrappers (no new decision needed, keeps every model unchanged):
  - date `yyyy-MM-dd`, datetime `yyyy-MM-ddTHH:mm` (wall time, same as `datetime-local`), time `HH:mm`.
  - Empty is `''`, as native inputs give.
  - `min`/`max` are strings in the same format.
- Add a decision row to `docs/decisions.md` when merged ("date inputs: shared fs-date/datetime/time inputs, Swiss format, ISO string values; no native date/time inputs").

## Scope
In:
- The three shared controls.
- Migration of all 27 native inputs:
  - users-admin user-admin-page
  - achievements award-dialog, awards-dialog
  - stores store-form-page
  - polls poll-form-page (2)
  - content release-notes-admin-page
  - verification region-passports-page
  - automation audit-table (2), region-sections (2)
  - account profile-page, sleep-mode-card (2)
  - events event-form-page (2)
  - intro-pickups einab-form-dialog, admin-intro-pickups-page
  - reports report-page
  - pickups store-pickup-history.tab (2), store-pickups.tab (date + time)
- i18n for the new texts, the e2e helpers, call-site updates, a guard against new native date inputs.

Out:
- Server, OpenAPI, Liquibase, seed (no data change).
- The range/multiple pickers above.
- `fs-weekday-time-picker` (already a 24h text field).
- Screenshot baseline regeneration: batch at the final gate, decision 69.

## Changes
**Client, shared (`client/src/app/shared/date-input/`)**
- `date-input.util.ts`, pure functions:
  - `parseDisplayDate(text)`: lenient formats from A2, rejects 31.02.
  - `formatDisplayDate`.
  - `isoDateToDate` / `dateToIsoDate` and the datetime/time equivalents.
  - Always use local components (`getFullYear`, `getHours` …). Never `toISOString()`, which would shift by the UTC offset.
- `fs-date-input.component.ts`, standalone, OnPush, `ControlValueAccessor` + `NG_VALUE_ACCESSOR`:
  - Wraps `p-datepicker` (`@openng/optimus-ui/datepicker`), as used today in `history-timeline`.
  - `showIcon`, `iconDisplay="input"`, `appendTo="body"`, `[showButtonBar]` and `[showClear]` when not `required`.
  - Inputs:
    - `inputId`: so existing `<label for>` keeps working.
    - `min`, `max`, `required`, `ariaLabel`, `ariaInvalid`.
    - `placeholder`: default `common.dateInput.placeholder` = "TT.MM.JJJJ" etc.
    - `testId`: rendered as `data-testid` on the host. The inner input gets `${testId}.input` only if a test needs it; keep the host name unchanged.
  - Implements `setDisabledState`. Works with reactive (`formControlName`) and template forms (`ngModel` + `name`).
  - Typed text runs through `parseDisplayDate` on blur/Enter. On a parse error it shows `common.dateInput.invalid` inline (`role="alert"`, `id` referenced by `aria-describedby`).
- `fs-datetime-input.component.ts`: same with `showTime`, `hourFormat="24"`, display `dd.mm.yyyy HH:mm`.
- `fs-time-input.component.ts`: `timeOnly`, `hourFormat="24"`. Reuse `validTime` from `weekday-time-picker` if exported; otherwise move it into the util.
- Reuse `OptimusLocaleService` (`core/i18n/optimus-locale.ts`) unchanged for names/Today/Clear. Ensure the calendar names are built before the first open: call `calendarNames(locale)` on the wrappers' first focus if still pending (decision 123 defers them to idle).
- Export via an index `DATE_INPUT_IMPORTS`.

**Client, features:** replace each native input with the wrapper.
- Keep:
  - the `id` (as `inputId`)
  - the `data-testid` (as `testId`)
  - `formControlName`/`ngModel` bindings
  - `required`, `autocomplete="bday"` (passed through)
  - `aria-invalid`
- Drop `pInputText` / `class="native"` styling.
- Conversion helpers stay untouched: `events.resource.ts`, `reports.util.ts`, `intro-category.ts`, `stores` and sleep-mode rules.
- `shared/history-timeline/history-timeline.component.html`: remove the redundant options (A5).

**Client, i18n:** `common.dateInput.placeholder`, `.timePlaceholder`, `.invalid`, `.openCalendar` (icon aria label) in de/en/gws/fr/it. Run `npm run i18n:check`.

**Guard:** an ESLint `no-restricted-syntax` rule, or a unit spec scanning `src/app/**/*.{ts,html}`, that fails on `type="date"`, `type="datetime-local"` or `type="time"`. Prefer the spec if template linting doesn't cover inline templates.

**E2E (`e2e/support/`):**
- `fillDate`, `fillDateTime`, `fillTime`:
  - locate the host's inner `input`, `fill(display)`, `press('Tab')`
  - assert the overlay is closed (decision 138's settle pattern; touch: click outside like `d10-history`'s helper).
- Update the call sites in:
  - ac1-achievements, s2-store-form, a9-sleep, e1-events, ea18-admin
  - plus any others found by `grep -rn "fill(" e2e | grep -E "Date|date|start|until|from|to'|time"`: ad1-users, v1-polls, ve3-passport, pu1/pu2/pu6, au*, a6-profile, a13 and the screenshot specs listed in the planner notes.
- Specs reading `inputValue()` of a date field compare the display format or read the bound state instead.

## UI
Yes, a visible change on about 18 forms: the field shows `TT.MM.JJJJ` or `dd.mm.yyyy` with a calendar icon and an Optimus popup instead of the native widget. It's a standard component already used in history filters, so no designer mockup is advised. Check phone width (412px) for popup fit with `appendTo="body"`.

## Tests
New:
- `date-input.util.spec.ts`:
  - parsing of every accepted format; rejects 31.02, 29.02 of non-leap years, 13th month, garbage
  - round-trips Date ↔ string with no zone shift (run under a non-UTC TZ via a fixed-offset check)
  - min/max comparison on strings
- Component specs for the three wrappers:
  - writeValue → display; typing → onChange ISO; picking a calendar day → ISO; clear → `''`
  - disabled state; invalid text shows the error and keeps the value; `inputId`/`testId` rendered
- The guard spec (no native date/time inputs).
- E2E `e2e/tests/shell/ad1-date-inputs.spec.ts`, tagged `@AD1`:
  - per language de/en/gws/fr/it: the sleep-mode form shows `dd.mm.yyyy`, the calendar month name follows the language, and typed and picked dates save the same ISO value (checked via API)
  - one datetime case (event form)
  - one time case (store pickup edit)
- Screenshot spec: an open date popup and an invalid-input state on the sleep-mode card, desktop + phone. The baseline comes at the final gate.

Existing, changed on purpose: all e2e `.fill()` call sites on date/datetime/time fields switch to the helpers (A4). Unit specs that query `input[type=date]` switch to the wrapper. `ad1-i18n.spec.ts` stays as it is.

## Done when
- [ ] `grep -rnE 'type="(date|datetime-local|time)"' client/src/app` is empty and the guard spec enforces it
- [ ] all 27 fields show `dd.mm.yyyy` / 24h in every UI language on an en-US browser (`locale: 'en-US'` in the new spec)
- [ ] form payloads unchanged (same ISO strings; existing flow specs green)
- [ ] keyboard: type, open the calendar from the icon, Escape closes; axe sweep green on affected routes
- [ ] `npm run i18n:check`, `npm run lint`, client unit tests, and the affected e2e specs green on desktop and mobile chromium
- [ ] decision row added; ledger/resume item 4 ticked

## Risks
- Time-zone shift: a `Date` built or read with UTC methods moves dates by a day. Mitigation: local components only, plus the spec.
- Region wall times (intro pickups, reports) depend on the existing helpers. The wrapper must pass the wall-time string through unchanged.
- The Optimus popup in dialogs (award, einab dialogs): needs `appendTo="body"`, a z-index above `p-dialog`, and focus-trap interplay.
- Mobile UX loses the native OS wheel. Typing and the calendar remain; watch the overflow audit at 412px.
- e2e flakes from open overlays covering later clicks. The helpers close and assert the overlay.
- Many screenshot baselines change; they're regenerated deliberately at the gate (decision 69), not to green a run.
- Calendar names are deferred to idle (decision 123). A very early open could show English names; mitigated by building them on first focus.
