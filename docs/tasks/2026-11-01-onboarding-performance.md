# `/onboarding` speed-up (D11.5.3, D18.3)

**Paused (decision 153, 2026-11-02):** the goal is now 70, which `/onboarding` already meets on CI (76–79). Pick this up again only if the owner wants a higher goal; the goal of 90 below is the old one.

## Goal
`/onboarding` reaches a Lighthouse mobile performance median of at least 90 on the CI runner, so the exception from decision 141 can end. The real aim is a faster first visit on a mid-range phone on 4G, which also helps the other signed-in pages that share the start-up path.

## Where it stands (measured 2026-11-01)
| | CI runner | Developer Mac (under load) |
|---|---|---|
| `/` | 93–95 (88 on a first attempt) | 100 |
| `/onboarding` | 76–79 | 89–91 |

On the Mac, `/onboarding` scores FCP 0.6 s, LCP 3.5 s, TBT under 30 ms and CLS about 0.01. LCP is the only metric losing points there. Lighthouse uses simulated throttling (slow 4G, 4× CPU).

Requests on `/onboarding` (90 in total):
- **Before the page appears (51):** 40 JS chunks (about 262 KB), the start translations (19 KB), the icon font (35 KB), CSS, 2 icon SVGs and 4 API calls including the avatar. Roughly 340 KB, which is about 1.7 s at the simulated 1.6 Mbps (an estimate).
- **After the page appears (39):** 30 JS chunks (about 86 KB), the full translations (67 KB), the Inter font (48 KB) and 7 API calls. Deliberate (decisions 84 and 122), and probably not part of LCP.

Largest chunks before the page appears: `chunk-Du0WAGXZ.js` (66 KB), `main` (44 KB), `chunk-LNe1fv8v.js` (22 KB), `chunk-CUO-pxJK.js` (17 KB). About 30 of the 40 chunks are 3 KB or smaller. The build produces 431 JS files from about 200 lazy-loading points.

## Decisions (owner, 2026-11-01)
1. There is no production yet. The future reverse proxy must serve HTTP/2 or HTTP/3; this is written down in `docs/runbook.md` §1. Nothing to build in this task.
2. Blocking the first navigation (decision 131) stays a last resort, because a failing route chunk would blank the whole first render. It is only considered if steps 1–4 fall short.

## Scope
- **In:** start-up JS, translations, fonts and duplicate requests on `/onboarding`, and the measurement that proves each step.
- **Out:**
  - lowering the threshold
  - server-side rendering or prerendering of signed-in pages
  - a rework of the second batch's timing, beyond the items below
  - the report-only pages (they are measured afterwards, not targeted)

## Steps
Do them in this order and measure after each one. Keep a step only if it helps; record the before and after in `docs/lighthouse.md`.

0. **Measure properly.** Extend `e2e/scripts/lighthouse.mjs` to report LCP, FCP, TBT, request count and bytes before LCP per page next to the score, and add them to the run summary. Comparisons use the CI job, or equally warmed, freshly started servers on an idle machine (decision 131).
1. **Merge the start-up chunks.** Tried 2026-11-02: the builder already optimizes chunks by default, see `docs/lighthouse.md`; a different approach is needed for this step. Find out how the builder can group what `main` imports statically, for example with Angular's chunk optimizer. Goal: about 10 start-up chunks instead of 40, with the lazy routes still separate. `client/scripts/preload-initial.mjs` keeps working on the result. Update the bundle budgets (decision 122) if needed.
2. **Shrink what the page needs first.** Run a bundle analysis (`stats.json`) on the four largest chunks and move anything `/onboarding` doesn't need to render behind a lazy load. Check whether the start translations (`START_MODULES` in `build-i18n.mjs`) can shrink.
3. **Redo the build-time theme.** Decision 131: Optimus theme CSS at build time with `theme: 'none'`. The commit is lost and has to be rebuilt. It cut about 23 % of CPU before LCP in a real browser, which matters on the slower CI CPU.
4. **Small fixes.**
   - The bell and message counts are each requested twice after the first page. Request them once.
   - An 84 KB `openng-icons-*.eot` was downloaded on `/`. Find out why and remove it; `@openng/icons/openng-icons.css` in `angular.json` is the first suspect.
   - Check whether the icon font is needed before the first page (35 KB).
   - Check whether the Inter font arriving late causes a visible swap.
   - The `fsScrollHint` forced layout (decision 131).
5. **Only if still below 90:** blocking the first navigation, see decision 2 above. Ask the owner first.

## Tests
- No test is weakened; `@D11.5.3` stays the measure.
- Each changed start-up path keeps its unit tests. Request-count e2e tests may need adjusting (decision 131 needed this for blocking navigation).
- Screenshot specs must stay unchanged. The build-time theme must not change a single baseline; if it does, that's a bug in the step.
- Add unit tests for any new rule (for example the deduplicated counts).

## Done
- `/onboarding` median of at least 90 on CI in three `main` runs in a row.
- `/onboarding` removed from `PERFORMANCE_REPORT_ONLY` in `e2e/scripts/lighthouse.mjs`, and the `.github/workflows/ci.yml` comment removed.
- D11.5.3 and D18.3 updated in `docs/ledger.md`, new decision row in `docs/decisions.md`, new measurement in `docs/lighthouse.md`.
