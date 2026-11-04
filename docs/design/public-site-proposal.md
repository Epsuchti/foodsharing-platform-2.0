# Public site: clearer, cleaner, one photo plus icons — design proposal

Status: round 4, with the owner's answers applied (section 10). No product code changed. Mockup: `public-site-mockup.html` next to this file (toggles: page, new/today, map role, theme, width window/1024/412, DE/EN; the how-it-works stepper has a five-language length test). Store rules live in `docs/tasks/2026-11-02-store-visibility.md` (S17, its D1–D9); this proposal only designs them.

## 1. Problem and findings (from the baselines and the code)

- **Unclear in seconds.** "Retten statt wegwerfen" is a slogan. Who it is for and what you physically do must be pieced together.
- **Too many actions, no leader.** Two equal hero buttons, Anmelden + Mitmachen in the menu, the petition banner on every page, 16 equal side-menu links.
- **Empty look.** Landing counters show 0 kg / 0 stores while `/statistics` shows other numbers. Missing gap between counters and journey title; empty deferred region block.
- **Jargon.** "Bezirk", "Fairteiler" (also "Fair-Teiler") and "Einführungsabholung" are unexplained.
- **Stepper.** The vertical list fills the left half of a desktop page.

### Store leaks

Enforcement and the full list are in the S17 plan. Found in this review and fixed there:

| # | Where | Leak | S17 |
|---|---|---|---|
| L1 **blocker** | `PublicIntroPickup.businessName`, rendered on `/r/:slug/intro-pickups` (baseline `intro-public` shows store names); also in `getPublicRegion` and the explainer | store names, public | D5 |
| L2 | `category`, `publicGeneralLocation`, `publicInfo` on the same model | hint per location | D5 |
| L3 | `listStoreMarkers`, authenticated store bubble, `listRegionStores`, search | all stores to the unverified | D1, D2 |
| L4 | role checks instead of `User.verified` (`mayViewPublic`, metadata, chains, nav) | store detail for pending people | D1 |
| L5 | `BookableIntroPickup.businessName` | names before booking | D5 |
| L6 | `/about/partners`, region descriptions, blog, press are free text | editors can name stores | D8, editor hint |

Counts are not a leak by owner decision (S17 D4). Copy that implies logging in shows stores goes away (`map.page.subtitle`, `loginHint`, search placeholder). Images are covered in section 5.

## 2. States

| Screen | States |
|---|---|
| Landing | counters skeleton / error + retry (`public.explainer.state`); deferred region block keeps its height |
| Stepper | loading / error as today; a server step key not mapped to a phase fails a unit test |
| Find region | no input, match, no match ("noch kein Bezirk" + join), location denied, list, offline |
| Public region | as today plus: no dates, no description, with or without the region's own `photoUrl`, not found |
| Sign-up | email form, invalid email, link sent, Google error, rate limit, method disabled (`loginMethods`) |
| Map anonymous | FSP, regions, baskets, "Betriebe pro Stadt"; loading, error, no bubbles (layer row hidden) |
| Map unverified | same + events + search; never store markers |
| Map verified | today's layers + store markers + filters; no bubbles |
| Intro pickup, signed in | list without names → booked card with the name of that one store → cancelled: name gone again |
| Store form | toggle off (default); on with warning and confirm; disabled while `publicity` is not ALLOWED; leaving ALLOWED clears it |

No text anywhere explains that stores are not listed (owner decision).

## 3. Visual spec

**Direction: one photo, then clean icons.** DSC08565, a mountain of food on green crates, is the only photo on the public site. It opens the landing page and sets the mood; everything else stays quiet: Inter 800/850 headlines (hero `clamp(2.4rem, 1.3rem + 4.2cqi, 4.4rem)`, tracking −0.04 em), white and `--fs-bg` sections, today's cards, line icons on soft green tiles. The crate green and `--fs-primary` sit close, so no new green is needed. No yellow accent: the mockup uses no `--fs-accent`, and public pages must drop the accent glow from `body`.

