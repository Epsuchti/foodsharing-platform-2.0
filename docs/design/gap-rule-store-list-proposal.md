# Gap rule with a shared store list (AU1) — design proposal

Status: proposal for `docs/tasks/2026-11-01-gap-rule-store-list.md` (D1–D9). No code changed. Mockup:
`gap-rule-store-list-mockup.html` (toggles Vorschlag/Heute, state, role, light/dark, desktop/phone 412 px; picker,
stepper, switch, warnings and preview are live).

## 1. The problem

The gap card on `/stores/:id/automation/policy` is a switch and a number that only looks at this store, so a person
blocked here books the same chain's shop around the corner. The plan adds one shared list (shared gap and "Regel aktiv").
The card must now pick stores from the region, make it obvious that saving here writes the other stores too, explain why
a store cannot be chosen or removed, warn about members without automation and about link rules that reach less far
than the gap, and show before saving which days the rule would decline and what the person would read.

## 2. States (gap card)

| # | State | What the card shows |
|---|---|---|
| 1 | Rule off | switch off; days field disabled (as today), picker, note, warnings and preview fold away |
| 2 | Rule on, empty list | days 0–60; picker "Betrieb hinzufügen …"; preview with the single-store text |
| 3 | Rule on, with list | member rows under the picker (badge per store, remove button or lock); shared-list note; days min 1; preview with the group text naming all stores |
| 4 | Warning, no automation | `banner` with `pi-exclamation-triangle`: "{store} prüft Eintragungen nicht automatisch …"; save stays enabled |
| 5 | Warning, link reach < gap | same banner, names the linked store and the window; save enabled; the same banner in the links card |
| 6 | Conflict (every store of the link rule and its owner are in the list) | banner `banner--danger` with `pi-ban`, names the store, tells what to do; **save disabled**; same in the links card |
| 7 | Locked member (no `mayUseAutomations` there) | row with lock icon + badge "Keine Berechtigung", no remove button, hint "Zum Verlassen der Liste die Regel ausschalten."; other rows stay removable; days field disabled with a hint |
| 8 | Candidates loading / error / offline | `<fs-data-state>` around the picker: `skeleton="form"` one row; error + "Erneut versuchen"; the member rows from the policy stay visible |
| 9 | Save error 403 / 400 / 409 | existing `automation.policy.error` line under the cards, now with the store name; the offending row gets the badge "Keine Berechtigung" / "In anderer Liste" |
| 10 | Read-only (`!mayEdit`) | everything disabled, rows without remove buttons, preview still visible |

## 3. Visual spec (tokens from `client/src/styles.scss`, classes from `automation.scss`)

The gap card keeps `.card.fs-card` in the `.rules` grid but spans the full row (`grid-column: 1 / -1`), since it now
holds a picker and the preview; the three other rule cards stay in the auto-fit grid under it.

1. **Header** `.switch-row` as today: "Mindestabstand", hint "Tage zwischen zwei Abholungen derselben Person in diesem
   Betrieb – und in den Betrieben der Liste.", toggle `automation.policy.gapEnabled`.
2. **Days** `.field` "Min. Tage Abstand" `p-inputnumber` `[min]="gapStoreIds.length ? 1 : 0"` `[max]="60"`, width
   `9rem`. Hint under it (`.muted`): "Mit einer Liste mindestens 1 Tag." Locked (state 7): disabled + hint
   `gapLockedGap` with the store names.
3. **Picker** `.field` label "Gilt auch für diese Betriebe (optional)" + hint `gapStoresHint`. `p-multiselect` full
   width, `[filter]`, `display="comma"`, `[maxSelectedLabels]="0"`, `selectedItemsLabel` "{0} von 9 gewählt", placeholder
   "Betrieb hinzufügen …", `[selectionLimit]="9"`, `appendTo="body"`, `releaseOpenGuard`. Option template: name + `.badge`:
   `badge--ok` `pi-check` "Automatisierung aktiv" / `badge--dry` `pi-exclamation-triangle` "Keine Automatisierung" /
   neutral `pi-list` "In anderer Liste" (option `disabled`). Sorted by name.
