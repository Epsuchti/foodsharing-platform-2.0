# Chat: edit and delete messages — design proposal

Status: proposal for `docs/tasks/2026-11-01-chat-edit-delete.md` (owner decisions there apply). No code changed.
Mockup: `chat-edit-delete-mockup.html` next to this file (inline CSS/JS; toggles for light/dark, desktop/phone 412 px,
role Sarah = sender / Nina = store manager / Jonas = other member, and "Heute" = the bubble as it is today).

Scope: the message bubble, the composer and two dialogs of `fs-conversation-view`
(`features/messaging/components/conversation-view.component.ts`), plus the chat-list preview. Walls are out of scope.

---

## 1. The problem

A sent message is final. A typo, a wrong pickup time or a message in the wrong chat can only be corrected with a
second message. The bubble has no actions at all: no menu, no hover affordance, nothing on long press. Moderators
(ORGA, store managers in store chats, region admins in region chats) cannot remove a problematic message either.
The server will now compute `mayEdit` / `mayDelete` per viewer (24 h window for the sender, moderators any time) and
will keep deleted rows in place (`body = ""`, `seq` kept), so the UI has to show four new things: a per-message menu,
an edit mode, an "edited" marker and a "deleted" placeholder, each in a way that others can read.

---

## 2. States of one message

| # | State | Data | Looks like | Menu |
|---|---|---|---|---|
| 1 | Normal | `!editedAt && !deletedAt` | as today | "···" when `mayEdit \|\| mayDelete` |
| 2 | Edited | `editedAt` set | body + time + **"bearbeitet"** (text, muted) with tooltip "Bearbeitet am 1. Nov. 2026, 14:32" | as 1 |
| 3 | Being edited (own) | client `editingId === m.id` | bubble gets a 2 px `--fs-primary` outline; composer shows the edit banner; Send turns into Save | hidden |
| 4 | Deleted by sender | `deletedAt && !deletedByModerator` | placeholder bubble: dashed border, muted, icon + "Nachricht gelöscht", time kept | none |
| 5 | Deleted by moderation | `deletedAt && deletedByModerator` | same placeholder, text "Von der Moderation gelöscht" | none |
| 6 | Pending (optimistic) | `clientKey`, not confirmed | as today | none (risk in the plan) |
| 7 | System | `!senderId` | as today | none |

Rules: the placeholder keeps the row, the day group, the avatar and the sender name in group chats, so a reply above
or below still has its context. The sender sees the same placeholder as everybody (no "Du hast …" variant). State 3
is exclusive: starting an edit while another edit is open replaces the draft (after the same unsaved-check as cancel).

Menu contents by role (server flags decide; the client only renders):

| Viewer | Own message ≤ 24 h | Own message > 24 h | Other's message | System |
|---|---|---|---|---|
| member | Bearbeiten, Löschen | no menu | no menu | no menu |
| moderator | Bearbeiten, Löschen | **Löschen (Moderation)** — the server sets `mayDelete`, `mayEdit` false | Löschen (Moderation) | no menu |

---

## 3. Visual spec (tokens from `client/src/styles.scss`)