- **Hero:** text left, photo right to the window edge, full hero height, no radius. Phone: 16:9 under the text.
- **Join band:** soft green (`--fs-primary-soft`) with green email button and outlined Google.
- **Cards "Was du tust":** icon tile, title, text.
- **Line:** the plain green stepper line.

**Tokens.** All existing `--fs-*`, plus, for the abstract mockup map only, `--fs-pub-map-*`, `--fs-pub-pin-event`. Basket pins move from brown to teal `#0f6e7a` (5.9:1 with white).

**Top bar for anonymous, side menu once signed in.** Anonymous: brand, So geht's, Karte, Bezirke, Über uns, language, theme, Anmelden, Mitmachen, plus a four-column footer. Signed in: the existing side menu on every page (phones: its tab bar), also on `/map`.

**One primary per screen.** Landing: green "Mit E-Mail mitmachen", outlined "Mit Google" beside it. Header Mitmachen and the phone sticky bar appear only after the hero scrolled away. Region: "In Zürich mitmachen". Find: "Zu deinem Bezirk". Sign-up: email field and "Anmeldelink schicken" first, then outlined Google and passkey.

**D11.5.1 stays as written:** a new user reaches "account created" in ≤ 2 clicks with Google. `public.landing.joinGoogle` stays a direct link with the same `href`, so it holds. Recheck by running the spec after the swap.

**New page** `/how-it-works`: stepper, the three phases, the trust block, FAQ, join band. The petition banner is off all public pages; while a petition runs, a plain footer link.

### Message rules

Lead with how little the start takes ("Konto in 2 Minuten", minutes on online stops only). Nothing about weeks or days on public pages, and no sum of durations. Nothing claims the whole way is quick: the FAQ says the intro pickups "richten sich nach den Terminen in deinem Bezirk", and "Was brauche ich? Einen amtlichen Ausweis, den deine Botschafter:innen einmal prüfen." states the ID without a duration.

Trust block (`public.how.trust`): "Verbindliche Verträge. Vertrauen über Jahre." / "Mit unseren Betrieben schliessen wir rechtlich verbindliche Vereinbarungen: Wir holen zur vereinbarten Zeit ab, und ab der Übergabe tragen wir die Verantwortung. Dieses Vertrauen ist über die Jahre gewachsen, Abholung für Abholung. Darum lernst du zuerst die Regeln, stimmst der Vereinbarung zu und gehst die ersten Male in Begleitung mit." Have someone with legal knowledge read "rechtlich verbindlich" before it ships.

### Stepper

Public `mode="preview"`: six stops in three phases. **Online** "in deinem Tempo" (Konto erstellen, Grundlagen lernen, Foodsaver-Quiz, Vereinbarung), **Vor Ort** "in Begleitung" (Einführungsabholungen), **Dabei** "dein Ausweis" (Foodsaver-Ausweis). `PROFILE_REGION` and `ID_CHECK` are hidden.
- **Durations:** `estimatedDuration` is optional and sent only for online steps (S17 D9, where PASSPORT keeps `PT2M`). The client shows it only when present, so stop 5 shows none.
- **No stop numbers.** Numbers 1–6 would collide with "Schritt 2 von 8" right after sign-up. The list stays an `<ol>`.
- **How to hide:** drop the two steps from `ExplainerService.PREVIEW`. `explainer.spec.ts` compares with `journeyPreview.length`, so it follows. Fix "Acht Schritte" in `public.explainer.journeyLead`.
- **Width:** horizontal when its container is ≥ 48rem (6 columns of ≥ 121 px), vertical below. Rem-based, so zoom and large text fall back on their own. Soft hyphens in long words (`Einführungs&shy;abholungen`, `Iifüehrigs&shy;abholige`, `d’intro&shy;duction`, `intro&shy;duttivi`), `overflow-wrap: anywhere` as last resort; `subgrid` keeps phase heads and dots aligned.
- **`/onboarding` (`full`) keeps all 8 real steps and stays vertical**, because progress, waiting contacts and blocked reasons need width. It gains a slim status rail from a 44rem column (`aria-hidden`) and phase headings without times. `compact` is unchanged.

