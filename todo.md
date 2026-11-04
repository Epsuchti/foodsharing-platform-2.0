# Todo

Open tasks an agent can do. Tasks that need a person are in `todo-eric.md`. Finished work lives in `docs/ledger.md` and `docs/decisions.md`. Update this file when you close or find something.

Last updated 2026-11-03, after the final gate was merged (`dc970052`).

## Before launch
- **`oasdiff`:** it has nothing to compare on `main` until the first `v*` tag; check that it runs after the first release.

## Not tested from the 2026-11-01 batch
- The commit-order race on the gap rule, and time zones other than Europe/Zurich.

## Open from the 2026-11-01 batch
- **Map tiles:** e2e and screenshots stub the tile hosts, so add a smoke check that one real tile request succeeds. If Eric's check in a normal browser (`todo-eric.md`) also fails, check the tile URL, CSP `img-src`, the service worker and the attribution.
- **Screenshot baselines:** only about 35 of the 436 changed images were looked at by eye. Review the rest, since differences under the 0.2% tolerance can hide in old ones. The mobile `bells-panel` capture with the drawer open only dims the first screen.
- **Bell panel:** the long-title overflow fix is covered by a test that fails without it; nobody has looked at the real panel with long titles in a browser.

## Flakes
- Flaky in the gate run, both passed on retry: `r7-region-admin.spec.ts:10` on firefox and `pages.screen.ts:71` (offline shell light-de) in screenshots.
- `d10-history.spec.ts` D10.3: hardened, cause not confirmed.
- Watch for `mb2-folders.spec.ts:85`, `events.screen.ts:26`, `history.screen.ts:20` and focus or border differences in screenshots under load.
- **Windows Playwright worker crash** (exit 3221226505 / 0xC0000409): hits a random test about once per full serial run and passes on rerun. Machine infrastructure, not app code. Sharded runs turn video off, which lowers memory.

## After the uploads cut-over (decision 158)
- Once the hosting cut-over in `todo-eric.md` is done and verified: remove `UploadDiskImport`, `app.uploads.import-dir`/`UPLOADS_IMPORT_DIR` and `UploadBlobRepository.idsWithoutOriginal`, and drop `upload.storage_key` in a new changelog (seeded rows are looked up by it in `UploadSeedFiles`; switch that to the fixed seed ids first).
- Follow-up (plan Q5, changes A41): don't store `lg` when the original is already at most 1280 px; serve the original instead (saves ~40 % of image bytes).

## Housekeeping
- **Load test on this Mac (optional, does not block launch):** `e2e/load/run.sh` failed three times on 2026-11-03 with `dial: i/o timeout` between k6 and the app, the third time on a quiet machine; every run since 2026-10-29 has had a few (`docs/load-test.md`). Suspect Docker Desktop's port forwarding to `host.docker.internal`: try k6 on the host or the app in the same Docker network. Don't weaken the thresholds.
- Worktrees and branches whose work is on `main` can be removed (`git branch --merged main`).
- `SpaForwardingControllerTest.languagePreferenceOrderIsCookieThenAcceptLanguageThenGerman` depends on a stale `static/it` in `target/classes`; run `./mvnw clean verify` first, and make the test independent of the build state instead of changing its expectation.
