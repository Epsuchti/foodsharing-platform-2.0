# Landing page in the old foodsharing.network colours — direction D «Leichter Reskin»

Status: design proposal, round 1. Siblings: A, B, C (full redesigns, own files). Mockup: `landing-old-colours-d-mockup.html` (bar: Reskin D / Heute · Verlauf Dezent / Mittel / Kräftig · Hell / Dunkel · Fenster / 412 px · Zahlen Braunes Band / Panel · Daten OK / Skeleton). No product code changed.

## 1. Problem
The owner prefers a small reskin of today's Variant D (decision 160) over a full redesign: same layout, sections, copy, spacing, components and test ids; only the surfaces and colours change. Reference: a white card with a thin border, a solid green rounded-square icon tile with a soft shadow on the left, a soft green tint at the left edge fading out, bold heading, muted body. Wanted on this page: panels that are **green on the left and brown on the right**, with the old brown `#533a20` as ink and the old green `#64ae24` as accent, AA throughout, light and dark, phone and desktop.

## 2. What exists already
The reference card is not in this client (the string is from another project). Closest relatives: the dashboard welcome and the region/store/group page heroes (`linear-gradient(135deg, var(--fs-primary-soft), var(--fs-surface))` + `1px solid var(--fs-border)`), the `.einab__icon` tile (2.5 rem, `--fs-radius-md`, soft green) and `fs-date-tile` today (solid `--fs-primary` / `--fs-on-primary`). D composes those into one shared panel + tile style; nothing is reused verbatim.

## 3. Visual spec
### 3.1 New shared styles (`styles.scss`, `@layer app`, usable app-wide)
```
.fs-panel--brand   border:1px solid var(--fs-border); border-radius:var(--fs-radius-lg); box-shadow:var(--fs-shadow-sm);
                   background:linear-gradient(90deg,
                     color-mix(in srgb, var(--fs-primary)   var(--fs-panel-green),              var(--fs-surface)) 0%,
                     color-mix(in srgb, var(--fs-primary)   calc(var(--fs-panel-green) / 3),    var(--fs-surface)) 38%,
                     color-mix(in srgb, var(--fs-brand-tan) calc(var(--fs-panel-brown) / 3),    var(--fs-surface)) 62%,
                     color-mix(in srgb, var(--fs-brand-tan) var(--fs-panel-brown),              var(--fs-surface)) 100%);
.fs-panel--brand-soft / -strong   --fs-panel-green/-brown one step down / up (table below)
.fs-tile           display:grid; place-items:center; flex:none; width/height:2.5rem; border-radius:var(--fs-radius-md);
                   background:var(--fs-primary); color:var(--fs-on-primary); box-shadow:var(--fs-tile-shadow);  .pi 1.2rem
.fs-tile--lg       3.25rem, .pi 1.5rem (panel heads)
```
The panel takes its green from `--fs-primary` of the scope it sits in, so inside the app it is today's green and on the landing page the old green — one class, no second green family.

### 3.2 New global tokens (`:root` / `:root.fs-dark`)
| Token | light | dark | use |
|---|---|---|---|
| `--fs-brand-tan` | `#b8905c` | `#b8905c` | warm tint source of the right edge (never text) |
| `--fs-panel-green` / `--fs-panel-brown` | `14%` / `9%` (Mittel) | `18%` / `14%` | mix of the left / right edge |
| Dezent | `9%` / `6%` | `12%` / `9%` | `.fs-panel--brand-soft` |
| Kräftig | `20%` / `13%` | `26%` / `20%` | `.fs-panel--brand-strong`; CTA band is always one step above the page setting |
| `--fs-tile-shadow` | `0 4px 12px color-mix(in srgb, var(--fs-primary) 32%, transparent)` | `0 4px 12px rgb(0 0 0/.4)` | tile |
| `--fs-band-bg` / `-text` / `-muted` | `#3a2814` / `#f4ece0` / `#d9c9b2` | `#2e2217` / `#f4ece0` / `#c4b39c` | counters strip (Q3) |
| `--fs-brand-green` | `#64ae24` | `#8fd14d` | the exact old green: counter numbers on the brown band; never under white text |

