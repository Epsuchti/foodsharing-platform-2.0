# Automatisierung: redesign of the store tab, the member view and the booking flow — design proposal

Status: proposal, revised after the owner's decisions (no Testmodus, no automation permissions, Q3 decided). No code changed. Mockup: `automation-redesign-mockup.html`
(inline CSS/JS; toggles light/dark, desktop/412 px, role, "Heute" vs proposal). Scope: `features/automation/**`, `shared/pickup-slot`, `features/pickups`. Follows
decisions 121/124/127, 151/152, 59; supersedes the dry-run parts of 59/60 (§8).

## 1. The problem
1. The UI speaks "bot" language (*Testmodus*, *Jetzt testen/ausführen*, "{n} geprüft: … abgelehnt", *Nachricht bei Ablehnung*, *Karenzzeit*).
   Rules are evaluated at sign-up, so a rule **prevents** a booking; nothing is "declined later". Run buttons and result banners describe a job.
2. A member cannot see the rules (tab hidden; a blocked person learns of the gap rule in the sign-up dialog as server text, without the date that would work),
   and on the Pickups tab a free place the person cannot take is a disabled grey "1 frei" chip, identical for team-full, region rule, gap and linked store.
4. Linked stores: X is booked, a timer removes it after `graceMinutes` and sends a long mail; the person never saw Y's free slots.
5. The linked-store picker lists only the actor's region (incl. subregions, max 200); the server demands an automation permission on every linked store.
6. Store Telegram chats exist in the API (`GET/PUT /api/stores/{id}/telegram-chats`, test) but have no UI; the advert/sign-out selects stay empty.
7. Three permissions (CAN_USE_AUTOMATIONS, CAN_SEE_ALL_AUTOMATION_DECISIONS, CAN_SEE_USER_PICKUP_COUNT_GROUPING) gate what managers and Botschafter:innen already own; they go.

## 2. Information architecture and roles (no automation permissions any more)
| Where | Who | What |
|---|---|---|
| Store tab **Automatisierung** `/stores/:id/automation/*` | `mayEditStore` (store managers; covers ORGA and region admins as today) | Status strip + sub-nav **Regeln** (`policy`), **Auto-Aufnahme** (`auto-accept`), **Hinweise** (`adverts`), **Austragungen** (`signout-alerts`), **Zusatzplatz** (`extra-slot`), **Kanäle** (`channels`, new: store Telegram chats), **Protokoll** (`audits`: all decisions of this store). Route paths and test ids of the six existing sections stay. |
| Store tab **Abholungen**, card "So kannst du dich hier eintragen" | every member with `maySeePickups` | Read-only summary of the active rules in plain language (§3.2), collapsed `<details>`, open state per store in `localStorage`. **Recommendation:** here, not a member-mode automation tab — members look at pickups, not at "Automatisierung"; the tab keeps one audience and one permission. |
| Slot chip + reason dialog on **Abholungen** | the member | A free place the person cannot take is a clickable "Gesperrt" chip (lock + text) opening *Warum nicht?* with rule, own data and what makes it bookable (§3.3). Eligibility reasons (team, region rule) use the same chip: one place for every "no". |
| Linked-store step in the sign-up dialog | the member | Sign-up in X requires a pickup in Y: step 2 lists Y's free slots in the window; both are booked in one request (§3.4). |
| Region pages `/regions/:id/automation/*` | Botschafter:innen (`mayAdministrateRegion`) + ORGA; everyone else: Protokoll (own decisions only) + Feiertage | Same set: **Betriebe** (overview, default for admins; column Regeln = An/Aus with icon, new column **Kanäle**), Protokoll (all decisions of the region), Putzen, Putz-Ausnahmen, Abholungs-Anzahl, Kennzahlen, Telegram-Chats, Feiertage. Store sections link up to their region page as today. |

