# UI feedback batch 2: compact day cards, slot dialog, compact team card, mention pill — design proposal

Status: proposal for the **[mockup]** / ⏸ steps of `docs/tasks/2026-11-04-ui-feedback-store-pickups.md` (day cards, slot dialog,
day chat) and `docs/tasks/2026-11-04-ui-feedback-team-texts.md` (team card §7, mention pill §5). No code changed.
Mockup: `ui-feedback-batch-2-mockup.html` next to this file (inline CSS/JS; toggles Teil 1+2 / 3 / 4, Vorschlag / Heute,
role Bea / Sarah / Tom, Raster / Liste, 2 Zeilen / 1 Zeile, light / dark, desktop 960 / phone 412 touch; slots, Tageschat,
Bestätigen, Austragen and "+ Eintragen" really do something).

## 1. The problem

1. **Day cards** (`store-pickups.tab.ts`): each day is a full `fs-card` (padding up to 1.5rem) with a long title
   ("Donnerstag, 5. November"), a head row (time, "regelmässig" chip, pencil far right), the slot row and a summary line.
   Four lines and ~9rem of height per pickup for one time and two avatars; on a 960px desktop two thirds of the card is white.
2. **Slot dialog**: today only managers can open a slot; it shows name, date, state, Bestätigen/Austragen. Decision 3 adds a
   rich view (co-pickups too) with contact actions and the person's history at this store, and a short view for the team.
3. **Team card**: avatar, name, a second line of flag pills, then four grey stat tiles — ~5.5rem per person, a 20-person
   page is mostly tiles.
4. **Mention**: `fs-user-chip size="xs"` renders an initials avatar + name; it reads as a person chip, not as "@Name" in a
   sentence, and it bumps the line height.

## 2. States

| Part | States |
|---|---|
| Day card | normal; **today** (`day--today`, primary-tinted date tile + border); pickup cancelled (`slotCount 0`: "Abgesagt", italic muted); own pending ("Dein Platz ist noch unbestätigt" under the slots); extra slots ("Zusatzplatz von Tom Keller", one entry per `extraSlots` item); day-chat button only when someone **other than me** is signed up that day; pending-only filter unchanged |
| Slot dialog | rich loading (`fs-data-state` skeleton: person row + 2 facts), rich loaded, rich error/forbidden/offline (DataState defaults inside the dialog body), short (no request); phone block only with `phone` in the response (S9) |
| Team card | manager / jumper / sleeping / unverified pills in words; no phone button for me or without `phone`; kebab only with `mayEditTeam`; distance only when the server sends it |
| Mention | other's bubble, own bubble (inverted colours), forum post, "you are mentioned" ring |

## 3. Visual spec (tokens from `client/src/styles.scss`)

**Day card** (`section.day`): border `1px --fs-border`, `--fs-radius-md`, `--fs-surface`, `--fs-shadow-sm`, padding
`0.55rem 0.65rem 0.55rem 0.55rem`; grid `var(--fs-date-tile) minmax(0,1fr)`, column gap 0.75rem.
- Date tile (`.date`, aria-hidden, the h2 carries the full text): `--fs-surface-muted`, `--fs-radius-sm`; weekday
  0.68rem/700 uppercase muted, day number 1.35rem/750 tabular, month 0.68rem muted. Today: `--fs-primary-soft` +
  `--fs-primary-strong`. Phone: tile 2.75rem wide, number 1.15rem.
- Head row: `h2.day__title` 0.95rem/650 "Donnerstag" + muted "5. November"; right: **Tageschat** button (text button
  0.82rem `--fs-primary-strong`, `pi-comments`; icon-only on phones) with aria-label "Tageschat für Do 5.11. öffnen".
- Pickup (`.pk`): grid, gap 0.3rem, `border-top 1px --fs-border` between pickups, padding `0.35rem 0`.
  Line 1 (`.pk__head`, flex wrap, gap 0.6rem, min-height 1.9rem): time 700 tabular · regular marker: 1.3rem round
  `--fs-surface-muted` with `pi-replay` (`role="img"`, aria-label "regelmässig", title) instead of the text chip ·
  description 0.85rem muted, ellipsis on desktop, wraps on phones · `fs-pickup-summary` pushed right · pencil 1.9rem icon
  button (managers). Line 2: the unchanged `fs-pickup-slot` row. On phones the summary moves under the slot row.
- Grid: `.days { display:grid; gap:0.6rem }`; from 60rem `repeat(2, minmax(0,1fr))`, `align-items:start` (Raster toggle).
  Result: a 3-slot pickup is ~4.2rem tall instead of ~9rem, and the two-week view fits on one desktop screen.

