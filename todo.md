# Todo

Open tasks an agent can do. Tasks that need a person are in `todo-eric.md`. Finished work lives in `docs/ledger.md` and `docs/decisions.md`. Update this file when you close or find something.

Last updated 2026-11-03, after the final gate was merged (`dc970052`).

## Before launch
- **Map tile baselines (MT1):** the attribution text changed on every map and `map.screen.ts` has two new vector screens (light, dark): regenerate in the end batch (decision 69) and check the dark map's contrast with the marker colours by eye.
- **MT2: tile alerts** (owner 2026-11-04: none for now, decision 163): a PrometheusRule in the hosting repo for a failed or stale build (no success for 35 days, `kube_job_status_failed`), an OOM-killed build pod, the tile server down, and the file missing (`caddy_http_request_duration_seconds_count{code="404"}` on the tile path, or a probe). Until then the dashboard "Map Tiles" is checked by hand after the 1st of each month.
- **`oasdiff`:** it has nothing to compare on `main` until the first `v*` tag; check that it runs after the first release.

## Not tested from the 2026-11-01 batch
- The commit-order race on the gap rule, and time zones other than Europe/Zurich.

## Open from the 2026-11-01 batch
- **Screenshot baselines:** only about 35 of the 436 changed images were looked at by eye. Review the rest, since differences under the 0.2% tolerance can hide in old ones. The mobile `bells-panel` capture with the drawer open only dims the first screen.
- **Bell panel:** the long-title overflow fix is covered by a test that fails without it; nobody has looked at the real panel with long titles in a browser.

## Open from the UI polish batch (2026-11-04)
- **Home "Waiting for you" for store managers:** applications to review in their own stores would be the actionable row, but there is no cheap endpoint (per-store team calls). Needs a count endpoint first (designer question 1, owner: ship without).
- **Hygiene/quiz reminder on the home screen:** skipped; the session carries no hygiene certificate or quiz expiry (`SessionUser` has `quizRole` only). Needs the expiry in the session or a small endpoint.
- **Screenshot baselines:** the new names `pickups-page-*`, `pickups-page-more-*`, `dashboard-pickups-tiles-*`, `dashboard-{help,waiting,events,stores}-*`, `dashboard-help-{loading,error}-*` need baselines in the next batch; the old `pickups-options-*` / `pickups-mine-*` baselines are orphaned.

## Flakes
- Flaky in the gate run, both passed on retry: `r7-region-admin.spec.ts:10` on firefox and `pages.screen.ts:71` (offline shell light-de) in screenshots.
- `d10-history.spec.ts` D10.3: hardened, cause not confirmed.
- Watch for `mb2-folders.spec.ts:85`, `events.screen.ts:26`, `history.screen.ts:20` and focus or border differences in screenshots under load.
- **Windows Playwright worker crash** (exit 3221226505 / 0xC0000409): hits a random test about once per full serial run and passes on rerun. Machine infrastructure, not app code. Sharded runs turn video off, which lowers memory.

## After the uploads cut-over (decision 158)
- Once the hosting cut-over in `todo-eric.md` is done and verified: remove `UploadDiskImport`, `app.uploads.import-dir`/`UPLOADS_IMPORT_DIR` and `UploadBlobRepository.idsWithoutOriginal`, and drop `upload.storage_key` in a new changelog (seeded rows are looked up by it in `UploadSeedFiles`; switch that to the fixed seed ids first).
- Follow-up (plan Q5, changes A41): don't store `lg` when the original is already at most 1280 px; serve the original instead (saves ~40 % of image bytes).

## Automation redesign (decision 167)
- Drop the unmapped `dry_run` columns of `pickup_policy`, `request_policy` and `open_slot_advert` (and the always-false audit `dry_run` columns) together with the read-only `WOULD_*` / `DRY_RUN_SUMMARY` / `*_WOULD_*` values (Q8), once no row older than the cut-over is kept. The seed changesets 105–108 still `loadData` the config columns: replace them with new seed changesets first. Until then, native SQL inserts into those tables must not rely on the column default.
- S2 screenshots: new baselines `pickups-rules-blocked-*`, `pickups-blocked-dialog-*`, `pickups-rules-loading-*`, `pickups-rules-error-*`; `pickups-signup-reasons-*` now shows the reason dialog behind the *Gesperrt* chip (regenerate via CI, decision 161).
- S3 screenshots: new baselines `pickups-linked-choose-*`, `pickups-linked-none-free-*`, `pickups-linked-not-member-*`, `pickups-link-missing-*`, `pickups-link-repair-*`, `pickups-linked-satisfied-*`, `pickups-linked-loading-*`, `pickups-linked-error-*`; the S2 `pickups-rules-blocked-*` card line now ends with "du wählst sie beim Eintragen gleich mit" (regenerate via CI, decision 161).
- S4 screenshots: new baselines `automation-picker-results-*`, `automation-picker-empty-*`, `automation-picker-loading-*`, `automation-picker-error-*`, `automation-link-locked-*`, `automation-channels-*`, `automation-channels-empty-*`; `automation-policy-*`, `automation-links-*`, `automation-gap-*`, `automation-audits-*`, `automation-adverts-*`, `automation-signout-*` and `automation-region-overview-*` change (status strip, sub-nav labels, rule cards, *Kanäle* column) — regenerate via CI (decision 161).
- S4 follow-ups: the picker shows only recently linked stores before typing (not the own region's list of the mockup); the *Kanäle* intro names "den foodsharing-Bot" instead of the bot's username (it is only in the user's notification settings); the gap card stays inside `pickup-policy.section.ts` (link rules, picker and simple rule cards are components now).

## Housekeeping
- **Load test on this Mac (optional, does not block launch):** `e2e/load/run.sh` failed three times on 2026-11-03 with `dial: i/o timeout` between k6 and the app, the third time on a quiet machine; every run since 2026-10-29 has had a few (`docs/load-test.md`). Suspect Docker Desktop's port forwarding to `host.docker.internal`: try k6 on the host or the app in the same Docker network. Don't weaken the thresholds.
- Worktrees and branches whose work is on `main` can be removed (`git branch --merged main`).
- `SpaForwardingControllerTest.languagePreferenceOrderIsCookieThenAcceptLanguageThenGerman` depends on a stale `static/it` in `target/classes`; run `./mvnw clean verify` first, and make the test independent of the build state instead of changing its expectation.
