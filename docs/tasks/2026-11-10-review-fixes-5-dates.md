# Review fixes 5/5: one date format (D6)

Package 5 of the 2026-11-10 review batch, last because it touches almost every screen (order 1 → 2 → 3 → 4 → 5). After it: one CI screenshot run for the whole batch (decision 161). **Fixer-sized** (client only, one layer, no contract), followed by `tester`.

> **Q4, assumed, owner to confirm at plan approval:**
> - (a) Date with time is exactly "10.11.2026, 18:30" in all 5 languages, French included.
> - (b) Date-only is "10.11.2026". No weekday or month names, also in day headings ("Donnerstag, 12. November" → "12.11.2026").
> - (c) Time-only "18:30" stays where the date is in the heading.
> - (d) The graphic date tiles (Do / 5 / Nov) stay.
> - (e) Relative times ("vor 5 Min.") in chat, bells and the activity feed stay, with the full date as a tooltip.
> - (f) Client only: mails, ICS and server texts are unchanged.

## Goal
Every date the client shows uses one numeric format, produced in one place.

## Decisions
- Central formatter: `core/dates/date-time.pipe.ts` (`DateTimeFormatter`, pipe `fsDate`). It is already used about 200 times. de-CH, it-CH and en-CH already give "10.11.2026, 18:30". fr-CH gives "10.11.2026 18:30". So build `date`/`dateTime` from `formatToParts` with a fixed pattern `dd.MM.yyyy` / `dd.MM.yyyy, HH:mm` (24 h), the same for every language. Time zone handling is unchanged.
- Styles `dateLong`, `dateTimeLong`, `weekday` and `dayShort` are removed from `DateStyle`. Callers move to `date` / `dateTime`. The type change makes the compiler list every use. `weekdayShort`, `dayNumber`, `monthShort` stay for the tiles (d). `relative` stays (e).
- Date-only values (ISO `yyyy-MM-dd` strings) must not shift by time zone. Parse them as local calendar dates in the formatter (check how `date-tile` / `fromIsoDate` do it and reuse that helper).

## Scope
In: the formatter, all `fsDate` / `DateTimeFormatter.format` callers of the removed styles, and the visible direct `Intl.DateTimeFormat` uses: `features/automation/automation.util.ts:58`, `features/automation/sections/pickup-policy.section.ts:822`, `features/automation/components/gap-preview.component.ts:134`, `features/events/components/event-list.component.ts:71` (month group headings: keep "November 2026"? It is a heading, not a date, so it **stays**). Also `features/stores/components/month-bar-chart.component.ts` (chart axis, **stays**). Internal key builders (`en-CA`, `en-US`, `en-GB` in pickups.util, history-timeline, achievements, content, account, intro-category, dashboard-page:350) are not display and stay. Also the date pickers' display format (`p-datepicker dateFormat`), if set per language, gets `dd.mm.yy`.
Out: relative times, tiles, server-formatted texts, mails, ICS, other layout.

## Changes
**Client**
- `core/dates/date-time.pipe.ts`: fixed-pattern `date`/`dateTime` via cached `formatToParts`. Remove the four styles. Add a Javadoc line with the format and Q4's rules.
- Replace callers (about 34 uses of the removed styles). For each one, check whether a sentence around it reads "am {date}" / "le {date}" and still works with a numeric date (i18n strings in 5 languages; adjust wording where it said "Donnerstag, …").
- Relative times: add `[title]` with `fsDate: 'dateTime'` where it's missing (chat messages, bells, activity feed).
- The direct `Intl` display sites above use `DateTimeFormatter`.

## UI
Visible on most screens (dates only). No mockup.

## Tests
- `core/dates/date-time.pipe.spec.ts`: the exact output for all 5 languages ("10.11.2026, 18:30", fr included), date-only from an ISO date without a zone shift, around midnight and DST, relative unchanged. Changed on purpose: the cases for the removed styles.
- Changed on purpose: component specs and e2e that assert long dates ("Donnerstag", "12. November"); grep the specs and `e2e/` for weekday/month names.
- E2E: no new feature ID. The i18n smoke (`expectNoOverflow`) must stay green. Screenshots: the batch-wide CI run regenerates all changed baselines. Review each PNG for date changes only.

## Done when
- [ ] No weekday or month names left in date output except the tiles, month headings and chart axis
- [ ] Same string in de, gws, en, fr, it; date-only values do not shift
- [ ] `npm run lint`, client tests, i18n check, i18n smoke green

## Risks
- Hidden long-date assertions in e2e. Run the affected specs, not the full suite.
- The screenshot diff is large (every page with a date). Review it after packages 1–4 so their visual changes are not hidden in the date noise. Alternatively, generate baselines after package 4 and again after 5.
