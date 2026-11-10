# "Reichen die Termine?" as a date check (EA21) — design proposal

Status: proposal from the owner's brief of 2026-11-10; the plan is being written in parallel, so data names below are
suggestions for the planner. No code changed. Mockup: `einab-date-check-mockup.html` (toggles Vorschlag/Heute, case,
role, light/dark, desktop/phone 412 px; "Termin anlegen" opens the prefilled dialog, "Speichern" updates the section).

## 1. The problem

The section on `/regions/:id/newcomers` is a 16-row table (kind of business × with/without Fairteiler visit) with
"Fehlt / Knapp / Genug". It counts places per kind, not people: it shows five "Fehlt" while 27 places are free for 8
needed, and it cannot show the real blocker (Luca Meier can book 1 of 15 dates because 14 are with a guide he already
had). The reader wants one answer: can each newcomer finish with the dates on offer in the next 30 days, and if not,
which date would fix it for the most people.

## 2. States

| # | State | What the section shows |
|---|---|---|
| 1 | All fine | verdict card only: tag "Reicht", green sentence, the two totals. No sub-line, no meter, no groups |
| 2 | Some stuck | verdict (warning): "3 von 6 Neuen können …", sub-line with the stuck count (+ "Plätze gibt es genug – sie passen nur nicht zu allen." when the totals are fine), people meter, totals; then "Diese Termine fehlen" with one card per need |
| 3 | Nobody / no dates | verdict (danger): "Niemand von den 4 Neuen kann …"; sub-line `noDates` when no free place exists; one card "Irgendein Termin mit freien Plätzen" |
| 4 | Person in two groups | the person is listed in both cards with a dashed chip "auch bei «Fairteiler-Besuch»" that scrolls to the other card; the headline counts people, not rows |
| 5 | Group larger than 3 (max places) | help line "Ein Termin mit 3 Plätzen hilft 3 von 4. Für alle braucht es 2 Termine."; dialog prefills 3 |
| 6 | Viewer without names (guide without `CAN_MANAGE_USERS`) | cards show anonymous avatars + "2 Neue · brauchen zusammen noch 4, können 2 buchen"; one note under the cards about who sees names |
| 7 | Viewer is the excluded guide | title "… als dir"; no button, a quiet note "Ein Termin von dir würde hier nicht zählen. …" |
| 8 | Viewer cannot offer dates (`CAN_MANAGE_USERS` only) | help line stays; outlined link "Begleitungen ansehen" (admin page, tab Begleitungen) + note |
| 9 | Loading | skeleton: one verdict block + two card blocks (`role="status"`, `shared.dataState.loading`) |
| 10 | Error / offline | `<fs-data-state>` error card inside the section with "Nochmals versuchen"; the rest of the page stays |
| 11 | Empty (nobody in training) | compact empty state, `pi-users`, "Keine Neuen in Einführung" + one line of text; no totals |
| 12 | Forbidden / not found | handled by the page-level `<fs-data-state>` as today |
| 13 | Dialog prefilled | "Neue Einführungsabholung" with a purpose banner on top; the banner turns into a warning when an edit breaks the fit (wrong kind, Fairteiler unchecked); saving is never blocked |
| 14 | After saving | toast `introPickups.form.saved`, the check reloads (`reload()` of its `loadResource`), the solved card is gone |

## 3. Visual spec (tokens from `client/src/styles.scss`, classes from `intro-pickups.scss`)

1. **Section head**: `h2.section-title` with `pi-calendar` (was `pi-chart-bar`), text unchanged. The muted intro line
   (`gapsText`) is dropped: the headline says it.
