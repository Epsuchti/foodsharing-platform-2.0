# Automation metrics readability (todo.md "After the base build", items 2 and 3)

## Goal
On `/regions/<id>/automation/metrics` (AU9), every metric has a readable label in all five languages, and a guard fails the build when a key has no label. The chart becomes readable: series are grouped, each group shows ranked totals, and the line chart has at most 5 lines and sums per week over long ranges.

## Decisions (owner, 2026-11-01)
1. One full, stand-alone label per key in `de`, `en`, `gws`, `fr`, `it` (`automation.metric.<KEY with "." -> "_">`), e.g. "Abgelehnt wegen Abstand". Dry-run keys are labelled as such ("Würde bestätigen (Test)").
2. Guard: a `MetricKey` enum in the contract becomes the type of `MetricSeries.key`. `check-i18n.mjs` fails when any value has no label. A server unit test checks that every key `MetricArchiveService` can produce is in `MetricKey`. `AutomationMapper.toSeries` skips an unknown key (e.g. an old archived key) with a warning; it never fails the response.
3. `SLOT_APPROVAL.DEFERRED` is not in the enum, because DEFERRED writes no audit.
4. Six groups behind a PrimeNG `SelectButton`. The default is Eintragungen. Series with a total of 0 are hidden. The key multiselect is removed.
5. Each group shows:
   - a ranked list of totals as horizontal bars,
   - a line chart (weekly sums when the range is over 31 days, daily otherwise; at most 5 lines, the largest totals),
   - a visually hidden data table.

   No stacked bars: one audit can count under several reason keys, and FILLED_AFTER_ADVERT is a subset of SENT.
6. The server keeps returning raw keys. Grouping, order, weekly sums and the top 5 are worked out on the client.
7. No designer mockup.

## Scope
- In:
  - the contract enum
  - the mapper skip
  - the server key test
  - all labels
  - the i18n guard
  - the group switch, totals bars, weekly sums, line limit and accessible table
  - e2e and screenshot updates
- Out:
  - resume item 4 (the `mm/dd/yyyy` date inputs on the same page)
  - any server-side aggregation or change to `metric_daily_count`
  - Liquibase and seed changes. The 8 seed keys stay; all of them are in the enum.

## Changes
### Contract
- `server/src/main/resources/openapi/schemas/automation.yaml`:
  - New `MetricKey` (`type: string`, `enum`, listed in group order) with:
    - `SLOT_APPROVAL.{CONFIRMED, DECLINED, WOULD_CONFIRM, WOULD_DECLINE, SKIPPED, FAILED}`
    - `SLOT_DECLINE_RULE.<each PolicyRule>`
    - `SLOT_SKIP_RULE.<each PolicyRule>`
    - `REQUEST.<each RequestDecision>`
    - `CANCELLATION.<each CancellationStatus>`
    - `ADVERT.SENT`, `ADVERT.FILLED_AFTER_ADVERT`, `EXTRA_SLOT.ADDED`, `INTRO_PICKUP.DONE`
  - `MetricSeries.key` becomes `$ref: './automation.yaml#/MetricKey'`.
  - Regenerate the server and client code (`client/scripts/generate-api.mjs`).
  - Check how the generator names values that contain dots (Java `MetricKey.fromValue`, TS const). Use the generated `fromValue`/values; never hand-copy the list.

### Server
- `automation/mapper/AutomationMapper.toSeries`:
  - Map the key with `MetricKey.fromValue`.
  - On `IllegalArgumentException`, log a warning (key only, no personal data) and leave the series out.
- `MetricArchiveService`: no change in behaviour. If a constant has to become package-visible for the test, do that and nothing more.
- Test: see Tests.

### Client
- `client/scripts/check-i18n.mjs`:
  - Next to the `ApiErrorCode` block (line 166 onwards), parse `MetricKey` from `schemas/automation.yaml`, reusing the same YAML-enum reading; factor it into a small helper.
  - Error when `automation.metric.<KEY_with_underscores>` is missing in any language.
  - Add `automation.metric.` to `DYNAMIC_PREFIXES`.
  - Update the header comment.
- New `client/src/app/features/automation/metric-groups.ts`:
  - `METRIC_GROUPS`: an ordered array of `{ id, keys: MetricKey[] }` with ids `PICKUP`, `DECLINE_REASONS`, `SKIP_REASONS`, `REQUESTS`, `CANCELLATIONS`, `ADVERTS` (the last one holds ADVERT.*, EXTRA_SLOT.* and INTRO_PICKUP.*).
  - `metricLabelKey(key)`: replaces every `.`. The current `replace('.', '_')` replaces only the first one.
  - `bucketWeeks(points, from, to)`: weeks start on Monday; the first and last weeks may be partial; each bucket is labelled with its start date.
  - `topSeries(series, n)`.
