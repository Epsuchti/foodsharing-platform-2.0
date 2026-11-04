# Landing page (logged out): Variant D — design proposal

Status: round 2. Round 1 offered A/B/C; the owner picked pieces of each, and Variant D is the merge (section 2). A/B/C stay in the mockup for comparison only. No product code changed. Mockup: `landing-page-mockup.html` next to this file (bar: Variante D / A / B / C / Heute, Hell/Dunkel, Fenster/412 px, Akzent sparsam/ohne, data state OK/Skeleton/Fehler/Leer/Offline, optional sections on/off; the "In deiner Nähe" section has its own switch between the two map options).

Relation to `public-site-proposal.md` (2026-11-02): only `/` is re-planned. The owner's new decisions override O11 (6 public steps → 4) and replace the region card block. O1 (no yellow) is back on the table (Q1). Header, footer, `/how-it-works`, `/map`, region page and store rules (S17) stay as decided there.

## 1. Problem

- The page is a slogan plus feature cards; it does not say in the first seconds what you physically do, nor speak to the reader ("du").
- Fairteiler and districts are unexplained; "find your region" is a bare form.
- Eight steps with weeks look like a job application; the owner wants four honest steps with minutes.
- Businesses get one line and a mailto; the real benefits are missing.
- A live map on `/` would hit `tile.openstreetmap.org` directly (`client/src/app/shared/map-view/map-view.component.ts:55`, `OSM_TILE_URL`). The OSM tile usage policy forbids heavy use, gives no SLA and may block sites; a logged-out page with the most views is exactly that case.

## 2. Variant D: today's hero, bubbles as the signature

| Section (order) | Taken from | What it is | Test ids (keep = today) |
|---|---|---|---|
| Hero | Heute | pill, "Retten statt wegwerfen.", "du" lead, Google + E-Mail buttons, "Schon dabei? Anmelden", right: the blurred green + warm blob with the floating example cards ("Abholung Bäckerei · Heute 18:30 · 2 Plätze frei", "♥ 12 kg gerettet") | `public.landing.hero`, `joinGoogle`, `joinEmail`, `login` (keep) |
| Worum es geht | B (copy) + new bubble | one paragraph from the user's view, three ways (Mitglied / zu Hause / Betrieb) as icon rows; bubble "Bäckerei an der Ecke · 4 Brote, 2 kg Gemüse" + "geteilt mit 3 Nachbar:innen" | `public.landing.mission`, `public.landing.way.<member\|home\|business>` |
| Zahlen | A | dark-green strip: kg gerettet · Abholungen · Foodsaver:innen · Partnerbetriebe, white numbers, `de-CH` grouping (1 842 300), count-up 1.2 s, reduced motion = final value | `public.explainer.state`, `counters`, `counter.kgSaved/pickups/foodsavers/stores` (keep) |
| Der Start dauert nur 2 Minuten | Heute (stepper look) | phase labels Online / Vor Ort / Dabei above, 4 circles on a line, title, one line, "ca. X Min." chip; step 4 chip "nach Terminen in deinem Bezirk"; end node "Du holst selbst ab"; static overview, no detail panel and no click (owner, round 3: the line already says enough). Phone: the same line, vertical (no accordion) | `public.explainer.journey`, `preview` (keep), `public.landing.journey.step.<1-4>` |
| In deiner Nähe | new | postcode search → result card (district name, aktive Kooperationen = `storeCount`, Mitglieder = `memberCount`, nearest Fairteiler, "Zur Bezirksseite", "Karte öffnen" → `/map`) and, beside it, the tile-free preview (section 3) | `public.landing.near`, `near.search`, `near.search.submit`, `near.result`, `near.regionLink`, `near.openMap`, `near.preview`, `near.state` |
| Was ist ein Fairteiler? (optional) | new, bubble | text + three rule chips; bubble "Fairteiler Wiedikon · offen für alle" + "Heute drin: Brot, Äpfel, Salat" | `public.landing.fairteiler` |
| Für Betriebe | C (card) + new copy | white card: pill, title, lead, **CTA first**, 6 benefits (icon + title + one line), small "Aufkleber für die Tür inklusive" line; next to it a `--fs-primary-soft` panel with the business bubble ("Abholung Mo–Sa, 18:30 · Dein Team: nur bereitstellen", "Haftungsfreistellung unterschrieben") and the optional quote slot | `public.explainer.business` (keep), `business.cta`, `business.benefit.<free\|cost\|staff\|accept\|reliable\|liability>`, `business.quote` |
| Weitere Wege zu helfen (optional) | new, bubble | Essenskörbe · Spenden · IT & Mitarbeit; bubble "Essenskorb: Lasagne, 4 Portionen" + "open source · werbefrei" | `public.landing.moreWays`, `.basket/.donate/.it` |
| Deine Verpflichtungen | new (owner, round 3) | lead "Betriebe müssen sich auf uns verlassen können"; white card "Das versprichst du" (zuverlässig sein / alles nehmen, nichts wegwerfen / nichts verkaufen / im Betrieb freundlich auftreten / vertraulich bleiben, paraphrased from the wiki Verhaltensregeln) + wiki link; soft-green card "Das musst du nicht" (keine Mindestzahl an Abholungen, keine Kosten und kein Vertrag, nie allein am Anfang). Desktop two columns, phone stacked | `public.landing.duties`, `duties.<reliable\|take\|nosale\|conduct\|privacy>`, `duties.not.<min\|money\|alone>`, `duties.rules` |
| FAQ | Heute, regrouped (round 3) | 12 questions in three groups, three columns on desktop: Mitmachen (what, who, cost, time, id) · Abholen (duties, cancel, all, safe) · Teilen ohne Konto (fairteiler, basket, business); footer "Im Wiki nachlesen" / "uns schreiben". A–C keep a flat list of the same questions | `public.explainer.faq`, `faq.<id>` (keep), `faq.group.<join\|pickup\|share>`, `faq.wiki`, `faq.support` |
| CTA band + sticky | A | soft-green band "Bereit? Dein Bezirk wartet."; phone sticky "Jetzt mitmachen" after the hero scrolled out | `public.landing.stickyJoin` |

