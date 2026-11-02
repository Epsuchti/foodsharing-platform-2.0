# Edit and delete own chat messages (resume item 16)

## Goal
In every conversation, senders can edit or delete their own messages within 24 hours. Moderators can delete other people's messages with a reason. Others see "bearbeitet" / "gelöscht". Every edit and delete records the original text in the history, and only scoped moderators can read it there.

## Decisions
- Owner, 2026-11-01:
  - **Chats covered:** all five conversation kinds: DIRECT (including basket requests), GROUP, STORE_TEAM, STORE_JUMPER and REGION. Walls (store, event, Fairteiler, profile, report) are out of scope.
  - **Time limit:** edit and delete are both allowed only within 24 hours of `sent_at`. The limit is configurable through `ConfigurationService` (key `messaging.edit-window-hours`, default 24). System messages can never be edited or deleted.
  - **Who reads originals:**
    - ORGA, in every chat;
    - store managers (`mayEditStore`), in STORE_TEAM/STORE_JUMPER chats;
    - region admins (`mayAdministrateRegion`), in REGION chats;
    - DIRECT and GROUP: ORGA only.
  - **Moderator delete:** the same moderators may delete other people's messages, with a required reason. Moderators never edit other people's messages. The 24-hour limit applies to the sender only.
  - Originals are kept 2 years (`RetentionPolicy.YEARS_2`).
  - A **new decision amends A33** ("message bodies are never recorded"): edit and delete events record the original text, readable only by the moderators above.
- Planner:
  - A deleted message keeps its row and `seq` and is marked deleted; `body` is cleared to `""`.
  - Every edit records one history entry, so a chain of edits stays complete.
  - Push messages, mails and Telegram notifications that were already sent are not changed.

## Scope
- In:
  - Changelog, entity, contract, service rules, history redaction, realtime update, conversation preview, UI, i18n, tests, seed.
- Out:
  - Walls.
  - Editing attachments (none exist).
  - Retracting notifications that were already delivered.
  - A moderator UI beyond the existing history view.

## Changes
**Database**
- New changelog `server/src/main/resources/db/changelog/changes/178-message-edit-delete.yaml` (next free number), for PostgreSQL and H2.
- Columns on `message`: `edited_at` TIMESTAMP NULL, `deleted_at` TIMESTAMP NULL, `deleted_by` UUID NULL.
- Mirror them in `messaging/domain/ConversationMessage.java`.

**Contract** (`openapi/paths/messaging.yaml`, `openapi/schemas/messaging.yaml`)
- `PATCH /api/conversations/{conversationId}/messages/{messageId}`, operationId `editMessage`, body `MessageUpdate{body 1..10000}`. Returns `ChatMessage`.
- `DELETE` on the same path, operationId `deleteMessage`, optional body `MessageDelete{reason 1..500}` (required for moderators). Returns `ChatMessage`.
- Error codes, added to `ApiErrorCode`:
  - 400 `MESSAGE_EMPTY`, `MESSAGE_TOO_LONG`, `DELETE_REASON_REQUIRED`
  - 403 `NOT_CONVERSATION_MEMBER`, `MESSAGE_NOT_EDITABLE` (not your message, a system message, or edit by a moderator)
  - 404 `MESSAGE_NOT_FOUND`
  - 409 `MESSAGE_EDIT_WINDOW_EXPIRED`, `MESSAGE_DELETED`
- `ChatMessage` gains `editedAt`, `deletedAt`, `deletedByModerator` (boolean), `mayEdit` and `mayDelete`. The server computes them for the viewer, so the client doesn't copy the rule. `body` drops `minLength` (`""` when deleted).
- `RealtimeEventType` gains `CONVERSATION_MESSAGE_UPDATED` (payload `ChatMessage` + `conversationId`).

**Server** (`server/src/main/java/network/foodsharing/messaging/`)
- `domain/MessageEditRules.java` (new, pure, unit tested):
  - `mayEdit(actor, msg, now, window)`
  - `mayDelete(actor, msg, now, window, isModerator)`
  - `requiresReason(...)`
- `service/ChatModeration.java` (new): `isModerator(actor, conversation)`.
  - ORGA in every conversation.
  - STORE_TEAM/STORE_JUMPER: `StorePermissionService` rights `mayEditStore` for the linked store (`StoreConversationRepository`).
  - REGION: `RegionPermissionResolver` `mayAdministrateRegion` (`RegionConversationRepository`).
  - DIRECT/GROUP: ORGA only.
- `service/MessagingService.java`: `editMessage` and `deleteMessage`.
  - Both lock the conversation row as `send` does, check membership, apply the rules (window from `ConfigurationService`, time from `Clock`), and update `edited_at`/`deleted_*`.
  - If the message is the latest one, `last_message_preview` is refreshed.
  - Both record `MessagingAction.MESSAGE_EDITED` / `MESSAGE_DELETED` with `secondarySubjectId` = messageId, `oldValue` = original, `newValue` = new text (edit), and `reason` (moderator delete).
  - Both publish the realtime update through `RealtimePublisher.toUsers` (after commit).