4. **Member rows** `ul.members` (a chip has no room for a badge and a lock): `1px --fs-border`, `--fs-radius-md`,
   padding `0.45rem 0.6rem`, grid `auto minmax(0,1fr) auto`: `pi-shop` muted, name (650) + badge as in the option,
   right: text icon button `pi-times` `aria-label` "{store} entfernen", or `pi-lock` muted + badge "Keine Berechtigung"
   (never the lock alone). No "Liste verlassen" button (owner): leaving = removing the members or switching the rule
   off. A locked row cannot be removed, so the list stays; under the locked rows one `.muted` line `gapLockedLeave`
   "Zum Verlassen der Liste die Regel ausschalten." (rule off leaves the group, D6).
5. **Shared-list note** `.banner.banner--info` `pi-info-circle`: "Gemeinsame Liste: Mindestabstand und «Regel aktiv»
   gelten für Coop Bahnhofbrücke, Coop Stauffacher und Coop Wiedikon. Speichern schreibt beides auch dort." (names,
   this store first, owner). Only with a non-empty list.
6. **Warnings** `.banner` (warning tokens) with `pi-exclamation-triangle`, one banner per kind, stores joined with
   "und". Conflict: `.banner--danger` (`--fs-danger-soft`, `--fs-danger-border`, `--fs-danger`) with `pi-ban` and the
   sentence "Entferne {store} aus der Liste oder ändere die Verknüpfung, dann kannst du speichern." The same conflict and
   link warnings render in the links card above the rule in question.
7. **Preview** `fs-gap-preview` (`automation.policy.gapPreview`), `--fs-surface-muted`, `--fs-radius-md`, padding
   `0.75rem`. Title "Vorschau" (1rem/650) + intro `gapPreview.intro`. Then an `ol` of 11 cells, `grid-template-columns:
   repeat(11, minmax(0,1fr))`, gap `3px`; cell `min-height 3.4rem`, `--fs-radius-sm`, weekday + day on top (0.72rem
   muted), icon and word below: **Abholung** `pi-shopping-bag`, `--fs-primary`/`--fs-on-primary`, 700; **Nein**
   `pi-times`, `--fs-danger-soft`/`--fs-danger`, border `--fs-danger-border`; **OK** `pi-check`, `--fs-primary-soft`/
   `--fs-primary-strong`. Under it `pi-calendar` "Nächste mögliche Abholung: Do 11.11.2026" (pickup + gap + 1; beyond
   the 11 cells it is the only line that says when) and the message block `.preview` (left `3px --fs-primary` bar as in
   the template editor) titled "Nachricht bei Ablehnung". Logic `gapPreview(pickup, gap, storeNames)` in
   `automation.util.ts`: cell `d` is BLOCKED when `max(0, |d| − 1) < gap`; example pickup = today + 7 in the region's
   zone; the message example uses the last blocked day after it. Dark mode: tokens only (see the "Dunkel" toggle).

## 4. Labels and i18n keys (`automation/i18n/*.json`; errors in `core/i18n/*.json`)