Removed from today: the three value cards (folded into Worum es geht), region picker + region card, `journeyPreview` with 8 steps, `public.explainer.businessMail`, counter `regions` on `/` (stays in the API and on `/statistics`).

**Bubbles.** One pattern, `fs-bubble` (illustration only: `aria-hidden`, `pointer-events: none`, cards labelled "Beispiel", no hover): blob A radial `--fs-primary-200` → `--fs-primary-soft`, blob B `--fs-accent` at 45 % (with Akzent "ohne": `--fs-primary-200`), one main card rotated −3°, one side card +4°, `--fs-shadow-lg`. Sizes: hero 22 rem, section bubbles 13 rem. Phone (< 900 px container): 14 rem high, `overflow: hidden`, radius `--fs-radius-xl`, cards 0.85 rem, stacked below the text (mission, business, Weitere Wege) or above it (Fairteiler); the hero bubble shows below the buttons instead of being hidden as today. Six card sets: hero, mission, fsp, district (for the result card when a region page wants it), business, ways.

**Business copy (de).** Lead: "Wir holen ab, was du nicht mehr verkaufen kannst. Du stellst es bereit – alles andere übernehmen geprüfte Foodsaver:innen aus deiner Nähe." Benefits: Kostenlos — "Keine Gebühren, kein Vertrag mit Laufzeit. Nichts wird weiterverkauft – die Lebensmittel werden verschenkt." · Weniger Entsorgungskosten — "Weniger im Container, weniger Leerungen, weniger Gebühren." · Kein Mehraufwand für dein Team — "Foodsaver:innen sortieren, packen und tragen selbst weg." · Wir nehmen fast alles — "Abgelaufenes MHD, Milchprodukte, Brot vom Vortag, beschädigte Verpackungen." · Zuverlässig – auch am Wochenende — "Feste Abholzeiten, früh oder spät, samstags und sonntags. Fällt jemand aus, springt das Team ein." · Haftungsfreistellung — "Jede Foodsaver:in unterschreibt sie in unserer Rechtsvereinbarung – vor der ersten Abholung." The sustainability image became the one-line tag under the list. Quote stays a placeholder marked "Beispielzitat" until a real one exists (Q4).

**Accent.** `--fs-accent` appears only as the warm hero/section blob at 45 % and the Fairteiler pin glyph in the preview (fridge icon, so never colour alone). No yellow tiles, buttons, chips or backgrounds. The mockup toggle "Akzent: ohne" overrides `--fs-accent` with `--fs-primary-200` (dark: `#2f6b45`) so the page can be judged with no warm tone at all.

## 3. Map decision: no tiles on `/`

Two options are in the mockup ("In deiner Nähe" switch):

1. **Static preview (recommended).** A stylised DACH SVG (land, water, parks, region pins, a few Fairteiler pins, `role="img"` with a text alternative) with the line "14 Bezirke · 11 Fairteiler. Die echte Karte lädt erst auf der Kartenseite." and the button "Karte öffnen" → `/map`. No network request, no third-party host on the landing page, renders identically in skeleton tests, works offline. Pin positions come from `listPublicRegionMarkers`/`listPublicFspMarkers` projected onto the fixed DACH frame (or are omitted and the SVG stays decorative; Q2).
2. **Click-to-load.** A dashed placeholder "Karte laden – die Karte holt Kartenbilder von einem externen Anbieter" and the real `MapView` only after the click; the postcode result then zooms it. Still a tile load per interested visitor, needs the DataState for the map and the "Karte aktivieren" overlay on phones.