**Slot dialog** (`p-dialog`, `width min(28rem, 94vw)`, header = `pickupAt | fsDate:'dateTimeLong'`, e.g. "Fr 6. Nov, 18:30"):
- Person row: `fs-user-avatar size="lg"` (3.5rem) · name 1.1rem/650 · phone as `tel:` link muted tabular (S9 only).
- Quick actions (`ul.quick`, round outlined buttons 0.82rem with icon + word, wrap): Profil (`routerLink /profile/:id`),
  Nachricht (`lookupConversation` → chat), Anrufen (`tel:`), Nummer kopieren (clipboard + toast). The last two only with phone.
- State line: `pi-check` "Bestätigt" `--fs-primary-strong` / `pi-hourglass` "Unbestätigt" `--fs-warning-text`, 650, then
  muted "· eingetragen Mi 4. Nov, 21:47" (`signedUpAt`, `dateTime`).
- Facts (`dl.facts`, 2 columns, same tile style as the team tiles today): bag "3 / Slots hier" (`pickupCount`), calendar
  "Fr 16. Okt 2026 / Letzter Termin hier" (`lastPickupAt | fsDate:'date'`, "noch keiner" when null).
- `details.upcoming` "Aktuell belegte Slots (2)" with `pi-chevron-down`, closed by default; list of `upcomingSlots` as
  "Do 5. Nov, 20:00", the current one bold primary "– dieser Termin", unconfirmed ones with muted "(unbestätigt)".
- Footer right-aligned: Schliessen (text) · managers: Austragen (danger outlined, `pi-user-minus`) · Bestätigen (primary,
  `pi-check`, only when unconfirmed; keeps the focus-on-open behaviour from the ✓ split pill). Phone: buttons stretch.
- Short mode: avatar lg, name, state line (small), one "Profil" quick action, Schliessen. No request, no facts.

**Team card** (`li.tm`): border `1px --fs-border`, `--fs-radius-md`, padding `0.45rem 0.55rem 0.45rem 0.6rem`, grid
`auto minmax(0,1fr) auto`, avatar `sm` (1.75rem) spanning both lines, linked to the profile.
- Line 1: name 650 as `routerLink` (underline on hover) + `pi-verified` `--fs-primary` with sr-text; pills 0.72rem/600,
  line-height 1.35rem, `0 0.45rem`: Verantwortlich (crown, `--fs-primary-soft`/`--fs-primary-strong`), Springer (clock),
  Schlafmütze (moon) on `--fs-surface-muted`, Nicht verifiziert (`--fs-warning-soft`/`--fs-warning-text`).
- Line 2: `dl.tm__stats` 0.8rem muted tabular, items with 0.8rem icon, separated by "·" in `--fs-border-strong`:
  "**12** Abholungen · Zuletzt 29.10.2026 · Dabei seit 15.01.2025 · 0.8 km". `dt`s are visually hidden, the `dd`s carry
  the words, so screen readers still get "Abholungen 12". Keep `data-testid` `stores.team.pickupCount` / `.distance`.
- Right: phone button + kebab, spanning both lines (unchanged components). ~2.9rem per person (was ~5.5rem).
- Optional density "1 Zeile ab 60rem" (toggle): stats right-aligned on line 1 without the words (icons + values only).

**Mention** (`fs-user-mention`, `a.fs-mention`): `display:inline`, padding `0.02em 0.4em`, radius 0.45em, `--fs-primary-soft`
background, `--fs-primary-strong` text, 650, no underline (underline on hover/focus), `box-decoration-break: clone` so a
wrapped mention keeps its ends; **no** line-height change. In own (primary) bubbles: `color-mix(--fs-on-primary 22%)`
background, `--fs-on-primary` text. When the mention is the viewer: an extra `1.5px` ring at 60 % primary
(`fs-mention--me`). The same class is used by `fs-markdown` for forum mentions (`.fs-mention` in global `styles`).

## 4. Labels and i18n keys (de shown; all 5 languages)

Existing: `pickups.tab.regularBadge` (now the aria-label/title of the ↻ marker), `pickups.tab.edit`, `.cancelled`,
`.waitingMine`, `.extraSlotBy`, `.confirmSlot`, `.removeSlot`, `.state.confirmed/unconfirmed`, `common.close`,
`stores.team.*` (unchanged words), `shared.userChip.verified`.
New: `pickups.dayChat.open` "Tageschat", `pickups.dayChat.openLabel` "Tageschat für {date} öffnen", `pickups.dayChat.name`
"Abholung {store}, {date}", `pickups.dayChat.opened` "Chat „{name}“ geöffnet."; `pickups.slotDetail.profile` "Profil",
`.message` "Nachricht", `.call` "Anrufen", `.copy` "Nummer kopieren", `.copied` "Nummer kopiert.", `.signedUpAt`
"eingetragen {date}", `.pickupCount` "Slots hier", `.lastPickup` "Letzter Termin hier", `.never` "noch keiner",
`.upcoming` "Aktuell belegte Slots ({count})", `.thisOne` "dieser Termin", `.waiting` "unbestätigt";
`shared.userMention.label` "{name} erwähnt – Profil öffnen" (aria-label of the pill).

