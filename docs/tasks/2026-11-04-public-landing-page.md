# Public landing page, Variant D (C17)

## Goal
`/` and `/de|gws|fr|it|en` (logged out) become Variant D of `docs/design/landing-page-proposal.md` (§2 table, §3, §5–§9) as rendered by `pageD()` in `docs/design/landing-page-mockup.html` (copy from `C`, `BIZ_D`, `C.duties`, `C.faqGroups`; bubble card sets from `bubble()`). Order: Hero → Worum es geht → Zahlen → Der Start (4 static steps) → In deiner Nähe (postcode search + result card + tile-free preview) → Was ist ein Fairteiler? → Für Betriebe → Weitere Wege zu helfen → Deine Verpflichtungen → FAQ (12 questions, 3 groups) → CTA band, plus a sticky "Jetzt mitmachen" on phones. No map tiles and no third-party host are requested from `/`.

## Decisions
Record as **decision 159** (158 is taken by MT1 on the tiles branch; whichever branch merges second renumbers) and **ledger row C17** (the old explainer tests ran under `@C1`; C16 is the last C row). It amends `plan.md` §11.2 (hero "primary CTA Mit Google", the region card with ambassadors / next EinAb dates / map, the contact form for businesses) and the 2026-11-02 public-site owner decisions in `docs/design/public-site-proposal.md` §10, which were never copied into `decisions.md`:
- **O1 "No yellow" → amended:** `--fs-accent` only as the warm bubble blob (45 %) and the Fairteiler pin glyph in the preview (always with the fridge icon). No yellow tiles, buttons, chips or backgrounds. No "ohne" toggle in the product.
- **O11 "6 public steps" → replaced:** the landing page shows 4 static steps (Konto, Grundlagen, Quiz, Einführungsabholungen & Ausweis) with no detail panel and no click. `/onboarding` keeps 8. `Explainer.journeyPreview` stays in the contract and on the server but is unused on `/`.
- **O8 "Email primary" → confirmed:** E-Mail solid and first, Google outlined second (hero and CTA band). D11.5.1 (≤ 2 clicks with Google) still holds.
- O4 (no long durations) holds: step 4's chip is "nach Terminen in deinem Bezirk"; O7 (ID line in the FAQ) holds via `faq.id`; O16 (counts always fine) covers `storeCount`/`memberCount` on the result card.
- **Map (owner, option 1):** a static inline SVG preview with "Karte öffnen" → `/map`. Pins come from `listPublicRegionMarkers` + `listPublicFspMarkers` projected onto the fixed DACH frame; on any error the pins are silently omitted and the canvas never shows a loading state (Q2).
- **Partner quote** kept, labelled "Beispielzitat" (Q4 overridden by the owner). **"Keine Mindestzahl an Abholungen"** kept (Q7): there is no minimum rule, only maxima (PU5).
- **"Partner werden"** → `/support?from=/&subject=partner`. `/support` prefills the subject only for allowlisted presets (`partner`), never with free text from the URL.
- **No contract change.** The result card uses `suggestRegions(postcode)` → first suggestion → `getPublicRegion(slug)` (`storeCount` = established stores = "aktive Kooperationen", `memberCount`, `pin`). Fairteiler names: the 2 nearest FSP markers within 10 km of the region pin (haversine, client-side), with names from `getPublicMapBubble('FSP', id).title`. The designer's optional `memberCount/storeCount/slug` on `MapBubble` is not needed, because D has no map popups on `/`.
- Postcode rule: 4 or 5 digits (CH/AT/DE). Anything else shows `near.search.invalid` and sends no request.

## Scope
In: the landing page and its section components, the static preview, the bubble pattern, i18n in five languages, map tokens in `styles.scss`, the `/support` subject preset, unit tests, e2e and screenshot specs, and docs (decision, ledger, plan §11.2).
Out: `/map` and `shared/map-view/map-view.component.ts` (do not touch; MT1 works there), tiles, click-to-load map (option 2), the "district" bubble set, region page, `/how-it-works`, header/footer, `JourneyStepper` (stays for `/onboarding` and the gallery), the server and the contract, seed (no new entity), baseline PNGs (end batch, decision 69).

## Changes
Branch from `main` (e.g. `feat/public-landing-page`), not from `feat/demo-banner`. Commit the three untracked design docs (`landing-page-proposal.md`, `landing-page-mockup.html`, `public-site-proposal.md`) with the branch, because the decision references them.

