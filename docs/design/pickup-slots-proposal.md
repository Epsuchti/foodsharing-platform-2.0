# Pickup slots: confirmed, waiting, free — design proposal

Status: proposal (design round, see decisions 121/124/127 for the house rules it follows). No code changed.
Mockup: `pickup-slots-mockup.html` next to this file (inline CSS/JS, toggles for version, role, theme, width).

Scope: the slot row of one pickup (`fs-pickup-slot`, `shared/pickup-slot/pickup-slot.component.ts`) and the
pickup list of the store tab (`features/pickups/tabs/store-pickups.tab.ts`). The same slot component is reused
wherever a pickup shows its people (intro pickups, options page), so the changes apply there too.

Reading order: problem → state model → visual spec → labels → manager actions → mobile → implementation →
alternatives → open questions.

---

## 1. The problem

On `/stores/:id/pickups` a taken slot is an avatar in a 2 px ring. Today the whole difference between
"confirmed" and "waiting for the store manager's confirmation" is the ring:

| Today | Confirmed | Waiting |
|---|---|---|
| ring | `2px solid var(--fs-primary)` | `2px dashed var(--fs-warning-border)` |
| light mode | #25834a green | #f1d6a6, a pale sand on white |
| dark mode | #4cba70 | `rgb(242 184 75 / 0.3)`: a 30 % amber on #1a201a, almost the card border |
| text | none | none, except your **own** pending slot ("Wartet auf Bestätigung" under the row) |

What is ambiguous, exactly:

1. **Colour and dash only.** At 38 px the dashes are 2 px long; in dark mode the 30 % amber ring has about
   1.6:1 contrast to the card, so a dashed ring reads as "no ring". WCAG 1.4.1 (use of colour) is not met
   because the dash is the only non-colour cue and it is not perceivable at this size.
2. **No words for other people's state.** A foodsaver sees two avatars and cannot tell if the pickup is
   covered ("2 confirmed") or still open ("2 waiting"). A manager cannot tell which of the 14 days need
   their action without hovering every avatar (the `aria-label` has it, the eye does not).
3. **"You" is a shadow.** The own slot gets `box-shadow: 0 0 0 3px soft, 0 0 0 4px primary`, which in the
   mobile baseline (`pickups-booked`) is indistinguishable from the confirmed ring next to it; the only clue
   is the "Wartet auf Bestätigung" line, and that line exists only while you are pending.
4. **Free slots mix two states.** "+ Eintragen" means "free, you can"; "1 frei" without a plus means "free,
   but not you". Today the second one only happens when you are already in the pickup; the server already
   sends `Pickup.eligibility` for every pickup in the list (`PickupMapper.toModel`, `PickupScheduleService`),
   but the client ignores it and only asks in the dialog. So a jumper or someone blocked by the region rule
   sees a green "+ Eintragen", clicks, and is told no.
5. **The manager's confirm action is hidden behind the avatar** (click → dialog → "Bestätigen"). Three clicks
   per person, no overview of pending sign-ups across the two weeks.

The baselines show it: `e2e/__screenshots__/screenshots-desktop/screenshots/pickups.screen.ts/pickups-tab-light-de.png`
(Thursday 5 November: one solid, one dashed ring, no text) and `screenshots-mobile/.../pickups-booked-light-de.png`.
The "Heute" toggle in the mockup reproduces today's look next to the proposal.

---

## 2. State model

A slot is exactly one of these. The first column is the data, the rest is how the proposal shows it.