2. **Verdict card** `.fs-card.verdict`, `data-status="OK|PARTIAL|NONE"`, grid `auto minmax(0,1fr)`, gap `0.9rem 1rem`,
   `border-inline-start: 4px solid` status colour (`--fs-primary` / `--fs-warning` / `--fs-danger`).
   Icon disc 3rem, radius 999px, soft status fill (`--fs-primary-soft` / `--fs-warning-soft` / `--fs-danger-soft`), glyph
   `pi-check-circle` / `pi-exclamation-triangle` / `pi-times-circle` in `--fs-primary-strong` / `--fs-warning-text` /
   `--fs-danger`. Body: status `.tag` (`tag--OK|LOW|MISSING` colours) with icon + word; headline `p`
   `clamp(1.15rem, 1rem + 0.7vw, 1.4rem)`/600, the count in `strong` 800 in the status text colour, `text-wrap: balance`;
   sub-line `.muted`; people meter = one 1.5rem disc per newcomer in training (filled `--fs-primary` + check = can
   finish, dashed 2px status border + person glyph = stuck), `aria-hidden`, capped at 24 discs (then omitted).
3. **Totals** inside the verdict card, under a `1px --fs-border` rule: `ul.totals`, auto-fit `minmax(min(100%,13rem),1fr)`;
   each tile `--fs-surface-muted`, `--fs-radius-md`, padding `0.6rem 0.8rem`: value 1.35rem tabular ("27 / 8"), existing
   label (`slotsVsNeeded`, `fairteilerVsNeeded`), and a tag `pi-check` "reicht" / `pi-exclamation-triangle` "zu wenig".
   The Fairteiler tile only with `fairteilerRequired`. They move here from the top stat row (question 1).
4. **Groups**: `h3` "Diese Termine fehlen" (1rem) + muted hint; `ul.needs` auto-fit `minmax(min(100%,21rem),1fr)`,
   gap `0.85rem`. Card `.fs-card.need` (padding `1.1rem`, gap `0.8rem`): icon tile 2.5rem `--fs-radius-md`
   `--fs-warning-soft`/`--fs-warning-text` (`pi-users` other guide, `pi-warehouse` Fairteiler, `pi-shop` kind,
   `pi-calendar` any); eyebrow 0.75rem/700 uppercase muted; title `h4` 1.08rem; reason `.muted` 0.88rem; people block
   `--fs-surface-muted`, `--fs-radius-md`: `fs-user-chip` per person with the subtitle `person` and the dashed "auch bei"
   chip (1px dashed `--fs-border-strong`, 0.75rem); optional line `pi-users` "Anbieten könnten: …" (question 5); foot
   above a `1px --fs-border` rule: `pi-lightbulb` help line 0.92rem/600 left, primary `p-button` `pi-plus` right.
5. **Dialog**: existing `fs-einab-form-dialog`, new optional input `purpose` (need + people). Banner `.purpose` first in
   the form: `--fs-primary-soft`, border `--fs-primary-200`, `--fs-radius-md`, `pi-info-circle`; second line `pi-check`
   "Passt für die Gruppe." Misfit: `--fs-warning-soft` / `--fs-warning-border` / `--fs-warning-text`,
   `pi-exclamation-triangle`. Prefill: places = min(people, 3), kind = first missing kind, Fairteiler checked for that
   need; hints under kind and places say why. Dark mode: tokens only.

## 4. Labels and i18n keys (`intro-pickups/i18n/*.json`, prefix `introPickups.funnel.check.` unless noted)

Counts use ICU plurals like `introPickups.funnel.people` (`all` needs a `one` form). Reused: `introPickups.funnel.gaps`,
`slotsVsNeeded`, `fairteilerVsNeeded`, `noGaps` (empty title), `common.introCategory.*`, all `introPickups.form.*`.
Removed with the table: `gapsText`, `students`, `openSlots`, `severity`, `gap.OK|LOW|MISSING`. gws/fr/it need a native glance.

