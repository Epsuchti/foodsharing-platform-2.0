# Team card collapses on phones when a member has a phone number (S9)

Owner report (2026-11-12, phone screenshot of release 4): on the store **Team** tab, a member card with a call button shows the name one letter per line ("J / o / n / a / s …") and every stat ("6 / Abholungen", "Zuletzt / 22.10.2026", …) wraps on its own line. Owner approved the fix below. **Fixer-sized** (client only, one layer, no contract), implemented on Opus, followed by `tester`.

## Cause
`features/stores/tabs/store-team.tab.ts`, `.tm` card: grid `auto minmax(0, 1fr) auto`. Column 3 (`.tm__btns`) holds `fs-phone-button`, which renders the full formatted number ("+41 77 123 45 07") and cannot shrink. At ~390 px it takes most of the width and column 2 (name + stats) collapses to one character. Cards without a phone (or without the menu) are fine.

## Goal
At every width from 320 px up, the team card shows the name on one line (ellipsis if it really doesn't fit, never letter-by-letter) and the stats wrap as whole "icon + number + word" items, while the member can still be called with one tap.

## Decisions
- `shared/phone/phone-button.component.ts`: new input `compact` (boolean, default `false`). Compact renders a round icon-only button (same `tel:` link, `pi pi-phone`, size matching the `p-button` ellipsis next to it). The accessible name already contains the number (`shared.phone.call`); add a `title` with the formatted number so it shows on hover. No new UI text expected; if one is needed, all five languages.
- Team card uses `compact` always (the card is the compact view, owner decision 5; the number is one tap away in the dialer and on the profile). Desktop loses the visible number on this card only; store contact (`store-page`) and other uses keep the full button.
- Guard the card itself so a wide column 3 can never collapse column 2 again: name link `min-width: 0` + `overflow: hidden; text-overflow: ellipsis; white-space: nowrap` on the text; stats items don't break inside (`white-space: nowrap` per item, wrapping only between items).
- `features/regions/tabs/region-members.tab.html:117` uses the same button: check it at 375 px. If it shows the same collapse, apply `compact` there too; otherwise leave it.

## Scope
In: phone-button (new input + styles), store-team card CSS/template, region members only if broken, dev gallery `people.section.ts` (show the compact variant), unit test for the phone button's compact rendering (tel href, aria-label, no number text).
Out: other layout, server, contract.

## Tests
- Unit: phone-button compact (spec next to the component).
- e2e `@S9` in `e2e/tests/stores/s9-team-list.spec.ts`: at 375×812 for a seed member with a phone, the name element's height is a single line (e.g. bounding box height < 2× line height) and `stores.team.call` is visible and has `href^="tel:"`.
- Screenshot spec `e2e/screenshots/stores-team.screen.ts`: add a mobile-width state with a member that has a phone (if the seed lacks one in that store, give a seed member a phone per `docs/seed-ids.md`). Baselines are regenerated in CI by the main session afterwards; don't update them locally.

## Done when
Team card at 375 px looks like the cards without phone (name on one line, stats inline), call button icon-only and working, targeted unit + e2e pass, `npm run lint` and `npm run i18n:check` pass.