**Menu trigger** `.msg__menu`: `p-button [text] [rounded] severity="secondary" icon="pi pi-ellipsis-h"`, size
`--fs-target-sm` (36 px mouse / 44 px touch), colour `--fs-text-muted`, placed in the `.msg` flex row *outside* the
bubble (left of an own bubble, right of someone else's) so the bubble width does not change. Desktop: `opacity: 0`
until `.msg:hover`, `.msg:focus-within` or `[aria-expanded="true"]` (always in the DOM, always focusable; no
long-press). Touch (`pointer: coarse`): always visible at `opacity: 1`. `aria-haspopup="menu"`, `aria-expanded`.

**Menu** `p-popover` beside the trigger, `role="menu"`, two `role="menuitem"` buttons `min-height: var(--fs-target-sm)`,
icons `pi-pencil` / `pi-trash`, delete in `--fs-danger`. For moderation the only item is "Löschen (Moderation)" with a
one-line hint under it in `--fs-text-muted` 0.8rem: "Mit Begründung, nur für die Moderation sichtbar". Arrow keys
move, Escape closes and returns focus to the trigger.

**Edit mode**: the composer keeps its textarea (mention autocomplete, Shift+Enter) and gets a banner above it,
`.composer__edit`: `border-left: 3px solid var(--fs-primary)`, background `--fs-primary-soft`, radius
`--fs-radius-sm`, padding 0.4rem 0.6rem; left `pi-pencil` + "Nachricht bearbeiten" (0.85rem/650 `--fs-primary-strong`)
over a one-line ellipsis of the original text (0.8rem `--fs-text-muted`); right an X button "Bearbeiten abbrechen".
Send button becomes `icon="pi pi-check"`, aria "Speichern", disabled while the trimmed text is empty or unchanged.
Enter saves, Escape cancels, focus moves to the textarea on start and back to the trigger on cancel/save. The
bubble being edited gets `outline: 2px solid var(--fs-primary); outline-offset: 2px`. Any draft typed before
"Bearbeiten" is parked and restored on cancel/save.

**Edited marker** `.bubble__edited`: inline after the time, 0.75rem `--fs-text-muted`, text "bearbeitet", separated by
a middle dot; `title` and `aria-label` "Bearbeitet am {date}". Text, never colour alone.

**Deleted placeholder** `.bubble--deleted`: `border: 1px dashed var(--fs-border-strong)`, background transparent
(own bubble loses the `--fs-primary-soft` fill), text `--fs-text-muted` italic 0.9rem, `pi-ban` icon
(`aria-hidden`), time kept. Own/other alignment and the bubble radii stay, so the row still reads as a message.

**Delete confirmation (own)**: the existing `ConfirmationService` dialog as for "Gruppe verlassen": header
"Nachricht löschen?", text, "Löschen" `p-button-danger`, "Abbrechen" text secondary.

**Moderator dialog**: `fs-required-message-dialog` severity `danger`, title "Nachricht von Sarah Müller löschen",
text explains that members see "Von der Moderation gelöscht" and the reason is stored in the history for the
moderation only; a quote of the message (first 200 chars, `blockquote` in `--fs-surface-muted`) above the field;
`minLength` 10, `maxLength` 500 (contract: `reason 1..500`; the dialog's house rule is 10). Confirm "Löschen".

**Dark mode**: nothing hard-coded; the dashed placeholder uses `--fs-border-strong` (#3d473b on #1a201a) and the edit
banner `--fs-primary-soft` (green 14 %) with `--fs-primary-strong` #82d59b text. Toggle "Dunkel" in the mockup.

**Chat list preview**: the server refreshes `lastMessagePreview` when the latest message changes. For a deleted
latest message the client shows "Nachricht gelöscht" in italics (question 3).

**Toasts** (`runMutation` `successToastKey`): "Nachricht gespeichert", "Nachricht gelöscht". Error codes map to the
existing `errors.<CODE>` catalogue, e.g. `errors.MESSAGE_EDIT_WINDOW_EXPIRED` "Die Nachricht ist älter als 24 Stunden und
kann nicht mehr geändert werden." (the hour count is the configured window, passed as `{hours}`), `errors.MESSAGE_DELETED`
"Die Nachricht wurde inzwischen gelöscht.", `errors.DELETE_REASON_REQUIRED` "Bitte gib einen Grund an."

---

## 4. Labels and i18n keys (`features/messaging/i18n/*.json`; gws/fr/it follow the file conventions)

| Key | de | en |
|---|---|---|
| `messaging.message.menu` | Nachrichtenoptionen | Message options |
| `messaging.message.edit` | Bearbeiten | Edit |
| `messaging.message.delete` | Löschen | Delete |
| `messaging.message.moderate` | Löschen (Moderation) | Delete (moderation) |
| `messaging.message.moderateHint` | Mit Begründung, nur für die Moderation sichtbar | With a reason, visible to the moderation only |
| `messaging.message.editing` | Nachricht bearbeiten | Edit message |
| `messaging.message.editCancel` | Bearbeiten abbrechen | Cancel editing |
| `messaging.message.editDiscard` | Änderungen verwerfen? | Discard changes? |
| `messaging.message.edited` | bearbeitet | edited |
| `messaging.message.editedAt` | Bearbeitet am {date} | Edited on {date} |
| `messaging.message.deleted` | Nachricht gelöscht | Message deleted |
| `messaging.message.deletedByModeration` | Von der Moderation gelöscht | Deleted by the moderation |
| `messaging.message.deleteTitle` | Nachricht löschen? | Delete message? |
| `messaging.message.deleteText` | Die Nachricht wird für alle entfernt. An ihrer Stelle steht «Nachricht gelöscht». | The message is removed for everyone. "Message deleted" takes its place. |
| `messaging.message.moderateTitle` | Nachricht von {name} löschen | Delete message by {name} |
| `messaging.message.moderateText` | Alle sehen stattdessen «Von der Moderation gelöscht». Dein Grund wird im Verlauf gespeichert und ist nur für die Moderation sichtbar. | Everyone sees "Deleted by the moderation" instead. Your reason is stored in the history and visible to the moderation only. |
| `messaging.message.reason` | Grund | Reason |
| `messaging.message.saved` | Nachricht gespeichert | Message saved |
| `messaging.message.deletedToast` | Nachricht gelöscht | Message deleted |
| `history.action.CONVERSATION.MESSAGE_EDITED` | Nachricht bearbeitet | Message edited |
| `history.action.CONVERSATION.MESSAGE_DELETED` | Nachricht gelöscht | Message deleted |
| `errors.MESSAGE_EDIT_WINDOW_EXPIRED` / `MESSAGE_DELETED` / `DELETE_REASON_REQUIRED` / `MESSAGE_NOT_EDITABLE` / `MESSAGE_NOT_FOUND` | see section 3 | — |

Reused: `common.save`, `common.cancel`, `common.close`, `messaging.formerMember`, `shared.requiredMessage.*`.

---

## 5. Accessibility

- The trigger has the name "Nachrichtenoptionen" plus `aria-haspopup="menu"` / `aria-expanded`; it is focusable even
  when visually hidden on desktop (opacity, not `display: none`), and `.msg:focus-within` reveals it.
- Every state has words: "bearbeitet", "Nachricht gelöscht", "Von der Moderation gelöscht"; the dashed border, the
  green outline and the danger colour are the second cue, never the only one.
- The deleted placeholder is still inside `role="log"`; screen readers read "Nachricht gelöscht, 14:32".
- Edit banner: `role="status"` so "Nachricht bearbeiten" is announced when it appears; the textarea label changes
  from "Nachricht" to "Nachricht bearbeiten"; Escape cancels (with the discard confirmation when the text changed).
- The required-message dialog keeps its `aria-invalid` / `aria-describedby` hint; the quote is a `blockquote`.
- Touch targets 44 px on coarse pointers (decision 124); menu items are full-width rows.

## 6. Mobile (412 px)

The "···" is always visible on touch (no hover). It sits outside the bubble, so the bubble's `max-width: 85 %`
drops to `calc(85% - var(--fs-target-sm))` for messages with a menu. The popover opens beside the trigger; both items
are 44 px rows. The edit banner wraps to two lines (title line with X, quote line below); the composer stays at the
bottom and the keyboard pushes the list up as today. Dialogs use the existing `min(…, calc(100vw - 2rem))` width.
No long press: it fights text selection and the browser context menu, and it cannot be discovered.

## 7. Test ids

Kept: every existing `messaging.conversation.*` id (`message`, `body`, `composer`, `send`, `pending`, `retry`, …) and
the `data-seq` / `data-own` attributes. New: `messaging.conversation.messageMenu` (trigger, on the `.msg` of that
message), `messaging.conversation.edit`, `messaging.conversation.delete` (menu items; the moderation item is `delete`
with `data-moderation="true"`), `messaging.conversation.editBanner`, `messaging.conversation.editSave`,
`messaging.conversation.editCancel`, `messaging.conversation.edited` (marker), `messaging.conversation.deleted`
(placeholder, `data-moderation="true|false"`), `messaging.conversation.deleteDialog` (required-message dialog; its
`.message` / `.confirm` / `.cancel` follow). `message` gains `data-deleted` and `data-edited` attributes.

## 8. Rejected alternatives

1. **Long press on phones, hover on desktop, no visible trigger.** Native-feeling, but undiscoverable, conflicts with
   text selection and the context menu, and leaves keyboard and screen-reader users without any way in. Rejected;
   the always-visible "···" on touch costs 36 px of bubble width and nothing else.
2. **Edit inside the bubble** (forum style: textarea replaces the body). Keeps the eye on the message, but a second
   textarea in a chat loses mention autocomplete and Enter/Shift+Enter, scrolls awkwardly mid-list on phones, and
   the plan already says "inline edit in the composer", which every messenger does. Rejected.
3. (also considered) Hiding deleted messages entirely. Leaves unexplained gaps and makes "what did they delete?"
   the conversation; the placeholder keeps replies readable. Rejected.

## 9. Questions for the owner (with recommendations)

1. **"···" always visible on touch, hover-revealed on desktop** (proposed) or always visible everywhere? Recommend
   as proposed: the desktop list stays calm and keyboard users still reach it.
2. **Moderator reason minimum**: contract says 1..500, the shared dialog's house rule is 10 characters. Recommend
   `minLength` 10 in the dialog and `reason 10..500` in the contract so client and server agree.
3. **Deleted latest message in the chat list**: the server stores the preview; for a deleted message recommend it
   stores `""` and the client shows "Nachricht gelöscht" in italics from `lastMessageDeleted` on
   `ConversationSummary` (one new boolean) instead of localising on the server.
4. **Should the sender learn why a moderator deleted their message?** Recommend no notification and no reason shown
   in this task (the placeholder says "Von der Moderation gelöscht"; the reason is history-only as decided). A
   direct message from the moderator stays the human way; a follow-up could post it via `TeamChatPort.sendDirect`.
5. **Placeholder for the sender's own deleted message**: one text for everyone (proposed) or "Du hast diese Nachricht
   gelöscht"? Recommend one text; alignment already says whose it was.
6. **Edited tooltip**: time only ("Bearbeitet am 1. Nov. 2026, 14:32", proposed) or also "zuletzt" when edited more
   than once? Recommend time only; the history has the chain.