| # | State | Data | Ring | Badge (icon) | Text | Interaction |
|---|---|---|---|---|---|---|
| 1 | **Confirmed** | `slot.confirmed`, not me | solid `--fs-primary` | ✓ check on `--fs-primary` | none (name on hover, in the a11y name) | managers: opens the slot dialog; others: none |
| 2 | **Waiting** | `!slot.confirmed`, not me | dashed `--fs-warning` | ⧗ hourglass on `--fs-warning` | foodsavers: none; **managers: first name in a pill + inline ✓** | managers: pill → slot dialog, ✓ → confirms at once |
| 3 | **Free, you can sign up** | free slot, `eligibility.eligible` | dashed, primary at 55 % | + | first: "+ Eintragen"; others: "+" | opens the sign-up dialog |
| 4 | **Free, not for you** | free slot, `!eligible` | dashed `--fs-border`, muted | lock (first pill only) | first: "2 frei"; others: empty circle; plus a hint line with the reason when the reason is time-bound | disabled |
| 5a | **You, confirmed** | `slot.user.id === myId`, confirmed | solid `--fs-primary` | ✓ (inline, inside the pill) | pill "**Du** ✓" on `--fs-primary-soft` | opens "Austragen?" |
| 5b | **You, waiting** | me, `!confirmed` | dashed `--fs-warning` | ⧗ (inline) | pill "**Du** ⧗" on `--fs-warning-soft`, plus the line "Dein Platz wartet auf Bestätigung" | opens "Austragen?" |

Per pickup, above the row (right of the time on desktop, its own line on phones):

| Pickup summary | When | Text |
|---|---|---|
| counts | at least one slot taken | "✓ 2 von 3 bestätigt" and, if anyone waits, " · ⧗ 1 wartet" / "3 warten" |
| you wait | you are the only one waiting | "⧗ Du wartest" instead of "1 wartet" |
| confirm all | manager, ≥ 2 waiting in this pickup | text button "Alle 3 bestätigen" |
| nothing taken | 0 slots taken | no summary; the free pills already say "3 frei"/"+ Eintragen" |

Rules that come out of this:

- **Two cues per state, never colour alone:** ring style (solid/dashed) **and** badge icon (check/hourglass/
  plus/lock). The badge sits on the avatar so the row stays as dense as today.
