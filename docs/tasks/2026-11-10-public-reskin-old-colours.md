# Public reskin in the old foodsharing colours (direction D "Leichter Reskin")

Status: approved 2026-11-10, owner accepted every recommendation of Q1–Q5 (marked in Decisions). Design: `docs/design/landing-old-colours-d-proposal.md` + `-mockup.html`, contrast background `landing-old-colours-b-proposal.md`.

## Goal
Every page a logged-out visitor sees uses the old foodsharing palette (brown ink `#533a20`, old green family, warm surfaces).
The landing page also gets the green-left/brown-right brand panels, icon tiles and the brown counters band. The logged-in app looks exactly as it does today.

## Decisions
- **D1 Intensity "Mittel"** (owner): `--fs-panel-green/-brown` 14 % / 9 % light, 18 % / 14 % dark. CTA band `.fs-panel--brand-strong` (Kräftig), per the designer (Q4).
- **D2 Counters** (owner): brown band `--fs-band-bg`, numbers `--fs-brand-green`. **Owner tweak:** the light labels are brighter. `--fs-band-muted` light goes from `#d9c9b2` to `#efe6d9` (11.4:1 on `#3a2814`), still a little dimmer than `--fs-band-text #f4ece0` (12.0:1). Dark stays `#c4b39c` (7.6:1 on `#2e2217`).
- **D3 Scope = session, not route** (Q1): `ShellComponent` puts the class `fs-public` on `<main class="shell__main">` while `!session.isAuthenticated()`. The reskin therefore follows "logged out" and needs no route list. A logged-in user on `/faq` or `/r/:slug` keeps today's look. The prerendered landing page (built without a session) already carries the class, so there's no flash on hydration.
- **D4 One global token scope** `.fs-public` in `client/src/styles.scss`, plus `:root.fs-dark .fs-public`. It holds every override from proposal §3.3 and **replaces** the planned `fs-landing-page :host` scope. The scope must re-declare every derived custom property, because `var()` inside a custom property is resolved where it's declared (`:root`):
  - our `--fs-tile-shadow`;
  - PrimeNG `--p-primary-color`, `-hover-color`, `-active-color`, `-contrast-color`, `--p-focus-ring-color`;
  - each PrimeNG component token that references `{primary.*}` and is used on public pages: button primary/outlined/text, checkbox, radiobutton, toggleswitch, form-field focus border, select/listbox selected option, progressspinner, tabs, and `--p-primary-50…950` if anything reads them. The implementer gets the list from the emitted theme CSS (DevTools or `document.styleSheets`) and copies the same expressions into the scope, which re-computes them there.
- **D5 Header, side menu, footer, page background unchanged** (designer Q4/Q5, our Q3): they sit outside `<main>`. Body-appended overlays (toasts, confirm dialog, PrimeNG overlay panels) also stay in app colours. Accepted.
- **D6 Panels beyond the landing page** (Q5): only the `/r/:slug` hero, whose 135° green gradient would clash, becomes `.fs-panel--brand` (it keeps the accent radial glow). Every other public page gets the token swap only: no layout, copy or test-id changes.
- **D7 Bookkeeping:** decision **178** (174–177 were taken by the learning wiki and review fixes 3–5 at merge) amends decision 160. The ledger C17 row notes "public reskin D (decision 178)". E2E tag `@C17`.

## Scope
**In: public routes** (no `authGuard`; reskinned whenever logged out):
- `/`, `/de|gws|fr|it|en`
- `/login`, `/login/sent`, `/login/verify` (minimal chrome)
- `/r/:slug`, `/find-region`, `/r/:slug/intro-pickups`, `/intro-pickups/unsubscribe`
- `/legal/:kind`, `/about/partners|press|education`, `/imprint`, `/pages/:slug`
- `/faq`, `/statistics`, `/release-notes`, `/petition`, `/donate`, `/support`, `/learn`, `/learn/:key`
- `/blog`, `/blog/:id`, `/map` (visitor layers)
- `/newsletter/unsubscribe/:token`, `/account/email/abort`
- `/forbidden`, `**` (404)
- guarded URLs opened while logged out (they redirect to `/login`)

**Out:**
- the logged-in app, including `/onboarding`, `/consent` and `/survey/:id` (all `authGuard`)
- header, side menu, footer, mobile tab bar, overlays, `--fs-bg`, map tiles and marker colours
- an app-wide primary swap (later follow-up)
- `/dev/gallery` (dev only; it follows the same rule, nobody needs to check it)

## Changes
**Contract / server / Liquibase / seed:** none.

**Client, global styles** (`client/src/styles.scss`):
- `:root` / `:root.fs-dark`: new tokens from proposal §3.2. `--fs-brand-tan`, `--fs-panel-green/-brown`, `--fs-tile-shadow`, `--fs-band-bg/-text/-muted` (light muted `#efe6d9`, D2) and `--fs-brand-green`.
- New block `.fs-public { … }` + `:root.fs-dark .fs-public { … }` (unlayered, next to `:root`) with all of §3.3 and D4.
- `@layer app`: `.fs-panel--brand`, `-soft`, `-strong`, `.fs-tile`, `.fs-tile--lg` exactly as proposal §3.1, defined globally but used only on public pages.