## 4. Map: store counts per city (anonymous and unverified)

The rule is S17 D3 and D4: one bubble per CITY-level region with at least one store, exact count, no minimum, no rounding, no merging by count, part-of-town and district roll up into the city, position from the region's own coordinates, only live established stores.

Design: layer "Betriebe pro Stadt" (`map.page.layer.storeAreas`), on by default. A dashed circle with the city and count ("Zürich", "16 Betriebe"; singular "1 Betrieb"), fixed size, does not split when zooming. A click opens the popup "16 Betriebe · Zürich", no link. Verified members see the full store layer and no bubbles. Subtitle: "Fairteiler, Bezirke und Essenskörbe in deiner Nähe." The mockup shows the seed values Zürich 16 and Winterthur 2, plus Uster 1 as a sample.

## 5. Images

One photo plus icons. **Rule for every public image, including a region's own `photoUrl`:** no store name, logo, shopfront, poster with a date, receipt or branded packaging. IFCO on the crates is a pooling company and fine when small. Originals stay outside the repo.

| Slot | Page | Source and crop | Ratio | Alt text (DE) | People |
|---|---|---|---|---|---|
| HERO | Landing | DSC08565; graffiti and bunting at the top cropped out, food anchored high | 4:3 file, hero height on desktop, 16:9 on phones | Ein Berg aus Brot, Trauben und Gemüse auf grünen Kisten | none |

**Icons** (PrimeIcons, as in the app; `aria-hidden`, on `--fs-primary-soft` tiles): Abholen `pi-box`, Mitnehmen `pi-shopping-bag`, Teilen `pi-share-alt`. The mockup draws matching inline line icons, since it has no icon font.

**Slots dropped, nothing in their place:** the trust block, sign-up and About are plain text, the second landing photo and the crate band are gone. The public region page has a plain header and shows the region's own `photoUrl` only if the region has one (as `regions.public.photoAlt`; the rule above goes into the editor hint).

**Production:** one derived file in `client/public/images/public/`: WebP with AVIF first, 480 / 960 / 1600 widths via `srcset`, `sizes`, explicit `width` and `height` against layout shift, EXIF and location stripped, preloaded. Self-hosted.

**Mockup only:** the photo is embedded as WebP (1380 px), because the browser pane refuses files of about 520–600 KB and more.

## 6. Copy and i18n keys (all five languages at implementation; gws/fr/it need a native check)

Keep keys, change text: `public.landing.title` = "Wir holen ab, was sonst im Müll landet."; `public.landing.lead`; `public.landing.joinEmail`; `public.explainer.journeyTitle` = "Der Start dauert nur 2 Minuten."; `journeyLead`; `regionTitle`; `faq.time.a`; `regions.find.title` = "Wo wohnst du?". Map copy: as in S17.

New: `public.landing.fact.{account,free,age}`; `public.do.*`; `public.who.*`; `public.journey.phase.{online,onsite,done}.{name,note}`; `public.journey.step.<KEY>.{title,text}`; `public.how.*`; `public.how.trust.{kicker,title,text}`; `public.explainer.faq.need.{q,a}`; `public.join.*`; `public.nav.*`; `public.footer.*`; `public.images.HERO.alt`; `regions.public.signupIn`, `regions.public.afterBooking`, `regions.public.photoAlt`. Store form keys per S17 (`stores.form.publicName` plus warning and confirm). "Fairteiler" becomes the one spelling.

## 7. Accessibility