- `client/src/app/features/automation/pages/region-sections.ts`, `RegionMetricsSection`:
  - Replace the `p-multiselect` with a `p-selectbutton` bound to a `group` signal (default `PICKUP`).
  - Use `computed`s for `groupSeries` (zero-total series dropped, sorted by total in descending order), `chartSeries` (the top 5, then `fillDays`, then `bucketWeeks` when the range is over 31 days) and `totals`.
  - `keyLabel` uses `metricLabelKey`. The raw-key fallback stays only as a defence at runtime.
  - Keep `loadResource`/`<fs-data-state>`. A group whose series are all 0 shows an inline empty hint (`automation.metrics.groupEmpty`), not the empty state of the whole page.
- New `client/src/app/features/automation/components/metric-totals.component.ts` (`fs-metric-totals`):
  - Ranked rows: label, a bar whose width is relative to the maximum, the total.
  - Bars use theme tokens only (`check-theme-tokens.mjs`).
  - Rows have `data-testid="automation.metrics.totals.row"`, `data-key` and `data-total`.
- `components/daily-line-chart.component.ts`:
  - Add a `.visually-hidden` `<table>`: columns are the series, rows are the days or weeks, `data-testid="<testId>.table"`. This makes the doc comment true.
  - Keep the legend's `data-key`/`data-total`.
  - Add an input for the bucket unit so the range caption can say "pro Woche" or "pro Tag".
- i18n, `features/automation/i18n/{de,en,gws,fr,it}.json`:
  - Labels for every `MetricKey` (about 50; the 8 existing ones stay).
  - `automation.metrics.group.<ID>` (6).
  - `automation.metrics.groupLabel`, `.groupEmpty`, `.perDay`, `.perWeek`, `.tableCaption`.
  - Remove `automation.metrics.keys` and `automation.metrics.allKeys` once they're unused.

## UI
Yes, a visible change on one page: a group switch, totals bars, a calmer chart and a hidden table. No designer mockup (owner). On a phone the SelectButton must wrap or scroll horizontally without cutting labels off. Check this at 360 px.

## Tests
- New server test `automation/service/MetricKeyCoverageTest`:
  - Build every producible key from the enums (`PickupPolicyDecision` except DEFERRED, `PolicyRule` × both prefixes, `RequestDecision`, `CancellationStatus`) plus the 4 constants.
  - Assert `MetricKey.fromValue` accepts each one.
  - Also assert that the enum holds no key that can't be produced (the sets are equal).
- New server mapper test: an unknown key is dropped and the known series stay.
- New client unit tests, in a `metric-groups.spec.ts` next to `automation.spec.ts`:
  - Every `MetricKey` value is in exactly one group.
  - `metricLabelKey` replaces every dot.
  - `bucketWeeks` with partial weeks and sums.
  - `topSeries` returns at most 5, ordered by total.
- Component spec for `RegionMetricsSection`:
  - The default group is PICKUP.
  - Zero-total series are hidden.
  - With 90 days the chart has about 13 weekly points.
  - The hidden table is present.
  - No label equals a raw key.
- `npm run i18n:check`: add a key to `MetricKey` without labels and confirm it fails, then revert. This is a manual negative check, noted in the PR.
- Changed on purpose:
  - `e2e/tests/automation/au7-au9-au10-region.spec.ts @AU9`: switch to each key's group (`automation.metrics.group`), then check the totals row/legend `data-key` total is not 0. Also check that the default view shows no raw `X.Y` text.
  - `e2e/tests/automation/au4-adverts.spec.ts` line 89 onwards: select the ADVERTS group before the `ADVERT.FILLED_AFTER_ADVERT` check.
  - `e2e/screenshots/automation.screen.ts`: the metrics capture shows the default group; add a capture of DECLINE_REASONS. The baselines are generated in the batch (decision 69).

## Done when
- [ ] `MetricKey` is in the contract, the code is regenerated, and `MetricSeries.key` is typed with it.
- [ ] The mapper skips unknown keys with a warning; tested.
- [ ] `MetricKeyCoverageTest` is green, and the enum equals the producible keys.
- [ ] `npm run i18n:check` fails on a missing metric label and passes with all ~50 × 5 labels.
- [ ] The page shows the group switch (default Eintragungen), totals bars, at most 5 lines, weekly sums over 31 days, and a hidden table; no raw keys are visible.
- [ ] The multiselect and its unused i18n keys are removed.
- [ ] The client unit, server and @AU9/@AU4 e2e tests are green; the screenshot specs are updated (baseline pending).
- [ ] `todo.md` items 2 and 3 are marked done with the decision number, and there is a decision row in `docs/decisions.md`.

## Risks
- The generator may name enum values containing dots oddly (`SLOT_APPROVAL_CONFIRMED`, quoted TS keys). Rely on the wire values, not the member names.
- Enums can change: a new `PolicyRule` or status now needs a contract value and five labels. The coverage test and `check-i18n` make that loud, which is intended.
- Archived keys that are no longer valid would silently drop out of the chart; the warning log is the only signal.
- Translating about 50 domain labels into `gws`, `fr` and `it`: keep the terms consistent with the existing `automation.rule.*`/`automation.decision.*` wording.
- Weekly sums for ranges just over 31 days have partial edge weeks that look lower. Show the bucket start dates in the table and caption.