| Key | de | en | gws | fr | it |
|---|---|---|---|---|---|
| `automation.policy.gapHint` (changed) | Tage zwischen zwei Abholungen derselben Person in diesem Betrieb – und in den Betrieben der Liste. | Days between two pickups of the same person in this store – and in the listed stores. | Täg zwüsche zwei Abholige vo de gliche Person i dem Betrieb – und i de Betrieb vo de Lischte. | Jours entre deux collectes de la même personne dans ce magasin – et dans les magasins de la liste. | Giorni tra due ritiri della stessa persona in questo negozio – e nei negozi dell’elenco. |
| `automation.policy.gapDays` (changed) | Min. Tage Abstand | Min. days apart | Min. Täg Abstand | Jours d’écart min. | Giorni min. di distanza |
| `automation.policy.gapMinWithList` | Mit einer Liste mindestens 1 Tag. | At least 1 day with a list. | Mit ere Lischte mindestens 1 Tag. | Au moins 1 jour avec une liste. | Almeno 1 giorno con un elenco. |
| `automation.policy.gapStores` | Gilt auch für diese Betriebe (optional) | Also applies to these stores (optional) | Gilt au für die Betrieb (optional) | S’applique aussi à ces magasins (facultatif) | Vale anche per questi negozi (facoltativo) |
| `automation.policy.gapStoresHint` | Höchstens 9 weitere Betriebe aus deiner Region. Abholungen dort zählen für den Abstand. | Up to 9 more stores from your region. Pickups there count towards the gap. | Höchstens 9 witeri Betrieb us dinere Region. Abholige dört zelled für de Abstand. | Au plus 9 autres magasins de ta région. Les collectes là-bas comptent pour l’écart. | Al massimo altri 9 negozi della tua regione. I ritiri lì contano per la distanza. |
| `automation.policy.gapStoresPlaceholder` | Betrieb hinzufügen … | Add a store … | Betrieb hinzuefüege … | Ajouter un magasin … | Aggiungi un negozio … |
| `automation.policy.gapStoresSelected` | {0} von 9 gewählt | {0} of 9 chosen | {0} vo 9 gwählt | {0} sur 9 choisis | {0} su 9 scelti |
| `automation.policy.gapBadge.on` | Automatisierung aktiv | Automation on | Automatisierig aktiv | Automatisation active | Automazione attiva |
| `automation.policy.gapBadge.off` | Keine Automatisierung | No automation | Kei Automatisierig | Pas d’automatisation | Nessuna automazione |
| `automation.policy.gapBadge.taken` | In anderer Liste | In another list | I ere andere Lischte | Dans une autre liste | In un altro elenco |
| `automation.policy.gapBadge.locked` | Keine Berechtigung | No permission | Kei Berechtigung | Pas d’autorisation | Nessuna autorizzazione |
| `automation.policy.gapRemove` | {store} entfernen | Remove {store} | {store} entferne | Retirer {store} | Rimuovi {store} |
| `automation.policy.gapLockedLeave` | Zum Verlassen der Liste die Regel ausschalten. | To leave the list, switch the rule off. | Zum d Lischte verlaa d Regel usschalte. | Pour quitter la liste, désactive la règle. | Per lasciare l’elenco, disattiva la regola. |
| `automation.policy.gapNote` | Gemeinsame Liste: Mindestabstand und «Regel aktiv» gelten für {stores}. Speichern schreibt beides auch dort. | Shared list: minimum gap and "rule on" apply to {stores}. Saving writes both there too. | Gmeinsami Lischte: Mindestabstand und «Regel aktiv» gälted für {stores}. Spichere schriibt beides au dört. | Liste commune : l’écart minimal et « règle active » valent pour {stores}. Enregistrer les écrit aussi là-bas. | Elenco condiviso: distanza minima e «regola attiva» valgono per {stores}. Salvare li scrive anche lì. |
| `automation.policy.gapLockedGap` | Der Abstand gilt für alle Betriebe der Liste; für {stores} fehlt dir die Berechtigung, darum kannst du ihn hier nicht ändern. | The gap applies to every listed store; you lack permission for {stores}, so you cannot change it here. | De Abstand gilt für alli Betrieb vo de Lischte; für {stores} fählt dir d Berechtigung, drum chasch en da nöd ändere. | L’écart vaut pour tous les magasins de la liste ; il te manque l’autorisation pour {stores}, tu ne peux donc pas le changer ici. | La distanza vale per tutti i negozi dell’elenco; ti manca l’autorizzazione per {stores}, quindi non puoi cambiarla qui. |
| `automation.policy.gapWarnNoAutomation` | {stores} prüft Eintragungen nicht automatisch: Abholungen dort zählen für den Abstand, Eintragungen dort werden aber nicht abgelehnt. | {stores} does not check sign-ups automatically: pickups there count towards the gap, but sign-ups there are not declined. | {stores} prüeft Iitragige nöd automatisch: Abholige dört zelled für de Abstand, Iitragige dört wärded aber nöd abglehnt. | {stores} ne vérifie pas les inscriptions automatiquement : les collectes là-bas comptent pour l’écart, mais les inscriptions n’y sont pas refusées. | {stores} non controlla le iscrizioni automaticamente: i ritiri lì contano per la distanza, ma le iscrizioni lì non vengono rifiutate. |
| `automation.policy.gapWarnLink` | Die Verknüpfung mit {store} ({window}) deckt weniger Tage ab als der Mindestabstand ({gap}). | The link with {store} ({window}) covers fewer days than the minimum gap ({gap}). | D Verknüpfig mit {store} ({window}) deckt weniger Täg ab als de Mindestabstand ({gap}). | Le lien avec {store} ({window}) couvre moins de jours que l’écart minimal ({gap}). | Il collegamento con {store} ({window}) copre meno giorni della distanza minima ({gap}). |
| `automation.policy.gapConflict` | Die Verknüpfung mit {store} ({window}) widerspricht dem Mindestabstand ({gap}). Entferne {store} aus der Liste oder ändere die Verknüpfung, dann kannst du speichern. | The link with {store} ({window}) contradicts the minimum gap ({gap}). Remove {store} from the list or change the link, then you can save. | D Verknüpfig mit {store} ({window}) widerspricht em Mindestabstand ({gap}). Entfern {store} us de Lischte oder änder d Verknüpfig, dänn chasch spichere. | Le lien avec {store} ({window}) contredit l’écart minimal ({gap}). Retire {store} de la liste ou modifie le lien, puis tu pourras enregistrer. | Il collegamento con {store} ({window}) contraddice la distanza minima ({gap}). Rimuovi {store} dall’elenco o modifica il collegamento, poi potrai salvare. |
| `automation.policy.gapPreview.title` | Vorschau | Preview | Vorschau | Aperçu | Anteprima |
| `automation.policy.gapPreview.intro` | Beispiel: eine Abholung am {date}. So entscheidet die Regel über eine zweite Eintragung an den Tagen davor und danach. | Example: a pickup on {date}. This is how the rule decides a second sign-up on the days before and after. | Bispil: e Abholig am {date}. So entscheidet d Regel über e zweiti Iitragig a de Täg dervor und dernah. | Exemple : une collecte le {date}. Voici comment la règle décide d’une seconde inscription les jours avant et après. | Esempio: un ritiro il {date}. Così la regola decide una seconda iscrizione nei giorni prima e dopo. |
| `automation.policy.gapPreview.pickup` / `.blocked` / `.ok` | Abholung / Nein / OK | Pickup / No / OK | Abholig / Nei / OK | Collecte / Non / OK | Ritiro / No / OK |
| `automation.policy.gapPreview.next` | Nächste mögliche Abholung: {date} | Next possible pickup: {date} | Nächschti möglichi Abholig: {date} | Prochaine collecte possible : {date} | Prossimo ritiro possibile: {date} |
| `automation.policy.gapPreview.messageTitle` | Nachricht bei Ablehnung | Message on decline | Nachricht bi Ablehnig | Message en cas de refus | Messaggio in caso di rifiuto |
| `automation.policy.gapPreview.message` | Du hast bereits am {date} eine Abholung bei {store}. Zwischen zwei Abholungen bei {stores} müssen mindestens {gap} liegen; hier wären es nur {actual}. | You already have a pickup on {date} at {store}. At least {gap} must lie between two pickups at {stores}; here it would be only {actual}. | Du hesch scho am {date} e Abholig bi {store}. Zwüsche zwei Abholige bi {stores} müend mindestens {gap} liege; da wäred es nur {actual}. | Tu as déjà une collecte le {date} chez {store}. Il faut au moins {gap} entre deux collectes chez {stores} ; ici il n’y aurait que {actual}. | Hai già un ritiro il {date} da {store}. Tra due ritiri da {stores} devono esserci almeno {gap}; qui sarebbero solo {actual}. |
| `errors.GAP_LIST_TOO_LARGE` | Höchstens 10 Betriebe pro Liste, diesen eingerechnet. | At most 10 stores per list, this one included. | Höchstens 10 Betrieb pro Lischte, dä iigrächnet. | Au plus 10 magasins par liste, celui-ci compris. | Al massimo 10 negozi per elenco, questo compreso. |
| `errors.GAP_LIST_STORE_TAKEN` | {store} steht bereits in einer anderen Liste. | {store} is already in another list. | {store} staht scho i ere andere Lischte. | {store} est déjà dans une autre liste. | {store} è già in un altro elenco. |
| `errors.GAP_LINK_CONFLICT` | Die Verknüpfung mit {store} widerspricht dem Mindestabstand. | The link with {store} contradicts the minimum gap. | D Verknüpfig mit {store} widerspricht em Mindestabstand. | Le lien avec {store} contredit l’écart minimal. | Il collegamento con {store} contraddice la distanza minima. |
| `automation.policy.gapForbidden` (403 with `storeId`) | Für {store} fehlt dir die Berechtigung für Automatisierungen. | You lack the automation permission for {store}. | Für {store} fählt dir d Berechtigung für Automatisierige. | Il te manque l’autorisation d’automatisation pour {store}. | Ti manca l’autorizzazione per le automazioni di {store}. |

