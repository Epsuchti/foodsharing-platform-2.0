# EinAbs from own store pickups, linked and synced (EA16, phase 1 of 2)

## Goal
Guides no longer link an external calendar (iCal). The `/guide` dashboard offers the guide's own **confirmed upcoming store pickups** as EinAb candidates. "Use" pre-fills the whole form, and the EinAb stays **linked** to that store pickup: when the guide is no longer signed up for it, the EinAb is cancelled and every booked newcomer is notified. Phase 2 (`2026-11-05-movable-pickups.md`) makes store pickups movable, and linked EinAbs move with them.

## Decisions (new decision 168; owner answers 2026-11-05)
- **The iCal input is removed completely.** That covers `GuideProfile.icalLink`, `GET /api/guide/ical-candidates`, `IcalCandidate`, the errors `ICAL_LINK_NOT_ALLOWED`/`ICAL_FETCH_FAILED`, `IcalImportService`, the `app.intropickup.ical-allowed-hosts`/`ical-allow-own-base-url` config, the dev fixture `/api/dev/fixtures/ical` (+ `resources/dev/ical-fixture.ics`, `DevNotificationService.icalFixture`) and the `ical_link` column.
- **The outputs stay:** `IntroPickupIcalService`, `/api/intro-pickups/{id}/calendar.ics`, `/api/guide/ical-feed-link`, `/api/public/intro-pickups/ical/{token}`, `ical_feed_token`.
- **Candidates** are all of the guide's future **confirmed** sign-ups, with no horizon; unconfirmed sign-ups are excluded. They only include stores that lie in, or below, a region where the guide may give EinAbs (`guideRegions()`). Sign-ups that already have a non-deleted EinAb of this guide with the same `storeId` + `startAt` are hidden. The source is `PickupUserService.myPickups()`, which already applies `StoreVisibility`.
- **Pre-fill** (`PickupCandidate` → form):
  - `startAt` = pickup time
  - `storeId`
  - `businessName` = store name
  - `privateAddress` = "street, postcode city"
  - `publicGeneralLocation` = **store city only**
  - `category` = mapped from the store category (table below)
  - `regionId` = the guide region that contains the store (the dashboard switches to it)
  - Everything else is today's default: slot count 2, minimum pickups empty, Fairteiler off, private info from the guide's template for the category. `publicInfo` stays empty and is not copied from the store.
  - There is no end time. EinAbs have only `startAt`, and the .ics output keeps its 1 h.
