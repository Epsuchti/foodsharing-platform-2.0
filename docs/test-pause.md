# Test pause (decision 129, 2026-10-30)

At the owner's request all testing is paused until implementation is finished; the only check is that the app boots. Afterwards the tests below are run and the missing ones are written.

## Last tested spot
- Last full test-worker round: `1a810ce` — server unit 1158/1158, client 604/604, ITs green except the 13 Docker/Testcontainers classes, Playwright chromium 991/994 (failures: AU12 offline retry, `/login/verify` sweep entry — both fixed afterwards, see below), `a11y:routes` clean, `feature-coverage --strict` 150/150.
- Pause starts at `376e4dd` (main). Everything after `1a810ce` has only had the merging worker's own short checks (decision 106).

## Merged after the last full round (need the full suite once)
| Merge | Content | Worker checks only |
|---|---|---|
| `8822b9e` | calendar names at idle (decision 123) | optimus-locale spec, ad1-i18n, d11-journey |
| `2b62a5e` | design round 2: map filters on phones, 44 px targets, wording, scroll hints (decision 124) | touched specs, map/journey/shell/pickups e2e, axe subset |
| `b566e58` | C5 combobox, AU12 test setup, axe waits for animations (decision 125) | c5/au12/b1 ×3, tests/shell |
| (direct) `/login/verify` sweep entry | a11y routes.ts | that sweep entry ×3 |
| `c73283d` | D10.3 audit, region log incl. working groups, 11 `@D10.3` tags | 11 touched specs |
| `8df9e19` | design round 3: personas, "More" nav menu, store "Route" button (decision 127) | personas (chromium + mobile), shell/stores/baskets subsets |
| `f6c89e5` | rule-case fixtures, `resourcesAvailable`, overflow check in i18n smoke (decision 128) | touched server/client tests, i18n-smoke |
| later | everything merged while paused | none — boot check only |

## Untested work (filled in while paused)
Every merge during the pause adds a row here: what changed, which tests must be written or run.

| Merge | Content | Tests to add / run |
|---|---|---|

## When the pause ends
1. Full `./mvnw clean verify` (on a Docker machine for the 13 Testcontainers ITs).
2. Playwright on all five projects + `lighthouse`, `npm run a11y:routes`, `feature-coverage --strict`.
3. Write the tests listed in "Untested work" (unit test per rule change, tagged e2e per feature, `*.screen.ts` specs).
4. Screenshot baselines in Docker (D18.2).
