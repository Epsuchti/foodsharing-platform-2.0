# Data inventory (D17.3)

Which tables hold personal data, which columns point at a user, what happens to each one when an account is deleted and where it appears in the data export. Retention periods are in [`retention.md`](retention.md); the deletion rules per module in decisions 70, 71, 74, 75, 77 and 87.

Guarded by tests, so this file cannot drift from the schema:

- `DataInventoryTest`: every foreign key to `user_account`, every UUID column named like a user reference (`*_user_id`, `author_id`, `actor_id`, `owner_id`, `sender_id`, `recipient_id`, `requester_id`, `guide_id`, `*_by`) and every JPA attribute referencing a user has a row here with its deletion outcome; no row names a column that does not exist; every table here with an expiry column (`expires_at`, `until_at`) has a remover in `retention.md`.
- `AccountDeletionIT.deletionHandlesEveryUserReferenceAsDocumented`: deletes every seed account but ORGA and checks every row that pointed at one of them against the **On deletion** column. `AccountDeletionIT.exportContainsEverySection` opens the export zip and finds every file of the **Export** column.

**On deletion** (the words before the first `:` are checked): `deleted` = the row is removed; `anonymised` = the row stays, the reference is cleared or replaced; `kept` = the row stays and points at the anonymised `user_account` row ("Gelöschtes Mitglied", no email, phone, address, avatar). Several words = the module decides per row (the note says how).

The account itself: `user_account` keeps its id (authored content stays attributable to "a deleted member"); email becomes `deleted-<id>@deleted.invalid`, names, phones, address, coordinates, birthday, avatar, about-me texts, Telegram chat id and mailbox link are cleared, role drops to FOODSHARER, `active = false`; a snapshot of the personal fields goes to `user_archive` (kept 2 years). History entries of ACCOUNT_LIFETIME actions about the user lose their values (plan §10.1).

## User references