| Key | de | en | gws | fr | it |
|---|---|---|---|---|---|
| `all` | Alle {total} Neuen können mit den Terminen der nächsten {days} Tage abschliessen. | All {total} newcomers can finish with the dates of the next {days} days. | Alli {total} Neue chönd mit de Termin vo de nächschte {days} Täg abschlüsse. | Les {total} nouveaux peuvent tous terminer avec les dates des {days} prochains jours. | Tutti i {total} nuovi possono concludere con le date dei prossimi {days} giorni. |
| `some` | {able} von {total} Neuen können mit den Terminen der nächsten {days} Tage abschliessen. | {able} of {total} newcomers can finish with the dates of the next {days} days. | {able} vo {total} Neue chönd mit de Termin vo de nächschte {days} Täg abschlüsse. | {able} des {total} nouveaux peuvent terminer avec les dates des {days} prochains jours. | {able} dei {total} nuovi possono concludere con le date dei prossimi {days} giorni. |
| `none` | Niemand von den {total} Neuen kann mit den Terminen der nächsten {days} Tage abschliessen. | None of the {total} newcomers can finish with the dates of the next {days} days. | Niemer vo de {total} Neue cha mit de Termin vo de nächschte {days} Täg abschlüsse. | Aucun des {total} nouveaux ne peut terminer avec les dates des {days} prochains jours. | Nessuno dei {total} nuovi può concludere con le date dei prossimi {days} giorni. |
| `status.OK` / `.PARTIAL` / `.NONE` | Reicht / Reicht nicht für alle / Reicht nicht | Enough / Not enough for everyone / Not enough | Langet / Langet nöd für alli / Langet nöd | Suffisant / Pas assez pour tous / Insuffisant | Basta / Non basta per tutti / Non basta |
| `stuck` | {count} Personen kommen mit den heutigen Terminen nicht weiter. | {count} people are stuck with today's dates. | {count} Persone chömed mit de hütige Termin nöd wiiter. | {count} personnes sont bloquées avec les dates actuelles. | {count} persone sono bloccate con le date attuali. |
| `unfit` | Plätze gibt es genug – sie passen nur nicht zu allen. | There are enough spots – they just don't fit everyone. | Plätz hät's gnueg – sie passed nur nöd zu allne. | Il y a assez de places – elles ne conviennent juste pas à tous. | I posti bastano – solo che non vanno bene per tutti. |
| `noDates` | In den nächsten {days} Tagen ist kein Termin mit freien Plätzen angeboten. | No date with open spots is on offer in the next {days} days. | I de nächschte {days} Täg isch kei Termin mit freie Plätz aabote. | Aucune date avec des places libres n'est proposée dans les {days} prochains jours. | Nei prossimi {days} giorni non è offerta nessuna data con posti liberi. |
| `totalOk` / `totalLow` | reicht / zu wenig | enough / too few | langet / z wenig | suffit / trop peu | basta / troppo pochi |
| `groupsTitle` | Diese Termine fehlen | These dates are missing | Die Termin fähled | Il manque ces dates | Mancano queste date |
| `groupsHint` | Gruppiert nach dem, was gebraucht wird – ein Termin kann mehreren helfen. | Grouped by what is needed – one date can help several people. | Gruppiert nach dem, wo's bruucht – ein Termin cha mehrere hälfe. | Regroupé par besoin – une date peut aider plusieurs personnes. | Raggruppato per necessità – una data può aiutare più persone. |
| `kind.OTHER_GUIDE` / `.FAIRTEILER` / `.CATEGORY` / `.ANY` | Andere Begleitung / Fairteiler-Besuch / Betriebsart / Freie Plätze | Another guide / Food-share point visit / Kind of business / Open spots | Anderi Begleitig / Fairteiler-Bsuech / Betriebsart / Freii Plätz | Autre accompagnement / Visite d’un Fairteiler / Type de commerce / Places libres | Altro accompagnatore / Visita a un Fairteiler / Tipo di negozio / Posti liberi |
| `need.OTHER_GUIDE` | Termin mit einer anderen Begleitung als {guides} | Date with a guide other than {guides} | Termin mit ere andere Begleitig als {guides} | Date avec un autre accompagnement que {guides} | Data con un accompagnatore diverso da {guides} |
| `need.OTHER_GUIDE_SELF` | Termin mit einer anderen Begleitung als dir | Date with a guide other than you | Termin mit ere andere Begleitig als dir | Date avec un autre accompagnement que toi | Data con un accompagnatore diverso da te |
| `need.FAIRTEILER` | Termin mit Fairteiler-Besuch | Date with a food-share point visit | Termin mit Fairteiler-Bsuech | Date avec visite d’un Fairteiler | Data con visita a un Fairteiler |
| `need.CATEGORY` | Termin in einer Betriebsart, die noch fehlt: {categories} | Date in a kind of business still missing: {categories} | Termin i ere Betriebsart, wo no fählt: {categories} | Date dans un type de commerce qui manque encore : {categories} | Data in un tipo di negozio che manca ancora: {categories} |
| `need.ANY` | Irgendein Termin mit freien Plätzen | Any date with open spots | Irgend en Termin mit freie Plätz | N’importe quelle date avec des places libres | Qualsiasi data con posti liberi |
| `why.OTHER_GUIDE` | Sie waren schon mit {guides} unterwegs, und {blocked} der {dates} freien Termine bietet {guides} an. | They already went with {guides}, and {guides} offers {blocked} of the {dates} open dates. | Sie sind scho mit {guides} unterwägs gsi, und {blocked} vo de {dates} freie Termin bütet {guides} aa. | Ils sont déjà sortis avec {guides}, qui propose {blocked} des {dates} dates libres. | Sono già usciti con {guides}, che offre {blocked} delle {dates} date libere. |
| `why.FAIRTEILER` | Kein freier Termin der nächsten {days} Tage hat einen Fairteiler-Besuch. | No open date in the next {days} days includes a food-share point visit. | Kei freie Termin vo de nächschte {days} Täg hät en Fairteiler-Bsuech. | Aucune date libre des {days} prochains jours ne comprend une visite de Fairteiler. | Nessuna data libera dei prossimi {days} giorni include una visita a un Fairteiler. |
| `why.CATEGORY` | Die freien Termine sind alle in Betriebsarten, die schon erledigt sind. | All open dates are in kinds of business already done. | Di freie Termin sind alli i Betriebsarte, wo scho erlediget sind. | Toutes les dates libres sont dans des types de commerce déjà faits. | Tutte le date libere sono in tipi di negozio già fatti. |
| `person` | braucht noch {needed}, kann {bookable} buchen | still needs {needed}, can book {bookable} | bruucht no {needed}, cha {bookable} bueche | a encore besoin de {needed}, peut en réserver {bookable} | ne servono ancora {needed}, può prenotarne {bookable} |
| `anonymous` | {count} Neue · brauchen zusammen noch {needed}, können {bookable} buchen | {count} newcomers · still need {needed} together, can book {bookable} | {count} Neui · bruuched zäme no {needed}, chönd {bookable} bueche | {count} nouveaux · ont encore besoin de {needed} en tout, peuvent en réserver {bookable} | {count} nuovi · servono ancora {needed} in tutto, possono prenotarne {bookable} |
| `namesNote` | Namen sehen Botschafter:innen und das Begrüssungsteam. | Names are visible to ambassadors and the welcome team. | Näme gsehnd d Botschafter:inne und s Begrüessigsteam. | Les noms sont visibles pour les ambassadeur·rices et l’équipe d’accueil. | I nomi sono visibili ad ambasciatori e team di benvenuto. |
| `also` | auch bei «{kind}» | also under "{kind}" | au bi «{kind}» | aussi sous « {kind} » | anche in «{kind}» |
| `help` (plural on `{people}`: 1 / 2 / other) | Ein Termin mit 1 Platz reicht. / Ein Termin mit 2 Plätzen hilft beiden. / Ein Termin mit {people} Plätzen hilft allen {people}. | One date with 1 spot is enough. / One date with 2 spots helps both. / One date with {people} spots helps all {people}. | Ein Termin mit 1 Platz langet. / Ein Termin mit 2 Plätz hilft beidne. / Ein Termin mit {people} Plätz hilft allne {people}. | Une date avec 1 place suffit. / Une date avec 2 places aide les deux. / Une date avec {people} places aide les {people}. | Basta una data con 1 posto. / Una data con 2 posti aiuta entrambi. / Una data con {people} posti aiuta tutti e {people}. |
| `helpMore` | Ein Termin mit {places} Plätzen hilft {places} von {people}. Für alle braucht es {dates} Termine. | One date with {places} spots helps {places} of {people}. Everyone needs {dates} dates. | Ein Termin mit {places} Plätz hilft {places} vo {people}. Für alli bruucht's {dates} Termin. | Une date avec {places} places aide {places} sur {people}. Pour tous, il faut {dates} dates. | Una data con {places} posti aiuta {places} su {people}. Per tutti servono {dates} date. |
| `fits` | Anbieten könnten: {guides} | Could offer it: {guides} | Aabiete chönnted: {guides} | Pourraient la proposer : {guides} | Potrebbero offrirla: {guides} |
| `create` / `createFor` (aria) | Termin anlegen / Termin anlegen: {need} | Create date / Create date: {need} | Termin aalege / Termin aalege: {need} | Créer une date / Créer une date : {need} | Crea una data / Crea una data: {need} |
| `cannotSelf` | Ein Termin von dir würde hier nicht zählen. Bitte eine andere Begleitung um einen Termin. | A date offered by you would not count here. Ask another guide for a date. | En Termin vo dir würd da nöd zelle. Frag e anderi Begleitig um en Termin. | Une date proposée par toi ne compterait pas ici. Demande une date à un autre accompagnement. | Una data offerta da te qui non conterebbe. Chiedi una data a un altro accompagnatore. |
| `cannotCreate` / `askGuides` | Termine bieten Begleitungen an. Du bist in diesem Bezirk keine Begleitung. / Begleitungen ansehen | Dates are offered by guides. You are not a guide in this region. / View guides | Termin büted Begleitige aa. Du bisch i dem Bezirk kei Begleitig. / Begleitige aaluege | Les dates sont proposées par les accompagnements. Tu n’en fais pas partie dans ce district. / Voir les accompagnements | Le date sono offerte dagli accompagnatori. In questo distretto non lo sei. / Vedi gli accompagnatori |
| `emptyText` | Sobald jemand in Einführung ist, siehst du hier, ob die Termine reichen. | As soon as someone is in training, you see here whether the dates are enough. | Sobald öpper i de Iifüehrig isch, gsehsch da, öb d Termin länged. | Dès que quelqu’un est en introduction, tu vois ici si les dates suffisent. | Appena qualcuno è in introduzione, qui vedi se le date bastano. |
| `introPickups.form.purpose` | Gedacht für {people}: {need}. | Meant for {people}: {need}. | Dänkt für {people}: {need}. | Prévue pour {people} : {need}. | Pensata per {people}: {need}. |
| `introPickups.form.purposeFits` / `.purposeMisfit` | Passt für die Gruppe. / So hilft der Termin dieser Gruppe nicht: {reason}. | Fits the group. / Like this the date does not help this group: {reason}. | Passt für d Gruppe. / So hilft de Termin dere Gruppe nöd: {reason}. | Convient au groupe. / Ainsi, la date n’aide pas ce groupe : {reason}. | Va bene per il gruppo. / Così la data non aiuta questo gruppo: {reason}. |