**Client, shell** (`client/src/app/layout/shell.component.ts`):
- add `[class.fs-public]="!session.isAuthenticated()"` on `<main>`. `session` is already injected; make it `protected`.
- doc comment pointing to decision 178.

**Client, landing** (`client/src/app/features/public/pages/landing-page.component.html` + `.scss`):
- Apply proposal §3.4 element by element: split sections, Betriebe card, quote, duties, "Das musst du nicht" and FAQ groups get `fs-panel--brand`. The row icons become `<span class="fs-tile" aria-hidden="true"><i class="pi …"></i></span>`, and the Fairteiler head and FAQ group labels get `.fs-tile--lg` / `.fs-tile`.
- CTA band: `fs-panel--brand-strong`, radius xl. Counters `.strip`: `--fs-band-*` + `--fs-brand-green`. Drop `:host-context(.fs-dark) .strip`.
- Mobile padding as in §7.
- Child components follow the tokens and need no changes, except their surfaces where §3.4 asks for it: `features/public/components/landing-near.component.ts` (idle/result/empty cards → panels plus `.fs-tile--lg`) and `count-up.component.ts` (verify only).
- Keep every test id from proposal §8.

**Client, region page** (`client/src/app/features/regions/pages/public-region-page.component.scss`):
- `.hero`: replace the `linear-gradient(135deg …)` with the `.fs-panel--brand` background (add the class in the `.ts` template, keep the radial accent layer and radius xl).

**i18n:** no new or changed keys; `npm run i18n:check` stays green.

**Docs:**
- `git add` `landing-old-colours-d-proposal.md` + `-mockup.html`.
- Delete the A/B/C mockups and proposals (Q5); keep B's proposal if the owner wants the contrast notes.
- Add decision 178, the ledger C17 note, and a "public scope" line in `docs/conventions.md` A7 (Theme).

## UI
Visible change on every logged-out page; the designer's mockup D exists already. Nothing new is needed, unless the owner wants to see the `/r/:slug` hero first (optional quick mockup).

## Tests
- **Unit** (`layout/shell.component.spec.ts`): `<main>` has `fs-public` when anonymous; it doesn't when authenticated; the class toggles on login and logout (session signal). Rule: "reskin only when logged out".
- **Unit** (`landing-page.component.spec.ts`): existing test ids still render. Add one assertion that each `way.*` row has an `aria-hidden` `.fs-tile`.
- **E2E** new `e2e/tests/public/c17-public-reskin.spec.ts` `@C17`:
  - visitor on `/`, `/faq` and `/login`: `main.fs-public`, computed `--fs-primary` = `#44801a`, a primary button's background is `rgb(68, 128, 26)`. This checks the PrimeNG scope.
  - Dark: `--fs-primary` = `#8fd14d`.
  - Logged-in foodsaver on `/faq` and `/dashboard`: no `fs-public`, `--fs-primary` = `#25834a`.
- **Axe:** the `tests/a11y/pages.spec.ts` sweep already covers all visitor routes above, in light, dark and Pixel 7. It must pass with no new rule exclusions (colour contrast included).
- **Screenshots:** the existing `landing.screen.ts` covers its states. Add `landing counters band (dark) @C17 @screenshot` if no dark landing shot exists yet.
- **Baselines** (decision 161): push the branch and run `gh workflow run screenshots-update.yml --ref <branch>` (all screens, mode `changed`), then review every changed PNG:
  - expected: visitor screens only (landing, auth, content, legal, regions public, intro-pickups public, map visitor, errors visitor, layout visitor);
  - **any logged-in screenshot diff is a bug to report**, not something to accept.

## Done when
- [ ] Tokens, `.fs-public` scope (incl. PrimeNG component tokens) and panel/tile classes are in `styles.scss`
- [ ] The shell sets `fs-public` only while logged out; unit test green
- [ ] The landing page matches mockup D at Mittel / brown band / brighter light labels, in light, dark, desktop and 412 px
- [ ] The `/r/:slug` hero is a brand panel; the other public pages change only by token swap
- [ ] The `@C17` e2e spec, axe sweep (light, dark, mobile), `npm run lint`, `i18n:check` and client unit tests are green
- [ ] CI baselines are regenerated and reviewed, with logged-in screens unchanged
- [ ] Decision 175, ledger C17 note and conventions A7 line written; D files committed, A/B/C removed

## Risks
- **PrimeNG tokens resolved at `:root`:** if a component token is missed, that component stays app-green on a public page. The e2e button check and a visual pass over login, support, map and blog catch this.
- **Overlays outside `<main>`** (p-select panel on support, toasts) show the app green next to brown content. This is accepted (D5); it could be revisited with an `appendTo` scope later.
- **Logged-out desktop side menu** stays app-green beside brown pages. Q3 decides whether that's acceptable.
- **Session load before the first render:** if the session resolves after the first paint, a logged-in user might briefly see the reskin. Verify that `initializeApp` loads the session first (expected per `app.routes.ts` warm-up note).
- **Merge order:** the 2026-11-10 review-fix plans may touch `landing-page.component.*` or `styles.scss`. Rebase before the baseline run.

## Open questions (answers go into Decisions)
Q1 Scope by session (D3) or by route list · Q2 borderline routes included · Q3 shell chrome unchanged (D5) · Q4 CTA Kräftig (D1) · Q5 `/r/:slug` hero panel (D6) + delete A/B/C files.