- **"You" is the labelled pill.** Only your own slot carries a filled background and the word "Du"; the
  others stay compact. The symmetric case is the first free slot, which is the only labelled free pill today
  (M7 R2 #16): one labelled thing per kind.
- **Waiting is the manager's work, so waiting gets the room.** For managers a waiting slot expands to a split
  pill (name ⧗ | ✓). Confirmed slots stay compact avatars even for managers: nothing to do there.
- **Eligibility comes from the list**, not from the dialog. `Pickup.eligibility` is already in the payload.

---

## 3. Visual spec

All values are `--fs-*` tokens from `client/src/styles.scss`; new tokens are at the end.

### 3.1 Slot

| Property | Value |
|---|---|
| Size | `--fs-slot-size`: 2.4rem (38 px) with a mouse, 2.75rem (44 px) on `pointer: coarse` (decision 121/124: 36/44 px targets; 38 keeps today's visual size) |
| Ring | `--fs-slot-ring` 2px; solid for confirmed, dashed for waiting and free |
| Confirmed ring | `var(--fs-primary)` (#25834a / #4cba70) |
| Waiting ring | `var(--fs-warning)` (#a35f0c light / #f2b84b dark). Today it is `--fs-warning-border` (#f1d6a6 / amber 30 %); the full warning colour has 4.9:1 on white and 9:1 on the dark card |
| Badge | `--fs-slot-badge` 1.05rem circle at the bottom right (`right: -0.2rem; bottom: -0.2rem`), `2px solid var(--fs-surface)` cut-out ring so it reads on any avatar colour; icon 0.62rem, stroke 3 |
| Badge colours | confirmed: `--fs-primary` with `--fs-on-primary` icon; waiting: `--fs-warning` with **new** `--fs-on-warning` icon (#ffffff light, #1a201a dark: the dark warning #f2b84b needs a dark glyph) |
| Avatar inside | `fs-user-avatar size="sm"` as today (`--size` 2rem), inner gap 2 px to the ring |
| Focus | `outline: 2px solid var(--fs-primary); outline-offset: 2px` (unchanged) |
| Gap between slots | 0.4rem (today 0.35rem; the badges need a hair more) |

### 3.2 Labelled pill ("Du", manager's waiting slot, first free slot)

| Property | Value |
|---|---|
| Shape | same height as a slot, `border-radius: 999px`, `padding: 0 0.7rem 0 0.2rem`, avatar `size="xs"`-ish 1.75rem at the left, label 0.85rem/650, state icon 0.95rem inline at the right |
| You, confirmed | ring solid `--fs-primary`, background `--fs-primary-soft`, text and icon `--fs-primary-strong` |
| You, waiting | ring dashed `--fs-warning`, background `--fs-warning-soft`, text and icon `--fs-warning-text` |
| Manager's waiting slot | ring dashed `--fs-warning`, no fill, first name (`displayName` up to the first space; full name in the a11y name and `title`) + ⧗ in `--fs-warning-text`; right edge squared and joined to the ✓ button |
| Inline ✓ button (`split__confirm`) | `--fs-target-sm` wide (36/44 px), same height, dashed `--fs-warning` ring continuing the pill, `--fs-surface` background, ✓ in `--fs-primary-strong`; hover `--fs-primary-soft`; its own focus ring |
| Free, you can | as today: dashed ring `color-mix(primary 55 %, border)`, "+ Eintragen" in `--fs-primary-strong` 0.85rem/650; hover fills `--fs-primary-soft` |
| Free, not for you | dashed `--fs-border`, `--fs-text-muted`, lock icon + "2 frei"; `disabled` |

### 3.3 Summary line

0.82rem, `--fs-text-muted`, tabular numerals, parts separated by a `--fs-border-strong` middle dot. The
confirmed part is `--fs-primary-strong` with a 0.9em check; the waiting part is `--fs-warning-text`, weight
650, with a 0.9em hourglass. Desktop: inline in `.pickup__head` after the time/badge/description, the pencil
pushed to the right (`margin-left: auto`). Phones: `flex-basis: 100%` so it takes its own line under the time,
the pencil stays on the first line (the mockup shows the wrap).

"Alle 3 bestätigen": `p-button [text]="true" size="small"`, `--fs-primary-strong`, inside the summary line.

### 3.4 Own status line

Kept, with new wording: "⧗ Dein Platz wartet auf Bestätigung" (`.pending`, `--fs-warning-text`, 0.88rem/600,
`data-testid="stores.pickups.unconfirmed"`). It is still worth a sentence even though the "Du ⧗" pill says it:
the pill is 38 px, the sentence is what a newcomer actually reads.

### 3.5 Eligibility hint line

0.82rem `--fs-text-muted` with a lock icon, under the slots, only when you are **not** in the pickup, it has
free slots and `eligibility.eligible === false` with a reason that is about *this pickup or time*:
`SIGNUP_NOT_YET_OPEN`, `REGION_RULE_TIMESPAN_LIMIT`, `REGION_RULE_DAY_LIMIT`, `PICKUP_IN_PAST`. Reasons that
are about *you* (`NOT_VERIFIED`, `JUMPER_CANNOT_PICKUP`, `SLEEPING`, `PHONE_REQUIRED`,
`HYGIENE_CERTIFICATE_REQUIRED`, `NOT_TEAM_MEMBER`) would repeat on every row for two weeks; for those the pill
stays locked and the reason is in its accessible name only (open question 3). `NO_FREE_SLOT` and
`ALREADY_SIGNED_UP` never show a hint: the row already shows it. The texts are the existing
`pickups.eligibility.*` strings, no new keys.

### 3.6 Manager toolbar

A third range chip for users with `mayConfirmPickups`: "⧗ Wartet (5)" with the count of waiting sign-ups in
the loaded list as a `--fs-warning`/`--fs-on-warning` count badge. Pressed, the list shows only pickups with a
waiting slot ("Nichts wartet auf deine Bestätigung." as the empty text). It combines with the range (two
weeks/all) as a filter, and it is hidden when the count is 0. This is the "whom do I still have to confirm"
answer across the two weeks; per pickup, the split pills are the action.

### 3.7 Dark mode

Nothing hard-coded. Waiting ring `--fs-warning` #f2b84b on the #1a201a card (9:1); the waiting badge is
#f2b84b with a #1a201a glyph (`--fs-on-warning`); the "Du ⧗" pill is `--fs-warning-soft` (amber 10 %) with
`--fs-warning-text` #f6d79b. Confirmed ring #4cba70, badge glyph `--fs-on-primary` #0d1f13. The mockup's
"Dunkel" toggle shows both roles.

### 3.8 New tokens

| Token | Light | Dark | Use |
|---|---|---|---|
| `--fs-on-warning` | `#ffffff` | `#1a201a` | glyph on a `--fs-warning` badge or count pill (the sibling of `--fs-on-primary` / `--fs-on-danger`) |
| `--fs-slot-size` | `2.4rem` (`2.75rem` on `pointer: coarse`) | same | slot diameter, replaces the literal 2.4rem/2.75rem in the component |
| `--fs-slot-ring` | `2px` | same | ring width |
| `--fs-slot-badge` | `1.05rem` | same | state badge diameter |

---

## 4. Labels and accessible names

### 4.1 Accessible names per element

| Element | Name (de) | Key |
|---|---|---|
| confirmed slot (button) | "Priya Sharma, bestätigt" | `shared.pickupSlot.confirmed` (exists) |
| waiting slot (button) | "Marco Bernasconi, wartet auf Bestätigung" | `shared.pickupSlot.unconfirmed` (exists; wording changed from "noch nicht bestätigt" so eye and ear use the same word) |
| manager's split pill (group) | same as the waiting slot; the pill button has the same name, the ✓ button "Marco Bernasconi bestätigen" | `shared.pickupSlot.confirmOne` (new) |
| you, confirmed | "Du (Sarah Müller), bestätigt – austragen" | `shared.pickupSlot.mineConfirmed` (new) |
| you, waiting | "Du (Sarah Müller), wartet auf Bestätigung – austragen" | `shared.pickupSlot.mineUnconfirmed` (new) |
| free, you can | "Freier Platz – eintragen" | `shared.pickupSlot.free` (exists) |
| free, not for you (first) | "2 freie Plätze – Für diese Abholung kann man sich noch nicht eintragen." | `shared.pickupSlot.freeLocked` (new, with the reason string) or `freeShort` when no reason |
| free, not for you (others) | "Freier Platz" | `shared.pickupSlot.freeOnly` (exists) |
| slot list (`ul`) | "2 von 3 Plätzen belegt" | `shared.pickupSlot.label` (exists) |
| summary (`p`) | plain text, read as is; the icons are `aria-hidden` | — |

The visible word "Du" is `aria-hidden` inside the pill because the name already says "Du (Sarah Müller)";
screen-reader users get the name once. The badges are `aria-hidden` (the state is in the name).

### 4.2 New i18n keys

`client/src/app/shared/i18n/*.json` (component) and `client/src/app/features/pickups/i18n/*.json` (tab).
gws/fr/it follow the existing conventions in those files (fr uses the median point `confirmé·e`, gws as in
`shared.pickupSlot.*`); they are listed so `npm run i18n:check` stays green, but the owner should glance at
gws and fr.

| Key | de | en | gws | fr | it |
|---|---|---|---|---|---|
| `shared.pickupSlot.unconfirmed` (changed) | {name}, wartet auf Bestätigung | {name}, waiting for confirmation | {name}, wartet uf Bestätigung | {name}, en attente de confirmation | {name}, in attesa di conferma |
| `shared.pickupSlot.you` | Du | You | Du | Toi | Tu |
| `shared.pickupSlot.mineConfirmed` | Du ({name}), bestätigt – austragen | You ({name}), confirmed – sign out | Du ({name}), bestätigt – ustrage | Toi ({name}), confirmé·e – se désinscrire | Tu ({name}), confermato – cancellati |
| `shared.pickupSlot.mineUnconfirmed` | Du ({name}), wartet auf Bestätigung – austragen | You ({name}), waiting for confirmation – sign out | Du ({name}), wartet uf Bestätigung – ustrage | Toi ({name}), en attente de confirmation – se désinscrire | Tu ({name}), in attesa di conferma – cancellati |
| `shared.pickupSlot.confirmOne` | {name} bestätigen | Confirm {name} | {name} bestätige | Confirmer {name} | Conferma {name} |
| `shared.pickupSlot.freeLocked` | {count, plural, one {# freier Platz} other {# freie Plätze}} – {reason} | {count, plural, one {# free slot} other {# free slots}} – {reason} | {count, plural, one {# freie Platz} other {# freii Plätz}} – {reason} | {count, plural, one {# place libre} other {# places libres}} – {reason} | {count, plural, one {# posto libero} other {# posti liberi}} – {reason} |
| `pickups.tab.summary.confirmed` | {confirmed} von {total} bestätigt | {confirmed} of {total} confirmed | {confirmed} vo {total} bestätigt | {confirmed} sur {total} confirmées | {confirmed} su {total} confermati |
| `pickups.tab.summary.waiting` | {count, plural, one {# wartet} other {# warten}} | {count, plural, one {# waiting} other {# waiting}} | {count, plural, one {# wartet} other {# warted}} | {count, plural, one {# en attente} other {# en attente}} | {count, plural, one {# in attesa} other {# in attesa}} |
| `pickups.tab.summary.waitingYou` | Du wartest | You are waiting | Du wartisch | Tu es en attente | Sei in attesa |
| `pickups.tab.waitingMine` | Dein Platz wartet auf Bestätigung | Your slot is waiting for confirmation | Din Platz wartet uf Bestätigung | Ta place attend la confirmation | Il tuo posto è in attesa di conferma |
| `pickups.tab.confirmAll` | Alle {count} bestätigen | Confirm all {count} | Alli {count} bestätige | Confirmer les {count} | Conferma tutti e {count} |
| `pickups.tab.confirmedAll` | {count} Einträge bestätigt. | {count} sign-ups confirmed. | {count} Iiträg bestätigt. | {count} inscriptions confirmées. | {count} iscrizioni confermate. |
| `pickups.tab.range.pending` | Wartet | Waiting | Wartet | En attente | In attesa |
| `pickups.tab.pendingEmpty` | Nichts wartet auf deine Bestätigung. | Nothing is waiting for your confirmation. | Nüt wartet uf dini Bestätigung. | Rien n’attend ta confirmation. | Niente in attesa della tua conferma. |

Unchanged and reused: `pickups.tab.waiting` (still used by the "before" state and by `my-pickups`),
`pickups.tab.confirmSlot`, `pickups.tab.removeSlot`, `pickups.tab.removeTitle/Text`, `pickups.tab.confirmed`,
`pickups.tab.removed`, `pickups.signout.*`, `pickups.signup.*`, `pickups.eligibility.*`,
`shared.pickupSlot.label/confirmed/free/freeOnly/signUp/freeShort`.

---

## 5. Store manager actions

Who: `ctx.may('mayConfirmPickups')` (confirm) and `mayEditPickups` (remove, edit), as today.

| Action | Today | Proposal | Clicks |
|---|---|---|---|
| Confirm one | avatar → dialog → "Bestätigen" | **✓ in the split pill**, one click, success toast `pickups.tab.confirmed`; the pill collapses to a compact confirmed avatar | 3 → 1 |
| See who waits | hover each avatar | names are written in the pills; the summary says "⧗ 2 warten"; the toolbar chip says "Wartet (5)" across the list | — |
| Remove (with message) | avatar → dialog → "Austragen" → message dialog | unchanged: pill (not the ✓) → slot dialog → "Austragen" → `fs-required-message-dialog`. Destructive with a required message stays two steps deliberately | 3 |
| Slot dialog | header = name, date line, buttons | adds a state line "⧗ Wartet auf Bestätigung" / "✓ Bestätigt" under the date so the dialog says what the row says | — |
| Confirm all in one pickup | — | "Alle 3 bestätigen" in the summary when ≥ 2 wait; runs `confirmSignup` per slot sequentially (`concat`), one toast `pickups.tab.confirmedAll`, reload after the batch; a failure in the middle leaves the rest waiting, visible after the reload | n → 1 |
| Confirm all in the store | — | not proposed (see below) |

**Is "confirm all" worth it?** Per pickup: yes, cheaply. It needs no API change (a client-side loop over the
existing `POST .../signups/{userId}/confirm`), it only appears when it saves at least one click, and the
realistic case is the manager who opens the tab on Sunday and finds the week's eight-slot pickup with four
new sign-ups. Across the whole list: no. The scenario where a manager confirms fifteen strangers without
looking is the one the confirmation step exists to prevent; the "Wartet (5)" filter plus per-pickup buttons
gets the honest case to two or three clicks. If a store never wants to confirm anyone, the existing
sign-up policy (`/automation/policy`, preconfirmation) is the right tool, not a bulk button. A server-side
batch endpoint can follow if the loop ever shows up in the load test; it is not needed for correctness.

Also for managers who are themselves signed up: their own slot is the "Du" pill like anyone's.

---

## 6. Mobile behaviour (412 px, touch)

- Slot and pill height 2.75rem (44 px); the ✓ button is 44×44 (`--fs-target-sm`); the pencil 44 px.
- The slot row wraps (as today). Worst case measured in the mockup: a manager's 8-slot pickup with 3
  waiting wraps to three lines (3 compact avatars + first split pill / two split pills / "+ Eintragen" and
  "+"). For foodsavers the same pickup is one line of 8 compact items (they see no names).
- Summary moves to its own line under time and badge; the pencil stays on the time line.
- The hint and own-status lines wrap normally; nothing is truncated with ellipsis.
- The manager's first name in the split pill is the one truncation: first name only (`displayName` before
  the first space), full name in `title` and in the accessible name. Long first names ("Maximiliane") fit;
  double first names wrap the row, which is fine.
- No horizontal overflow: checked at 412 px in the mockup (frame `scrollWidth === clientWidth`); the only
  element wider than the frame is the store tab strip, which is its own horizontal scroller as on the real
  store page.
- The toolbar: range chips first, "Wartet (n)" third; the manager buttons wrap under them with the primary
  one first (existing M7 R2 rule, `order: -1` on `stores.pickups.add`).

---

## 7. Implementation sketch

### 7.1 Components

| Piece | Change |
|---|---|
| `shared/pickup-slot/pickup-slot.component.ts` | new inputs `eligibility?: PickupEligibility \| null` (drives state 4 and the lock), `confirmable: boolean` (managers: render split pills for waiting slots); new output `confirmClick: PickupSlot`. Template: state badge `<span class="slot__state" aria-hidden>` with `pi-check`/`pi-hourglass`; own slot as a labelled pill with "Du"; waiting + `confirmable` as `<div class="split" role="group">` with the pill button and the ✓ button; first free slot gets `pi-lock` when not eligible. Styles move the literals to `--fs-slot-*` tokens. `aria-label`s per section 4.1 |
| `features/pickups/tabs/store-pickups.tab.ts` | summary line (`pickupSummary(pickup)` computed per row: confirmed, pending, mineWaiting), "Alle n bestätigen" button → `confirmAll = runMutation((pickup) => concat(...pending.map(confirmSignup)))`, the "Wartet (n)" chip (`pendingOnly` signal filtering `days()`; `pendingCount` computed), the hint line from `pickup.eligibility`, `[eligibility]="pickup.eligibility"` and `[confirmable]="ctx.may('mayConfirmPickups')"` on `fs-pickup-slot`, `(confirmClick)="confirmSlot.run({pickup, slot})"`; the slot dialog gets the state line; `freeClickable` becomes `!isSignedUp(pickup) && pickup.eligibility?.eligible !== false` |
| `styles.scss` | `--fs-on-warning`, `--fs-slot-size`, `--fs-slot-ring`, `--fs-slot-badge` (light, dark, `pointer: coarse` override for the size) |
| `features/pickups/pages/my-pickups-page.component.ts` | unchanged; it already shows "Bestätigt" / "Wartet auf Bestätigung" as text and uses `data-confirmed` |
| `features/pickups/components/signup-dialog.component.ts` | unchanged |
| Other users of `fs-pickup-slot` (intro pickups, options page) | get the badges automatically; they pass no `eligibility`/`confirmable`, so states 4 and the split pill do not appear there |
| Server | nothing. `Pickup.eligibility` is already in the list payload; `confirmSignup` is called per slot |

Pure functions worth a unit test (rule: every rule change comes with a unit test):
`slotState(slot, myId)` → `'confirmed' | 'waiting' | 'mineConfirmed' | 'mineWaiting'`,
`freeState(pickup, myId)` → `'open' | 'locked'`, `pickupSummary(pickup, myId)`, `hintReason(eligibility)`
(only the time-bound reasons return a key), `pendingCount(pickups)`.

### 7.2 Test ids

Kept, same elements, same attributes (the e2e specs survive unchanged):

| Test id | Element | Attributes |
|---|---|---|
| `stores.pickups.slot.taken` | every taken slot button, including your own and the manager's pill button | `data-user`, `data-confirmed="true\|false"` |
| `stores.pickups.slot.free` | every free slot button | `disabled` when locked |
| `stores.pickups.unconfirmed` | the own-status line (new wording) | — |
| `stores.pickups.confirmSlot`, `stores.pickups.removeSlot`, `stores.pickups.slotDialog`, `stores.pickups.removeDialog` | slot dialog and its buttons | — |
| `stores.pickups.pickup`, `stores.pickups.day`, `stores.pickups.list`, `stores.pickups.state`, `stores.pickups.range.*` | unchanged | — |

New:

| Test id | Element |
|---|---|
| `stores.pickups.slot.confirm` | inline ✓ button of a waiting slot (managers) |
| `stores.pickups.slot.mine` | additionally on your own slot button (so specs can find "my" slot without the name); `data-confirmed` as on `.taken` |
| `stores.pickups.summary` | summary line; `data-confirmed="2" data-pending="1"` |
| `stores.pickups.confirmAll` | "Alle n bestätigen" |
| `stores.pickups.range.pending` | "Wartet (n)" chip; `aria-pressed` |
| `stores.pickups.eligibilityHint` | the lock hint line; `data-reason` |

### 7.3 e2e and screenshot specs

- `tests/pickups/pu3-signup.spec.ts`: unchanged assertions keep passing (`slot.taken[data-confirmed]`,
  `stores.pickups.unconfirmed`, `confirmSlot` in the dialog). Add: manager confirms via
  `stores.pickups.slot.confirm` (one click) and the row's `stores.pickups.summary[data-pending]` drops; "Alle n
  bestätigen" on a seeded pickup with two waiting sign-ups (S-01 Thursday has one; the spec signs a second
  user up through the API first); the "Wartet (n)" chip filters to pickups with pending slots and shows the
  empty text when none remain.
- `tests/pickups/pu4-eligibility.spec.ts`: "already signed up → free slot disabled" still holds; add
  "jumper on S-02: free slot disabled from the list, no dialog needed" and "not yet open shows the hint line"
  (needs a seed pickup beyond the prefetch horizon or a `SIGNUP_NOT_YET_OPEN` fixture).
- `tests/personas/store-manager.spec.ts`: the confirm walk gets shorter (dialog step gone); lower the click
  budget deliberately in the same change (decision 127: budgets only fail when a walk gets longer).
- `tests/personas/foodsaver.spec.ts`: unchanged (asserts `unconfirmed` visible and `data-confirmed`).
- `a11y`: axe sweep covers the new buttons; add an assertion that every `stores.pickups.slot.*` button has a
  non-empty accessible name containing the state word (`bestätigt`/`wartet`/`frei`).
- Screenshot specs (`screenshots/pickups.screen.ts`): `pickups-tab`, `pickups-booked`,
  `pickups-signup-eligible`, `pickups-signup-reasons`, `pickups-options`, `pickups-agenda` change (slots are in
  them). New screens: `pickups-tab-manager` (bieb on S-01 with the waiting slot, the split pill and the
  "Wartet" chip), `pickups-tab-dark` for foodsaver and bieb (the dark ring was the complaint), and
  `pickups-tab-locked` (jumper on S-02: lock pills). Baselines regenerate in the end batch (decision 69),
  never per task.
- Unit specs: `pickup-slot.component.spec.ts` (states, names, `confirmClick`), `store-pickups.tab.spec.ts`
  (summary, pendingOnly filter, confirmAll sequence and partial failure).

---

## 8. Alternatives considered

1. **Name chips for every taken slot** (avatar + full name + state, like `fs-user-chip`). Clearest at a glance,
   but an 8-slot pickup becomes a 3–4 line block on the phone and the two-week list stops being scannable; and
   the names are not the question, the state is. Rejected; names appear only where there is something to do
   (manager's waiting slots) or where it is you.
2. **Keep rings, fix only the colours and add a legend** (stronger dashed amber in dark mode, a "— — wartet"
   legend above the list). Cheapest, but still colour-and-dash only at 38 px (1.4.1), still nothing in words
   per pickup, still three clicks per confirmation. Rejected as insufficient; the colour fix is folded into
   the proposal anyway.
3. **Two groups per pickup** ("Bestätigt: ●●●  Wartet: ●●") instead of one row with badges. Unambiguous,
   but it reorders people (your slot jumps between groups when confirmed), doubles the row height for every
   pickup with one waiting person, and the free slots have no natural place. Rejected; the summary line gives
   the counts without splitting the row.
4. **Confirm from the slot dialog only, but with a store-wide "Alle bestätigen" in the toolbar.** Fewer UI
   pieces, but it either confirms people the manager has not looked at or forces the dialog hunt again.
   Rejected in favour of per-pickup confirm-all plus the inline ✓.

---

## 9. Open questions for the owner

1. **"Du" vs. your own name.** The own pill says "Du"; the hover title and accessible name carry the real
   name. Keep "Du" (proposed), or show the first name like the manager's pills do for everyone?
2. **Inline ✓ without a dialog.** Confirming is reversible only by removing the person (which sends a
   message). Is a one-click confirm acceptable, or should the ✓ open the dialog with "Bestätigen" focused
   (one extra click, keeps the review moment)?
3. **Eligibility hints.** Show the lock hint only for time-bound reasons (proposed) or for every reason once
   per list (for example a banner "Du kannst dich in diesem Betrieb nicht eintragen: Springer können sich
   nicht eintragen." above the day cards)? Jumpers today see "+ Eintragen" everywhere and learn the rule
   from the dialog.
4. **"Alle n bestätigen" per pickup**: wanted as proposed, wanted only from 3 waiting, or not at all?
5. **"Wartet (n)" chip**: as a filter chip next to the range (proposed) or as a count only (no filter) in
   the summary of the tab?
6. **Wording:** "wartet" vs. "unbestätigt" for the state word; "Dein Platz wartet auf Bestätigung" vs. the
   current "Wartet auf Bestätigung"; gws "warted" and fr "Toi" need a native check (decision 124 style).
7. **Other slot users:** intro pickups and the options page get the badges automatically. Should the
   options page (`/pickups`, "Wo kann ich helfen?") also show the summary counts, or is "2 von 3 Plätzen frei"
   enough there?
8. **Screenshot scope:** add the dark variants of the pickups tab to the baseline matrix (currently only
   light `-de` baselines exist for pickups), since dark mode was the trigger?

## 10. Owner decisions (2026-10-31)

1. Your own pill says "Du".
2. ✓ opens the existing dialog with "Bestätigen" focused; there is no one-click confirm.
3. Eligibility: no lock pill. Free slots always look bookable. When the automation (policy rules) would block a sign-up, the sign-up dialog says why and that it can't be booked. The hint line under the pickup goes away.
4. "Alle n bestätigen" per pickup: yes, from 2 waiting. It opens one confirmation dialog for all of them.
5. "Wartet (n)": the designer's choice is a filter chip in the toolbar, with the count.
6. Wording: "unbestätigt" instead of "wartet" (de); the other languages follow.
7. The "Abholungen" overview page also gets the summary counts.
8. No dark baselines for the pickups tab (decision 109 stays).