## 5. Accessibility

- The date tile is decorative; the `h2` keeps the full "Donnerstag, 5. November" (`aria-labelledby` on the section).
- The ↻ marker is `role="img"` with aria-label and `title`; the state words stay in the slot a11y names and the summary.
- Day-chat and pencil buttons have aria-labels with the date / "Abholung bearbeiten"; 44px on touch (`--fs-target-sm`).
- Dialog: `aria-labelledby` the date/time title; quick actions are real links/buttons with words, not bare icons.
- Team stats: `dl` with hidden `dt`s; pills are words; the verified check has sr-text. Links underline on hover/focus.
- Mention: a link with the visible "@Name" plus an aria-label naming the action; colour is backed by the "@" and the weight.

## 6. Mobile (412px, touch)

Day card: tile 2.75rem, head row wraps (description under the time), summary under the slots, Tageschat as an icon button;
one column. Dialog: 94vw, footer buttons stretch, quick actions wrap to two rows. Team: two lines as on desktop, the stats
wrap to a third line only for very long dates; buttons stay right. Mention: unchanged (inline).

## 7. Test ids to keep

`stores.pickups.day`, `.pickup`, `.regularBadge` (move to the ↻ marker), `.description`, `.summary`, `.edit`, `.slot.*`,
`.slotDialog`, `.slotState`, `.confirmSlot`, `.removeSlot`, `.addExtraSlot`, `.extraSlot`, `.unconfirmed`, `.cancelled`;
new `stores.pickups.dayChat`, `stores.pickups.slotDetail.{profile,message,call,copy,upcoming}`.
`stores.team.member`, `.manager`, `.jumperBadge`, `.sleeping`, `.unverified`, `.pickupCount`, `.distance`, `.call`, `.menu`.
`messaging.conversation.mention` (now on the `a.fs-mention`).

## 8. Rejected alternatives

1. **Flat agenda list without cards** (day as a sticky group header, pickups as table rows): densest, but it loses the
   per-day boundary the day chat and the "today" marker hang on, and it reads badly with 2+ pickups a day on phones.
2. **Horizontal slot strip inside the head row** (time · slots · pencil on one line, no second line): works for ≤3 slots
   but an 8-slot pickup wraps unpredictably and the pencil jumps around; a fixed second line is calmer and still halves the height.
3. (team) **One line only, always**: with "Nicht verifiziert" + "Springer" + a long name the stats fall off on 48–60rem;
   kept as an opt-in density above 60rem instead.

## 9. Questions (with recommendation)

1. **Grid or list on desktop?** Recommendation: **2-column grid from 60rem** (fits the two weeks on one screen); the list
   toggle shows the single-column variant.
2. **"regelmässig" as icon marker (↻ with tooltip) instead of the text chip?** Recommendation: **icon** — the word is in
   the aria-label/title and nothing else in the row is grey, so it still reads; the one-time pickups stand out more.
3. **Day chat as text button "Tageschat" (desktop) + icon (phone), or icon everywhere?** Recommendation: **text on
   desktop** — it is a new feature, the word teaches it; icon-only from the second month would be fine.
4. **Co-pickups see the full rich dialog incl. "Slots hier"/"Letzter Termin hier" and the upcoming list** (as decided)?
   The mockup does that; recommendation: keep, but the phone block stays S9-gated (Sarah sees no number in the mockup).
5. **Team card density: 2 lines (default) or the 1-line variant above 60rem?** Recommendation: **2 lines** everywhere —
   the words next to the numbers ("Zuletzt", "Dabei seit") are what make the stats readable; the 1-line variant is the
   fallback if the owner finds 2.9rem per person still too tall.
6. **Mention: ring for "you are mentioned"?** Recommendation: **yes**, cheap and it answers "why did I get a bell".

## Owner decisions (2026-11-04)
1. Day cards: **one column** on all widths (no 2-column grid), easier to read.
2. "regelmässig": ↻ icon marker with tooltip/aria-label.
3. Day chat button: label "Chat öffnen" (en "Open chat", plus gws/fr/it), not "Tageschat"; text on desktop, icon on phones.
4. Co-pickup people see the full rich dialog (phone S9-gated), as decided.
5. Team card: 2 lines everywhere.
6. Mention ring when you are the one mentioned: yes.
