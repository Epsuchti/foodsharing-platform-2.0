# Build ledger

One row per feature ID from plan.md §13/§14 and per deliverable checkbox. A feature is `done` only when its tagged Playwright test passes.

IDs for §14 rows: `EA<n>` = §14.1 row n, `AU<n>` = §14.2 row n, `SV1` = §14.3, `AD1` = §14.4. Deliverables: `D<section>.<n>`.

| ID | Feature | Status | Backend | Frontend | E2E test | Screenshot test | Notes |
|---|---|---|---|---|---|---|---|
| A1 | Sign-up (Google / magic link, §8), registration attempt tracking, email blocklist | todo | | | | | |
| A2 | Login / logout, passkeys, session refresh after role change | todo | | | | | |
| A3 | Email verification & email change with confirmation (request / complete / abort, recorded in history) | todo | | | | | |
| A4 | All profile fields from §15.1, editable by the user; admin-editable fields by ambassadors with history | todo | | | | | |
| A5 | Profile photo upload with crop | todo | | | | | |
| A6 | Public profile: photo, name, regions & groups, stores, pickup count, kg saved, posts, bananas, buddies count, wall, achievements, KAM roles, commitments statistics | todo | | | | | |
| A7 | Internal notes on users ("about me internal" + user notes wall for ambassadors) | todo | | | | | |
| A8 | Profile change history, quiz session history, passport history, verification history (§10) | todo | | | | | |
| A9 | Sleep mode: none / temporary (from–until) / full, with message; shown everywhere the user appears | todo | | | | | |
| A10 | Notification settings: per region forum (new threads / all), per thread (bell/mail), per FSP (bell/mail), newsletter, pickup reminders on/off, mention notifications on/off, activity feed filters; channels bell / email / push / Telegram | todo | | | | | |
| A11 | Calendar token: personal ICS feed of pickups, EinAbs, events, with reminders; regenerate / delete token | todo | | | | | |
| A12 | Language selection (de, en, gws, fr, it) | todo | | | | | |
| A13 | Delete account with reason (anonymise content, archive record as `fs_foodsaver_archive`), no-automatic-delete flag for admins | todo | | | | | |
| A14 | Business card PDF (verified foodsavers, choice of role/region) | todo | | | | | |
| A15 | Trust bananas: give (foodsaver+) with text, list received/sent, delete own, banana count | todo | | | | | |
| A16 | Buddies ("Ich kenne"): request, accept, list, remove | todo | | | | | |
| A17 | Theme switcher (light/dark/system) | todo | | | | | |
| A18 | Email bounce handling: flag invalid addresses, show warning to user and admins, admin can clear | todo | | | | | |
| A19 | Dashboard: onboarding card, my next pickups, pickup options with free slots, activity feed with filters (forum, stores, regions, groups, mailboxes, events), nearby baskets (optional container), bells | todo | | | | | |
| A20 | Info mail message / infomail flag for important announcements | todo | | | | | |
| A21 | Data export (GDPR): download all my data as JSON | todo | | | | | |
| A22 | Security: active sessions, login history, log out everywhere | todo | | | | | |
| R1 | Region tree with all types (continent, country, federal state/canton, region, city, big city, district, part of town) and working groups; closure table for fast ancestry queries | todo | | | | | |
| R2 | Join / leave region, home region; nightly add to parent regions; application-based joining where configured | todo | | | | | |
| R3 | Region page: description, teaser, photo, members, ambassadors, stores, FSPs, events, polls, achievements, pickups, forum, ambassador forum, wall, statistics (members, gender list, age bands, pickups, kg, cooperations) | todo | | | | | |
| R4 | Public region page (logged out) with description, contact, events, children, next EinAbs | todo | | | | | |
| R5 | Region options: report button, mediation button, region pickup rule (active, timespan days, limit, per-day limit, inactive hours before pickup), allow hiding in forum, report reason options + "other", notify on address change | todo | | | | | |
| R6 | Region pins on the map (description, active/inactive) | todo | | | | | |
| R7 | Region admin (orga): create/edit/delete/move regions, set type, email name, ambassadors, conversation, moderated flag | todo | | | | | |
| R8 | Member management: member list with roles/verification/last pickup, remove member, grant/revoke admin, permission overview | todo | | | | | |
| R9 | Working groups: list, create, edit (name, teaser, description, photo), apply types (nobody / requires properties / everybody / open), application requirements (min pickups, banana count, weeks), applications accept/decline with bell, group categories (development, exchange, administrative, project, archived), archive | todo | | | | | |
| R10 | Working-group functions with their region-wide rights (§9) | todo | | | | | |
| R11 | Group mail (send email to all group members) | todo | | | | | |
| R12 | Video conference room per region/group (Jitsi or BigBlueButton link, configurable) | todo | | | | | |
| R13 | Region statistics cache (active home-region foodsavers, active cooperations, pickups last month, kg last month, active FSPs, baskets last month) | todo | | | | | |
| S1 | Store list per region and "my stores" with team status, cooperation status, next pickup, free slots | todo | | | | | |
| S2 | Create store, edit all store fields from §15.2 | todo | | | | | |
| S3 | Store chains: name, headquarters (address, country), status, press allowed, forum thread, notes, common store information, estimated store count; KAMs; chain list | todo | | | | | |
| S4 | Categories admin (store categories, grocery types) with merge | todo | | | | | |
| S5 | Team: members, jumpers (standby), invited, applied; team status (closed / open / searching); per-member stats (pickup count, first/last pickup, added date) | todo | | | | | |
| S6 | Apply to team (with text if required), accept/decline with required message, cancel request | todo | | | | | |
| S7 | Invitations (store managers invite; user accepts/declines; withdraw) | todo | | | | | |
| S8 | Move to/from standby, remove member (required message), appoint/remove store manager | todo | | | | | |
| S9 | Team list: phone buttons (permission-dependent), last pickup, pickup count, distance to store, sleep mode | todo | | | | | |
| S10 | Team chat and jumper (standby) chat, auto-membership | todo | | | | | |
| S11 | Store wall / notes with milestones (cooperation start, status changes) and delete rules per permission table | todo | | | | | |
| S12 | Store log (§10.2) with date range and action filter | todo | | | | | |
| S13 | Delete/archive store | todo | | | | | |
| S14 | Store statistics: pickups, kg saved per pickup weight category, per month | todo | | | | | |
| S15 | Navigation buttons and map position | todo | | | | | |
| S16 | Region of the store shown, store moved between regions by admins | todo | | | | | |
| PU1 | Regular pickups (weekday, time, max slots, description), interval settings | todo | | | | | |
| PU2 | One-time pickups; edit slot count and description of any date | todo | | | | | |
| PU3 | Sign up / sign out; store managers remove others; slot confirmation (unconfirmed → confirmed); prefetch time (how far ahead sign-up opens) | todo | | | | | |
| PU4 | Eligibility check before sign-up (verified, hygiene certificate, phone, region pickup rule, team status, sleep mode) with a clear reason | todo | | | | | |
| PU5 | Region pickup rule enforcement (max pickups in timespan, per day, ignored N hours before pickup) | todo | | | | | |
| PU6 | Pickup history per store (date range) and per user; delete all future pickups of a user (when leaving) | todo | | | | | |
| PU7 | Pickup options for user ("where can I help?", free slots in my stores) | todo | | | | | |
| PU8 | User agenda for a date | todo | | | | | |
| PU9 | Pickup reminders (disable per user) | todo | | | | | |
| N1 | Conversations 1:1 and group, store team & jumper chats, region conversation, lookup/create, rename, read status, unread counts, mentions | todo | | | | | |
| N2 | Realtime delivery (WebSocket) | todo | | | | | |
| N3 | Bells with read/unread, delete, all bell types (forum thread to activate, WG application, new member for welcome team, store requests, slot confirmations, …) | todo | | | | | |
| N4 | Web push notifications, subscriptions per device, opt-in prompt | todo | | | | | |
| N5 | **Telegram**: link account via bot (`/start <token>`), per-user Telegram notifications, region/store Telegram chats for alerts, test message (from reference) | todo | | | | | |
| N6 | Mail notifications with rate limiting, outbox and templates in five languages | todo | | | | | |
| N7 | Newsletter: opt-in, compose per region/global, send via built-in queue (or Listmonk), unsubscribe | todo | | | | | |
| MB1 | Every region and working group can get an address `<email_name>@<domain>`; mailbox members configured by ambassadors / group admins | todo | | | | | |
| MB2 | Inbox, sent, trash folders; read/unread, answered flag; unread count in navigation | todo | | | | | |
| MB3 | Read HTML/plain mails safely (sanitised), attachments download | todo | | | | | |
| MB4 | Compose, reply, forward with attachments, sent from the mailbox address | todo | | | | | |
| MB5 | Incoming mail via IMAP polling or provider webhook; bounce processing; attachment storage | todo | | | | | |
| MB6 | Personal mailbox for ambassadors/admins if configured | todo | | | | | |
| F1 | Forum per region & group, separate ambassador forum | todo | | | | | |
| F2 | Threads: create, edit, delete, sticky, close/open, activation by moderators (moderated regions) | todo | | | | | |
| F3 | Posts: create, edit (with edit marker), delete, reply with quote header, emoji reactions with user list, hide/unhide (moderation, with reason) | todo | | | | | |
| F4 | Follow thread via bell/email; follow whole forum (bell for every new thread) | todo | | | | | |
| F5 | Forum search per region | todo | | | | | |
| E1 | Events (offline / online / other) in regions & groups, with location (name, address, coordinates), start/end, description, public flag, online link | todo | | | | | |
| E2 | Invitations and RSVP (yes / maybe / no), participant list, event wall | todo | | | | | |
| E3 | Event calendar export | todo | | | | | |
| V1 | Polls: single choice, multiple choice, thumbs, score voting; scopes (all FS, verified FS, store managers, ambassadors, verified FS of home region); start/end, shuffle options, eligible votes count, cancel with author, notifications, results | todo | | | | | |
| B1 | Food baskets: create with photo, description, weight, grocery types and kinds, location type (home / custom), coordinates, contact type (message / phone / mobile), lifetime; edit, delete | todo | | | | | |
| B2 | Nearby baskets list & map; request basket with message; owner accepts/declines/marks as taken; status | todo | | | | | |
| FS1 | Food share points: create (in member region), name, photo, description, address, coordinates; activation status; managers; followers (bell/mail); wall | todo | | | | | |
| M1 | Map with layers: stores (filter by cooperation status and team status), FSPs, baskets, regions, region pins, events, users (permission-dependent); location search; marker details | todo | | | | | |
| Q1 | Quizzes: Foodsaver, Store manager, Ambassador, Hygiene, Foodsharer, Saving food, Reports (and French foodsaver quiz variant → all quizzes in all five languages) | todo | | | | | |
| Q2 | Quiz sessions: timed & untimed questions, mandatory questions, failure points, results with explanations and wiki links, confirmation; wait/disqualification rules; hygiene expiry; practice/test mode | todo | | | | | |
| Q3 | Quiz editor for quiz AG: quiz settings (max failure points, question counts), questions (duration, wiki link, mandatory), answers with rating and explanation, question comments | todo | | | | | |
| Ve1 | Legal agreement and privacy policy (versioned, re-consent on change), store manager confidentiality agreement | todo | | | | | |
| Ve2 | Verify / unverify users with required message, verification history, identity confirmation | todo | | | | | |
| Ve3 | Passport: PDF (single and batch per region), pass requests, pass history, last pass date, Apple/Google Wallet, bell/mail on creation optional; layout approved by our admins | todo | | | | | |
| AC1 | Achievements: region and global, name, description, icon, validity in days, visibility (hidden / private / store managers / scope / global), duplicate mode; award/revoke; hygiene certificate auto-awarded; KAM certificate | todo | | | | | |
| G1 | Rule-violation reports: report a user (optionally store-related) with reason from region options, to region report team; list per region and per user; edit, status, wall for discussion; arbitration access | todo | | | | | |
| G2 | Mediation requests (shows mediation team contact) | todo | | | | | |
| G3 | Moderation: thread activation, hidden posts, moderation log | todo | | | | | |
| G4 | User ratings / feedback | todo | | | | | |
| C1 | CMS content pages in five languages (vision, team, partners, press, foodsharing cities, demands, transparency, education, communities, FAQ, privacy, imprint, learning pages) | todo | | | | | |
| C2 | Blog: create, edit, publish/unpublish, delete, per region, teaser, picture | todo | | | | | |
| C3 | Public statistics (kg saved, pickups, foodsavers, stores, cooperations, FSPs, baskets) with animated counter | todo | | | | | |
| C4 | Release notes / "what's new" | todo | | | | | |
| C5 | Global search (users, regions, groups, stores, threads, FSPs) with index | todo | | | | | |
| C6 | Resources / lending: own and region resources, categories, private/openness, images, favourites, commons | todo | | | | | |
| C7 | Donations page, donation campaign, admin configuration | todo | | | | | |
| C8 | Petition page | todo | | | | | |
| C9 | Support ticket form including user agent and current page | todo | | | | | |
| C10 | Uploads: images (resize, 3 sizes), attachments, usage tracking and cleanup | todo | | | | | |
| C11 | Feature toggles (orga) | todo | | | | | |
| C12 | Email blocklist and IP block admin | todo | | | | | |
| C13 | OAuth2/OIDC provider for partner apps: authorization code + PKCE, userinfo, scopes `openid profile email regions`, consent with "remember", region-restricted clients, client admin | todo | | | | | |
| C14 | Configuration admin (key/value settings) | todo | | | | | |
| C15 | Maintenance jobs (§6.3) | todo | | | | | |
| C16 | Team page (public, from working groups) | todo | | | | | |
| EA1 | Region landing page with region picker; landing guard sends logged-in users to their home region | todo | | | | | |
| EA2 | Public page of available EinAb slots per region (public location only), notification subscription widget | todo | | | | | |
| EA3 | "What is my Foodsharing ID" help page with screenshot | todo | | | | | |
| EA4 | Passwordless email login per region, privacy consent (versioned), phone required & validated, phone prompt dialog | todo | | | | | |
| EA5 | Booking rules per region: max active bookings (default 3), no duplicate guide, no duplicate category, Fairteiler visit required by 3rd EinAb, ignored categories | todo | | | | | |
| EA6 | EinAb categories: supermarket, takeout, market, bakery, restaurant, Fairteiler cleaning, Messe, Grossabholung, online | todo | | | | | |
| EA7 | My bookings, cancel booking | todo | | | | | |
| EA8 | Newcomer progress (stages, next step) | todo | | | | | |
| EA9 | Notifications about new bookable EinAbs (message or email channel), unsubscribe, auto-switch-off once verified | todo | | | | | |
| EA10 | Guide dashboard: create/edit/delete EinAbs (category, start, slot count, minimum pickup count, location, business name, private address, public location & general area, online call link, private/public info), Fairteiler visit flag | todo | | | | | |
| EA11 | Private info templates | todo | | | | | |
| EA12 | Assign another guide, assignable guides list; book or cancel on behalf of a newcomer | todo | | | | | |
| EA13 | Slot comments (guide feedback per newcomer), admin comments | todo | | | | | |
| EA14 | Mark EinAb done | todo | | | | | |
| EA15 | ICS per EinAb, personal guide ICS feed with token | todo | | | | | |
| EA16 | iCal import candidates (guide's foodsharing calendar link) | todo | | | | | |
| EA17 | Guide profile, guide regions, enable/disable guides, grant admin | todo | | | | | |
| EA18 | Admin: list EinAbs, delete unbooked with reason, historical EinAb creation, adjust booking count | todo | | | | | |
| EA19 | Past EinAb CSV import with preview/confirm, dedupe by user + day + store, store-name aliases, needs-attention rows | todo | | | | | |
| EA20 | User data refresh queue with progress bar | todo | | | | | |
| EA21 | Newcomer funnel with names, stalled users, gap analysis | todo | | | | | |
| EA22 | Bezirk settings (slug, name, sort order, active, cleaning store, booking rules, user info API) | todo | | | | | |
| AU1 | Pickup sign-up automation per store: auto confirm/decline sign-ups with rules: minimum gap days between a user's pickups, cleaning rule (Fairteiler cleaning in last 12 months; exemptions; minimum free cleaning slots), experience rule, public-holiday rule, linked-store rules (pickup in X requires a pickup in Y/Z within a window before/after, grace minutes, bidirectional, custom message), excluded dates (manual + holidays); dry-run; run now; audit with decision & reasons; message to user on decline | todo | | | | | |
| AU2 | Ownership of automations (owner name/email, editable flag, bot account) | todo | | | | | |
| AU3 | Team request automation: auto-accept store applications, distance rule (max km from home) | todo | | | | | |
| AU4 | Open-slot advertisements: N hours before a pickup with free slots, post rotating messages to store chat and/or Telegram; several adverts per store; late-cancellation message to the user who signed out late; resend advert after late cancellation; planned notifications preview; sent audit; "filled after advert" metric | todo | | | | | |
| AU5 | Store info (sign-out alerts): notify store managers when someone signed out and nobody took over; cutoff hours before pickup; cancellations list | todo | | | | | |
| AU6 | My pickups + extra slot: first person of a pickup may add one extra slot; extra-slot audit | todo | | | | | |
| AU7 | Future pickup users grouped by pickup count (fairness view) | todo | | | | | |
| AU8 | See all automation decisions | todo | | | | | |
| AU9 | Metrics tab: daily anonymous counts archived from audits, filled-after-advert, EinAb and extra slots, scoped to region | todo | | | | | |
| AU10 | Telegram: bot token, list chats, test message, forwarding of messages sent to self | todo | | | | | |
| AU11 | Region message account connections (send messages as a region account) | todo | | | | | |
| AU12 | Foodsharing outage circuit breaker + offline banner | todo | | | | | |
| AU13 | Audit retention cleanup | todo | | | | | |
| SV1 | Surveys (§14.3): create/edit/delete, co-authors, question types TEXT/SINGLE/MULTIPLE/BOOLEAN/STORE, one response per user, results, fill page without menu, login link returns | todo | | | | | |
| AD1 | Admin & operations (§14.4): user management per region, admins list, permission matrix, last login, verified filter, version health, Prometheus, loading bar, i18n check, SMTP defaults, Mailpit | todo | | | | | |

## Deliverables

| ID | Deliverable | Status | Backend | Frontend | E2E test | Screenshot test | Notes |
|---|---|---|---|---|---|---|---|
| D4.1 | Skeleton repo with the layout above; `./mvnw clean package` produces one runnable jar. | done | server/pom.xml, Dockerfile | client build in jar | d4-smoke @D4.1 | |  |
| D4.2 | `local` profile boots with H2 + seed data and no external accounts needed. | done | application-local, seed changesets | | d4-smoke @D4.2 | | LocalProfileSmokeIT |
| D4.3 | Opening `http://localhost:8080/some/deep/link` serves the Angular app. | done | SpaForwardingController | | d4-smoke @D4.3 | |  |
| D5.1 | Split spec skeleton with `common.yaml` and the `auth`, `public`, `onboarding` tags. | done | openapi/ split spec | | d4-smoke @D5.1 | | OpenApiContractTest |
| D5.2 | ArchUnit test: every `@RestController` implements exactly one interface from `network.foodsharing.api`. | done | ArchitectureTest rule 1 | | | | proved by ArchitectureTest (backend) |
| D5.3 | ESLint rule banning `HttpClient` outside `core/`/`api/`. | done | | eslint.config.js no-restricted-imports + check-data-state.mjs | | | proved by npm run lint in mvn verify |
| D5.4 | CI job running spec validation and `oasdiff breaking`. | in-progress | .github/workflows/ci.yml contract job | | | | scripts/check-openapi.mjs + oasdiff.sh; CI not run (no remote) |
| D7.1 | `db.changelog-master.yaml` + `001-initial`. | done | db.changelog-master.yaml + 001..006 | | | | LiquibasePostgresIT |
| D7.2 | Seed CSVs + `context: seed` changesets. | done | db/seed/** | | d4-smoke @D4.2 | |  |
| D7.3 | Dev user switcher (local only) + test that it is absent in `prod`. | todo | | | | | |
| D8.1 | Google login end-to-end incl. account creation and linking. | todo | | | | | |
| D8.2 | Email magic link with Mailpit locally, rate limit, open-redirect guard. | todo | | | | | |
| D8.3 | Passkey registration and login. | todo | | | | | |
| D8.4 | Cookie sessions + CSRF + session list. | todo | | | | | |
| D8.5 | `application-prod.properties` reads `GOOGLE_CLIENT_ID`/`GOOGLE_CLIENT_SECRET` from env. | done | application-prod.properties | | | | config |
| D10.1 | `history` module, table, `HistoryService`, `@Tracked` listener. | in-progress | history module, TrackedFieldListener | | | | backend tests green; e2e in T7 |
| D10.2 | ArchUnit/test rule: every `@Transactional` non-read-only service method records history. | done | ArchitectureTest rule 4 + fixture test | | | | proved by ArchitectureRuleFixtureTest |
| D10.3 | All views from the table above. | todo | | | | | |
| D11.5.1 | New user goes from landing page to "account created" in **≤ 2 clicks** with Google. | todo | | | | | |
| D11.5.2 | At any time the user sees which step they are on and the single next action. | todo | | | | | |
| D11.5.3 | Lighthouse mobile ≥ 90 performance and ≥ 95 accessibility on `/` and `/onboarding`. | todo | | | | | |
| D11.5.4 | Every step works on a 360 px wide screen and in all five languages. | todo | | | | | |
| D11.5.5 | Usability test with 5 real newcomers from Zürich before launch. | todo | | | | | |
| D15.1 | ER diagram generated from Liquibase via SchemaSpy in CI, kept in `docs/`. | todo | | | | | |

## Milestones

| Milestone | Status | Design rounds | Planner review | Notes |
|---|---|---|---|---|
| M1 | todo | 0/3 | | |
| M2 | todo | 0/3 | | |
| M3 | todo | 0/3 | | |
| M4 | todo | 0/3 | | |
| M5 | todo | 0/3 | | |
| M6 | todo | 0/3 | | |
| M7 | todo | 0/3 | | |