**Client, `client/src/app/features/public/`**
- `pages/landing-page.component.ts`: rewrite the template in the order above; move the styles to `landing-page.component.scss`. Keep `GOOGLE_JOIN_URL`, the `lang` input and the `SeoService` alternates exactly as they are. Keep the `explainer` `loadResource` (`isEmpty: () => false`) and `<fs-data-state data-testid="public.explainer.state">` around the counters only. The strip shows kgSaved · pickups · foodsavers · stores with `fs-count-up` (unchanged component); `regions` is dropped on `/`. Drop the imports of `JourneyStepper`, `RegionPicker` and `RegionCard` (the picker embeds a MapView), the values cards and `businessMail`. Replace `EXPLAINER_FAQ` with `LANDING_FAQ_GROUPS` (join: what, who, cost, time, id · pickup: duties, cancel, all, safe · share: fairteiler, basket, business).
- `components/landing-bubble.component.ts` (`fs-landing-bubble`): `kind` input (`hero|mission|fsp|business|ways`) and `size` input (`lg|sm`). Blob A, blob B (accent 45 %), main card −3°, side card +4°, "Beispiel" label, `aria-hidden`, `pointer-events: none`. The phone layout (container < 900 px) is 14 rem, clipped and shown (the hero bubble goes below the buttons and is no longer hidden). It replaces today's `.hero__art`. Icons are PrimeIcons; for the fridge, use the same glyph the map uses, copied, without editing map-view.
- `components/landing-journey.component.ts`: a static `<ol>` with phase items Online / Vor Ort / Dabei, steps `public.landing.journey.step.1-4`, the end node "Du holst selbst ab", wrapper testid `public.explainer.preview`. It is horizontal from 900 px and vertical below (line on the left). No buttons, no `aria-current`.
- `components/landing-near.component.ts`: the postcode form (`<label>`, `aria-describedby` hint with `aria-live`), `postcode = signal<string|undefined>()`. `near = loadResource({ params: () => postcode(), loader })`, where the loader is suggest → getPublicRegion → nearest FSP names, and returns `NearResult | null`. `isEmpty` is true for no suggestion. Use `<fs-data-state data-testid="public.landing.near.state">`: skeleton = 3 lines, empty = "Noch kein Bezirk – gründe einen", error = "Suche gerade nicht möglich" (also 503 `GEOCODING_UNAVAILABLE`) + retry, notFound = "Bezirk nicht gefunden", offline. Before any search, show the dashed hint card. The result card has `aria-live="polite"`, plus `near.regionLink` → `/r/:slug` and `near.openMap` → `/map`. A failing bubble call leaves out that name; no FSP within 10 km gives `.result.noFsp`.
- `components/landing-map-preview.component.ts`: the inline SVG from the mockup's `LAND` (`role="img"`, `aria-label` = `.previewAlt`). It reads the shared markers resource (below). Pins render only on success (decorative, `aria-hidden`), and no fs-data-state is shown, by design (§5). The note says `.previewNote` with counts, or `.previewNoteNoCount` when the markers are missing. The "Karte öffnen" link goes to `/map`.
- `landing-markers.resource.ts` (exempt from `check-data-state` by name): a component-level provider on the page wrapping one `loadResource` that loads both marker lists via `forkJoin`, each `catchError → []`. The preview reads it for pins; the near loader reads its FSP markers for the nearest names (falls back to none).
- `landing-geo.ts`: `distanceKm`, `nearestFsps(pin, markers, max=2, km=10)`, `projectToFrame(lat, lon)` (a linear fit to the mockup frame `{x:170,y:20,w:410,h:420}`, using the mockup's Zürich/Berlin/Wien/Hamburg/Genf positions; outside the frame → `null`), and `isPostcode`.
- Put `In deiner Nähe` (near + preview) inside `@defer (on viewport)` with a placeholder of the same height, so `/` stays light for Lighthouse and the prerender. The sticky join uses an IntersectionObserver on the hero and the footer, only in the browser (`isPlatformBrowser`), and is shown only on phones.
- `i18n/{de,en,gws,fr,it}.json`: the keys of proposal §7 with the de texts from the mockup. Leave out `step.*.point1..3` and `step.next/prev` (no detail panel). Reword `lead` to "du" and `counter.stores` to "Partnerbetriebe"; add `counter.pickups`. Reword the existing FAQ answers to the mockup (`time` changes). Add `id, duties, cancel, all, safe, fairteiler, basket, business` and the group titles, and `previewNoteNoCount`. Remove `valuesLabel`, `value1-3*`, `regionTitle/Lead`, `businessText/Cta` and `counter.regions`.

**Client, elsewhere**
- `client/src/styles.scss`: `--fs-pub-map-land/water/park/line` light and dark (values from the mockup lines 18 and 27).
- `features/content/pages/support-page.component.ts`: a new `subject` query input. `SUBJECT_PRESETS = { partner: 'content.support.subjectPreset.partner' }` prefills `subject()` once, only when it is empty. Add the key to `features/content/i18n/*.json` (5 languages).

**Server / contract / Liquibase / seed:** none.

**Docs:** `docs/decisions.md` row 159, `docs/ledger.md` row C17, and `plan.md` §11.2 (search `### 11.2`) amended to point to decision 159. In `todo-eric.md`, add a native-speaker check of the new gws/fr/it texts.

## UI
Yes, a large visible change. The approved mockup is `docs/design/landing-page-mockup.html` (Variant D, Akzent "sparsam", optional sections on, map option 1). No new designer round is needed.

## Tests
- Unit (`ng test`): `landing-geo.spec.ts` (distance, nearest-2 within 10 km, projection of Zürich inside the CH shape and of out-of-frame points to `null`, postcode 4/5 digits). `landing-near` spec (invalid → no call; empty; result with counts and FSP names; bubble failure drops a name; 404 → notFound; 503 → error + retry). `landing-map-preview` spec (pins on success, none on error, never a loading state). `landing-page` spec (section order and testids, E-Mail before Google, no `MapViewComponent` in the tree). Support spec (`subject=partner` prefills; an unknown value is ignored).
- E2e: replace `e2e/tests/public/explainer.spec.ts` with `e2e/tests/public/c17-landing.spec.ts` tagged `@C17`. It covers:
  - all section testids
  - counters equal the live explainer
  - 8004 → result card shows the `getPublicRegion('zuerich-stadt')` counts; regionLink → `/r/zuerich-stadt`; openMap → `/map`
  - invalid postcode hint; empty (route `suggest` → `[]`); 503 + retry; explainer error + retry (kept from C1)
  - FSP markers 500 → preview without pins, search still works
  - partner CTA → `/support` with the prefilled subject
  - no request to any host other than the app origin while `/` loads and after a search (`page.on('request')`)
  - reduced motion shows the final counter values
  - phone: sticky join appears after scrolling
  - axe after the result card is shown
- Screenshots: move the `explainer` block out of `e2e/screenshots/onboarding.screen.ts` into a new `e2e/screenshots/landing.screen.ts` (`@C17 @screenshot`): full page, result card, counters skeleton/error, near empty/error, all variants (light/dark, desktop/phone). The `explainer-*` baselines are deleted and the new ones are generated in the end batch (decision 69).
- Unchanged and must stay green: `d4-smoke` (prerendered hero testid), `d11-journey` (@D11.5.1 Google CTA), `a11y/routes.ts` ('/' and the five languages), `i18n-smoke`, `d11-lighthouse` (perf ≥ 70, a11y ≥ 95), the persona walkthroughs.

## Done when
- [ ] `/` matches Variant D in light and dark, at 412 px and desktop, in all five languages; `npm run i18n:check` passes with no new missing keys.
- [ ] The page has no `MapView`, no OSM or other third-party request, and no live tiles; "Karte öffnen" links to `/map`.
- [ ] Counters, postcode search and result card use DataState with every state in §5; the preview fails silently.
- [ ] E-Mail is solid and first; the Google flow still takes ≤ 2 clicks (D11.5.1).
- [ ] The partner CTA opens `/support` with the subject prefilled.
- [ ] All proposal §2 testids are present; unit, `@C17` e2e and screenshot specs are written; axe, Lighthouse, `d4-smoke`, lint and `check-data-state` are green.
- [ ] `map-view.component.ts`, the contract and the server are untouched; decision 159, ledger C17 and plan §11.2 are updated.

## Risks
- **Lighthouse on `/`** (93 now, gate 70): the page triples in length. Keep it to CSS shapes and the inline SVG with no images, defer "In deiner Nähe", and lazy-load nothing heavy.
- **Prerender:** `matchMedia`, IntersectionObserver and `location` only in the browser. `loadResource` already stays `loading` on the server, so the counters are prerendered as a skeleton, as today.
- **Merge with MT1:** the decision number and ledger rows collide. There is no code overlap as long as map-view and the CSP stay untouched.
- A search makes up to 4 public calls (suggest, region, 2 bubbles), well under the `/api/public/**` per-IP limit.
- If the `SUPPORT_FORM` toggle is off, the partner CTA lands on the disabled support page. That is accepted; it is the same as the FAQ "uns schreiben" link.
- The gws/fr/it texts need a native check (todo-eric).