Recommendation: option 1. It meets "not dominant", "no live tiles", and the search result card already answers the question the map would answer. `/map` itself needs a production tile provider or self-hosted tiles anyway (section 10) — a follow-up, not part of this page.

Postcode flow: `suggestRegions(postcode)` → `RegionRef` → `getPublicRegion(slug)` for `storeCount`, `memberCount`, `pin` → nearest Fairteiler from `listPublicFspMarkers` by distance to the pin (client side, 2–3 names). No contract change.

## 4. Rejected or merged: A, B, C

- **A "Karte zuerst"** — map hero rejected (tiles, dominance); its numbers strip, CTA band and sticky bar are in D.
- **B "Scroll-Reise"** — chapter typography and timeline rejected (too long, line illustrations replaced by bubbles); its "du" copy and the mission text are in D.
- **C "Bento"** — grid and yellow tiles rejected; its business card is in D with new copy.

## 5. States (DataState)

| Element | skeleton | empty | error | not-found | offline |
|---|---|---|---|---|---|
| Counters (`public.explainer.state`) | strip-high block | never empty (`isEmpty: () => false`); 0 renders "0" | card + Erneut versuchen | — | card + retry |
| Postcode search (`near.state`) | spinner in the button | "Noch kein Bezirk in deiner Nähe – gründe einen" | "Suche gerade nicht möglich" (`503 GEOCODING_UNAVAILABLE`) + retry | — | as error |
| Result card | 3 skeleton lines | — | retry | region gone → "Bezirk nicht gefunden" | as error |
| Static preview | none (inline SVG; pins appear when markers load, canvas never waits) | canvas without pins | canvas without pins (silent; the search still works) | — | same |
| Click-to-load map (option 2 only) | grey canvas | "Noch keine Bezirke auf der Karte" + join | "Karte konnte nicht geladen werden" + retry | — | "Du bist offline" + retry |
Stepper, mission, business, FAQ: static. Mutations: none.

## 6. Visual spec (tokens only)

