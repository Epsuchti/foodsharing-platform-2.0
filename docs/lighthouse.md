# Lighthouse

Lighthouse mobile preset (performance + accessibility), `e2e/scripts/lighthouse.mjs`. Plan §11.5 / D11.5.3 gates
two pages with hard thresholds (performance ≥ 90, accessibility ≥ 95, median of three runs after a warm-up, own CI
job "Lighthouse"). The main M6 pages are **report only** (plan-m7 F6): measured and listed here, no thresholds.

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