- `domain/MessagingAction.java`: add the two actions and update the Javadoc (A33 amended).
- `admin/domain/ConfigurationKey.java`: add `messaging.edit-window-hours` (INTEGER, 24).
- History redaction:
  - Add `default boolean mayReadValues(SessionPrincipal actor, UUID subjectId) { return true; }` to `history/HistoryAccessRule.java`.
  - `history/service/HistoryQueryService.java` (and `HistoryCsvExporter`) clear `oldValue`/`newValue`/`reason` for that rule when it returns false.
  - `service/MessagingHistoryAccessRule.java` overrides it with `ChatModeration.isModerator`. `canRead` also lets non-member moderators read (e.g. ORGA).
- `web/MessagingController.java` and its mapper: implement both operations and the new `ChatMessage` fields.
- `service/MessagingAccountData.java`: include `editedAt`/`deletedAt` in the export.

**Client** (`client/src/app/features/messaging/`)
- `components/conversation-view.component.ts`:
  - A message menu (Bearbeiten / Löschen) shown when `mayEdit`/`mayDelete`.
  - Inline edit in the composer, with save/cancel.
  - A confirm dialog for delete. Moderators get `RequiredMessageDialogComponent` for the reason.
  - "bearbeitet" label (with a tooltip showing the edit time) and a placeholder "Nachricht gelöscht" / "Von Moderation gelöscht".
  - Calls go through `runMutation`.
- `messaging.resource.ts`: handle `CONVERSATION_MESSAGE_UPDATED` by replacing the message by id. On reconnect, besides `fillGap`, reload the loaded window (`beforeSeq` = last seq + 1, limit = loaded count, max 100), so edits that arrived while offline show up.
- The chat list preview shows the updated preview from the server.
- New test ids: `messaging.conversation.messageMenu|edit|delete|editSave|editCancel|edited|deleted`.
- i18n in `features/messaging/i18n/{de,en,gws,fr,it}.json`, and the history labels `history.action.CONVERSATION.MESSAGE_EDITED|MESSAGE_DELETED`.

**Seed**
- `db/seed/changes/131-messaging-seed.yaml` is applied, so add a new seed changeset with:
  - one edited message;
  - one message deleted by its sender;
  - one message deleted by a moderator in a store chat;
  - matching history rows.
- Fixed UUIDs; update `docs/seed-ids.md`.

## UI
- A visible change to the message bubble: a message menu, an edit mode, and the edited/deleted states.
- A designer mockup is advised (phone long-press vs desktop hover menu, deleted placeholder).

## Tests
- New:
  - `MessageEditRulesTest`: the window boundaries (exactly 24 h, configurable), system messages, other people's messages, the moderator-delete matrix per conversation kind, the reason required only for moderators.
  - `MessagingServiceTest`: preview refresh, history old/new values recorded, realtime published, deleted message cannot be edited.
  - `MessagingApiIT`: both endpoints and all error codes. History API: originals visible to a store manager in the store chat and to ORGA, hidden from other members (values null), and hidden from region admins in DIRECT chats.
  - `ConfigurationKey` test if one exists. ArchUnit history rule is satisfied.
  - Client unit tests: menu visibility from the flags, realtime replace, reconnect reload.
  - e2e:
    - `e2e/tests/messaging/n1-conversations.spec.ts` (`@N1`): edit, delete, the other member sees bearbeitet/gelöscht.
    - `n2-realtime.spec.ts` (`@N2`): live update.
  - Screenshot specs for the menu, edit mode and deleted states (no baselines; decision 69).
- Existing tests that change on purpose:
  - Assertions on `MessagingAction` values.
  - A history test asserting that bodies are never recorded, if one exists.

## Done when
- [ ] Changelog applied on PostgreSQL and H2; entity matches
- [ ] Contract regenerated; ArchUnit green (history recorded on both mutations)
- [ ] Rules, moderation scope and redaction covered by unit tests and IT
- [ ] Realtime update and reconnect reload work; preview updates
- [ ] UI with `runMutation`, i18n check passes, `@N1`/`@N2` e2e green, screenshot specs added
- [ ] Seed and `docs/seed-ids.md` updated; new decision amending A33 recorded; `docs/conventions.md` A33 updated; ledger updated

## Risks
- Data protection: deleted text stays in the history for 2 years, readable by moderators only. The redaction must also cover the CSV export and any admin history view, or deleted text leaks to members.
- `MessagingHistoryAccessRule` today lets every member read the history. The value redaction is the only barrier, so test it per conversation kind.
- Optimistic pending messages (`clientKey`) cannot be edited until the server confirms them; hide the menu while a message is pending.
- Notifications already delivered still show the original text (accepted; mention it in the decision).