The German message copy equals `automation.rule.gap.group` in the server bundle (same commit, plan risk 5). Without a
list the preview shows today's `automation.rule.gap` text; with gap 0 `gapPreview.none` "Mit 0 Tagen Abstand lehnt die
Regel keine Eintragung ab." `{gap}`/`{actual}` use the day plural. The error line resolves `args.storeId` to a name via
`gapStores` + candidates, falling back to the generic text. gws and fr need the owner's native glance.

## 5. Accessibility

- Every state has words: badges carry text next to the icon; cells carry the word ("Abholung" / "Nein" / "OK") and
  `data-result`; `aria-label` per cell "Mo 9.11.2026: Nein" so a screen reader gets date and verdict in one go. The
  cell list is `ol` with `aria-label` "Vorschau der Abstandsregel"; the current pickup cell has `aria-current="date"`.
- Banners: warnings `role="status"`, the blocking conflict `role="alert"`; the save button's `aria-describedby` points
  at the conflict so "why is Speichern disabled" is answered by the control itself.
- Picker and days field: label `for` → `inputId`, hints via `aria-describedby`; disabled options keep their badge text;
  remove buttons `aria-label` "{store} entfernen"; the lock has a visible badge, not a tooltip; `min` follows the list.
- Targets at `--fs-target-sm` (36 / 44 px, decision 124); the preview cells are not interactive.