### 3.3 Landing scope (`fs-landing-page :host`, `:host-context(.fs-dark)`) — overrides of today's tokens
| Token | today light | D light | D dark | AA |
|---|---|---|---|---|
| `--fs-text` | `#1a2a20` | `#533a20` | `#f4ece0` | 10.5:1 white, 8.1:1 strongest green tint; dark 14.5:1 |
| `--fs-text-muted` | `#5c6b60` | `#725a3c` | `#c4b39c` | 6.5:1 white, 5.0:1 Kräftig-left, 5.5:1 Kräftig-right; dark 8.3:1 / 5.7:1 on green-left |
| `--fs-primary` | `#25834a` | `#44801a` (old green darkened for white) | `#8fd14d` | white 4.8:1; dark ink `#1b130b` 10.0:1 |
| `--fs-on-primary` | `#fff` | `#fff` | `#1b130b` | |
| `--fs-primary-strong` | `#195d35` | `#35690f` | `#a8de70` | 6.6:1 white, 4.8:1 Kräftig-left, 5.8:1 on soft; dark 10.8:1 / 7.4:1 |
| `--fs-primary-soft` / `-200` | `#e3f3e7` / `#b4e7c2` | `#e9f3dc` / `#c5e3a3` | `rgb(143 209 77/.14)` / `#3d5a1e` | eyebrow, chips, step circles, blob |
| `--fs-surface` / `-muted` | `#fff` / `#f3f6f1` | `#fff` / `#f5f0e8` | `#251a10` / `#2e2217` | dark surfaces warm, not grey |
| `--fs-border` / `-strong` | `#e1e8dd` / `#c7d4c2` | `#e9dfd0` / `#cdbfa8` | `#3d2f20` / `#574735` | |
| `--fs-shadow-*` | cool | warm `rgb(83 58 32/…)` | unchanged | |
| `--p-primary-color` / `-hover-color` / `-contrast-color` | preset | = `--fs-primary` / `-strong` / `-on-primary` | same | **required**: `.fs-btn` and `p-button` read the `--p-*` tokens first, so the scope must set them too |
Unchanged: `--fs-bg`, body gradients, `--fs-accent` (yellow blob + FSP pin), map tokens, radii, fonts, header (Q4/Q5).

### 3.4 Where the panel goes (everything else identical to today)
| Element | today | D |
|---|---|---|
| Mission, Weitere Wege, Fairteiler (`split`) | open | `.fs-panel--brand`, padding `clamp(1.4rem, 1rem + 2vw, 2.25rem)`; bubble sits on the green side when flipped |
| Rows (`way.*`, `benefit.*`, `moreWays.*`, `duties.*`, `duties.not.*`) | bare `pi` in `--fs-primary` | `.fs-tile` 2.5 rem, white glyph |
| Fairteiler head, near idle / result / empty | — / white card / dashed | `.fs-tile--lg` (box / search / users / map-marker) beside the title; cards become panels |
| Betriebe card, duties cards, FAQ groups | white card / soft green / open | panels; FAQ group label gets a 2.5 rem tile (users / shopping-bag / box); `<details>` stay white cards on the tint |
| Betriebe quote panel, "Das musst du nicht" | `--fs-primary-soft` | panel like its neighbour (check tiles keep the "no duty" meaning) |
| CTA band | `--fs-primary-soft`, radius xl | panel one step stronger, radius xl |
| Counters strip | `--fs-primary-strong`, white | brown band `--fs-band-bg`, numbers `--fs-brand-green`, labels `--fs-band-muted` (toggle: or a panel, Q3) |
| Hero, journey, search form, map preview, sticky | — | unchanged (only the token swap) |

## 4. States
Identical to Variant D (counters skeleton / data; search idle → skeleton → result / empty / invalid / error / offline / not found; sticky after the hero). Skeletons shimmer `--fs-surface-muted → --fs-border` (warm); error states keep `--fs-danger`. Mockup: Zahlen OK / Skeleton; PLZ 4 digits = Zürich Stadt, 5 = Berlin Neukölln, 9999 = empty, else invalid hint.

