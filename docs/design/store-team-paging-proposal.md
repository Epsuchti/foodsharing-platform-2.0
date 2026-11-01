# Store team as one paged list, and the per-member statistics card — design proposal

Status: proposal for `docs/tasks/2026-11-01-store-team-paging.md`, round 2 (owner decisions of 2026-11-01 and the
round-1 feedback applied: one status chip group with "Verantwortliche" in it, person cards with stat tiles). No code changed.
Mockup: `store-team-paging-mockup.html` next to this file (inline CSS/JS; toggles for Vorschlag/Heute, Team/Statistik,
role, light/dark, desktop/phone 412 px; the filter bar in the mockup really filters, sorts and pages a 23-person team).

## 1. The problem

`/stores/:id/team` renders every member and every jumper in two cards from one `getStoreTeam` call. A store with 60
people is a 60-row page with no search, no way to find "who has not picked up for months", and the jumpers are a second
list with the same columns. The statistics card "Abholungen pro Mitglied" is the same list again, unpaged, sorted by
count only, and it cannot show people who picked up in the range but have since left the team. Both lists now come one
page at a time from the server (page size 20); the UI must say where you are in the list and what you filtered.

## 2. States

| # | State | Team tab | Statistics card |
|---|---|---|---|
| 1 | Loading | filter bar visible and usable, `skeleton="list"` 6 rows below it | same, inside the card |
| 2 | Refreshing (filter changed) | the old rows stay, `aria-busy`, skeleton only on the first load (DataState default) | same |
| 3 | Loaded, 1 page | count line "23 Personen", rows, no paginator | count line, table, no paginator |
| 4 | Loaded, > 1 page | paginator under the rows (`totalPages > 1`), `pageLinkSize 3`; a page change scrolls the card top into view | same |
| 5 | Empty, no filter | "Noch niemand im Team." (existing key, as a muted line) | "Keine Abholungen im Zeitraum." |
| 6 | Empty, filter active | illustration `search`, "Niemand passt zum Filter", button "Filter zurücksetzen" | same |
| 7 | Error / offline / forbidden / not found | DataState defaults with retry | same; the whole card is hidden without `mayEditStore` (it never renders a forbidden state for foodsavers) |

Rows carry badges in words, never colour alone: "Verantwortlich" (crown), "Springer" (clock, new), "Schlafmütze"
(moon), "Nicht verifiziert" (warning), and in the statistics "Ehemalig" (sign-out, new).

## 3. Visual spec (tokens from `client/src/styles.scss`)

**Team tab, top to bottom:** toolbar with "Einladen" (managers, unchanged) → one card `stores.team.members` → the
applications and invitations cards (unchanged, still from `getStoreTeam`).

Card content:
1. Filter bar (`.filters`): a 2-column grid from 48rem (`minmax(0, 1fr) 14rem`), one column below.
   - Search: `pInputText type="search"`, label "Suche" (0.85rem/650 as `.field > label`), placeholder "Name", debounced
     300 ms (as `region-members.tab.ts`), clears the page to 0.
   - Sort: `p-select` with label "Sortieren", options Name / Abholungen / Letzte Abholung, `appendTo="body"`, full width.
   - Chip row (`role="group"` "Status", full grid width), one single-select group: "Alle 23", "Verantwortliche", "Team 19",
     "Springer 4". `status` = ALL | MANAGER | MEMBER | JUMPER; **Team means status MEMBER, managers included** (so
     Alle = Team + Springer, as `StoreTeamCounts` counts them; Verantwortliche is a subset of Team and has no count).
     Chip style as the statistics range chips: `0.3rem 0.8rem`, `border 1px --fs-border`, `--fs-surface`, 999px,
     `min-height var(--fs-target-sm)`; active: `border --fs-primary`, `--fs-primary-soft`, `--fs-primary-strong`. The
     count is a `0.78rem/700` pill on `--fs-surface-muted` (active: `color-mix(--fs-primary 16 %, transparent)`).