## 6. Mobile (412 px, touch)

Card stacks: header, days (field `9rem`, stepper 44 px), picker full width (overlay opens under it), member rows one
per line with the badge wrapping under the name, banners full width. Preview: 11 cells stay in one row (`minmax(0,1fr)`,
about 28 px each) with day number, icon and a short word ("Abh." / "Nein" / "OK"); the full word is in `aria-label` and
in the legend line under the row. The message block wraps (`overflow-wrap: anywhere`). No horizontal overflow.

## 7. Test ids

Kept: `automation.policy.gapCard|gapEnabled|gapDays|save|error` (`data-code`), `automation.policy.links|link.<i>`.
New (all `automation.policy.`): `gapStores` (multiselect), `gapStores.state` (DataState), `gapStores.retry`,
`gapMember` (`data-store`, `data-locked`, `data-automation`), `gapMember.remove`, `gapLockedLeave`, `gapNote`, `gapWarning`
(`data-kind="NO_AUTOMATION|LINK|CONFLICT"`, `data-store`), `link.<i>.gapWarning`, `gapPreview`, `gapPreview.day`
(`data-date`, `data-result="PICKUP|BLOCKED|OK"`), `gapPreview.next`, `gapPreview.message`.

## 8. Rejected alternatives

1. **Chips inside the multiselect (`display="chip"`) as the only view of the list**, as the links card does. No room
   for "Keine Automatisierung" or a lock, a 16 px "×", and a locked chip's remove would have to be intercepted.
2. **A plain sentence instead of the 11 cells** ("Nach einer Abholung am 8.11. ist die nächste ab 11.11. möglich").
   Shorter, but "2 Tage" = Mon→Wed is the question people ask; the cells answer it, the sentence stays as the next line.

## 9. Questions (with recommendation)

Answered 2026-11-01 (owner): example pickup = today + 7; no "Liste verlassen" button; link one-liners in the links
card; days field locked with a locked member; the note names the stores. Open:
1. **Leaving with a locked member.** The locked row cannot be removed, so the only exit is switching the rule off, which
   also switches this store's own gap rule off (D6). Is that acceptable, or should the server accept an empty list
   from a store even when it contains locked members (D6 "empty list" = leaving), so the UI could offer "alle
   entfernen"? Recommendation: accept rule-off as the exit for now and keep the hint on the locked row; it is a rare
   case (a person who manages one store of a chain) and the alternative re-opens the question of who may change what.