**Decisions visibility:** a store manager sees all decisions of their store (store Protokoll); a Botschafter:in all of the region; everyone else only their
own. **Rule switch:** *Eintragungen automatisch bestätigen* + *Testmodus* become one switch **Regeln anwenden** (An/Aus = `enabled`); no observe mode: a rule is
off or it blocks. *Jetzt testen* / *Jetzt ausführen* and the dialog *Entscheidung testen* go; the preview is the member sentence on every rule card. Adverts
keep run now (a scheduled send, no dry run). Auto-Aufnahme becomes the same An/Aus switch.

## 3. Screens
**3.1 Regeln (store manager).** Status strip (`automation.store.summary`): pill An (shield-check) / Aus (○), "n Regeln aktiv", Auto-Aufnahme, Hinweise n,
Kanäle n. One column of rule cards: title, switch, the **member sentence** (same text as the summary), fields when on. Cards: Mindestabstand (own + group
per decision 152, `fs-gap-preview`, group chips with *Regeln aktiv* / *Keine Regeln* badges + hint that a store with rules off counts for the gap but blocks
nothing), Erfahrung, Putzen, Feiertage, Verknüpfte Betriebe, Ausgenommene Tage. Link rule: store chips (max 5, "oder" = any-of), window, *Auch in den
verknüpften Betrieben anwenden* (locked with "Keine Berechtigung: du verwaltest Y nicht" unless `mayEditStore(Y)`; one-way always allowed), disclosure
*Erweitert* with *Frist nach Austragung* (old `graceMinutes`, Q4) and the message (only for that late removal). Mirror rules read-only. Sticky save bar on phones.
**3.2 Member summary (Abholungen tab).** Icon + sentence lines from structured data, never server texts: "Zwischen zwei Abholungen hier mindestens 7 Tage; in
Coop Bahnhofbrücke, Denner Langstrasse und Migros Limmatplatz zusammen mindestens 3 Tage", "Eine Abholung hier braucht eine Abholung bei Babu's Bakery am selben
Tag oder bis 3 Tage danach – du wählst sie beim Eintragen gleich mit", "Neue brauchen zuerst eine bestätigte Abholung hier", "Ein Putzeinsatz in einem
Putz-Fairteiler des Bezirks in den letzten 12 Monaten", "An Feiertagen und am 24.12., 31.12. entscheiden die Betriebsverantwortlichen". Rules off → card hidden.
**3.3 Blocked slot + reason dialog.** `fs-pickup-slot` gets a fourth free state `slot--blocked`: dashed `--fs-border-strong` ring, lock icon, label **Gesperrt**
(phone: lock only, text in `aria-label`), `aria-haspopup="dialog"`. Dialog *Warum kannst du dich hier nicht eintragen?*: store + date, one block per reason: rule
chip (`automation.rule.*`), the concrete sentence with the person's data ("Deine letzte Abholung hier war am Do 29.10.; die nächste ist ab Mi 04.11. möglich"), a
*Das macht es möglich* line with an action where one exists (jump to the next day; *Abholung bei Babu's Bakery wählen* → §3.4; *Putzeinsatz finden*; eligibility
reasons keep `pickups.eligibility.*`). Footer: link to the summary card. Managers are pre-confirmed and never see a blocked chip.
**3.4 Linked-store step in the sign-up dialog.** Step 1 as today. When a link rule applies, step 2 *Schritt 2 von n: Abholung bei Y* with states **satisfied**
(check line "Deine Abholung bei Babu's Bakery am Fr 13.11. 18:30 erfüllt die Verknüpfung" → one *Eintragen*, no extra step), **choose** (radio list of Y's pickups in
the window, day/time + "n frei"; several stores in one rule → one list with the store per row; several rules → one step each; *Beide eintragen* / *Weiter* disabled
until chosen; Y pickups blocked by Y's rules listed as "Gesperrt dort", not hidden), **none free** (danger banner "Bei Babu's Bakery ist zwischen Do 12.11. und
So 15.11. kein Platz frei", link to Y's pickups, confirm disabled), **not a member** ("Du bist nicht im Team von Babu's Bakery", *Team-Beitritt anfragen*,
disabled), **cancel** → nothing booked. Both sign-ups go in one request and succeed or fail together. Later sign-out of the Y slot: Q4.
**3.5 Cross-region store picker (Q3 decided).** Dialog *Betrieb verknüpfen*: selected chips, search field (min 2 chars, debounced, server search over every store
the actor may know, `StoreVisibility`, across regions), results grouped by region path ("Zürich › Winterthur"), row = name, address, badge *Regeln aktiv* / *Keine
Regeln*. No lock in the picker: any known store links one-way; the lock lives only on the bidirectional checkbox (§3.1). Before typing: recently linked + own
region. Limit 5. States: idle, loading, results, empty, error, offline.
**3.6 Kanäle.** Same component as the region section (`fs-telegram-chats`, scope input): intro with the bot name, configured chats (title, id, type, Aktiv switch,
*Test senden*, remove), *Chat hinzufügen* from chats the bot knows, save; card "Wer sendet hierhin?" listing the adverts/sign-out alerts on Telegram. The
advert/sign-out Telegram selects get an inline link *Chats verwalten*.
**3.7 States** (every data-loading element: `loadResource` + `<fs-data-state>`; `data-state` attribute on each root):
| Screen | skeleton | empty | error + retry | forbidden | not found | offline |
|---|---|---|---|---|---|---|
| Store tab | `form` (strip + 2 cards) | n/a | reload `getStoreAutomation` | `askWho` "Die Automatisierung richten die Betriebsverantwortlichen ein." | store 404 → store page | banner, cached data stays |
| Member summary | 2 `inline` lines | card hidden | inline error + retry in the card | hidden (403 = not a member) | hidden | last data + "offline" badge |
| Reason dialog | `inline` | — | retry, dialog stays | — | "Abholung nicht mehr vorhanden" | retry |
| Linked step | `list` 3 rows | none-free state | retry | not-member state | — | retry |
| Store picker | `list` 4 rows | "Keine Betriebe für «…»" | retry + Details | — | — | "Suche braucht Verbindung" |
| Kanäle / region pages | `list` | automation.telegram.none + add row | retry | askWho "Diese Seiten sehen Botschafter:innen des Bezirks und ORGA." | — | retry |