- Type Inter Variable; H1 `clamp(2.1rem, 1.4rem + 3.2vw, 3.6rem)` 800, tracking −0.035em (today's); section titles `clamp(1.5rem, 1.1rem + 1.4vw, 2.1rem)` 800.
- Surfaces: page `--fs-bg`, cards `--fs-surface` + `--fs-border` + `--fs-shadow-sm`, soft panels `--fs-primary-soft`, strip `--fs-primary-strong` (dark theme: `--fs-primary-soft` with `--fs-primary-strong` numbers), preview `--fs-pub-map-*` tokens from the public-site round.
- Buttons: today's `fs-btn`; the hero keeps both buttons equal-weight as today (Google outlined left, E-Mail solid right: one primary per public-site O8).
- Stepper: circles 2.6 rem on a 2 px `--fs-border-strong` line, current `--fs-primary` fill, done `--fs-primary-soft` with a check; phase labels 0.78 rem uppercase `--fs-primary-strong` on a `--fs-primary-200` rule.
- Motion: count-up 1.2 s ease-out; bubbles static. Everything off under `prefers-reduced-motion`.
- Radii `--fs-radius-lg` for cards, `--fs-radius-xl` for the CTA band and phone bubbles.

## 7. Labels and i18n keys (de in the mockup; en, gws, fr, it at implementation)

Hero: keep `public.landing.eyebrow/title/lead/joinGoogle/joinEmail/haveAccount/login/artExample/artPickup/artPickupTime/artSaved`; `lead` reworded to "du".
Mission: `public.landing.mission.title/.text`, `public.landing.way.member|home|business.title/.text`, bubble texts `public.landing.art.mission.main/.mainSub/.side`.
Counters: keep `public.explainer.impactTitle`, `counter.kgSaved/foodsavers`; `counter.pickups` "Abholungen"; `counter.stores` → "Partnerbetriebe".
Stepper: `public.explainer.journeyTitle` → "Der Start dauert nur 2 Minuten.", `journeyLead`, `public.landing.phase.online|onsite|in` "Online / Vor Ort / Dabei", `public.landing.step.1..4.title/.text/.duration/.point1..3`, `step.end` "Du holst selbst ab", `step.next/.prev`.
Near you: `public.landing.near.title` "In deiner Nähe", `.lead`, `.search.label` "Finde deinen Bezirk", `.search.placeholder`, `.search.submit`, `.search.invalid`, `.search.noMatch`, `.result.kind` "Dein Bezirk", `.result.cooperations` "{n} aktive Kooperationen", `.result.members` "{n} Mitglieder", `.result.fsps` "Fairteiler in der Nähe: {names}", `.result.noFsp`, `.result.regionLink` "Zur Bezirksseite", `.openMap` "Karte öffnen", `.previewNote` "{regions} Bezirke · {fsps} Fairteiler. Die echte Karte lädt erst auf der Kartenseite.", `.previewAlt` (text alternative). Option 2 only: `.load.title/.text/.button`.
Fairteiler: `public.landing.fairteiler.title/.text/.rule1..3`, `art.fsp.*`.
Business: `public.explainer.businessTitle` → "Für Betriebe: Partner werden", `public.landing.business.lead/.cta/.tag`, `.benefit.free|cost|staff|accept|reliable|liability.title/.text`, `.quote/.quoteBy`, `art.business.*`.
Weitere Wege: `public.landing.moreWays.title/.lead`, `.basket|donate|it.title/.text`, `art.ways.*`.
FAQ: keep `public.explainer.faq.<id>.q/.a`; new ids `fairteiler`, `id`. Band: `public.landing.band.title/.text`; sticky `public.landing.sticky.join`.

## 8. Accessibility

- Bubbles are `aria-hidden`, non-interactive, labelled "Beispiel"; nothing in them is content a reader needs (the facts are repeated in text).
- Stepper is a static `<ol>` (no buttons, no `aria-current`); step numbers are text, phase labels are list items with text, not colour.
- Search: `<label>` + `aria-describedby` hint with `aria-live`; result card `aria-live="polite"`; invalid input is text, not a red border alone.
- Static preview: `role="img"` + `aria-label` naming what it shows; pins are decorative; "Karte öffnen" is a real link.
- Count-up keeps `data-value`; strip text white on `--fs-primary-strong` (8.6:1).
- Focus ring: the existing 3 px `--fs-primary` 45 % outline everywhere.

## 9. Mobile (412 px)

- Hero text first, buttons full width, bubble below (14 rem, clipped, smaller cards).
- Stepper vertical: phase label, its steps, next phase label; same circles, the line runs down the left.
- Near you: search, result card, then the preview (18 rem) — one column; "Karte öffnen" leads to `/map` instead of loading tiles inline.
- Strip 2 × 2; benefits one column; sticky "Jetzt mitmachen" after the hero is out of view, hidden above the footer.

## 10. Contract and follow-ups

- No contract change for D: `ExplainerCounters`, `suggestRegions`, `getPublicRegion` (`storeCount`, `memberCount`, `pin`), `listPublicFspMarkers`, `listPublicRegionMarkers` cover everything. `journeyPreview` stays in `Explainer` but is unused on `/` (Q3).
- `/support` reads `?from=`; subject prefill from `?subject=partner` is a client-only change.
- **Tiles (follow-up, not this page):** `/map` loads `https://tile.openstreetmap.org/{z}/{x}/{y}.png` directly. Before `/map` is linked from a public landing page it needs a production tile provider (e.g. a commercial OSM-based provider with an API key and usage caps) or self-hosted tiles, plus attribution; `OSM_TILE_URL` becomes configuration. Record as a todo for the operations owner.
- Route: the owner wrote `/karte`; the app route is `/map` (`app.routes.ts:178`); the mockup links there.

## 11. Questions (each with my recommendation)

- **Q1 Accent:** "sparsam" (warm blob + Fairteiler pins) or "ohne"? → sparsam; the warm blob is what makes the hero feel like today's, and it carries no information.
- **Q2 Preview pins:** drawn from the live marker endpoints (two small public calls, pins projected onto the fixed DACH frame) or a purely decorative SVG with fixed pins? → live markers, silently omitted on error; the canvas never shows a loading state.
- **Q3 Stepper source:** static 4 steps (recommended) or a new `journeyPreview` shape? → static; `/onboarding` keeps 8; the explainer call feeds only the counters.
- **Q4 Partner quote:** omit until a real one exists (recommended) or keep the "Beispielzitat"? → omit; the panel keeps the bubble alone.
- **Q5 Optional sections at launch:** Fairteiler explainer and Weitere Wege? → both yes (short, and they answer "Fairteiler" from the preview).
- **Q6 Hero buttons:** keep today's order (Google left, E-Mail right) or E-Mail first as in public-site O8? → E-Mail solid and first, Google outlined; D11.5.1 (≤ 2 clicks with Google) still holds.
- **Q7 "Keine Mindestzahl an Abholungen":** true for every district, or do some districts expect a minimum (or mark long-inactive foodsavers)? → keep the line only if no platform rule sets a minimum; otherwise say "Dein Bezirk sagt dir, was er erwartet."