| Table | Column | Module | Personal data | On deletion | Export |
|---|---|---|---|---|---|
| `*` | `created_by` | all | audit column of every entity (BaseEntity) | kept: audit trail, points at the anonymised row | — |
| `*` | `updated_by` | all | audit column of every entity (BaseEntity) | kept: audit trail, points at the anonymised row | — |
| `user_account` | `deleted_by` | user | who deleted an account | kept: the ORGA member who deleted | `profile.json` |
| `user_account` | `identity_confirmed_by` | user | who confirmed the identity | kept: verification trace | `profile.json` |
| `user_archive` | `user_id` | user | snapshot of the deleted account (name, email, phones, address) | kept: removed by `UserArchiveCleanup` after 2 years | — |
| `user_archive` | `deleted_by` | user | who deleted | kept: removed with the archive | — |
| `user_note` | `user_id` | user | ambassador notes about a member | deleted | — |
| `user_note` | `author_id` | user | notes written by the member | deleted | — |
| `auth_identity` | `user_id` | auth | login methods (email, OIDC subject) | deleted | `sessions.json` |
| `auth_session` | `user_id` | auth | sessions (user agent, IP hash, times) | kept: revoked at deletion, removed by `AuthRecordsCleanup` 30 days later | `sessions.json` |
| `email_change` | `user_id` | auth | old and new email address | deleted | `sessions.json` |
| `passkey_credential` | `user_id` | auth | passkey public keys and names | deleted | `sessions.json` |
| `passkey_user_entity` | `user_id` | auth | WebAuthn user handle | deleted | `sessions.json` |
| `oauth_authorization_request` | `user_id` | oauthserver | pending OAuth logins | deleted | `oauthConnections.json` |
| `oauth_consent` | `user_id` | oauthserver | consents to OAuth clients | deleted | `oauthConnections.json` |
| `oauth_refresh_token` | `user_id` | oauthserver | refresh tokens of OAuth clients | deleted | `oauthConnections.json` |
| `legal_acceptance` | `user_id` | content | consent to privacy policy / terms (version, time, IP hash) | kept: proof of consent | `legalConsents.json` |
| `blog_post` | `author_id` | content | authored blog posts | kept: authored content | `blogPosts.json` |
| `bell` | `user_id` | notification | bells | deleted | `notifications.json` |
| `user_notification_setting` | `user_id` | notification | notification preferences | deleted | `notifications.json` |
| `push_subscription` | `user_id` | notification | push endpoints of the devices | deleted | `notifications.json` |
| `newsletter_recipient` | `user_id` | notification | newsletter deliveries | deleted | `notifications.json` |
| `telegram_link_token` | `user_id` | notification | pending Telegram link | deleted | `notifications.json` |
| `telegram_chat` | `owner_id` | notification | alert chat of a region or store (owner is never a user) | kept: not personal | — |
| `mail_outbox` | `user_id` | notification | queued and sent mails (recipient address, rendered text) | kept: removed by `NotificationCleanup` 90 days after sending | `notifications.json` |
| `region_membership` | `user_id` | region | region and group memberships | deleted | `regions.json` |
| `region_admin` | `user_id` | region | ambassador / group admin roles | deleted | `regions.json` |
| `region_permission` | `user_id` | region | granted region permissions | deleted | `regions.json` |
| `region_permission` | `granted_by` | region | who granted | deleted, kept: rows of a deleted holder go, grants to others stay | `regions.json` |
| `group_application` | `user_id` | region | applications to working groups (text) | deleted | `regions.json` |
| `group_application` | `decided_by` | region | who decided an application | deleted, kept: rows of the deleted applicant go, decisions about others stay | `regions.json` |
| `store_membership` | `user_id` | store | store team memberships (application text) | deleted | `stores.json` |
| `store_membership` | `invited_by` | store | who invited to a team | deleted, kept: rows of the deleted member go, invitations of others stay | `stores.json` |
| `store_chain_kam` | `user_id` | store | key account manager of a chain | deleted | `stores.json` |
| `store_wall_post` | `author_id` | store | store wall posts | kept: authored content | `stores.json` |
| `store_wall_post` | `deleted_by` | store | who removed a wall post | kept: moderation trace | `stores.json` |
| `pickup_signup` | `user_id` | pickup | pickup sign-ups | deleted, kept: future sign-ups are released, past ones stay for statistics | `pickups.json` |
| `pickup_signup` | `confirmed_by` | pickup | store manager who confirmed a slot | deleted, kept: follows the sign-up row; confirmations of others stay | `pickups.json` |
| `pickup_extra_slot` | `user_id` | pickup | extra slot added by a sign-up | kept: statistics, removed by `AuditRetentionJob` after 180 days | `pickups.json` |
| `pickup_policy_audit` | `user_id` | automation | automatic pickup decisions | anonymised: user id cleared (decision 71), archived as counts by `AuditRetentionJob` | `automation.json` |
| `request_policy_audit` | `user_id` | automation | automatic team request decisions | anonymised: user id cleared, archived by `AuditRetentionJob` | `automation.json` |
| `open_slot_advert_audit` | `user_id` | automation | open-slot adverts sent | anonymised: user id cleared, archived by `AuditRetentionJob` | `automation.json` |
| `signout_cancellation` | `user_id` | automation | late sign-outs | kept: needed for the store's results (decision 71), removed by `AuditRetentionJob` after 180 days | `automation.json` |
| `cleaning_rule_exemption` | `user_id` | automation | exemption from a cleaning rule | deleted | `automation.json` |
| `mailbox` | `owner_id` | mailbox | personal mailbox (address) or region mailbox | deleted, kept: the personal mailbox goes with its mails (decision 87), region mailboxes stay | `mailboxes.json` |
| `mailbox_member` | `user_id` | mailbox | access to region mailboxes | deleted | `mailboxes.json` |
| `mail_message` | `sent_by_user_id` | mailbox | mails sent from a mailbox | deleted, kept: mails of the personal mailbox go, mails sent from region mailboxes stay | `mailboxes.json` |
| `conversation` | `last_sender_id` | messaging | sender of the last message (preview) | kept: points at the anonymised row | `messages.json` |
| `conversation_member` | `user_id` | messaging | chat memberships | deleted, kept: direct chats stay for the partner, group/store/region chats go (decision 71) | `messages.json` |
| `message` | `sender_id` | messaging | chat messages | kept: authored content | `messages.json` |
| `forum_thread` | `author_id` | forum | forum threads | kept: authored content | `forum.json` |
| `forum_thread` | `last_post_author_id` | forum | author of the last post (preview) | kept | `forum.json` |
| `forum_post` | `author_id` | forum | forum posts | kept: authored content | `forum.json` |
| `forum_post` | `edited_by` | forum | who edited a post | kept: moderation trace | `forum.json` |
| `forum_post` | `hidden_by` | forum | who hid a post | kept: moderation trace | `forum.json` |
| `forum_post_reaction` | `user_id` | forum | reactions | deleted | `forum.json` |
| `forum_follow` | `user_id` | forum | followed forums | deleted | `forum.json` |
| `forum_thread_follow` | `user_id` | forum | followed threads | deleted | `forum.json` |
| `event` | `author_id` | event | events created | kept: authored content | `events.json` |
| `event_invitation` | `user_id` | event | invitations and RSVPs | deleted | `events.json` |
| `event_wall_post` | `author_id` | event | event wall posts | kept: authored content | `events.json` |
| `calendar_token` | `user_id` | calendar | personal ICS feed token | deleted | `calendar.json` |
| `poll` | `author_id` | poll | polls created | kept: authored content | `polls.json` |
| `poll` | `cancelled_by` | poll | who cancelled a poll | kept: moderation trace | `polls.json` |
| `poll_vote` | `user_id` | poll | votes (choices) | deleted | `polls.json` |
| `survey` | `owner_id` | survey | surveys created | kept: authored content | `surveys.json` |
| `survey_coauthor` | `user_id` | survey | survey co-authors | deleted | `surveys.json` |
| `survey_response` | `user_id` | survey | survey answers | kept: needed for the results (decision 71) | `surveys.json` |
| `quiz_session` | `user_id` | quiz | quiz attempts and answers | deleted | `quiz.json` |
| `quiz_question_comment` | `author_id` | quiz | comments on quiz questions | kept: authored content | `quiz.json` |
| `learning_progress` | `user_id` | onboarding | learning chapters read | deleted | `onboarding.json` |
| `user_onboarding_step_done` | `user_id` | onboarding | onboarding steps done | deleted | `onboarding.json` |
| `user_onboarding_step_done` | `confirmed_by` | onboarding | ambassador who confirmed a step | deleted, kept: follows the step row; confirmations of others stay | `onboarding.json` |
| `onboarding_nudge` | `user_id` | onboarding | reminder mails sent | deleted | `onboarding.json` |
| `passport_issue` | `user_id` | verification | foodsaver passports issued | deleted | `verification.json` |
| `passport_issue` | `issued_by` | verification | ambassador who issued a passport | deleted, kept: follows the passport row; passports of others stay | `verification.json` |
| `passport_request` | `user_id` | verification | passport requests | deleted | `verification.json` |
| `passport_request` | `fulfilled_by` | verification | ambassador who fulfilled a request | deleted, kept: follows the request row; requests of others stay | `verification.json` |
| `wallet_pass` | `user_id` | verification | wallet passes | deleted | `verification.json` |
| `intro_pickup` | `guide_id` | intropickup | EinAbs led by a guide | kept: statistics and newcomer progress | `intropickups.json` |
| `intro_pickup_slot` | `guide_id` | intropickup | slot guide | kept | `intropickups.json` |
| `intro_pickup_slot` | `booking_user_id` | intropickup | newcomer bookings | anonymised, kept: open bookings are released, done ones stay for the counting (decision 71) | `intropickups.json` |
| `intro_pickup_comment` | `author_id` | intropickup | comments on a booking | deleted: comments go with the booking | `intropickups.json` |
| `intro_pickup_comment` | `booking_user_id` | intropickup | comments about a newcomer | deleted | `intropickups.json` |
| `intro_pickup_subscription` | `user_id` | intropickup | EinAb alerts | deleted | `intropickups.json` |
| `guide_profile` | `user_id` | intropickup | guide profile | deleted | `intropickups.json` |
| `private_info_template` | `guide_id` | intropickup | a guide's private info templates | deleted | `intropickups.json` |
| `basket` | `owner_id` | basket | food baskets (text, location, phone) | kept: status DELETED, text and phone cleared, home location rounded to ~1 km (decision 87) | `baskets.json` |
| `basket_request` | `requester_id` | basket | basket requests | deleted | `baskets.json` |
| `food_share_point` | `created_by_user_id` | fsp | food share points created | kept: public place, not personal | `foodSharePoints.json` |
| `fsp_follower` | `user_id` | fsp | followed / managed food share points | deleted | `foodSharePoints.json` |
| `fsp_wall_post` | `author_id` | fsp | food share point wall posts | kept: authored content | `foodSharePoints.json` |
| `report` | `reporter_id` | report | reports filed | kept: case file (decision 74) | `reports.json` |
| `report` | `reported_user_id` | report | reports about the member | kept: case file (decision 74) | `reports.json` |
| `report_wall_post` | `author_id` | report | report discussion | kept: case file | `reports.json` |
| `mediation_request` | `requester_id` | report | mediation requests | kept: case file | `reports.json` |
| `mediation_request` | `counterpart_user_id` | report | mediation counterpart | kept: case file | `reports.json` |
| `support_ticket` | `user_id` | support | support requests (text, email) | anonymised: user id cleared, text stays for the support history | `supportTickets.json` |
| `rating` | `sender_id` | profile | bananas given | deleted | `community.json` |
| `rating` | `recipient_id` | profile | bananas received | deleted | `community.json` |
| `buddy` | `user_id` | profile | buddy list | deleted | `community.json` |
| `buddy` | `buddy_id` | profile | buddy of someone else | deleted | `community.json` |
| `user_wall_post` | `user_id` | profile | posts on the member's wall | deleted | `community.json` |
| `user_wall_post` | `author_id` | profile | posts written on other walls | kept: authored content on someone else's wall | `community.json` |
| `awarded_achievement` | `user_id` | achievement | achievements and certificates | deleted (decision 75) | `achievements.json` |
| `awarded_achievement` | `awarded_by` | achievement | who awarded | deleted, kept: awards of the deleted member go, awards given to others stay | `achievements.json` |
| `awarded_achievement` | `revoked_by` | achievement | who revoked | deleted, kept: as `awarded_by` | `achievements.json` |
| `resource` | `owner_id` | resource | shared resources | kept: soft-deleted (`deleted_at`), favourites removed | `resources.json` |
| `resource_favorite` | `user_id` | resource | favourites | deleted | `resources.json` |
| `upload` | `owner_user_id` | upload | uploaded files | deleted, kept: avatar and private uploads go, pictures of kept content (wall posts, blog) stay | `uploads/` |
| `upload` | `usage_owner_id` | upload | the entity an upload belongs to (user avatar, basket, store …) | deleted, kept: as `owner_user_id` | `uploads/` |
| `history_entry` | `actor_id` | history | who did what (plan §10) | kept: the audit trail stays; ACCOUNT_LIFETIME entries about the user lose their values | `history.json` |
| `job_run` | `actor_id` | common | who started a job manually | kept: removed by `CleanupJob` after 90 days | — |

## Personal data without a user reference

| Table | Column | Module | Personal data | On deletion | Export |
|---|---|---|---|---|---|
| `login_token` | `email` | auth | magic-link requests (email) | — not linked to the account; removed by `LoginTokenCleanup` | — |
| `registration_attempt` | `email_hash` | auth | sign-up attempts (email and IP hashes) for abuse limits | — removed by `AuthRecordsCleanup` after 90 days | — |
| `ip_block` | `cidr` | auth | blocked IP ranges | — removed by `AuthRecordsCleanup` 30 days after the block ended | — |
| `email_blocklist` | `pattern` | auth | blocked addresses / domains | — kept until an ORGA member removes it | — |
| `mail_outbox` | `to_address` | notification | recipient address of queued mails | — removed by `NotificationCleanup` 90 days after sending | — |
| `mail_message` | `from_address` | mailbox | external correspondents of region mailboxes | — kept with the mailbox | — |
| `support_ticket` | `email` | support | contact address of a support request | — kept with the ticket | — |
| `search_index` | `title` | search | names of members in the search index | — rebuilt nightly (the anonymised name replaces the old one) | — |