## 4. Visual spec (tokens `client/src/styles.scss`, classes `automation.scss`)
Rule pill: Aus `--fs-surface-muted`/`--fs-text-muted` ○; An `--fs-primary-soft`/`--fs-primary-strong` shield-check (rules protect the team; not an error colour).
Rule card `.card.fs-card`, member sentence in a `--fs-surface-muted` box with info icon, switch right. Blocked chip: `var(--fs-slot-ring) dashed var(--fs-border-strong)`,
`--fs-surface-muted` fill, `color: var(--fs-text)`, lock 0.9em, min-height `--fs-target-sm`. Reason block: 3px left border `--fs-danger-border`, rule chip `.badge--bad`,
fix line `--fs-primary-strong` + arrow. Linked rows: radio + tabular time + "n frei" `.badge--ok`; satisfied line `--fs-primary-soft`. Strip: wrapping pills on
`--fs-surface-raised`, `--fs-radius-md`. Locked checkbox: `.badge--bad` with lock next to it. Dark mode via tokens only.

## 5. Labels and i18n (de; all five languages `de/en/gws/fr/it`; keys follow `automation.*`, `pickups.*`, `shared.*`)
`automation.policy.enabled` Regeln anwenden · `automation.policy.enabledHint` An: Eintragungen, die eine Regel verletzen, sind nicht möglich. Die Regeln stehen für Mitglieder auf der Seite «Abholungen». · `automation.policy.disabledHint` Aus: alle Eintragungen warten auf eure Bestätigung. · `automation.common.on` An · `automation.common.off` Aus (exist)
`automation.section.channels` Kanäle · `automation.section.audits` Protokoll (was Entscheidungen) · `automation.region.section.audits` Protokoll · `automation.store.summary.rules` {count, plural, one {# Regel aktiv} other {# Regeln aktiv}} · `automation.store.summary.channels` Kanäle: {count} Telegram · `automation.store.askWho` Die Automatisierung richten die Betriebsverantwortlichen ein. · `automation.region.askWho` Diese Seiten sehen Botschafter:innen des Bezirks und ORGA.
`automation.policy.intro` Eine Eintragung, die eine aktive Regel verletzt, ist nicht möglich: Mitglieder sehen den Platz als «Gesperrt» und erfahren, warum. · `automation.policy.linksHint` Wer sich hier einträgt, braucht im selben Zeitraum eine Abholung in einem der verknüpften Betriebe; beim Eintragen wird sie gleich mitgewählt. · `automation.policy.linkStoresOr` oder · `automation.policy.pickStore` Betrieb verknüpfen · `automation.policy.lateRemoval` Frist nach Austragung im verknüpften Betrieb (Minuten) · `automation.policy.lateRemovalHint` Wer sich dort austrägt, hat so lange Zeit für eine neue Abholung; danach wird der Platz hier frei. · `automation.policy.linkMessage` Nachricht an die Person (beim Freigeben des Platzes) · `automation.policy.bidiLocked` Keine Berechtigung: du verwaltest {store} nicht · `automation.policy.bidiHint` Einseitig geht immer (die Abholung dort wird nur gelesen). Beidseitig schreibt die Regel auch in den anderen Betrieb; dafür musst du ihn verwalten.
`automation.policy.gapBadge.on` Regeln aktiv · `automation.policy.gapBadge.off` Keine Regeln · `automation.policy.gapWarnNoAutomation` {stores} hat die Regeln aus: Abholungen dort zählen für den Abstand, Eintragungen dort werden aber nicht gesperrt. · `automation.policy.excludedHint` An diesen Tagen entscheiden die Betriebsverantwortlichen; die Eintragung wartet auf Bestätigung. · `automation.policy.holidaysHint` An Feiertagen entscheiden die Betriebsverantwortlichen.
`automation.picker.title` Betrieb verknüpfen · `.search` Betrieb suchen (Name oder Ort) · `.count` {count, plural, one {# Betrieb} other {# Betriebe}} · `.empty` Keine Betriebe für «{query}» · `.emptyHint` Sichtbar sind Betriebe, deren Namen du kennen darfst. · `.offline` Die Suche braucht eine Verbindung. · `.recent` Zuletzt verknüpft · `.badge.rules` Regeln aktiv · `.badge.noRules` Keine Regeln · `.hint` Du kannst jeden Betrieb verknüpfen, den du kennen darfst – auch aus anderen Bezirken. «Auch in den verknüpften Betrieben anwenden» geht nur für Betriebe, die du verwaltest. · `.done` {count, plural, one {# Betrieb} other {# Betriebe}} übernehmen
`automation.channels.title` Telegram-Chats dieses Betriebs · `.intro` Hinweise und Austragungs-Hinweise mit Kanal Telegram gehen an diese Chats; ohne aktiven Chat an die Chats des Bezirks. Füge den Bot {bot} zuerst der Gruppe hinzu. · `.emptyHint` Bis dahin gehen Hinweise an die aktiven Chats des Bezirks. · `.users` Wer sendet hierhin? · `.manage` Chats verwalten
`pickups.rules.title` So kannst du dich hier eintragen · `.count` {count, plural, one {# Regel} other {# Regeln}} · `.gap` Zwischen zwei Abholungen hier mindestens {days} · `.groupGap` in {stores} zusammen mindestens {days} · `.linked` Eine Abholung hier braucht eine Abholung bei {stores} {window} – du wählst sie beim Eintragen gleich mit · `.experience` Neue brauchen zuerst eine bestätigte Abholung hier · `.cleaning` Ein Putzeinsatz in einem Putz-Fairteiler des Bezirks in den letzten {months} · `.manual` An Feiertagen und am {dates} entscheiden die Betriebsverantwortlichen · `.ask` Fragen? Die Betriebsverantwortlichen helfen.
`pickups.blocked.title` Warum kannst du dich hier nicht eintragen? · `.makesPossible` Das macht es möglich: · `.GAP` Zwischen zwei Abholungen hier müssen mindestens {gap} liegen. Deine letzte Abholung hier war am {last}. · `.GAP.fix` eine Abholung ab {next}. · `.GAP.action` Zum {next} springen · `.LINKED_STORE` Dazu brauchst du eine Abholung bei {stores} {window} ({from} – {to}). Du hast dort keine. · `.LINKED_STORE.action` Abholung bei {store} wählen · `.EXPERIENCE` Du hattest hier noch keine bestätigte Abholung. · `.EXPERIENCE.fix` eine erfahrene Person in derselben Abholung, oder frag die Verantwortlichen. · `.CLEANING_REQUIRED` Dein letzter Putzeinsatz war vor {ago}; nötig ist einer in den letzten {required}. · `.CLEANING_REQUIRED.action` Putzeinsatz finden · `.MANUAL_DATE` An diesem Tag entscheiden die Betriebsverantwortlichen; frag sie direkt. · `.allRules` Diese Regeln gelten für alle im Team. · `.seeRules` Alle Regeln dieses Betriebs · `.ok` Verstanden
`pickups.linked.step` Schritt {step} von {total}: Abholung bei {store} · `.satisfied` Deine Abholung bei {store} am {date} erfüllt die Verknüpfung ({window}). · `.choose` Wähle eine Abholung bei {store} ({from} – {to}): · `.chooseAny` Eine Abholung in einem dieser Betriebe genügt ({from} – {to}): · `.blockedThere` Gesperrt dort · `.atomic` Beide Eintragungen werden zusammen gespeichert – oder keine. · `.noneFree` Bei {store} ist zwischen {from} und {to} kein Platz frei. Ohne Abholung dort kannst du dich hier nicht eintragen. · `.notMember` Du bist nicht im Team von {store}. · `.apply` Team-Beitritt anfragen · `.openStore` Abholungen von {store} · `.confirmBoth` Beide eintragen · `.next` Weiter · `.done` Eingetragen bei {stores}.
`shared.pickupSlot.blocked` Gesperrt · `shared.pickupSlot.blockedLabel` Für dich gesperrt, {count} frei – Grund anzeigen ({rules, plural, one {# Regel} other {# Regeln}}) · `automation.region.channels` Kanäle · `automation.region.channelsRegion` Bezirk · `automation.decision.BLOCKED` Gesperrt (replaces DECLINED for sign-up decisions; `CONFIRMED` stays)
Removed keys: `automation.common.dryRun|dryRunHint|dryRunBanner|dryRunTag|runDry|runNow|runResult`, `automation.policy.dryRun|saveFirst|grace`, `automation.request.saveFirst|runResult`, `automation.adverts.dryRun`, `automation.decision.WOULD_*|DRY_RUN_SUMMARY`, `automation.metric.*_WOULD_*`, `automation.preview.*`, `automation.grouping.askWho` (permission text).

## 6. Accessibility and mobile (412 px)
Every state has icon + text (blocked: lock + "Gesperrt"; An/Aus: icon + word; satisfied: check + sentence; badges with icons). Blocked chip = `<button aria-haspopup="dialog">`
named with free and reason counts. Dialogs: `p-dialog` modal, `closeAriaLabel`, focus on the title (reason) or first radio (linked step), Esc cancels without booking, focus
returns to the chip. Reason blocks are a `<ul>`; rule switch `p-toggleswitch` with `inputId` + `aria-describedby` hint; the locked checkbox is described by its reason; picker
results `role="listbox"` with `aria-live` count; lock/text on `--fs-surface-muted` ≥ 4.5:1 in both themes.
Strip wraps; sub-nav is the existing sticky swipe row; rule cards one column, switch right of the title; save bar sticks above the tab bar. Blocked chip = lock
only (44 px). Reason dialog, linked step and picker open as sheets (`p-dialog` `breakpoints`), rows ≥ 44 px, confirm pinned bottom; picker keeps chips + search
pinned top. Kanäle rows wrap: title line, then switch + test + remove.

## 7. Test ids
Keep (used by `e2e/tests/automation*` and screenshots): `automation.store.state`, `automation.store.nav(.policy|…)`, `automation.policy.root|save|error|enabled|
gapCard|gapEnabled|gapDays|gapStatus|gapStores(.state)|gapMember(.remove)|gapWarning|gapNote|gapLeave|gapLockedLeave|groupGapDays|gapPreview(.day|.message)|
groupGapPreview|experience|cleaning|cleaningFsps|exemptionsLink|excludedDate|excludedList|excludedPicker|links|link.<i>(.stores|.mode|.grace|.bidirectional|.message|
.remove|.mirror)|addLink`, `automation.request.*`, `automation.adverts.*`, `automation.signout.*`, `automation.extraSlot.*`, `automation.audits.*`, `automation.region.*`,
`automation.telegram.*` — each minus its `dryRun|dryRunBanner|runDry|run|runResult|saveFirst` ids. Removed: `automation.policy.preview`, `automation.preview.*`
(specs change). New: `automation.store.summary(.mode|.rules|.channels)`, `automation.policy.pickStore`, `automation.policy.link.<i>.bidiLocked`, `automation.picker.dialog|
search|state|result|select|done`, `automation.channels.state|chat|enabled|test|remove|pick|add|save|manage`, `stores.pickups.rules(.toggle|.line)`,
`stores.pickups.slot.blocked`, `pickups.blocked.dialog|reason|action|seeRules|ok`, `pickups.signup.linked(.step|.option|.satisfied|.noneFree|.notMember|.apply|.confirm|.next)`.

## 8. Notes for the planner (not a contract)
1. **Dry run goes.** `PickupPolicy.dryRun`, `RequestPolicy.dryRun`, `OpenSlotAdvert.dryRun` (check) leave contract and tables (new changelog); `runPickupPolicy`,
   `runRequestPolicy`, `AutomationRunResult.dryRun` go (the 5-min safety job calls `PickupPolicyRunner` directly; e2e asserts through audits); `runAdverts` stays
   without `dryRun`. Audit kinds `WOULD_*|DRY_RUN_SUMMARY` and metric keys `*_WOULD_*` are no longer written (Q8). Mirror policies are "created disabled".
   `RegionAutomationStore.dryRun` goes. Migration of existing rows: Q9. Seed: Q10.
2. **Permissions go.** Remove `CAN_USE_AUTOMATIONS`, `CAN_SEE_ALL_AUTOMATION_DECISIONS`, `CAN_SEE_USER_PICKUP_COUNT_GROUPING` from the permission enum, the
   STORES_COORDINATION grants in `AppProperties`, the matrix and the client `Permission` enum. Server: `StorePermissionService.mayUseAutomations` → `mayEditStore`
   (`x-permission` on every store automation + store telegram-chats path); `AutomationAuditService` + `AutomationHistoryAccessRule` → store manager (all of the
   store) / `mayAdministrateRegion` or ORGA (region) / own audits; `PickupCountGroupingService`, `CleaningExemptionService`, `MetricArchiveService`,
   `FspCleaningRegionService` → ambassador branch only. Client: `store-page.component.ts` tab → `p.mayEditStore`; `region-automation-page.component.ts` →
   `isAmbassador || isOrga`; `StorePermissions` automation field description (stores.yaml ~l.424). Docs: ledger AU2/AU7/AU8, decisions 59/60, privacy permission list.
3. `Pickup.eligibility` grows `ruleBlocks: PolicyBlock[]` = `{rule: PolicyRule, satisfied, params: {lastPickupAt?, nextPossibleAt?, gapDays?, stores?: StoreRef[],
   windowMode, before, after, from?, to?, monthsAgo?, monthsRequired?}}` + reason `AUTOMATION_RULE`; evaluated per pickup in `listPickups`. The client renders
   localized sentences from `params`, never server texts.
4. `GET /api/stores/{storeId}/pickup-rules` (`maySeePickups`): structured active rules for the member card (enabled, gap, group stores, links with StoreRef +
   window, experience, cleaning months, manual dates, holiday rule). Or embed in `listPickups` as `rules`.
5. Sign-up with linked pickups: `signUp` body `{linkedPickups: [{storeId, pickupAt}]}`, one transaction, errors `LINKED_PICKUP_REQUIRED` (names the rule),
   `LINKED_PICKUP_FULL`, `LINKED_PICKUP_BLOCKED`. `GET …/pickups/{pickupAt}/linked-options` → per rule the stores' pickups in the window with free count, `member`
   flag and Y's own `ruleBlocks`; `satisfiedBy` when an existing pickup already matches.
6. Store search `GET /api/stores/search?q=&size=20` across regions, bounded by `StoreVisibility.mayKnowStore` (decision 156); rows carry region path,
   `automationEnabled`, `mayEdit` (drives the bidirectional lock). Server link rule (Q3 decided): one-way needs `mayEditStore(X)` + `mayKnowStore(Y)`;
   `bidirectional` additionally `mayEditStore(Y)` for every linked store, else `ACCESS_DENIED` with `storeId` (the client locks the checkbox beforehand).
   `StoreAutomation` and `RegionAutomationStore` get `telegramChatCount`; `graceMinutes` keeps its column with the new meaning (Q4).

## 9. Rejected alternatives
- **Member-mode automation tab** (visible to all, read-only): members do not look for "Automatisierung"; needs a second permission split per section.
- **Sequential booking** (book X, redirect to Y with banner + visible timer): still books X without Y, keeps the removal mail, two page loads on a phone.

## 10. Questions (with recommendation)
Decided by the owner (2026-11-04: all recommendations below accepted, Q1, Q4, Q5, Q7–Q10 included): **Q2** no observe mode at all (summary hidden only while rules are off); **Q3** any store manager of X links any known
store Y one-way across regions, bidirectional needs managing Y (checkbox locked with reason); **Q6** dry-run-only run endpoints and buttons go.
- **Q4 Sign-out of the linked Y slot after booking.** Server re-evaluates X on `PickupSignedOutEvent`; X's own pill shows "Verknüpfung fehlt" (hourglass + text),
  bell + push; the person has `graceMinutes` (*Frist nach Austragung*, default 120) to pick another Y pickup from the same dialog, then X is freed with the
  message template. Alternative: free X at once — harsh when the store cancelled Y.
- **Q1 Where does the member summary live?** Card on the Pickups tab (§2); the reason dialog links to it. **Q5 Any-of vs all-of:** keep the server's model (stores in a rule = "oder", several rules all required). **Q7 Default section:** strip + Regeln.
- **Q8 (new) `WOULD_*` audit kinds and metric keys.** Keep the enum values read-only so old audits (7 days) and archived counts still render, drop them from the metric
  groups and the audit filter; remove the values with the next contract cleanup after retention.
- **Q9 (new) Existing dry-run policies on migration.** `dryRun = true` → `enabled = false` (nothing starts blocking that did not block before; managers switch
  it on deliberately). Alternative: keep enabled (surprise blocks on day one).
- **Q10 (new) Seed (decision 60).** S-01's dry-run policy becomes **off** (seed scenarios and the region pickup-rule e2e for foodsaver keep working); S-06 stays
  live. Alternative: S-01 on with the e2e expectations rewritten for blocked chips — more realistic screenshots.
