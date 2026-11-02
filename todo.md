# Todo

Open points only. Finished work lives in `docs/ledger.md` and `docs/decisions.md`. Update this file when you close or find something.

Last updated 2026-11-02, after merging `integration/2026-11-01` (decisions 142–152) into `main`.

## Before launch
- **Final gate (plan-m7 Part D):**
  - fill in the test results in `docs/launch-report.md`
  - regenerate `docs/coverage.md`
  - set M7 done in the ledger
  - tick `plan.md`
  - go through the "needs a human" table with the owner
- **Needs a human** (`docs/launch-report.md`):
  - a green CI run on GitHub after the push: fix what the e2e and screenshot jobs report (D5.4, D18.1)
  - a server, domain and mail provider; then `smoke.sh`, `release.sh --check` and the k6 load test against it
  - legal texts
  - a real usability test with newcomers
  - the product question whether verification requires a confirmed identity

## Deployment checks for the 2026-11-01 batch
- The production reverse proxy must send `X-Forwarded-For` from an address in `TRUSTED_PROXIES`, or the rate limit (decision 144) sees the proxy's IP for everyone.
- The production database role must be allowed to `CREATE EXTENSION unaccent` (search rework, decision 147).
- Gap rule migration (changelogs 181, 182): not tested on PostgreSQL with real data; run the pre-deploy SQL check on a copy of production first.
- Not tested: the commit-order race on the gap rule, and time zones other than Europe/Zurich.

## Open from the 2026-11-01 batch
- **Map tiles:** the map did not load OpenStreetMap tiles in the built-in browser pane, but `curl` gets them. Probably the pane's network. Confirm in a normal browser before changing code; if real, check the tile URL, CSP `img-src`, the service worker and the attribution. e2e and screenshots stub the tile hosts, so add a smoke check that one real tile request succeeds.
- **Fairteiler cleaning shifts (decision 148), not built:**
  - reminder mails and jobs
  - automatic confirmation of cleaning sign-ups by the pickup policy
  - export of cleaning sign-ups in the account data export, and an `AccountDeletionContributor` for future sign-ups (deletion is covered today by the FK cascade; sign-ups are kept pointing at the anonymised user, decide if they should be removed)
  - a time zone per Fairteiler (the cleaning page uses Europe/Zurich)
  - check the Telegram and mail length of the decline text with several cleaning Fairteiler
- **Native-speaker review** of the `gws`, `fr` and `it` texts of the new features, above all `gws` for the gap rule.
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
