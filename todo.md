# Todo

Open tasks an agent can do. Tasks that need a person are in `todo-eric.md`. Finished work lives in `docs/ledger.md` and `docs/decisions.md`. Update this file when you close or find something.

Last updated 2026-11-02, after moving the human tasks to `todo-eric.md`.

## Before launch
- **Final gate (plan-m7 Part D):**
  - a green CI run on the gate commit of `main`: fix what the e2e and screenshot jobs report (D5.4, D18.1)
  - run `release.sh --check`, `smoke.sh` and the k6 load test locally in Docker
  - fill in the test results in `docs/launch-report.md`
  - regenerate `docs/coverage.md`
  - set M7 done in the ledger
  - tick `plan.md`
- **Load and Security workflows:** run both once after the fixes for the k6 results folder and the OWASP timeout. The Load thresholds have never been evaluated on a shared runner, and the first OWASP run takes about two hours without an API key.

## Not tested from the 2026-11-01 batch
- The commit-order race on the gap rule, and time zones other than Europe/Zurich.

## Open from the 2026-11-01 batch
- **Map tiles:** e2e and screenshots stub the tile hosts, so add a smoke check that one real tile request succeeds. If Eric's check in a normal browser (`todo-eric.md`) also fails, check the tile URL, CSP `img-src`, the service worker and the attribution.
- **Screenshot baselines:** only about 35 of the 436 changed images were looked at by eye. Review the rest, since differences under the 0.2% tolerance can hide in old ones. The mobile `bells-panel` capture with the drawer open only dims the first screen.
- **Bell panel:** the long-title overflow fix is covered by a test that fails without it; nobody has looked at the real panel with long titles in a browser.
- **Browsers:** firefox and webkit were not run on anything.
- **Client unit tests:** the full `ng test` was not re-run after the metrics spec fix (`e7721e2e`).

## Flakes
- `d10-history.spec.ts` D10.3: hardened, cause not confirmed.
- Watch for `mb2-folders.spec.ts:85`, `events.screen.ts:26`, `history.screen.ts:20` and focus or border differences in screenshots under load.
- **Windows Playwright worker crash** (exit 3221226505 / 0xC0000409): hits a random test about once per full serial run and passes on rerun. Machine infrastructure, not app code. Sharded runs turn video off, which lowers memory.

## Housekeeping
- Worktrees and branches whose work is on `main` can be removed (`git branch --merged main`).
- `SpaForwardingControllerTest.languagePreferenceOrderIsCookieThenAcceptLanguageThenGerman` depends on a stale `static/it` in `target/classes`; run `./mvnw clean verify` first, and make the test independent of the build state instead of changing its expectation.