- Never colour alone: layers differ by shape, icon, name and "an/aus"; the count bubble is text; the warning has an icon and a bold first sentence.
- Contrast: existing tokens (primary 4.75:1); basket pin 5.9:1. axe still to run on the build.
- Stepper: nested `<ol>`, `lang` on the list, rail `aria-hidden`. The hero photo has alt text, icons are `aria-hidden`. No text sits on the photo.
- Map: pins and bubbles are named buttons, 44 px targets; `/find-region` is the non-map route. Layers: `role="group"`, `aria-pressed`.
- Store toggle: `role="switch"`, `aria-checked`, warning in a `role="note"`, confirm step; focus returns to the switch.
- Keyboard: skip link, visible focus, FAQ on `<details>`, `aria-live="polite"` for the region result, `aria-invalid` + `aria-describedby` on email. Reduced motion: count-up off.

## 8. Mobile (412 px)

Top bar: brand, Anmelden (Mitmachen after the hero), menu. Both join buttons full width, then the photo. Sticky "Mit E-Mail mitmachen" after the hero, landing only. Map first, layers below. Signed in: top row and tab bar instead of the side menu. Targets ≥ 44 px. Audited with a script at window 1440, 1024 and 412, DE/EN, light/dark, all pages, roles and states, five stepper languages: no horizontal overflow.

## 9. Test ids

Keep: `public.landing.{hero,joinGoogle,joinEmail,login}`, `public.explainer.{state,counters,counter.*,journey,preview,region,picker.*,regionCard,regionCard.date,faq,faq.*,business,businessMail}`, `regions.find.picker.*`, `regions.public.*`, `auth.login.*`, `map.page.{layers,layer.*,search,searchSubmit,nearMe,map,storeFilter.*,userFilter.*}`, `map.bubble*`; `data-step`. `map.page.loginHint` and `map.page.login` go away.

New: `public.nav.*`, `public.how.trust`, `map.page.layer.storeAreas`, `map.page.storeArea`, `introPickups.booked.business`, `stores.form.publicName` and its warning and confirm. E2E per S17. Baselines regenerate in the batch (decision 69).

**Rejected:** (1) horizontal 8-step stepper as in round 1, replaced by the owner's 6; (2) an interactive stepper with a detail panel, hiding facts behind clicks; (3) generic ghost store dots on the map, because any dot suggests a location; (4) flat produce tiles and a set of photos, replaced by one photo plus icons.

## 10. Owner decisions (2026-11-02)

**Look and message.** O1 No yellow; clean green on light surfaces. O2 DSC08565 is the only photo; the rest are icons, or the slot goes. O3 Headline stays. O4 No long durations on public pages. O5 Trust text about binding contracts and years of trust. O6 "Fang mit Schritt 1 an" removed; petition banner off public pages, footer link while it runs. O7 FAQ line about the ID, no duration.

**Sign-up and navigation.** O8 Email primary, Google secondary. O9 D11.5.1 stays as written; no separate click rule for email. O10 Top bar for anonymous, side menu once signed in.

**Stepper.** O11 Public stepper has 6 steps; identity check, "from when you see stores" and Profil & Bezirk are not shown; `/onboarding` keeps 8. O12 `estimatedDuration` optional, only for online steps.

**Stores** (binding wording in S17). O13 No text anywhere explains that stores are not listed. O14 Bubbles at city level; part of town rolls up. O15 Exact counts, no rounding. O16 Counts are always fine: no minimum, no hiding; the region count and landing counter stay; `/statistics` stays. O17 Public pickup: date, Vor Ort/Online, city, free places. O18 A pickup's city is the linked store's city. O19 Business name only after booking that pickup, only that store. O20 `/about/partners` lists only stores with the new flag, default off, with a warning. O21 Store managers and ORGA switch the flag on and off; on only with `publicity = ALLOWED`. O22 Unverified people can never join a store. O23 Partners text stays, including "Grossverteiler".

## 11. Open questions

None.