## 5. Labels and i18n keys
No new keys, no changed copy; the mockup uses every `de.json` string of `public.landing.*` / `public.explainer.*` verbatim. Tiles are `aria-hidden` like today's icons.

## 6. Accessibility
- Every text pair in §3.3 is ≥ 4.5:1 in both modes at every intensity; measured at the strongest edge (Kräftig: left `#dae6d1`, right `#f6f1ea`; dark left `#383b1b`). The exact `#64ae24` only ever carries text on the brown band (5.1:1) or is a fill without text.
- Tiles: white glyph on `#44801a` 4.8:1 (non-text needs 3:1); the tile duplicates the heading, never carries meaning alone. Chips and "Das musst du nicht" keep text + check glyph; invalid postcode keeps text + info icon; `aria-live` unchanged.
- Focus ring stays 3 px `--fs-primary` at 45 %; on the tinted panels it remains visible (ring is darker than any tint).
- Dark mode: no pure black; panel gradient deep green → warm brown on `#251a10`.

## 7. Mobile (412 px)
Same as Variant D: one column, bubbles 14 rem and clipped (now inside the panel, rounded with it), panel padding 1.2 rem, band 1.4 rem, tiles keep their size, sticky "Jetzt mitmachen" after the hero. The gradient stays horizontal, so even a narrow panel reads green-left / brown-right.

## 8. Test ids to keep
All of decision 160, unchanged: `public.landing.hero|joinEmail|joinGoogle|login|mission|way.*|near|near.*|fairteiler|business.*|moreWays.*|duties.*|duties.not.*|duties.rules|band|band.*|stickyJoin`, `public.landing.journey.step.1-4`, `public.explainer.state|counters|counter.*|journey|preview|business|faq|faq.group.*|faq.*|faq.wiki|faq.support`. The `landing.screen.ts` baselines all change (end batch, decision 69 / 161).

## 9. Rejected alternatives
- **Exact `#64ae24` as `--fs-primary`** with brown button text (B's Q2): keeps the old green pixel-exact but changes every button's text colour on this page alone and the PrimeNG preset still renders white — two button looks. D keeps white-on-green and darkens the fill to `#44801a`; the exact green appears as numbers on the brown band.
- **Diagonal gradient (135°) like the dashboard welcome**: "green top-left, brown bottom-right" loses the owner's left/right reading in short wide panels (band, FAQ groups) and in tall phone panels. Horizontal 90° reads the same at every size.

## 10. Questions (with recommendation)
- **Q1 Scope**: landing page only (§3.3 scoped on `fs-landing-page`) or app-wide switch of `--fs-primary` to the old-green family? → Landing first; the shared `.fs-panel--brand` / `.fs-tile` go global now (they pick up whichever primary is in scope), the primary swap app-wide in a follow-up once seen in the logged-in shell.
- **Q2 Intensity**: Dezent / Mittel / Kräftig → Mittel (14 % / 9 %) for panels, Kräftig for the CTA band; Dezent is barely visible on a laptop at 80 % brightness, Kräftig competes with the bubbles.
- **Q3 Counters**: brown band with old-green numbers, or a brand panel like the rest? → Brown band: it is the one place the exact `#64ae24` can sit on text-safe ground and it gives the page its "brown" anchor; a fifth tinted panel in a row would blur.
- **Q4 Header**: keep today's white header and green wordmark (shell out of scope)? → Keep for this task; the page ink is brown but the header text is `--fs-text` of the shell. If it jars, a separate small task gives the logged-out header `--fs-text #533a20` + wordmark `#44801a`.
- **Q5 Page background**: keep `--fs-bg #f6f8f4` (cool) or warm it to cream `#faf6ef`? → Keep: it is set on `html`, so changing it means a route class on the shell; the panels carry the warmth and the white hero stays as today.
- **Q6 Tiles on the Betriebe benefits** (6 tiles in a 2-column grid): keep tiles, or bare green icons as today? → Keep tiles; they are the owner's reference motif and align the grid.