- **Category mapping** (store categories are editable DB rows, EinAb categories an enum, so they don't match exactly): a fixed server-side map by store category name. The guide can change the result.

  | Store category | EinAb category |
  |---|---|
  | Supermarkt, Bio-Laden | SUPERMARKET |
  | Bäckerei | BAKERY |
  | Restaurant, Hotel | RESTAURANT |
  | Markt | MARKET |
  | Take-away | TAKEOUT |
  | Grossverteiler | GROSSABHOLUNG |
  | none or unknown | SUPERMARKET |

  Unit-tested.
- **Link:** the new nullable column `intro_pickup.linked_pickup_at` together with the existing `store_id`; the guide is `guide_id`. Linked means `linked_pickup_at != null`. A create from a candidate sends the new optional `IntroPickupCreate.linkedPickupAt`. The server checks that the current user has a **confirmed** sign-up at `storeId`+`linkedPickupAt`, otherwise 409 `STORE_PICKUP_NOT_SIGNED_UP`. On create it sets `startAt = linkedPickupAt`. If `startAt` differs from `linkedPickupAt`, the link is not stored.
- **Existing EinAbs** and EinAbs created by hand stay unlinked.
- **Cancel sync:** the guide's sign-up for a linked store pickup disappears → the EinAb is cancelled in the same transaction, booked or not. All removal paths count:
  1. the guide signs out, or a manager removes them (`PickupSignupService.signOut`, existing `PickupSignedOutEvent`)
  2. automation removal (`removeByAutomation`, existing event)
  3. "delete all future pickups" (`PickupUserService.deleteAllFuture`, a new event)
  4. leaving or removal from the store team (`StorePickupInfoImpl.deleteFutureSignups`, a new event)

  The new event is `PickupEvents.PickupSignupsDeletedEvent(userId, List<(storeId, pickupAt)>, byUserId, reason)`, published by paths 3 and 4. What the cancel does:
  - all slots → CANCELLED, with booked places not counted against the newcomers (same as `cancelBooking`)
  - `deletedAt` set
  - history `IntroPickupAction.AUTO_CANCELLED` (metadata `cause` = SIGNED_OUT / REMOVED / AUTOMATION / ALL_FUTURE_DELETED / LEFT_TEAM)
  - every booked newcomer gets `INTRO_PICKUP_CANCELLED_BY_GUIDE` with reason text `introPickups.sync.cancelReason` ("The store pickup of this EinAb was cancelled")
  - the guide gets a bell `INTRO_PICKUP_AUTO_CANCELLED` (new type)

  The `softDelete` guard BOOKED_SLOTS_PREVENT_DELETE does **not** apply to this path. Past EinAbs are never touched.
- **Guide edits:** the guide may edit everything. Changing `startAt` or `storeId` of a linked EinAb **removes the link** (`linked_pickup_at = null`, history metadata `unlinked=true`). The form shows a hint while the EinAb is linked: "Linked to your store pickup on …; changing date or store removes the link." Other fields keep the link. Store name/address changes are not copied over later.
- **Reassign:** a linked EinAb cannot be handed to another guide: 409 `LINKED_INTRO_PICKUP_NOT_REASSIGNABLE`, and the button is hidden for linked EinAbs.
- **Sign-out warning:** when the current user signs out of a pickup with a linked EinAb, the dialog says "This cancels your EinAb; N booked newcomers will be notified."
  - The pickup module declares the interface `LinkedIntroPickupInfo` (`Optional<LinkedInfo(introPickupId, bookedCount)> forSignup(userId, storeId, pickupAt)`) with a default bean returning empty, the same pattern as `StorePickupInfo`/`StoreDefaults`.
  - intropickup implements it.
  - The own sign-up in the pickup slot model gets an optional `linkedIntroPickup {introPickupId, bookedCount}`, set only for the viewer's own sign-up.
  - Managers removing someone see the same text.
- Booked newcomers get messages about every synced change (owner). In phase 1 that is the cancel; phase 2 adds the move.

## Contract (contract first, then regenerate)
- `schemas/intro-pickups.yaml`:
  - remove `GuideProfile.icalLink`, `GuideProfileUpdate.icalLink` and `IcalCandidate`
  - add `PickupCandidate {storeId, storeName, startAt, address, city, category, regionId}`
  - `IntroPickupCreate.linkedPickupAt?` (date-time)
  - `IntroPickupDetail.linkedPickupAt?`
- `paths/intro-pickup-guide.yaml`:
  - remove `/api/guide/ical-candidates`
  - add `GET /api/guide/pickup-candidates` (operationId `listPickupCandidates`, x-permission `CAN_GIVE_INTRO_PICKUPS@region`, 200 array)
  - PATCH profile: drop the 400 ICAL text
  - create: 409 `STORE_PICKUP_NOT_SIGNED_UP`
  - assign: 409 `LINKED_INTRO_PICKUP_NOT_REASSIGNABLE`
- `paths/dev.yaml`: remove `/api/dev/fixtures/ical`.
- `schemas/common.yaml`: remove `ICAL_LINK_NOT_ALLOWED`, `ICAL_FETCH_FAILED`; add `STORE_PICKUP_NOT_SIGNED_UP`, `LINKED_INTRO_PICKUP_NOT_REASSIGNABLE`.
- `schemas/pickups.yaml`: own sign-up `linkedIntroPickup?`.
- oasdiff reports breaking changes. Only our own client uses them; note that in the PR.

## Server
- Delete `IcalImportService` (and its part of `IcalServicesTest`; keep the feed-output tests).
- `GuideProfileService.update(phone)`; `ProfileView` without `icalLink`.
- `IntroPickupAccountData` no longer exports `icalLink`.
- `AppProperties` drops both iCal fields.
- `application.properties`, `-local` and `-e2e` lose the iCal lines.
- `DevController`/`DevNotificationService` lose the fixture.
- Javadocs/package-info: drop "iCal import".
- New `IntroPickupCandidateService` (or a method on `IntroPickupGuideService`) with the candidate rules and a pure `StoreCategoryMapping` (unit tests).
- New `IntroPickupPickupSync` in intropickup: `@EventListener` (same transaction) for `PickupSignedOutEvent` and `PickupSignupsDeletedEvent`. It finds linked EinAbs by `(guideId, storeId, linkedPickupAt)` (new repository method) and calls `IntroPickupGuideService.autoCancel(pickup, cause)`. The notifications go out after commit, like today.
- `IntroPickupAction.AUTO_CANCELLED`; `NotificationType.INTRO_PICKUP_AUTO_CANCELLED` with bell texts and a mail template in all five languages.
- `update()`: unlink rule. `assign()`: 409 when linked. `create()`: link check.
- Layering: the controller only calls services; intropickup → pickup service and events are allowed. pickup → intropickup only through the `LinkedIntroPickupInfo` interface (ModulesTest must pass).
- History: every new mutating method records history or carries `@NoHistory`.

## Liquibase and seed
- New `188-intro-pickup-linked-pickup.yaml`:
  - `dropColumn guide_profile.ical_link`
  - `addColumn intro_pickup.linked_pickup_at TIMESTAMP WITH TIME ZONE NULL` (same type as `start_at`)
  - index `ix_intro_pickup_linked (guide_id, store_id, linked_pickup_at)`
- Never edit 046. Works on H2 and PostgreSQL.
- Seed:
  - `guide_profile.csv` without `ical_link`
  - `intro_pickup.csv` gets `linked_pickup_at` (NULL for existing rows), plus one new linked upcoming EinAb of the guide on one of his confirmed future store sign-ups, with one booked newcomer (fixed UUIDs in the 0030 range, `docs/seed-ids.md`)
  - The seed date shift must shift `linked_pickup_at` with `start_at` (`IntroPickupSeedDateShift`) so it matches the shifted pickup sign-up (`PickupSeedDateShift`).
  - Make sure the guide (`00000001-0001-…-010`) has at least two confirmed future sign-ups and one unconfirmed one after the e2e anchor 2026-11-02.
- Regenerate `docs/er`.

## Client
- `guide-settings-page`: remove the iCal field and hint.
- `guide-dashboard-page`:
  - The iCal card becomes "Your upcoming store pickups": `loadResource(listPickupCandidates)` + `<fs-data-state>`, hidden when empty, as today.
  - Items show the store, date/time and city.
  - testids `introPickups.guide.pickupCandidates`, `.pickupCandidate`, `.pickupCandidateUse`.
  - "Use" switches the region if needed and opens the form with the prefill.
- `einab-form-dialog`: `EinAbPrefill` gets `storeId, publicGeneralLocation, category, linkedPickupAt`. The form model carries `storeId`/`linkedPickupAt` into `body()` on create. A linked hint (`introPickups.form.linkedHint`) is shown, and a hint appears once date or store is changed on a linked EinAb (`introPickups.form.unlinkHint`).
- EinAb card: a "linked to store pickup" chip; the reassign button is hidden when linked.
- Store pickups sign-out/remove dialog: the warning when `linkedIntroPickup` is present (`pickups.signout.linkedIntroPickup`, with count).
- i18n: remove the dead iCal keys (feature + shared error labels); add new keys in de/en/gws/fr/it; `npm run i18n:check` must pass.

## Tests
- Unit:
  - `StoreCategoryMapping`
  - the candidate filter (confirmed only, region, already-used hidden)
  - the unlink rule
  - the auto-cancel (booked not counted, newcomers notified, past untouched)
  - reassign 409
- `IntroPickupApiIT`:
  - replace the iCal cases: candidates; create linked; 409 without a sign-up; sign-out → EinAb cancelled + bell/mail to the newcomer (Mailpit/outbox)
  - `deleteAllFuture` and team removal also cancel
  - old path 404
  - check the iCal mentions in `QueryCountIT` and `BackupRestoreIT`
- e2e: replace `ea16-ical-import.spec.ts` with `e2e/tests/intro-pickups/ea16-pickup-candidates.spec.ts` `@EA16`:
  - the candidate list shows confirmed pickups only
  - "use" pre-fills date, store name, address, city and category; save creates the EinAb, and the candidate disappears
  - sign out of that store pickup → the warning is shown → the EinAb is gone from the dashboard and the booked newcomer has a bell (seed newcomer)
- Screenshots: add `@EA16` to the guide dashboard and settings screenshot test in `e2e/screenshots/intro-pickups*.ts`, and add the sign-out warning dialog state. Regenerate in CI: `gh workflow run screenshots-update.yml --ref <branch> -f grep=@EA16`. Review every PNG.

## Docs
- Ledger EA16 row: "Own confirmed store pickups as linked EinAb candidates; cancel sync". plan.md line 950 (drop external ICS) and line 1012. Decision 168 (amends the EA16 / A50 notes). Remove any own-base-url note in `docs/conventions.md` (search `own-base-url`).

## Open for the owner (planner's recommendation applied; confirm in the summary)
- No 28-day horizon, as answered.
- The sign-out warning is shown (13).
- The category table above (16/17).
- No designer mockup (the card keeps today's layout).
- Old `ical_link` values are dropped without notice.
