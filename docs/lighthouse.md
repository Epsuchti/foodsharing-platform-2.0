# Lighthouse

Lighthouse mobile preset (performance + accessibility), `e2e/scripts/lighthouse.mjs`. Plan §11.5 / D11.5.3 gates
two pages with hard thresholds (performance ≥ 90, accessibility ≥ 95, median of three runs after a warm-up, own CI
job "Lighthouse"). The main M6 pages are **report only** (plan-m7 F6): measured and listed here, no thresholds.
`/onboarding` performance is report-only too until the speed-up is done (decision 141,
`docs/tasks/2026-11-01-onboarding-performance.md`); the job prints its score to the log and the run summary.

```
E2E_BASE_URL=http://localhost:8080 node e2e/scripts/lighthouse.mjs            # gated pages, exit 1 below a threshold
E2E_BASE_URL=http://localhost:8080 node e2e/scripts/lighthouse.mjs --report   # + report-only pages (exit code still from the gated pages)
```

## Measurement 2026-10-30 (measured under load)

Packaged jar, profile `e2e`, Windows developer machine with several other workers (builds, Playwright suites) running
at the same time, `CHROME_PATH` = Playwright's `chrome-headless-shell` 1248. Performance numbers are therefore far
below an idle machine (decision 84 measured `/onboarding` at 93–94 idle) and are a lower bound, not a regression
signal; the CI Lighthouse job on its own runner stays the reference for the thresholds. Accessibility does not
depend on load.

| Page | User | Gate | Performance (median) | Runs | Accessibility |
|---|---|---|---|---|---|
| `/` | visitor | threshold | 89 | 88, 89, 93 | 100 |
| `/onboarding` | newbie | threshold | 69 | 69, 78, 69 | 100 |
| `/map` | visitor | report only | 58 | 47, 62, 58 | 100 |
| `/statistics` | visitor | report only | 76 | 63, 77, 76 | 100 |
| `/about/vision` | visitor | report only | 74 | 71, 74, 74 | 100 |
| `/blog` | visitor | report only | 69 | 71, 69, 61 | 100 |
| `/profile/me` | foodsaver | report only | 57 | 57, 57, 57 | 100 |
| `/baskets` | foodsaver | report only | 63 | 63, 63, 68 | 100 |
| `/food-share-points/<Wiedikon>` | foodsaver | report only | 61 | 61, 58, 63 | 100 |
| `/search?q=Wiedikon` | foodsaver | report only | 59 | 58, 59, 62 | 100 |
| `/regions/<Zürich>/reports` | reports | report only | 62 | 62, 62, 61 | 100 |

Every page scores 100 for accessibility. Re-measure on an idle machine (or take the CI job's annotation) before
drawing conclusions about the report-only performance numbers.

## Start-up bundle 2026-11-02 (`docs/tasks/2026-11-01-onboarding-performance.md`, steps 0–2)

The report now prints FCP, LCP, TBT, and the requests and KB before LCP next to each score. Developer Mac, packaged jar:
`/onboarding` scores 91 with FCP 609 ms, LCP 3536 ms, TBT 10 ms, and 51 requests and 342 KB before LCP. This is not a
reference for the threshold; CI is (76–79 on 2026-11-01).

Step 1 does not help as planned: `NG_BUILD_OPTIMIZE_CHUNKS=1` changed neither the 51 requests nor the start-up chunks,
because the builder already runs its optimizer from three lazy chunks up (38 start-up chunks with it).

Raw size of the start-up chunks: 990 KB. `@angular/core` 257, `@angular/router` 99, `@openng/optimus-ui` 97,
`@openng/optimus-ui-themes` 72, `@angular/common` 54, `app/api/api` 44, `@openng/optimus-ui-styles` 33, `rxjs` 27. The
Optimus theme and styles (about 120 KB with `optimus-ui-styled`) are the largest part the app can still move out, which
points to step 3 (build-time theme).