2. Count line `.muted` 0.9rem: "{n} Personen" from `page.totalItems`, so a filter always has a visible result count.
3. Person card (`li.person`, replaces today's text row): bordered, `--fs-radius-md`, padding `0.75rem 0.85rem`, grid
   `auto minmax(0,1fr) auto`. Left: `fs-user-avatar` 2.5rem. Middle, line 1: name 650 with the verified icon; line 2:
   badges in words (crown "Verantwortlich" `--fs-primary-strong`; clock "Springer" neutral `--fs-surface-muted`; moon
   "Schlafmütze"; "Nicht verifiziert" `--fs-warning-soft`/`--fs-warning-text`). Middle, line 3: a `dl` of stat tiles
   (`.tile-s`: `--fs-surface-muted`, `--fs-radius-sm`, `0.35rem 0.6rem`, icon `--fs-text-muted` 1.1em at the left,
   value 650 tabular on top, label 0.72rem `--fs-text-muted` under it): bag "24 Abholungen", calendar "29.10.2026
   Zuletzt", calendar-plus "15.01.2025 Dabei seit", and for `maySeeMemberDistance` a pin "0.8 km Entfernung". Right:
   phone button and the row menu (unchanged). The tiles are information, not buttons (no hover, no focus).
   `data-status="MEMBER|JUMPER"` and `data-manager` stay on the card.
4. Paginator: `p-paginator` as on the region members tab; the current page is `--fs-primary-soft`/`--fs-primary-strong`.

**Statistics card** (managers only, under the chart, `stores.statistics.members`): title "Abholungen pro Mitglied",
the same filter bar with the status group Alle / Verantwortliche / Team / Springer / Ehemalige (no counts) and the
sort default "Abholungen"; the range chips at the top of the tab (Letzte 12 Monate / Gesamte Zeit) feed `from`/`to`
into this query too. Table columns: Mitglied (user
chip + badges Verantwortlich / Springer / Ehemalig), Abholungen (right, tabular), Letzte Abholung (date within the
range, "–" when none; right-aligned, `--fs-text-muted`). Column headers are plain `th` (no header sorting; one sort
control for both lists keeps the phone and the desktop identical). `.flag--former`: `pi pi-sign-out` + "Ehemalig",
`--fs-surface-muted`, `--fs-text-muted`, `data-status="FORMER"`.

Default order (`NAME`): managers first, then name. `PICKUP_COUNT`/`LAST_PICKUP` sort descending with "no pickup" last,
so a manager with 0 pickups drops to the end; the crown badge keeps them recognisable.

Dark mode: nothing hard-coded; the mockup's "Dunkel" toggle shows all tokens.

## 4. Labels and i18n keys (`client/src/app/features/stores/i18n/*.json`)

| Key | de | en | gws | fr | it |
|---|---|---|---|---|---|
| `stores.team.search` | Suche | Search | Suechi | Recherche | Cerca |
| `stores.team.searchPlaceholder` | Name | Name | Name | Nom | Nome |
| `stores.team.statusFilter` | Status | Status | Status | Statut | Stato |
| `stores.team.filter.all` | Alle | All | Alli | Tous | Tutti |
| `stores.team.filter.manager` | Verantwortliche | Managers | Verantwortlichi | Responsables | Responsabili |
| `stores.team.filter.member` | Team | Team | Team | Équipe | Team |
| `stores.team.filter.jumper` | Springer | Jumpers | Springer | Remplaçant·es | Jolly |
| `stores.team.sort` | Sortieren | Sort by | Sortiere | Trier par | Ordina per |
| `stores.team.sort.name` | Name | Name | Name | Nom | Nome |
| `stores.team.sort.pickupCount` | Abholungen | Pickups | Abholige | Récupérations | Ritiri |
| `stores.team.sort.lastPickup` | Letzte Abholung | Last pickup | Letschti Abholig | Dernière récupération | Ultimo ritiro |
| `stores.team.count` | {count, plural, one {# Person} other {# Personen}} | {count, plural, one {# person} other {# people}} | {count, plural, one {# Person} other {# Persone}} | {count, plural, one {# personne} other {# personnes}} | {count, plural, one {# persona} other {# persone}} |
| `stores.team.jumper` | Springer | Jumper | Springer | Remplaçant·e | Jolly |
| `stores.team.emptyFilter` | Niemand passt zum Filter | Nobody matches the filter | Niemert passt zum Filter | Personne ne correspond au filtre | Nessuno corrisponde al filtro |
| `stores.team.resetFilter` | Filter zurücksetzen | Reset filter | Filter zruggsetze | Réinitialiser le filtre | Reimposta il filtro |
| `stores.team.list` (a11y name of the list) | Teamliste | Team list | Teamlischte | Liste de l’équipe | Elenco del team |
| `stores.statistics.filter.former` | Ehemalige | Former | Ehemaligi | Ancien·nes | Ex membri |
| `stores.statistics.former` | Ehemalig | Former | Ehemalig | Ancien·ne | Ex membro |
| `stores.statistics.lastPickup` | Letzte Abholung | Last pickup | Letschti Abholig | Dernière récupération | Ultimo ritiro |
| `stores.statistics.membersEmpty` | Keine Abholungen im Zeitraum. | No pickups in this range. | Kei Abholige i dem Zitruum. | Aucune récupération sur la période. | Nessun ritiro nel periodo. |

The statistics filter bar reuses `stores.team.search|statusFilter|filter.*|sort|sort.*|count|emptyFilter|resetFilter`
(same words, one source). Existing keys kept: `stores.team.members` (card title "Team"), `noMembers`, `manager`,
`sleeping`, `unverified`, `pickupCount`, `lastPickup` ("Zuletzt"), `addedAt`, `distance`, `km`, `actions`,
`stores.statistics.perMember|member|metric.pickups`. Removed: `stores.team.jumpers`, `stores.team.noJumpers`.
gws and fr need the owner's native glance (as in the pickup-slots round).

## 5. Accessibility

- Chip group: `role="group"` with `aria-label` "Status"; each chip `aria-pressed`, exactly one pressed; the count is
  inside the button text ("Springer 4" reads as one name).
- Stat tiles are a `dl`: each tile `dt` (label) + `dd` (value) with the icon `aria-hidden`, so a screen reader hears
  "Abholungen 24, Zuletzt 29.10.2026"; the visual order (value above label) is CSS only.
- Search `label for`, `type="search"`; the sort `p-select` has `inputId` + label. No placeholder-only labels.
- Count line is `aria-live="polite"` so a filter change is announced ("4 Personen"); the rows list has `aria-label`
  "Teamliste". The row's `data-status` and the badge text both carry the status: never colour alone.
- Paginator buttons keep Optimus' names; the current page has `aria-current="page"`.
- Focus after "Filter zurücksetzen" goes to the search field. Focus after a page change stays on the paginator.
- Targets: chips `min-height var(--fs-target-sm)` (36 px mouse / 44 px touch), inputs 44 px on touch (global rule).

## 6. Mobile (412 px, touch)

Search, then sort, each full width; the four chips wrap to two lines and stay 44 px tall. Person card: avatar, name
and badges on the first lines with the phone and menu buttons at the right; the stat tiles drop under them as a
2-column grid over the full card width (3–4 tiles = two rows, each tile 44 px tall). The
statistics table keeps three columns; the date column shortens to the numeric `dd.MM.yy` via the existing `fsDate`
'date' format, and the name cell wraps. No horizontal overflow (checked in the mockup frame). The paginator with
`pageLinkSize 3` is 5 buttons wide and fits.

## 7. Test ids

Kept, same elements: `stores.team.state`, `stores.team`, `stores.team.invite`, `stores.team.members` (now the single
list card), `stores.team.member` (`data-user`, `data-status`, `data-manager`), `stores.team.manager`,
`stores.team.sleeping`, `stores.team.unverified`, `stores.team.pickupCount`, `stores.team.distance`, `stores.team.call`,
`stores.team.menu`, `stores.team.menuPanel` and the menu items, `stores.team.applications|application|invitations|
invitation` and their buttons, `stores.team.messageDialog`, `stores.statistics.members`, `stores.statistics.member`
(`data-user`). Removed: `stores.team.jumpers`.
New: `stores.team.list` (DataState of the page), `stores.team.search`, `stores.team.filter.all|manager|member|jumper`,
`stores.team.sort`, `stores.team.count`, `stores.team.paginator`, `stores.team.jumperBadge`, `stores.team.emptyFilter`,
`stores.team.resetFilter`; `stores.statistics.members.state|search|filter.all|manager|member|jumper|former|sort|count|
paginator|emptyFilter|resetFilter`, `stores.statistics.jumperBadge`, `stores.statistics.formerBadge`.

## 8. Rejected alternatives

1. **Two paged cards (Team, Springer), each with its own search and paginator.** Keeps today's shape, but doubles every
   control, and the owner's questions ("who in the whole store has not picked up since summer?") span both lists.
   Rejected; the status chip gives the two-card view back in one click.
2. **Sortable column headers in the statistics table** (click "Abholungen" to sort). Familiar on desktop, but the team
   rows are not a table, so the two lists would sort differently, and on the phone a header click is a poor target
   with no visible state. Rejected in favour of one sort select used by both.

## 9. Questions for the owner (with recommendation)

1. **What "Team" means next to "Verantwortliche".** Proposed: status MEMBER including managers (Alle = Team + Springer,
   the counts add up, and a manager is a team member). The alternative "Team = members who are not managers" makes
   the chips disjoint but the count 19 would have to become 17 and nothing in the data model has that status.
   Recommendation: managers included; "Verantwortliche" has no count (at most three per store).
2. **Does "Alle" in the statistics include former people?** Recommendation: yes. The card is "who picked up in the
   range"; former people are part of that answer and carry the "Ehemalig" badge. "Ehemalige" narrows to them only.
3. **Filter state in the URL** (`?status=JUMPER&sort=PICKUP_COUNT`) so the back button and shared links keep it?
   Recommendation: not now; in-memory signals as on the region members tab, same as every other list in the app.
4. **Sort by pickups drops managers from the top.** Recommendation: accept; the crown badge marks them, and a sort
   that silently keeps managers first would lie about the numbers.
5. **Statistics chips without counts** (counts would need a per-range query). Recommendation: no counts there; the
   live count line "{n} Personen" under the bar says how many match.