Further small keys follow the mockup wording: `why.ANY` (= `noDates`), `why.OTHER_GUIDE_SELF`, `form.purposeGuide` ("Du bist
die Begleitung dieses Termins."), `form.purposeReason.category|fairteiler`, `form.slotsPrefilled`, `form.categoryPrefilled`,
`form.startHint`. `{guides}` and `{categories}` are joined with the locale's "oder" via `Intl.ListFormat` (disjunction).

## 5. Accessibility

- Status never by colour alone: the verdict has the status tag (icon + word) and the sentence; total tiles carry
  "reicht" / "zu wenig" with an icon; the meter is decorative (`aria-hidden`) because the sentence holds the numbers.
- Structure: `h2` section, verdict sentence as `p` inside a `role="status"` region (announced after a reload), `h3`
  "Diese Termine fehlen", `ul` of cards each `aria-labelledby` its `h4`; people as `ul`.
- The button's accessible name is `createFor` ("Termin anlegen: Termin mit Fairteiler-Besuch"), so a list of buttons is
  distinguishable; the "auch bei" chip is a link with visible text that moves focus to the other card (`tabindex="-1"`).
- Avatars are decorative next to the name; anonymous avatars are `aria-hidden`, the count is text.
- Dialog: `aria-describedby` points at the purpose banner; its fit line is `role="status"` so a change that breaks the
  fit is announced; hints are tied to their fields with `aria-describedby`. Targets follow `--fs-target-sm` (36/44 px).

## 6. Mobile (412 px, touch)

Verdict stacks (icon disc 2.5rem above the tag and sentence), totals one per row, cards in one column in server order
(most people first). In the card foot the help line sits above a full-width 44 px button. Person rows wrap the "auch
bei" chip under the subtitle. The dialog is `min(42rem, 96vw)` with a scrolling body and fixed footer; the banner stays
the first thing visible. No table, no horizontal scrolling.

## 7. Test ids

Kept: `introPickups.funnel.state|window|admin|total|stalled|step|count|stepStalled|names|person`, `introPickups.funnel.need`
(moves onto the first total tile), all `introPickups.form.*`. Removed: `introPickups.funnel.gapTable`, `introPickups.funnel.gap`
(the e2e and screenshot specs tagged for EA21 that use them must be rewritten, not deleted). New: `introPickups.funnel.check`
(`data-status`), `.check.headline`, `.check.state` (+ `.retry`), `introPickups.funnel.fairteilerNeed`,
`introPickups.funnel.checkGroup` (`data-kind`, `data-people`), `.checkGroup.person`, `.checkGroup.also`, `.checkGroup.help`,
`.checkGroup.fits`, `.checkGroup.create`, `.checkGroup.cannot`, `.checkGroup.askGuides`, `introPickups.form.purpose`
(`data-fit`), `introPickups.form.purposeFit`.

## 8. Rejected alternatives

1. **A newcomer × date matrix** (who can book which date, with the blocking rule in each cell). Exact, but it is the
   table again, unreadable at 412 px, and it shows names to guides who may only see counts.
2. **A list per stuck person with an action each** ("Luca Meier: andere Begleitung nötig → Termin anlegen"). Easy to
   read, but it leads to one date per person; grouping by need is what lets one date serve several (owner's wish).

## 9. Questions (with recommendation)

1. **Where do the two totals live?** Today they are tiles in the top stat row. Recommendation: move them into the
   verdict card (as in the mockup) so the numbers sit next to the sentence that explains them; the top row keeps Neue /
   in Einführung / stocken. Keeping them on top as well would show the same numbers twice.
2. **Button wording.** The brief says "Termin anlegen"; the same dialog is opened elsewhere by "Termin anbieten"
   (`introPickups.guide.create`). Recommendation: reuse "Termin anbieten" so one action has one name; the mockup shows
   "Termin anlegen" until you decide. Same for "Betriebsart" versus the form's "Art des Betriebs" (recommendation:
   keep "Betriebsart" in the card title, it reads better in a sentence).
3. **Who gets the button?** The dialog makes the person who saves the guide. So (a) a guide the group already had
   cannot help and (b) an ambassador who is not a guide cannot offer a date. Recommendation: as in the mockup, no button
   in both cases, a short note, and for (b) a link to the guides list. "Create on behalf of a guide" would be a new
   permission and is better left out of this change.
4. **Names for guides.** Today the page shows names only with `CAN_MANAGE_USERS`; guides see counts. Recommendation:
   keep that rule (changing it is a data-protection decision); the need and the count are enough to offer a date.
5. **"Anbieten könnten: Anna Roth oder Mirjam Vogel"** on the other-guide card (guides of the region nobody in the
   group has had). Recommendation: yes, it tells an ambassador whom to ask; it costs one more list from the server.
6. **One date that must meet two rules** (e.g. Fairteiler visit and another guide). Recommendation: that is one card
   with a combined title ("Termin mit Fairteiler-Besuch und einer anderen Begleitung als Julien Favre"); a person
   appears in two cards only when two separate dates are needed. The server would send a need as a set of conditions.
7. **Does the "Registriert in" filter change the check?** Recommendation: no. The check always covers everyone in
   training; a filtered check could say "Reicht" while an older newcomer is stuck.
8. **Should the people in a group be told when the date is saved?** Recommendation: not in this change; they see the
   new date in their booking list. A notification would be a separate task (wording, opt-out).
