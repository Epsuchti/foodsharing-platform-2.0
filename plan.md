# Foodsharing Platform 2.0: Implementation Plan

This document is the single source of truth for rebuilding foodsharing.network on a modern stack.
It covers the architecture, the onboarding experience, the complete feature list (full parity with
foodsharing.network plus everything from `foodsharing-einab-tabelle`), and the route to launch.

Status: draft v3 (2026-10-27).

**Ground rules decided so far**

- **Everything ships at launch. No exceptions.** There is no MVP/P1/P2 split. Phases in §20 are
  internal build milestones only; nothing goes live until every feature in §13 and §14 is done.
- **`foodsharing-einab-tabelle` is absorbed completely.** Every one of its features becomes a native
  part of this platform (§14), so the separate tool can be switched off.
- **Separate platform, multi-region from day one**, starting with **Zürich** as the first live region.
- **Users re-register** (one click with Google). No data migration from foodsharing.network.
- **Languages at launch:** `de`, `en`, `gws` (Swiss German), `fr`, `it`.
- **Every UI element that loads data has loading, empty and error states with error handling** (§12).
- **Every action is recorded in an action history** (§10).
- **Regional email addresses** (`zuerich@<domain>`) are part of launch.
- **Notifications go to in-app push and Telegram** (plus bell and email).
- **Passport layout is checked by our admins**, not a legal team.
- **Quality gate:** Playwright e2e tests for every feature, screenshot tests for every page and state,
  and several design rework rounds per milestone (§18).
- **Domain:** not decided yet; all URLs/mail domains are configuration (`app.domain`).

Every section ends with checkable deliverables so it can be turned into issues directly.

---

## Table of contents

1. [Goals and principles](#1-goals-and-principles)
2. [Glossary](#2-glossary)
3. [Tech stack](#3-tech-stack)
4. [Repository layout and build](#4-repository-layout-and-build)
5. [OpenAPI contract-first workflow](#5-openapi-contract-first-workflow)
6. [Backend architecture](#6-backend-architecture)
7. [Database, migrations and test data](#7-database-migrations-and-test-data)
8. [Authentication and accounts (incl. Google)](#8-authentication-and-accounts-incl-google)
9. [Roles and permissions](#9-roles-and-permissions)
10. [Action history (audit trail)](#10-action-history-audit-trail)
11. [Onboarding experience](#11-onboarding-experience)
12. [Frontend architecture, UI states and error handling](#12-frontend-architecture-ui-states-and-error-handling)
13. [Complete feature list (parity with foodsharing.network)](#13-complete-feature-list-parity-with-foodsharingnetwork)
14. [foodsharing-einab-tabelle features, built in](#14-foodsharing-einab-tabelle-features-built-in)
15. [Data model and fields](#15-data-model-and-fields)
16. [Regions](#16-regions)
17. [Cross-cutting concerns](#17-cross-cutting-concerns)
18. [Testing and quality](#18-testing-and-quality)
19. [Deployment and operations](#19-deployment-and-operations)
20. [Build milestones and launch](#20-build-milestones-and-launch)
21. [Decisions and open questions](#21-decisions-and-open-questions)
22. [Appendix: sources](#22-appendix-sources)

---

## 1. Goals and principles

**Why:** foodsharing.network (PHP/Symfony + legacy PHP templates + Vue, MySQL, ~60 modules) is slow to
change. Its own roadmap freezes new features until the legacy code is refactored. We build a separate
platform on a small, typed, contract-first stack that one team can move fast on.

**Goals**

1. **Complete feature parity** with foodsharing.network, including the small things (action
   histories, store notes, profile change history, region pins, all user and store fields). See §13, §15.
2. **All `foodsharing-einab-tabelle` features built in natively** (EinAbs, newcomer funnel, surveys,
   pickup automations, open-slot alerts, Telegram, metrics, admin tools). The automations run directly on
   our own data instead of polling foodsharing.network. See §14.
3. **Joining is as easy as possible**: one-click Google sign-up, no passwords, a public page that
   explains foodsharing and shows the path to becoming a foodsaver, and a progress view that always
   says "you are here, this is next". See §11.
4. **Robust UI**: every data-loading element handles loading, empty, error and offline states. See §12.
5. **One deployable**: the Angular app is built and bundled into the Spring Boot jar.
6. **Contract-first API**: `openapi.yaml` is written first; Spring controllers implement generated
   interfaces, Angular uses the generated client. No hand-written HTTP calls for app endpoints.
7. **Multi-region**: any number of regions in a tree (§16); Zürich is the first live region.

**Principles**

- Mobile-first. Most foodsavers use the site on their phone at the store door.
- Pages load their data with few API calls; list endpoints are paginated.
- Plain, boring tech; no microservices. One Spring Boot app, one Postgres.
- Same code runs on H2 (local) and Postgres (prod). No database-specific SQL in the app.
- Privacy by design (GDPR / Swiss nDSG): only collect what a step needs, and say why.
- Region admins configure rules (booking limits, pickup rules, automations) in the UI, not via code.
- Every write is traceable: who did what, when, and why (§10).

---

## 2. Glossary

The code uses English names; the UI shows the community's terms in each language.

| Code / English | UI (German) | Meaning |
|---|---|---|
| Region | Bezirk | Organisational unit. Regions form a tree (Europe → country → canton/state → region → city → district). |
| WorkingGroup | Arbeitsgruppe (AG) | Group attached to a region; can carry a function (Welcome, Stores, Reports …). |
| Foodsharer | Foodsharer | Registered user. Can use food baskets and food share points. |
| Foodsaver | Foodsaver (FS) | Passed quiz, legal agreement, introductory pickups and ID check; may pick up at stores. |
| StoreManager | Betriebsverantwortliche*r (BV/BIEB) | Manages one or more store cooperations. |
| Ambassador | Botschafter*in (BOT) | Coordinates a region. |
| Orga | Orga | Global admins. |
| Store | Betrieb | Cooperating business (supermarket, bakery, …). |
| StoreChain | Betriebskette | Chain with key account managers (KAM). |
| Pickup / Slot | Abholung / Slot | A pickup appointment with N slots. |
| Jumper / Standby | Springer*in | Store team member on the standby list. |
| IntroPickup (EinAb) | Einführungsabholung (EA / EinAb) | Supervised pickup a newcomer must do before verification. |
| IntroGuide | EinAb-Geber*in | Experienced foodsaver who runs EinAbs. |
| FoodSharePoint (FSP) | Fairteiler (FT) | Public place where anybody can drop or take food. |
| FoodBasket | Essenskorb | Food offered by a user for pickup by others. |
| Banana | Vertrauensbanane | Trust token one user gives another. |
| Buddy | "Ich kenne" | Mutual acquaintance link. |
| SleepMode | Schlafmütze | User temporarily inactive. |
| Passport | Ausweis | Foodsaver ID card (PDF, Apple/Google Wallet). |
| Bell | Glocke | In-app notification. |
| Region mailbox | Bezirks-Postfach | Shared email address of a region or working group. |

---

## 3. Tech stack

Mirrors `foodsharing-einab-tabelle`, upgraded where the UI library requires it.

| Layer | Choice | Notes |
|---|---|---|
| Language (backend) | Java 21 (Temurin) | Same as reference. |
| Framework | Spring Boot 4.0.x | `webmvc`, `security`, `oauth2-client`, `validation`, `data-jpa`, `liquibase`, `mail`, `actuator`, `websocket`. |
| API contract | OpenAPI 3.0.3, `openapi-generator` 7.15+ | `spring` generator (`interfaceOnly`, `useTags`, `useSpringBoot3`), `typescript-angular` generator. |
| Persistence | Spring Data JPA / Hibernate, Liquibase YAML changelogs | `ddl-auto=validate`. |
| DB local | H2 file DB, `MODE=PostgreSQL` | Seeded with test data (§7.3). H2 console under `/h2-console` in `local` profile only. |
| DB prod | PostgreSQL 17 | |
| Scheduling | `@Scheduled` + ShedLock (JDBC) | Safe if we ever run two instances. |
| Realtime | Spring WebSocket (STOMP) | Chat, bells, live pickup lists. |
| Mail out | Spring Mail; Mailpit locally | Outbox table + retries. ProtonMail SMTP defaults in prod (as reference). |
| Mail in (regional mailboxes) | IMAP polling (Jakarta Mail) or inbound webhook from the mail provider | §13.13. |
| Push | Web Push (VAPID, `nl.martijndwars:web-push`) + Telegram Bot API | Both channels at launch. |
| Frontend | Angular 22, standalone components, signals, zoneless | Optimus UI 2.x requires Angular ≥ 22. |
| UI library | `@openng/optimus-ui` 2.x + `@openng/optimus-ui-themes` (Aura preset) + `@openng/icons` | MIT fork of PrimeNG 21; same API as the reference app's PrimeNG, so reference components port 1:1 (`migrate-from-primeng` schematic). |
| Maps | Leaflet + OpenStreetMap tiles; Photon/Nominatim geocoding | |
| PDF | OpenPDF (passports, business cards) | |
| Wallet passes | Apple PassKit (`.pkpass`), Google Wallet API | |
| Build | Maven wrapper + `frontend-maven-plugin` (Node 24 LTS) | One `./mvnw clean package` builds everything. |
| Observability | Actuator health/info/prometheus, Micrometer | Version health group as in reference. |
| Container | Docker (`eclipse-temurin:21-jre`), multi-arch buildx, GHCR | Same `deploy/release.sh` flow as reference. |
| CI | GitHub Actions | Build, tests, contract checks, image push on tag. |

---

## 4. Repository layout and build

```
foodsharing-platform-2.0/
├── plan.md                         ← this file
├── AGENTS.md                       ← coding rules for humans and AI agents (§18.7)
├── README.md
├── .github/workflows/ci.yml
├── server/
│   ├── pom.xml                     ← drives the whole build, incl. the client
│   ├── mvnw, .mvn/
│   ├── Dockerfile
│   ├── deploy/{release.sh,version.txt}
│   ├── start_smtp_server.sh        ← Mailpit on :2525 (SMTP) / :8025 (UI), also IMAP test mailbox
│   ├── local-secrets.example.properties
│   └── src/
│       ├── main/java/network/foodsharing/
│       │   ├── FoodsharingApplication.java
│       │   ├── config/             ← SecurityConfig, SpaForwardingController, StaticResourceConfig, …
│       │   ├── security/           ← session filter, OIDC handlers, PermissionService
│       │   ├── common/             ← ApiException, ApiErrorCode, ApiExceptionHandler, paging, clock, history
│       │   └── <module>/           ← one package per feature module (§6.1)
│       │       ├── web/            ← XyzController implements generated XyzApi
│       │       ├── service/
│       │       ├── domain/         ← entities, enums
│       │       ├── repository/
│       │       └── mapper/         ← entity ↔ generated model
│       └── main/resources/
│           ├── openapi/            ← openapi.yaml + paths/*.yaml + schemas/*.yaml
│           ├── db/changelog/       ← Liquibase master + numbered changelogs
│           ├── db/seed/            ← local test data (context "seed")
│           ├── templates/mail/     ← mail templates per language
│           ├── messages{,_de,_en,_gws,_fr,_it}.properties
│           └── application{,-local,-prod}.properties
└── client/
    ├── package.json, angular.json, openapi-generator.json
    ├── public/i18n/{de,en,gws,fr,it}.json
    ├── scripts/check-i18n.mjs
    └── src/
        ├── proxy.conf.json         ← /api, /oauth2, /login/oauth2, /ws → :8080
        └── app/
            ├── api/                ← GENERATED, gitignored
            ├── core/               ← auth, session, guards, interceptors, i18n, loading, errors, date pipes
            ├── shared/             ← reusable UI (data-state, journey stepper, user chip, map, pickup table, …)
            ├── layout/             ← shell, header, nav, footer
            └── features/<module>/  ← lazy-loaded routes per module
```

### 4.1 Build pipeline (same as reference)

1. `generate-sources`: `openapi-generator-maven-plugin` generates Java interfaces + models into
   `target/generated-sources/openapi` (package `network.foodsharing.api` / `.api.model`).
2. `frontend-maven-plugin`: installs Node/npm into `client/node`, runs `npm install` and `npm run build`.
   `npm run build` runs `openapi:generate` first (typescript-angular → `client/src/app/api`), then `ng build`.
3. `prepare-package`: `maven-resources-plugin` copies `client/dist/client/browser/**` into
   `target/classes/static`.
4. `spring-boot-maven-plugin` builds the jar (+ `build-info` for the version endpoint).
5. `SpaForwardingController` forwards every non-API, non-file path to `index.html` so Angular deep links work.

Maven profile `-Pskip-client` skips steps 2-3 for fast backend-only builds.

### 4.2 Local development

| Command | What it does |
|---|---|
| `cd server && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local` | Backend on :8080 with H2 + seed data |
| `cd client && npm start` | Regenerates the API client, `ng serve` on :4200 with proxy |
| `server/start_smtp_server.sh` | Mailpit for login links, notifications and regional mailbox tests |
| `./mvnw clean package && java -jar server/target/*.jar` | Production-like single jar |

Deliverables:
- [x] Skeleton repo with the layout above; `./mvnw clean package` produces one runnable jar.
- [x] `local` profile boots with H2 + seed data and no external accounts needed.
- [x] Opening `http://localhost:8080/some/deep/link` serves the Angular app.

---

## 5. OpenAPI contract-first workflow

### 5.1 Rules

1. **The contract changes first.** Every new endpoint or field starts as a change to
   `server/src/main/resources/openapi/`. Code follows.
2. **Every `@RestController` implements a generated `*Api` interface** (one interface per OpenAPI tag).
   Controllers have no mapping annotations of their own; the interface carries them. Enforced by ArchUnit.
3. **The Angular app only talks to the backend through generated services** from `client/src/app/api`.
   No raw `HttpClient` calls for application endpoints (carry-over rule from the reference `AGENTS.md`).
   Enforced by an ESLint `no-restricted-imports` rule outside `core/` and `api/`.
4. Generated code is never committed (gitignored) and never edited.
5. Controllers map between generated models and domain objects via mappers; generated models never
   reach the service layer or entities.
6. Every operation documents its error responses (`4xx`/`5xx` → `ApiError`) so the frontend can handle them (§12.3).

### 5.2 Generator configuration

Backend (`pom.xml`, as reference):

```xml
<generatorName>spring</generatorName>
<inputSpec>${project.basedir}/src/main/resources/openapi/openapi.yaml</inputSpec>
<apiPackage>network.foodsharing.api</apiPackage>
<modelPackage>network.foodsharing.api.model</modelPackage>
<configOptions>
  <interfaceOnly>true</interfaceOnly>
  <useTags>true</useTags>
  <useSpringBoot3>true</useSpringBoot3>
  <skipDefaultInterface>true</skipDefaultInterface>
  <openApiNullable>false</openApiNullable>
  <dateLibrary>java8</dateLibrary>
  <useBeanValidation>true</useBeanValidation>
  <performBeanValidation>true</performBeanValidation>
</configOptions>
```

Frontend (`openapi-generator.json`, as reference): `providedInRoot`, `stringEnums`,
`useSingleRequestParameter`, `withInterfaces`, `ngVersion: 22`.

### 5.3 Spec structure

The reference spec is one 4,800-line file. With far more scope we split it; both generators resolve
relative `$ref`s.

```
openapi/
├── openapi.yaml          ← info, servers, security schemes, tags, paths: { $ref: paths/... }
├── paths/                ← one file per tag/module (auth, onboarding, users, history, regions, groups,
│                           stores, pickups, intro-pickups, automations, messages, bells, notifications,
│                           telegram, mailboxes, forum, events, map, baskets, food-share-points, quizzes,
│                           verification, passports, reports, polls, surveys, achievements, content, blog,
│                           statistics, search, resources, donations, support, admin, public, oauth-clients)
└── schemas/
    ├── common.yaml       ← ApiError, ApiErrorCode, FieldError, Page, PageMeta, GeoPoint, Address, …
    └── <one file per module>
```

### 5.4 Conventions

- Base path `/api`. Public (unauthenticated) endpoints under `/api/public/**`.
- Resource-oriented paths, plural nouns: `/api/stores/{storeId}/pickups/{pickupId}/slots`.
- `operationId` = Java/TS method name, e.g. `listStorePickups`, `signUpForSlot`.
- Errors: one `ApiError { code: ApiErrorCode, message, traceId, fieldErrors?: FieldError[], retryable }`.
  `ApiErrorCode` is a string enum shared by backend and frontend; the frontend translates codes via
  i18n (`errors.<CODE>`), as in the reference (`ApiErrorCode`, `api-error.ts`).
- Pagination: `page`, `size` query params; response `{ items: [...], page: PageMeta }`.
- Timestamps: `date-time` in UTC; regions carry a `timeZone` (e.g. `Europe/Zurich`) used for display and
  for "same day" rules.
- IDs: UUID for all entities (as reference `BaseEntity`).
- Mutating endpoints accept an optional `reason` where the action history needs one (§10).
- Each endpoint declares its permission in `x-permission` (documentation; enforced in services).
- CI: `openapi-generator validate` + breaking-change diff (`oasdiff`) against `main` on every PR.

Deliverables:
- [x] Split spec skeleton with `common.yaml` and the `auth`, `public`, `onboarding` tags.
- [x] ArchUnit test: every `@RestController` implements exactly one interface from `network.foodsharing.api`.
- [x] ESLint rule banning `HttpClient` outside `core/`/`api/`.
- [ ] CI job running spec validation and `oasdiff breaking`. — *Gate note: built (`scripts/check-openapi.mjs`, `oasdiff.sh`, CI workflow) and green locally; never run on a Git remote (no remote configured), see launch report "Needs a human".*

---

## 6. Backend architecture

### 6.1 Modules

Package-by-feature. Each module owns its entities, repositories, services and controllers; other
modules use its service API only (checked with Spring Modulith `ApplicationModules.verify()`).

| Module | Responsibility |
|---|---|
| `auth` | Login (Google, email link, passkeys), sessions, account linking, logout, IP/login blocking |
| `user` | Profile, all user fields, settings, user options, sleep mode, consent, account deletion/archive, buddies, bananas, ratings |
| `history` | Action history for every entity (§10) |
| `onboarding` | Journey state machine, progress, next step, nudges (§11) |
| `region` | Region tree, membership, options, pins, statistics, working groups, functions, applications |
| `quiz` | Quizzes, questions, answers, sessions, rules (wait times, expiry, easy/test mode) |
| `verification` | Legal agreements, identity check, verification, passports, wallets, business cards |
| `intropickup` | EinAbs, slots, bookings, guides, templates, iCal, funnel, CSV import (from reference) |
| `store` | Stores, chains, KAMs, categories, groceries, team, invitations, applications, store log, notes/wall |
| `pickup` | Regular & one-time pickups, slots, sign-up/out, confirmation, history, region pickup rule, weights |
| `automation` | Pickup policies, application auto-accept, open-slot alerts, sign-out alerts, extra slots, metrics (from reference) |
| `messaging` | Conversations, messages, read state, WebSocket push |
| `notification` | Bells, mail outbox, web push, Telegram, per-user preferences |
| `mailbox` | Regional and working-group email addresses, inbox/sent/trash, attachments |
| `forum` | Threads, posts, reactions, subscriptions, moderation |
| `event` | Events, locations, invitations, online rooms |
| `map` | Marker endpoints, geocoding |
| `basket` | Food baskets and requests |
| `fsp` | Food share points, managers, followers, wall |
| `report` | Rule-violation reports, report reasons, mediation requests |
| `poll` | Region polls (votings) |
| `survey` | Surveys (from reference) |
| `achievement` | Badges / certificates |
| `content` | CMS pages, blog, FAQ, release notes, learning pages |
| `statistics` | Public, region and store statistics, daily aggregation |
| `resource` | Shared items / lending |
| `search` | Global search |
| `donation` | Donation pages and admin config |
| `support` | Support ticket form |
| `admin` | Orga tools: feature toggles, email blocklist, IP blocks, OAuth clients, configuration |
| `oauthserver` | OAuth2/OIDC provider for partner apps (wiki, chat) |
| `upload` | File/image storage abstraction |

### 6.2 Layering rules (from reference)

- `web` (controller, implements generated interface) → `service` (transactions, permissions, business
  rules, history entries) → `repository` (Spring Data). Controllers never touch repositories.
- `ApiException(HttpStatus, ApiErrorCode)` thrown from services; `ApiExceptionHandler` maps every
  exception (including validation, optimistic locking, access denied and unexpected errors) to `ApiError`
  with a `traceId` that also appears in the logs.
- `CurrentActorService` resolves the signed-in user; services check permissions via `PermissionService`.
- `spring.jpa.open-in-view=false`; services return mapped views or fetch what they need.
- Domain events (`EinAbCreatedEvent`, `SlotSignedOutEvent`, …) + `@TransactionalEventListener(AFTER_COMMIT)`
  for notifications, so mails/pushes never fire for rolled-back work.
- External calls (Telegram, SMTP, IMAP, geocoding, wallet APIs) go through clients with timeouts,
  retries with backoff and a circuit breaker (pattern from reference `FoodsharingAvailability`);
  failures never break the user's action.
- Configuration via typed `@ConfigurationProperties` (`AppProperties`), env-var overridable.

### 6.3 Background jobs

| Job | Interval | Purpose |
|---|---|---|
| Mail outbox worker | 10 s | Send queued mails, retry with backoff, rate limiting |
| Mailbox fetcher | 1 min | Fetch incoming regional mail (IMAP) into mailboxes; bounce processing |
| Open-slot alerts | 5 min | Post alerts for pickups with free slots N hours ahead (§14) |
| Sign-out check | real time + daily summary | Tell store managers about slots left empty after sign-outs (§14) |
| Pickup reminders | hourly | Remind users of their next pickup (unless disabled in user options) |
| Onboarding nudges | daily | Remind stalled newcomers (30 days without progress) |
| Region maintenance | nightly | Add members to parent regions, wake expired sleep mode, inactive account handling |
| Store maintenance | nightly | Remind about unconfirmed slots, empty pickups, inactive store managers |
| Statistics aggregation | nightly | Region/store/user statistics (pickups, kg saved, posts, bananas …) |
| Audit retention | nightly | Archive automation audits into anonymous daily counts, then delete (reference pattern) |
| Quiz/achievement expiry | daily | Hygiene certificate expiry warnings (90 days before, valid 365), achievement validity |
| Cleanup | nightly | Dead push subscriptions, expired tokens, old bells, orphaned uploads |

---

## 7. Database, migrations and test data

### 7.1 Profiles

| Profile | DB | Mail | Extras |
|---|---|---|---|
| `local` | H2 file `./data/foodsharing` (`MODE=PostgreSQL;DB_CLOSE_DELAY=-1`) | Mailpit `localhost:2525` | Seed data, H2 console, dev login switcher, `local-secrets.properties` import, Telegram/push in log-only mode |
| `test` | H2 in-memory for unit/slice tests, Testcontainers Postgres for integration tests | in-memory | |
| `prod` | Postgres from `SPRING_DATASOURCE_*` env | SMTP/IMAP from env | Secure cookies, HSTS |

### 7.2 Migration rules (from reference `AGENTS.md`)

- Liquibase YAML, one file per change: `db.changelog-NNN-short-name.yaml`, included by the master file.
- Changes must run on both H2 and Postgres. No `CLOB`/`@Lob`/`TEXT`; long text is `VARCHAR(32600)`
  in both entity and migration.
- Never edit a released changelog; add a new one.
- Enum columns stored as `VARCHAR` (`@Enumerated(STRING)`).
- Geo: `latitude`/`longitude` as `DOUBLE`; distance via bounding box in SQL + haversine in Java (no PostGIS,
  keeps H2 working).
- Every table: `id UUID PK`, `created_at`, `updated_at`, `created_by`, `updated_by`, optimistic `version`.

### 7.3 Seed / test data

Liquibase changesets with `context: seed` loaded from CSV under `db/seed/`, enabled only in `local`
(`spring.liquibase.contexts=seed`). Idempotent with fixed UUIDs, so links in docs stay valid.

| Seed | Content |
|---|---|
| Regions | Full tree from §16: Europe → Switzerland (all 26 cantons) → Zürich with its sub-regions; Germany and Austria with a few regions each; global and Swiss working groups with all functions |
| Users | One per role and journey stage: `newbie@local`, `profile@local`, `quiz@local`, `einab1@local` (1 of 3 EinAbs), `pending@local` (waiting for ID check), `foodsaver@local`, `jumper@local`, `sleepy@local`, `bieb@local` (store manager), `guide@local` (EinAb guide), `bot@local` (ambassador Zürich), `welcome@local`, `reports@local`, `orga@local`; all fields filled with realistic data in all five languages' preferences |
| Stores | 25 stores in Zürich across all categories, every team status and cooperation status, regular & one-time pickups, two chains with KAMs, notes, store logs |
| Pickups | Past 90 days (history, statistics, weights) and next 30 days with full/free/unconfirmed slots |
| EinAbs | Upcoming EinAbs in every category incl. online and Fairteiler cleaning; past done EinAbs with comments |
| Automations | Pickup policies with every rule type, open-slot alerts, audits with every decision type |
| Community | Forum threads with posts, reactions and hidden posts, events, polls, surveys, baskets, FSPs, conversations, bells, mailbox messages, reports, bananas, achievements |
| Quizzes | Foodsaver, store manager, ambassador, hygiene, foodsharer, saving food, reports quizzes with ~20 questions each in all languages |
| Content | Explainer page texts, learning pages, FAQ, privacy policy, legal agreements, blog posts |
| History | Action history entries for all of the above |

In `local`, the login page shows a **dev user switcher** ("Log in as …") listing the seed users,
so every role and journey stage is one click away. Never available outside `local` (bean only exists in
that profile; covered by a test).

Deliverables:
- [x] `db.changelog-master.yaml` + `001-initial`.
- [x] Seed CSVs + `context: seed` changesets.
- [x] Dev user switcher (local only) + test that it is absent in `prod`.

---

## 8. Authentication and accounts (incl. Google)

Goal: **no passwords**, and signing up takes one click.

### 8.1 Login methods (all at launch)

| Method | Flow |
|---|---|
| **Google** (OIDC) | `spring-boot-starter-oauth2-client`, scopes `openid email profile`. First login creates the account with name, verified email, avatar and language prefilled. |
| **Email magic link** | Enter email → link valid 30 min (reference: `LoginToken`, `login-token-validity-minutes=30`). Works for sign-up and login. Rate-limited per email and IP (reference `LoginRateLimiter`). |
| **Passkeys** (WebAuthn) | Offered after first login ("log in faster next time"). Spring Security WebAuthn support. Parity with foodsharing's passkey feature. |

Notes:
- Accounts are keyed by verified email. If a Google login's email matches an existing account, the
  identity is linked to it (Google emails are verified). Users can link/unlink methods in settings, but
  never remove the last one.
- foodsharing.network's password, password reset and TOTP 2FA are covered by magic link + passkeys +
  Google: no passwords to leak or reset. Passkeys are a stronger second factor than TOTP.
- Return URL handling only allows in-app paths (open-redirect protection, as reference `AuthService`).
- Disposable/blocked email domains rejected (parity: email blocklist); IP blocks for abuse (parity: `fs_ipblock`).
- Every login, logout, failed login and method change goes to the action history (§10).

### 8.2 Sessions

- Server-side sessions in table `auth_session` (hashed token, user, created, last seen, user agent, IP hash),
  as in the reference, delivered as an **HttpOnly, Secure, SameSite=Lax cookie** (frontend and API share one
  origin). CSRF protection via Spring Security's cookie-to-header token.
- Sliding expiry (configurable `app.auth.session-validity-days`, reference 365).
- "Active sessions" list in settings with "log out everywhere".
- After a role change the session info refreshes automatically (parity: "Relogin").
- WebSocket handshake authenticated by the same cookie.

### 8.3 Sign-up data, collected just in time

All user fields from §15.1 exist, but each is asked for only when a step needs it (see §11):

| Step | Fields |
|---|---|
| Create account | email (or Google), display name, language |
| Profile & region | first/last name, home region (postcode → suggested region), mobile phone (validated, as reference `phone-number.ts`), optional landline |
| Verification | date of birth, gender, full address (street, postcode, city, coordinates) |
| Optional anytime | photo, about me (public), about me (internal for the team), homepage, position/role description, newsletter, contact visibility |

Deliverables:
- [x] Google login end-to-end incl. account creation and linking.
- [x] Email magic link with Mailpit locally, rate limit, open-redirect guard.
- [x] Passkey registration and login.
- [x] Cookie sessions + CSRF + session list.
- [x] `application-prod.properties` reads `GOOGLE_CLIENT_ID`/`GOOGLE_CLIENT_SECRET` from env.

---

## 9. Roles and permissions

Three layers, all resolved by one `PermissionService` (tested with a full permission matrix, like the
store permission tables in the foodsharing docs: wall, team list, pickup list × org / ambassador /
store coordination admin / store manager / member / jumper / unverified).

1. **Global journey role** (from foodsharing): `FOODSHARER < FOODSAVER < STORE_MANAGER < AMBASSADOR`,
   plus `ORGA`. Gained by completing journey steps (quizzes, verification) and confirmed by an ambassador
   where foodsharing requires it. Quiz role and confirmed role are tracked separately (parity: `quiz_rolle`).
2. **Scoped roles**: region member / ambassador of region X; working-group member / admin; store team
   member / jumper / invited / applied / store manager of store Y; KAM of chain Z; FSP manager; event
   organiser; mailbox member. Working-group functions (Welcome, Voting, FSP, Store coordination, Report,
   Mediation, Arbitration, FS management, PR, Moderation, Board, Election, Resources) grant region-wide
   rights exactly as on foodsharing.network (e.g. a store coordination group supersedes ambassadors).
3. **Region-scoped feature permissions** (from einab-tabelle): `CAN_GIVE_INTRO_PICKUPS`,
   `CAN_MANAGE_USERS`, `CAN_USE_AUTOMATIONS`, `CAN_SEE_USER_PICKUP_COUNT_GROUPING`,
   `CAN_SEE_ALL_AUTOMATION_DECISIONS`, `CAN_MANAGE_SURVEYS`. Granted by ambassadors per region; can also
   be granted through a working-group function.

Frontend guards read a `SessionInfo` (generated model) with the role, scoped roles and permissions;
the backend always re-checks. Reference pattern: `authGuard` + `data: { permissions: [...] }` on routes.
Forbidden routes show a clear "no access, ask X" page (§12.3), never a blank page.

---

## 10. Action history (audit trail)

"Who did what, when, and why" for everything, shown in the UI where people need it.

### 10.1 Mechanism

- One `history_entry` table: `id`, `occurred_at`, `actor_id` (nullable for system/automation),
  `actor_type` (`USER|SYSTEM|AUTOMATION`), `subject_type` (`USER|STORE|PICKUP|REGION|GROUP|INTRO_PICKUP|…`),
  `subject_id`, `secondary_subject_id` (e.g. the affected user), `action` (enum per subject type),
  `field`, `old_value`, `new_value`, `reason`, `metadata` (`VARCHAR(32600)` JSON), `region_id`.
- `HistoryService.record(...)` is called from services in the same transaction as the change.
  An ArchUnit rule + tests make sure every mutating service method records history.
- Field-level changes on profile, store and region recorded automatically via a Hibernate listener
  for fields marked `@Tracked`.
- Retention per action type (e.g. pickup sign-ups kept 2 years, personal data changes as long as the
  account exists, anonymised on deletion).

### 10.2 History views (parity + more)

| View | Who sees it | Content | foodsharing source |
|---|---|---|---|
| **Store log** | Store managers, store coordination, ambassadors | All 27 store log actions: request to join / declined / approved / cancelled, added without request, moved to jumper / team, removed, left, appointed / removed store manager, slot sign-up / sign-out / removed / confirmed, wall post deleted, invited / invitation withdrawn / accepted / declined, store deleted, slot count / description changed, region pickup rule on/off, regular pickup settings / interval changed; filter by date range and action types | `StoreLogAction`, `fs_store_log` |
| **Profile change history** | Ambassadors, FS management, the user (own) | Email change requests (requested/completed/aborted), region joins/leaves, identity confirmed, name/address/phone changes by admins, role changes | `fs_foodsaver_change_history`, `ChangeHistoryKey` |
| **Verification history** | Ambassadors, FS management | Verified/unverified with who and why | `fs_verify_history` |
| **Passport history** | Ambassadors, the user | Passport generated/requested, by whom | `fs_pass_gen`, `fs_pass_request` |
| **Quiz session history** | Ambassadors, quiz AG, the user | Every attempt, score, status | `QuizSessionHistoryModal` |
| **Pickup history** | Store team, user (own) | Who picked up when, per store and per user | pickups history endpoints |
| **EinAb history** | Guides, ambassadors | Bookings, cancellations, attendance, comments | einab-tabelle |
| **Automation audit** | `CAN_SEE_ALL_AUTOMATION_DECISIONS`, store managers | Every automated decision with reasons and dry-run flag | einab-tabelle audits |
| **Region admin log** | Ambassadors, orga | Member removals, admin grants, option changes, working-group changes | `fs_region_function`, region endpoints |
| **Moderation log** | Moderation AG | Thread activations, hidden/unhidden posts, deletions with reason | forum |
| **Report log** | Report / arbitration team | Report status changes and notes | reports |
| **Login & security log** | The user (own), orga | Logins, new device, method changes, session revocations | new |
| **My activity** | The user | Their own actions across the platform; export as JSON (GDPR) | new |

Every view uses the shared `HistoryTimeline` component with filters (date range, action type, actor),
pagination and CSV export for admins.

Deliverables:
- [x] `history` module, table, `HistoryService`, `@Tracked` listener.
- [x] ArchUnit/test rule: every `@Transactional` non-read-only service method records history.
- [x] All views from the table above.

---

## 11. Onboarding experience

This is where we must be clearly better than foodsharing.network. Today a newcomer registers, has to
discover the quiz, find out who gives introductory pickups (often via the einab-tabelle tool or chats),
wait for an ambassador, and get a passport, with no single place showing progress.

### 11.1 The journey

One state machine (`onboarding` module) drives the public explainer, the progress bar, the dashboard
card, nudges and the ambassador's funnel.

```
 Visitor ──▶ 1 Account ──▶ 2 Profile & region ──▶ 3 Learn ──▶ 4 Quiz ──▶ 5 Agreement
                                                                              │
   ┌──────────────────────────────────────────────────────────────────────────┘
   ▼
 6 Intro pickups (n of N, + Fairteiler if required) ──▶ 7 ID check ──▶ 8 Passport ─▶ Foodsaver
                                                                                      │
                                    further tracks: Hygiene quiz · Store manager · EinAb guide · Ambassador
```

| # | Step | Done when | What the user sees / does | Who else acts |
|---|---|---|---|---|
| 1 | Create account | Account exists | "Continue with Google" or email link. User is now a **Foodsharer** and can already use baskets & FSPs. | – |
| 2 | Profile & home region | Name, phone, home region set | Postcode → region suggestion on a map; shows the region's ambassadors, welcome team and next EinAb dates. | Welcome AG gets a bell |
| 3 | Learn the rules | Learning pages read (tracked per chapter) | Short illustrated chapters (rules, hygiene, how a pickup works, etiquette), ~2 min each, from CMS, in all five languages. | – |
| 4 | Foodsaver quiz | Quiz passed | Timed/untimed questions; on failure, explanations + rules: tries 1-2 immediate retry, after 3rd fail wait 30 days, 4th retry allowed, 5th fail disqualifies (parity with `QuizTransactions`). Practice mode (parity: easy mode / test sessions). | – |
| 5 | Legal agreement | Agreement accepted (versioned) | Plain-language summary + full text, one checkbox; privacy policy consent (versioned, as reference privacy consent). | – |
| 6 | Intro pickups (EinAbs) | N counted EinAbs done (N = region setting, default 3), Fairteiler visit if region requires it | Bookable EinAbs filtered to what this user may book (region rules from §14), one-tap booking, calendar file, reminders. Progress "2 of 3". | EinAb guide confirms attendance |
| 7 | ID check | Ambassador / FS-management verified identity | "Bring your ID to your next EinAb / meet an ambassador"; status visible with the name of who will check. | Ambassador / FSManagement AG |
| 8 | Passport | Passport issued | Download PDF, add to Apple/Google Wallet. User becomes **Foodsaver**; next suggestion: stores with open teams nearby. | Generated on verification; layout approved by our admins |

Further tracks after completion, each with its own mini-journey and progress: hygiene quiz (expires after
365 days, warning 90 days before), store manager quiz + confidentiality agreement, EinAb guide
(permission request → ambassador approval → first EinAb), ambassador quiz.

Each step carries: `status` (`LOCKED | AVAILABLE | IN_PROGRESS | WAITING_FOR_OTHERS | DONE`),
`progress` (e.g. 2/3), `nextAction` (CTA + deep link), `estimatedDuration`, `blockedReason`, and who to
contact. Reference: `NewcomerProgressService` stages `REGISTERED → FIRST_BOOKED → IN_PROGRESS →
FAIRTEILER_MISSING → COMPLETE → VERIFIED` and `NextStep` enum, generalised to the full journey.

Region admins configure: required EinAb count, Fairteiler requirement, ignored categories, whether
learning pages are mandatory, extra region-specific steps (e.g. "attend newcomer meeting").

### 11.2 Public explainer page (`/`, logged out)

Clear, warm, fast (static content + one public stats call), in all five languages. Sections:

1. **Hero**: one sentence ("Rette Lebensmittel in deiner Nähe"), primary CTA **"Mit Google mitmachen"**,
   secondary "Mit E-Mail mitmachen".
2. **What is foodsharing?** Three cards: *Foodsharer* (share & take food baskets, Fairteiler),
   *Foodsaver* (pick up at stores), *Businesses* (cooperate with us).
3. **Your path to foodsaver**: the journey component from §11.3 in "preview" mode, with per-step
   explanations, typical duration ("~2-6 weeks total") and what is required (ID, phone).
4. **Find your region**: postcode / locate-me → region card with ambassadors, active stores,
   **next free EinAb dates** (public endpoint, as the reference's public slots page), and the map.
5. **Impact**: live counters (kg saved, pickups, foodsavers, stores), per region if chosen.
6. **FAQ** (CMS), **for businesses** (contact form), footer: vision, about, team, partners, press, blog,
   foodsharing cities, demands, donations, transparency, privacy, imprint, contact.

SEO: prerender public routes (Angular SSG), `hreflang` for the five languages.

### 11.3 Progress visuals (logged in)

- **Journey bar** in the header on every page while onboarding is not complete: compact horizontal
  stepper (`p-stepper` on Optimus tokens) with all 8 steps and the current one highlighted; tapping opens
  the journey page. On mobile, a slim progress bar with "Step 4 of 8: Quiz".
- **Journey page** `/onboarding`: vertical `p-timeline` with each step's status, what to do now (big CTA),
  what happens next, who is waiting on whom ("Waiting for ambassador Anna, usually 3 days"), and a
  `p-metergroup` for EinAb progress (done / booked / still needed).
- **Dashboard card** "Your next step" with a single CTA.
- Clear success states when a step completes; role badge changes.
- **Nudges** (bell + push + Telegram if linked + email, per user preference): stalled 30 days (reference
  `STALLED_AFTER`), a bookable EinAb was published (reference `NotificationService`), a waiting step resolved.

### 11.4 Ambassador / welcome-team side

- **Newcomer funnel** per region (from einab-tabelle): counts per step, median days in step, stalled users,
  gap analysis (EinAb places needed vs. open per category, severity `MISSING/LOW/OK`), filter by
  registration window; names visible to `CAN_MANAGE_USERS`, counts only for guides.
- **Verification queue**: users waiting for ID check, with EinAb history and guide comments; verify in one
  click with a required message (parity with "required messages when verifying users").
- Welcome AG gets a bell for every new member of the region.

### 11.5 UX acceptance criteria

- [x] New user goes from landing page to "account created" in **≤ 2 clicks** with Google.
- [x] At any time the user sees which step they are on and the single next action.
- [x] Lighthouse mobile ≥ 90 performance and ≥ 95 accessibility on `/` and `/onboarding`.
- [x] Every step works on a 360 px wide screen and in all five languages.
- [ ] Usability test with 5 real newcomers from Zürich before launch. — *Gate note: replaced during the build by three AI persona design rounds (decision 3, `docs/design-review-m7.md`); the real test with Zürich newcomers is still owed before launch, see launch report "Needs a human".*

---

## 12. Frontend architecture, UI states and error handling

### 12.1 Setup

- Angular 22, standalone components, signals for state, zoneless, new control flow (`@if`, `@for`),
  as in the reference.
- `ng add @openng/optimus-ui` → `provideOptimus(...)` with an Aura preset extended with the foodsharing
  green palette from the reference (`primary.500 = #25834a`, …) and dark mode.
- Icons: `@openng/icons` (`pi pi-*`).
- Lazy-loaded feature routes per module; public routes prerendered.
- Core services from the reference: `SessionService`, `AuthFacadeService`, `authGuard`, `landingGuard`,
  `regionGuard` (was `bezirkGuard`), `loadingInterceptor` + global request bar, `I18nService` with JSON
  catalogs and `check-i18n.mjs`, time-zone date pipe (generalised from `ZurichDateTimePipe`),
  `api-error.ts`, `return-url.ts`.
- Global `p-toast`, `p-confirmdialog`, offline banner.
- PWA: installable, offline shell, web push.

### 12.2 Every data-loading element has explicit states

Rule: **no component renders API data without going through the shared state handling.** A component
that loads data always shows one of these states:

| State | What the user sees | Component |
|---|---|---|
| `loading` (first load) | Skeleton matching the final layout (`p-skeleton`), never a blank area | `<fs-data-state>` |
| `refreshing` | Existing data stays, small inline spinner | `<fs-data-state>` |
| `success` | The data | content projection |
| `empty` | Friendly empty state with explanation and the next action ("No pickups yet — find stores with free slots") | `<fs-empty-state>` |
| `error` | Translated message for the `ApiErrorCode`, **Retry** button, "details" with `traceId` to copy for support | `<fs-error-state>` |
| `forbidden` (403) | "You don't have access" + who to ask (e.g. the store manager) | `<fs-error-state>` |
| `not found` (404) | "This store/thread doesn't exist anymore" + link back | `<fs-error-state>` |
| `offline` | Last loaded data (if cached) marked as offline + auto-retry when back online | `<fs-data-state>` + offline banner |

Implementation:

- A `loadResource()` helper wraps generated API calls (Angular `resource`/`rxResource`) and returns a
  signal `DataState<T> = { status, data, error, reload() }`. ESLint + code review rule: templates may only
  read API data through `DataState`.
- `<fs-data-state [state]="...">` renders skeleton / error / empty templates, with slots to customise them.
  Used for pages, cards, tables, dropdowns that load options, maps, charts, dialogs.
- Tables (`p-table`) and lists use the same component for their body; paginated loads show errors inline
  on the failed page with retry, without losing already loaded pages.
- Selects/autocompletes that load options show "Could not load options — retry" inside the overlay.

### 12.3 Error handling

- **HTTP interceptor**: 401 → session expired dialog and redirect to login with return URL;
  403/404 → passed to the component state; 409 (optimistic lock) → "This was changed by someone else —
  reload"; 429 → "Too many requests, try again in N seconds"; 5xx/network → retryable error. Automatic
  retry with backoff only for idempotent GETs (max 2).
- **Mutations** (save, sign up for slot, send message): button shows loading, double submit prevented;
  on error the form stays filled, field errors from `ApiError.fieldErrors` are shown on the fields,
  other errors as a toast with retry; optimistic UI updates (e.g. slot sign-up, reactions) roll back on error.
- **Forms**: client validation mirrors the OpenAPI constraints (generated where possible); server
  validation is always shown per field.
- **Realtime**: WebSocket reconnects with backoff; a small "reconnecting…" indicator; missed messages
  re-fetched after reconnect.
- **Global**: `ErrorHandler` catches unexpected client errors, shows a non-blocking toast and reports them
  (with `traceId` and route) to `/api/client-errors` for monitoring.
- **Backend side**: every exception maps to `ApiError` (§6.2); no stack traces to the client; `traceId`
  in logs and response header.
- All messages translated in all five languages; `i18n:check` fails the build if an `ApiErrorCode` has no
  translation.

### 12.4 Shared components

| Component | Used by |
|---|---|
| `DataState`, `EmptyState`, `ErrorState` | every data-loading element |
| `HistoryTimeline` | all history views (§10) |
| `JourneyStepper` (compact + full + preview modes) | header, onboarding page, explainer page |
| `PickupSlotList` (sign up/out, confirm, avatars, free-slot highlight) | store page, dashboard, EinAbs |
| `UserChip` / `UserAvatar` (role badge, sleep mode) | everywhere |
| `RegionPicker` (postcode search + tree + map) | onboarding, settings, admin |
| `MapView` (Leaflet; markers for stores, FSPs, baskets, events, regions, pins) | map, store, FSP, basket |
| `Wall` (posts, reactions, images, milestones) | store, FSP, region, profile, event, report |
| `RichTextEditor` (`p-editor`) + Markdown renderer | forum, blog, CMS, mailbox |
| `CalendarExportButton` | pickups, EinAbs, events |
| `AuditTable` | automation pages |
| `PhoneButton`, `NavigationButtons` (Google Maps, Apple Maps, Waze, OSM) | store, FSP, basket, events |

### 12.5 Navigation

Logged out: Explainer, Map, Find region, About (vision, team, partners, press, blog), Politics (foodsharing
cities, demands), Education, Donate, Help, Login. Logged in: Dashboard, Stores (mine), Regions & Groups
(mine), Messages, Mailboxes, Bells, Baskets, Map, Profile menu (profile, settings, passport, history,
logout), Admin (by permission). Mobile: bottom tab bar (Dashboard, Pickups, Messages, Bells, Menu).

---

## 13. Complete feature list (parity with foodsharing.network)

Derived from the public repo `gitlab.com/foodsharing-dev/foodsharing` (63 modules under `src/Modules`,
48 REST controllers under `src/RestApi`, ~100 database tables, Vue pages, roles & permission docs).
**Every row ships at launch.** Each row becomes one GitHub issue labelled with its module.

### 13.1 Accounts, profile and settings

| ID | Feature | Source |
|---|---|---|
| A1 | Sign-up (Google / magic link, §8), registration attempt tracking, email blocklist | Register, `registration_attempt` |
| A2 | Login / logout, passkeys, session refresh after role change | Login, Logout, Relogin, Passkey |
| A3 | Email verification & email change with confirmation (request / complete / abort, recorded in history) | EMailVerify, `fs_mailchange` |
| A4 | All profile fields from §15.1, editable by the user; admin-editable fields by ambassadors with history | Profile, Settings |
| A5 | Profile photo upload with crop | `PUT users/current/photo` |
| A6 | Public profile: photo, name, regions & groups, stores, pickup count, kg saved, posts, bananas, buddies count, wall, achievements, KAM roles, commitments statistics | Profile components, `ProfileCommitmentsStat` |
| A7 | Internal notes on users ("about me internal" + user notes wall for ambassadors) | `about_me_intern`, `fs_usernotes_has_wallpost` |
| A8 | Profile change history, quiz session history, passport history, verification history (§10) | `ProfileHistoryModal`, `QuizSessionHistoryModal` |
| A9 | Sleep mode: none / temporary (from–until) / full, with message; shown everywhere the user appears | SleepingMode, `sleep_*` |
| A10 | Notification settings: per region forum (new threads / all), per thread (bell/mail), per FSP (bell/mail), newsletter, pickup reminders on/off, mention notifications on/off, activity feed filters; channels bell / email / push / Telegram | Notifications, `UserOptionType` |
| A11 | Calendar token: personal ICS feed of pickups, EinAbs, events, with reminders; regenerate / delete token | CalendarRestController |
| A12 | Language selection (de, en, gws, fr, it) | Locale |
| A13 | Delete account with reason (anonymise content, archive record as `fs_foodsaver_archive`), no-automatic-delete flag for admins | DeleteAccount |
| A14 | Business card PDF (verified foodsavers, choice of role/region) | BusinessCard |
| A15 | Trust bananas: give (foodsaver+) with text, list received/sent, delete own, banana count | Banana |
| A16 | Buddies ("Ich kenne"): request, accept, list, remove | Buddy |
| A17 | Theme switcher (light/dark/system) | ThemeSwitcher |
| A18 | Email bounce handling: flag invalid addresses, show warning to user and admins, admin can clear | `fs_email_bounces`, EmailBounceList |
| A19 | Dashboard: onboarding card, my next pickups, pickup options with free slots, activity feed with filters (forum, stores, regions, groups, mailboxes, events), nearby baskets (optional container), bells | Dashboard, Activity |
| A20 | Info mail message / infomail flag for important announcements | `infomail_message` |
| A21 | Data export (GDPR): download all my data as JSON | new (legal requirement) |
| A22 | Security: active sessions, login history, log out everywhere | new |

### 13.2 Regions and working groups

| ID | Feature | Source |
|---|---|---|
| R1 | Region tree with all types (continent, country, federal state/canton, region, city, big city, district, part of town) and working groups; closure table for fast ancestry queries | `UnitType`, `fs_bezirk_closure` |
| R2 | Join / leave region, home region; nightly add to parent regions; application-based joining where configured | Region, MaintenanceRegionCommand |
| R3 | Region page: description, teaser, photo, members, ambassadors, stores, FSPs, events, polls, achievements, pickups, forum, ambassador forum, wall, statistics (members, gender list, age bands, pickups, kg, cooperations) | Region components |
| R4 | Public region page (logged out) with description, contact, events, children, next EinAbs | PublicRegionPage |
| R5 | Region options: report button, mediation button, region pickup rule (active, timespan days, limit, per-day limit, inactive hours before pickup), allow hiding in forum, report reason options + "other", notify on address change | `RegionOptionType` |
| R6 | Region pins on the map (description, active/inactive) | `fs_region_pin` |
| R7 | Region admin (orga): create/edit/delete/move regions, set type, email name, ambassadors, conversation, moderated flag | RegionsAdmin |
| R8 | Member management: member list with roles/verification/last pickup, remove member, grant/revoke admin, permission overview | RegionRestController |
| R9 | Working groups: list, create, edit (name, teaser, description, photo), apply types (nobody / requires properties / everybody / open), application requirements (min pickups, banana count, weeks), applications accept/decline with bell, group categories (development, exchange, administrative, project, archived), archive | WorkGroup, Application, `apply_type`, `fetch_count`, `week_num` |
| R10 | Working-group functions with their region-wide rights (§9) | `WorkgroupFunction`, `fs_region_function` |
| R11 | Group mail (send email to all group members) | WorkingGroupRestController |
| R12 | Video conference room per region/group (Jitsi or BigBlueButton link, configurable) | `regions/{id}/conference` |
| R13 | Region statistics cache (active home-region foodsavers, active cooperations, pickups last month, kg last month, active FSPs, baskets last month) | `fs_region_statistics` |

### 13.3 Stores

| ID | Feature | Source |
|---|---|---|
| S1 | Store list per region and "my stores" with team status, cooperation status, next pickup, free slots | StoreRestController |
| S2 | Create store, edit all store fields from §15.2 | Store DTOs, `fs_betrieb` |
| S3 | Store chains: name, headquarters (address, country), status, press allowed, forum thread, notes, common store information, estimated store count; KAMs; chain list | StoreChain, `fs_chain` |
| S4 | Categories admin (store categories, grocery types) with merge | Categories |
| S5 | Team: members, jumpers (standby), invited, applied; team status (closed / open / searching); per-member stats (pickup count, first/last pickup, added date) | `fs_betrieb_team`, MembershipStatus |
| S6 | Apply to team (with text if required), accept/decline with required message, cancel request | requests endpoints |
| S7 | Invitations (store managers invite; user accepts/declines; withdraw) | invitations endpoints |
| S8 | Move to/from standby, remove member (required message), appoint/remove store manager | managers, standby endpoints |
| S9 | Team list: phone buttons (permission-dependent), last pickup, pickup count, distance to store, sleep mode | StoreTeam, `StoreTeamMemberWithDistance` |
| S10 | Team chat and jumper (standby) chat, auto-membership | `team_conversation_id`, `springer_conversation_id` |
| S11 | Store wall / notes with milestones (cooperation start, status changes) and delete rules per permission table | `fs_betrieb_notiz`, WallPost |
| S12 | Store log (§10.2) with date range and action filter | StoreLogAction |
| S13 | Delete/archive store | DELETE stores/{id} |
| S14 | Store statistics: pickups, kg saved per pickup weight category, per month | `fs_stat_abholmengen`, `fs_fetchweight` |
| S15 | Navigation buttons and map position | changelog |
| S16 | Region of the store shown, store moved between regions by admins | changelog |

### 13.4 Pickups

| ID | Feature | Source |
|---|---|---|
| PU1 | Regular pickups (weekday, time, max slots, description), interval settings | `fs_abholzeiten`, RegularPickup |
| PU2 | One-time pickups; edit slot count and description of any date | `fs_fetchdate`, OneTimePickup |
| PU3 | Sign up / sign out; store managers remove others; slot confirmation (unconfirmed → confirmed); prefetch time (how far ahead sign-up opens) | PickupRestController, `prefetchtime` |
| PU4 | Eligibility check before sign-up (verified, hygiene certificate, phone, region pickup rule, team status, sleep mode) with a clear reason | eligibility endpoint |
| PU5 | Region pickup rule enforcement (max pickups in timespan, per day, ignored N hours before pickup) | RegionOptionType |
| PU6 | Pickup history per store (date range) and per user; delete all future pickups of a user (when leaving) | history endpoints, `DELETE users/{id}/pickups` |
| PU7 | Pickup options for user ("where can I help?", free slots in my stores) | `users/current/pickups/options` |
| PU8 | User agenda for a date | `users/{id}/agenda/{date}` |
| PU9 | Pickup reminders (disable per user) | `DISABLE_PICKUP_REMINDER` |

### 13.5 Messaging, notifications, Telegram

| ID | Feature | Source |
|---|---|---|
| N1 | Conversations 1:1 and group, store team & jumper chats, region conversation, lookup/create, rename, read status, unread counts, mentions | MessageRestController |
| N2 | Realtime delivery (WebSocket) | websocket/ |
| N3 | Bells with read/unread, delete, all bell types (forum thread to activate, WG application, new member for welcome team, store requests, slot confirmations, …) | BellRestController |
| N4 | Web push notifications, subscriptions per device, opt-in prompt | PushNotification |
| N5 | **Telegram**: link account via bot (`/start <token>`), per-user Telegram notifications, region/store Telegram chats for alerts, test message (from reference) | einab-tabelle |
| N6 | Mail notifications with rate limiting, outbox and templates in five languages | Mails, MailQueueWorkerCommand |
| N7 | Newsletter: opt-in, compose per region/global, send via built-in queue (or Listmonk), unsubscribe | newsletter endpoints, listmonk.md |

### 13.6 Regional mailboxes

| ID | Feature | Source |
|---|---|---|
| MB1 | Every region and working group can get an address `<email_name>@<domain>`; mailbox members configured by ambassadors / group admins | `fs_mailbox`, `fs_mailbox_member`, `email_name` |
| MB2 | Inbox, sent, trash folders; read/unread, answered flag; unread count in navigation | MailboxRestController, MailboxFolder |
| MB3 | Read HTML/plain mails safely (sanitised), attachments download | `fs_mailbox_message` |
| MB4 | Compose, reply, forward with attachments, sent from the mailbox address | `POST mailboxes/{id}/mails` |
| MB5 | Incoming mail via IMAP polling or provider webhook; bounce processing; attachment storage | DeleteIMAPMailsCommand, MoveEmailAttachmentsCommand |
| MB6 | Personal mailbox for ambassadors/admins if configured | `mailbox_id` on user |

### 13.7 Forum, events, polls

| ID | Feature | Source |
|---|---|---|
| F1 | Forum per region & group, separate ambassador forum | ForumRestController |
| F2 | Threads: create, edit, delete, sticky, close/open, activation by moderators (moderated regions) | `fs_theme`, ThreadStatus |
| F3 | Posts: create, edit (with edit marker), delete, reply with quote header, emoji reactions with user list, hide/unhide (moderation, with reason) | `fs_theme_post`, `fs_post_reaction` |
| F4 | Follow thread via bell/email; follow whole forum (bell for every new thread) | follow endpoints |
| F5 | Forum search per region | `search/regions/{id}/forum` |
| E1 | Events (offline / online / other) in regions & groups, with location (name, address, coordinates), start/end, description, public flag, online link | `fs_event`, `fs_location` |
| E2 | Invitations and RSVP (yes / maybe / no), participant list, event wall | `fs_foodsaver_has_event`, `fs_event_has_wallpost` |
| E3 | Event calendar export | Calendar |
| V1 | Polls: single choice, multiple choice, thumbs, score voting; scopes (all FS, verified FS, store managers, ambassadors, verified FS of home region); start/end, shuffle options, eligible votes count, cancel with author, notifications, results | `fs_poll`, Voting |

### 13.8 Food baskets, food share points, map

| ID | Feature | Source |
|---|---|---|
| B1 | Food baskets: create with photo, description, weight, grocery types and kinds, location type (home / custom), coordinates, contact type (message / phone / mobile), lifetime; edit, delete | `fs_basket`, `fs_basket_has_*` |
| B2 | Nearby baskets list & map; request basket with message; owner accepts/declines/marks as taken; status | `fs_basket_anfrage` |
| FS1 | Food share points: create (in member region), name, photo, description, address, coordinates; activation status; managers; followers (bell/mail); wall | `fs_fairteiler`, FoodSharePoint |
| M1 | Map with layers: stores (filter by cooperation status and team status), FSPs, baskets, regions, region pins, events, users (permission-dependent); location search; marker details | MapRestController |

### 13.9 Quizzes, verification, passports, achievements

| ID | Feature | Source |
|---|---|---|
| Q1 | Quizzes: Foodsaver, Store manager, Ambassador, Hygiene, Foodsharer, Saving food, Reports (and French foodsaver quiz variant → all quizzes in all five languages) | QuizID |
| Q2 | Quiz sessions: timed & untimed questions, mandatory questions, failure points, results with explanations and wiki links, confirmation; wait/disqualification rules; hygiene expiry; practice/test mode | `fs_quiz_session`, QuizTransactions |
| Q3 | Quiz editor for quiz AG: quiz settings (max failure points, question counts), questions (duration, wiki link, mandatory), answers with rating and explanation, question comments | QuizEditor, `fs_question_has_wallpost` |
| Ve1 | Legal agreement and privacy policy (versioned, re-consent on change), store manager confidentiality agreement | Legal, `privacy_*_accepted_date` |
| Ve2 | Verify / unverify users with required message, verification history, identity confirmation | VerificationRestController |
| Ve3 | Passport: PDF (single and batch per region), pass requests, pass history, last pass date, Apple/Google Wallet, bell/mail on creation optional; layout approved by our admins | PassportGenerator, `fs_pass_*` |
| AC1 | Achievements: region and global, name, description, icon, validity in days, visibility (hidden / private / store managers / scope / global), duplicate mode; award/revoke; hygiene certificate auto-awarded; KAM certificate | `fs_achievement` |

### 13.10 Governance and moderation

| ID | Feature | Source |
|---|---|---|
| G1 | Rule-violation reports: report a user (optionally store-related) with reason from region options, to region report team; list per region and per user; edit, status, wall for discussion; arbitration access | `fs_report`, ReportRestController |
| G2 | Mediation requests (shows mediation team contact) | MediationRequest |
| G3 | Moderation: thread activation, hidden posts, moderation log | Region options |
| G4 | User ratings / feedback | `fs_rating` |

### 13.11 Content, statistics and other

| ID | Feature | Source |
|---|---|---|
| C1 | CMS content pages in five languages (vision, team, partners, press, foodsharing cities, demands, transparency, education, communities, FAQ, privacy, imprint, learning pages) | Content |
| C2 | Blog: create, edit, publish/unpublish, delete, per region, teaser, picture | Blog |
| C3 | Public statistics (kg saved, pickups, foodsavers, stores, cooperations, FSPs, baskets) with animated counter | Statistics |
| C4 | Release notes / "what's new" | release-notes |
| C5 | Global search (users, regions, groups, stores, threads, FSPs) with index | SearchRestController |
| C6 | Resources / lending: own and region resources, categories, private/openness, images, favourites, commons | Resource |
| C7 | Donations page, donation campaign, admin configuration | Donation |
| C8 | Petition page | Petition |
| C9 | Support ticket form including user agent and current page | SupportPage |
| C10 | Uploads: images (resize, 3 sizes), attachments, usage tracking and cleanup | Uploads |
| C11 | Feature toggles (orga) | FeatureToggles |
| C12 | Email blocklist and IP block admin | EmailBlocklist, `fs_ipblock` |
| C13 | OAuth2/OIDC provider for partner apps: authorization code + PKCE, userinfo, scopes `openid profile email regions`, consent with "remember", region-restricted clients, client admin | OAuth, oauth.md |
| C14 | Configuration admin (key/value settings) | `configuration` |
| C15 | Maintenance jobs (§6.3) | src/Command |
| C16 | Team page (public, from working groups) | Team |

---

## 14. foodsharing-einab-tabelle features, built in

The reference app is an add-on that logs into foodsharing.network with admin accounts and reads or writes
data there. Here every feature becomes part of the platform and uses our own data directly. Nothing from it
is dropped; things that only existed to talk to foodsharing.network become their native equivalent.

### 14.1 Introductory pickups (EinAbs) → module `intropickup`, onboarding step 6

| Reference feature | In the platform |
|---|---|
| Region landing page with region picker; landing guard sends logged-in users to their home region | Explainer "find your region" + dashboard redirect to home region |
| Public page of available EinAb slots per region (public location only), notification subscription widget | Public "next EinAb dates" on explainer & region page; "notify me" for newcomers |
| "What is my Foodsharing ID" help page with screenshot | "Your foodsaver ID" shown on profile & passport, with a help tooltip |
| Passwordless email login per region, privacy consent (versioned), phone required & validated, phone prompt dialog | §8 login, step 5 consent, phone in step 2 (same validation, same prompt if missing) |
| Booking rules per region: max active bookings (default 3), no duplicate guide, no duplicate category, Fairteiler visit required by 3rd EinAb, ignored categories | Region settings; enforced in `IntroPickupBookingService` |
| EinAb categories: supermarket, takeout, market, bakery, restaurant, Fairteiler cleaning, Messe, Grossabholung, online | Same enum; online EinAbs with call link |
| My bookings, cancel booking | Journey page + "my EinAbs" |
| Newcomer progress (stages, next step) | Onboarding journey (§11) |
| Notifications about new bookable EinAbs (message or email channel), unsubscribe, auto-switch-off once verified | Bell / push / Telegram / email per user preference; one-click unsubscribe; stops after verification |
| Guide dashboard: create/edit/delete EinAbs (category, start, slot count, minimum pickup count, location, business name, private address, public location & general area, online call link, private/public info), Fairteiler visit flag | Same, linked to real stores (business name and address taken from the store) |
| Private info templates | Same |
| Assign another guide, assignable guides list; book or cancel on behalf of a newcomer | Same |
| Slot comments (guide feedback per newcomer), admin comments | Same; shown in the verification queue |
| Mark EinAb done | Guide confirms attendance → counts towards step 6 |
| ICS per EinAb, personal guide ICS feed with token | Same, included in the personal calendar feed (A11) |
| iCal import candidates (guide's foodsharing calendar link) | Guide's own pickups in this platform are offered as EinAb candidates ("turn this pickup into an EinAb"); external ICS link import still supported (https only, SSRF-safe as reference) |
| Guide profile, guide regions, enable/disable guides, grant admin | `CAN_GIVE_INTRO_PICKUPS` per region; guide profile settings |
| Admin: list EinAbs, delete unbooked with reason, historical EinAb creation, adjust booking count | Same |
| Past EinAb CSV import with preview/confirm, dedupe by user + day + store, store-name aliases, needs-attention rows | Same, for regions moving over from paper lists or the old tool |
| User data refresh queue with progress bar | Not needed for data (users are ours); the progress-bar pattern is reused for CSV imports and batch passport generation |
| Newcomer funnel with names, stalled users, gap analysis | §11.4 |
| Bezirk settings (slug, name, sort order, active, cleaning store, booking rules, user info API) | Region settings |

### 14.2 Store automations → module `automation`

| Reference feature | In the platform |
|---|---|
| **Pickup sign-up automation** per store: auto confirm/decline sign-ups with rules: minimum gap days between a user's pickups, cleaning rule (Fairteiler cleaning in last 12 months; exemptions; minimum free cleaning slots), experience rule, public-holiday rule, **linked-store rules** (pickup in X requires a pickup in Y/Z within a window before/after, grace minutes, bidirectional, custom message), excluded dates (manual + holidays); dry-run; run now; audit with decision & reasons; message to user on decline | "Pickup policies" evaluated instantly at sign-up (no polling). Configured by store managers / `CAN_USE_AUTOMATIONS`. Dry-run records "would decline". Holiday calendars per country/canton (Swiss calendar from reference first). |
| Ownership of automations (owner name/email, editable flag, bot account) | Policies belong to the store; edit rights via permissions; system actor in history |
| **Team request automation**: auto-accept store applications, distance rule (max km from home) | "Auto-accept applications" policy on the store |
| **Open-slot advertisements**: N hours before a pickup with free slots, post rotating messages to store chat and/or Telegram; several adverts per store; late-cancellation message to the user who signed out late; resend advert after late cancellation; planned notifications preview; sent audit; "filled after advert" metric | Same, channels: store chat, **in-app push** to team + jumpers, **Telegram** |
| **Store info (sign-out alerts)**: notify store managers when someone signed out and nobody took over; cutoff hours before pickup; cancellations list | Real-time event on sign-out + daily summary; channels bell / push / Telegram |
| **My pickups + extra slot**: first person of a pickup may add one extra slot; extra-slot audit | Store setting "first sign-up may add one extra slot", audited |
| **Future pickup users grouped by pickup count** (fairness view) | Store/region report, permission `CAN_SEE_USER_PICKUP_COUNT_GROUPING` |
| **See all automation decisions** | Permission `CAN_SEE_ALL_AUTOMATION_DECISIONS` |
| **Metrics tab**: daily anonymous counts archived from audits, filled-after-advert, EinAb and extra slots, scoped to region | Statistics module, same archive-then-delete pattern |
| **Telegram**: bot token, list chats, test message, forwarding of messages sent to self | Telegram integration (N5): bot per installation, chats per region/store, test button |
| Region message account connections (send messages as a region account) | Region system sender for automated messages |
| Foodsharing outage circuit breaker + offline banner | Integration status page (Telegram, SMTP, IMAP, geocoding, wallet) + offline banner when our own API is unreachable |
| Audit retention cleanup | §6.3 |

### 14.3 Surveys → module `survey`

Create/edit/delete surveys (`CAN_MANAGE_SURVEYS`), co-authors with candidate search, question types `TEXT`,
`SINGLE_CHOICE`, `MULTIPLE_CHOICE`, `BOOLEAN`, `STORE` (prefilled from the link's `store` parameter; here a
real store reference), one response per user, results view for creator/co-authors, survey fill page
without app menu, login link returns to the survey. Distinct from polls (V1), which are region votings.

### 14.4 Admin and operations

User management per region (list, enable/disable, verify, refresh, grant permissions, move region, create
user, grant/revoke admin), admins list with enable/disable, permission matrix, last login, verified-user
filter, version health indicator, Prometheus metrics, request loading bar, translations with
`i18n:check`, ProtonMail SMTP defaults, Mailpit locally.

---

## 15. Data model and fields

All entities: UUID id, `created_at`, `updated_at`, `created_by`, `updated_by`, `version`.
Fields marked `@Tracked` write field-level history (§10).

### 15.1 User (all fields from `fs_foodsaver` + reference `User`)

| Group | Fields |
|---|---|
| Identity | email, email verified at, display name, first name, last name, gender (not selected / male / female / diverse), date of birth, photo, preferred language |
| Contact | mobile phone, landline phone, homepage, contact visibility (public / team / none) |
| Address | street, postcode, city, country, latitude, longitude, home region |
| Profile text | about me (public), about me (internal, team only), position / role description |
| Role & status | role (foodsharer…orga), quiz role, verified, verified at, active, last login, registration date, beta tester flag |
| Consent | privacy policy accepted (date + version), privacy notice / confidentiality accepted (date + version), legal agreement (date + version), newsletter opt-in |
| Sleep | sleep status (none / temporary / full), sleep from, sleep until, sleep message |
| Passport | last passport date, passport requests |
| Statistics | pickup count, kg saved, pickup rate, post count, banana count, buddy count, rating count / average, give count, engagement count, last stats update |
| Options | pickup reminder disabled, mention notifications disabled, activity feed filters, notification channels, Telegram chat link, push subscriptions, calendar token |
| Admin | info mail message, no automatic delete, email bounced, deleted at / by / reason (archive) |
| Reference (einab) | foodsaver ID (displayed number), region-scoped permissions, iCal link and feed token, last verified at |

### 15.2 Store (all fields from `fs_betrieb` + DTOs)

| Group | Fields |
|---|---|
| Basics | name, region, category, chain, groceries (multi), status date, created at / by |
| Location | street, postcode, city, country, latitude, longitude |
| Cooperation | cooperation status (unclear / no contact / in negotiation / does not want to / established / gives to other charity / permanently closed), cooperation start date, convincing effort (not set / no problem / after some persuasion / difficult / looked bad but worked) |
| Contact | contact person, phone, fax, email |
| Publicity | press mention allowed (yes / no / not chosen), sticker (yes / no / not chosen), public info, public pickup time (morning / noon-afternoon / evening / night) |
| Pickups | expected amount (weight category), prefetch time (sign-up horizon), regular pickups, use region pickup rule |
| Requirements | hygiene certificate, verified, phone, application text |
| Team | team status (closed / open / searching), team conversation, jumper conversation, store managers |
| Text | special notes (internal), description |
| Automation (einab) | pickup policies, open-slot alerts, sign-out alerts, extra-slot setting, excluded dates |
| Lifecycle | deleted at, archived |

### 15.3 Region (all fields from `fs_bezirk` + reference `Bezirk`)

Name, slug, type, parent, teaser, description, photo, email name (mailbox), mailbox, apply type and
requirements (min pickups, min bananas, min weeks), moderated, conversation, time zone, country,
sort order, active, region options (§13 R5), statistics cache, EinAb settings (max active bookings,
prevent duplicate guide / category, Fairteiler requirement, ignored categories, cleaning store),
onboarding settings (§11.1), holiday calendar.

### 15.4 Overview

```
User ─┬─ AuthIdentity (GOOGLE|EMAIL|PASSKEY)    AuthSession   LoginToken   TelegramLink   PushSubscription
      ├─ JourneyState                             QuizSession → Quiz → Question → Answer (+ comments)
      ├─ LegalAcceptance (document, version)      Verification / IdentityCheck / Passport / PassRequest
      ├─ RegionMembership → Region (tree + closure, type, options, pins, mailbox)
      ├─ GroupMembership  → WorkingGroup → Region (functions, applyType)   GroupApplication
      ├─ RegionPermission (user, region, permission)
      ├─ StoreMembership  → Store → Region, StoreChain(KAMs), Category, Grocery
      ├─ Banana, Buddy, Rating, NotificationPreference, CalendarToken, UserNote
Store ─┬─ RegularPickup ─ Pickup ─ PickupSlot → User          StoreNote/Wall   StoreStatisticsDaily
       ├─ StorePolicy, OpenSlotAlert, SignOutAlert, ExtraSlot, AutomationAudit, ExcludedDate
IntroPickup → Region, Store?, Guide ─ IntroPickupSlot → User ─ SlotComment     PrivateInfoTemplate
Conversation ─ ConversationMember ─ Message    Bell    MailOutbox    Mailbox ─ MailboxMember ─ MailboxMessage
ForumThread ─ ForumPost ─ Reaction ─ ThreadFollower      Event ─ Location ─ EventInvitation
Poll ─ PollOption ─ Vote     Survey ─ SurveyQuestion ─ SurveyResponse ─ SurveyCoauthor
FoodBasket ─ BasketRequest   FoodSharePoint ─ FspFollower ─ FspManager      WallPost ─ WallReaction
Report   Achievement ─ AwardedAchievement   ContentPage   BlogPost   Upload   Resource ─ ResourceCategory
HistoryEntry   MetricDailyCount   RegionStatistics   FeatureToggle   EmailBlocklistEntry   IpBlock
OAuthClient ─ OAuthConsent   Configuration   DonationConfig
```

Deliverable:
- [x] ER diagram generated from Liquibase via SchemaSpy in CI, kept in `docs/`.

---

## 16. Regions

- Unlimited regions in a tree; any region can be activated for onboarding when it has ambassadors.
- **Zürich is the first live region**, with its sub-regions modelled like on foodsharing.network
  (e.g. Zürich Stadt and surrounding regions such as Winterthur, Limmattal, Oberland, Unterland,
  Zimmerberg; the exact list is confirmed with the Zürich ambassadors before launch and imported via an
  admin CSV import of regions).
- Seed and initial production data: Europe → Switzerland → all 26 cantons; Germany and Austria top levels
  so the tree is ready for more regions; global working groups (welcome, quiz, IT, PR, store chains CH,
  hygiene, voting, report/mediation/arbitration admin groups) as on foodsharing.network.
- Region admins (orga) can add, move, merge and archive regions in the UI; nothing requires a deployment.
- Per region: time zone, languages offered, holiday calendar (canton), EinAb and onboarding settings,
  pickup rule, mailbox, Telegram chats.
- Region switcher in the header for users in several regions; home region drives defaults.

---

## 17. Cross-cutting concerns

| Concern | Decision |
|---|---|
| i18n | **de, en, gws, fr, it at launch.** Backend `messages_*.properties` and mail templates per language; frontend JSON catalogs; `i18n:check` in CI fails on missing keys in any language. User-generated content (CMS, quizzes, learning pages, legal texts) has per-language versions with fallback to de. Region time zone for all dates; locale-aware number and date formats. |
| Accessibility | WCAG 2.2 AA; Optimus components are accessible by default; axe checks in e2e tests. |
| Privacy | Data minimisation (§8.3), versioned privacy policy with re-consent, data export, deletion with anonymisation and archive, phone/address visibility per permission table, every admin access to personal data recorded in history (§10). |
| Security | Spring Security, CSRF, CSP (no inline scripts), rate limits (login, messaging, basket requests, mail), IP blocks, upload type/size checks, HTML sanitising for mailbox and rich text, SSRF-safe URL fetching (reference `IcalImportService` pattern), dependency scanning (Dependabot + `npm audit`). |
| Files | `StorageService` interface: local disk volume or S3-compatible bucket; images resized to 3 sizes; mailbox attachments in the same store. |
| Geodata | Photon or self-hosted Nominatim for postcode → coordinates; cached. |
| Performance | p95 API < 200 ms; indexes reviewed per changelog; N+1 checks in tests (Hibernate statistics). |
| Observability | Structured JSON logs in prod with `traceId`, Micrometer metrics, client error reports, `/actuator/health` (liveness/readiness), version group. |
| Feature flags | Feature toggles table (C11) to build modules dark before launch. |

---

## 18. Testing and quality

The reference repo keeps no test suite; this platform does, because it replaces a production system.

### 18.1 Backend
- Unit tests for rules: booking rules, pickup policies, region pickup rule, quiz wait/expiry, permission
  matrix, journey state machine, history recording.
- Slice tests (`@WebMvcTest`) against generated interfaces, including every documented error response.
- Integration tests with Testcontainers Postgres proving every Liquibase changelog runs on Postgres; one
  smoke test boots the `local` profile on H2 with seed data.
- ArchUnit: controllers implement generated APIs, no repository access from `web`, module boundaries,
  mutating services record history.

### 18.2 Frontend unit/component tests
- Component tests for `DataState`/`ErrorState`, journey stepper, booking flows.
- Every data-loading component has a test for loading, empty and error (with retry) states.

### 18.3 Playwright end-to-end tests ("we know it works")

Playwright (`@playwright/test`, folder `e2e/` at repo root) runs against the **packaged jar** with the
`local` profile and seed data, so tests cover the real single deployable.

- **Setup:** `globalSetup` builds/starts the jar (or reuses a running one), waits for `/actuator/health`,
  resets the H2 database to the seed state before each test file. Auth via the dev user switcher stored
  as `storageState` per role, so tests start logged in as `newbie`, `foodsaver`, `bieb`, `guide`, `bot`, `orga`.
- **Google login** tested with a fake OIDC provider (mock-oauth2-server container) configured in the
  test profile; **magic link** tested by reading the mail through the Mailpit API.
- **Coverage rule:** every feature row in §13 and §14 has at least one e2e test tagged with its ID
  (e.g. `test('S6 apply to team @S6', …)`). A script compares the tags with the feature list and fails CI
  if a row has no test. This is the launch checklist in executable form.
- **Key journeys** (each one test file): landing → Google sign-up → full onboarding → Foodsaver;
  EinAb guide creates EinAb → newcomer books → guide confirms → ambassador verifies → passport;
  store manager creates store, regular pickups, invites, confirms slots; pickup policy declines a
  sign-up with reason; open-slot alert reaches push + Telegram mock; messaging in realtime between two
  browser contexts; regional mailbox receive/reply via Mailpit; forum post + moderation; basket request;
  poll and survey; report flow; account deletion and data export.
- **Error states:** Playwright `page.route()` injects 500/403/404/409/429 and network failures for every
  page's API calls and asserts the skeleton, error message, retry button and recovery (§12.2/§12.3).
- **Languages:** smoke run of the main journeys in all five languages (no missing keys, no overflowing text).
- **Accessibility:** `@axe-core/playwright` on every page; no serious/critical violations allowed.
- **Devices:** projects for desktop Chrome, desktop Firefox, WebKit (Safari) and mobile (Pixel 7, iPhone 14).

### 18.4 Screenshot (visual regression) tests ("it looks correct")

- Playwright `expect(page).toHaveScreenshot()` for **every page and every important state**: loading
  skeleton, empty, error, filled; dialogs; journey steps 1-8; mobile and desktop, light theme, German
  (decision 109: dark mode is covered by the axe sweep in both themes, other languages by the i18n checks).
- Stable screenshots: fixed clock (`page.clock`), seed data with fixed UUIDs/dates, animations disabled,
  web fonts preloaded, map tiles stubbed, avatars from local fixtures; screenshots generated inside the
  official Playwright Docker image so CI and local runs match.
- Baselines committed in `e2e/__screenshots__/`; a diff above the threshold fails CI and the HTML report
  shows expected / actual / diff. Updating baselines (`--update-snapshots`) is a deliberate step reviewed in
  the PR.
- Component-level screenshots for shared components (`DataState`, `JourneyStepper`, `PickupSlotList`, …)
  in a component gallery page (`/dev/gallery`, local profile only), so design changes to a shared
  component are reviewed once.

### 18.5 Design rework rounds ("as user friendly and good looking as possible")

Design is not done after the first implementation. Every milestone (§20) includes **at least three design
rework rounds** on its screens before it counts as done:

1. **Round 1 — function & flow:** walk through each journey on mobile and desktop using the screenshot
   report as a contact sheet; remove steps, clicks and fields that aren't needed; check every screen has
   one clear primary action.
2. **Round 2 — visual polish:** consistent spacing scale, typography, colour (foodsharing green tokens),
   icons, empty-state illustrations, success/celebration states, dark mode; compare against the
   explainer page as the visual reference.
3. **Round 3 — real users:** short usability test (3-5 people from Zürich: newcomers, foodsavers, store
   managers, ambassadors) on staging; fix the top findings.
4. Further rounds until the review checklist below passes; screenshot baselines are updated after each
   round so the before/after is visible in the PR.

Review checklist per screen: purpose clear within 5 seconds; primary action obvious; works one-handed on a
360 px phone; text fits in all five languages; loading/empty/error states designed (not default); contrast
and focus states pass axe; consistent with the shared components; nothing unexplained for a newcomer.

AI-assisted reviews are part of each round: screenshots of all states are reviewed against the checklist
and proposed changes are implemented, then re-screenshotted, so several design iterations happen per
milestone cheaply before humans test.

### 18.6 CI (GitHub Actions)
1. `openapi validate` + `oasdiff breaking` (PRs).
2. `./mvnw verify` (backend tests, client build, `i18n:check` for 5 languages, ESLint, Prettier).
3. Playwright e2e (sharded) + feature-coverage check against §13/§14 + axe.
4. Playwright screenshot tests; HTML report with diffs uploaded as an artifact.
5. On tag: multi-arch Docker image to GHCR.

### 18.7 `AGENTS.md` (created with the skeleton)
Carry over the reference rules: Postgres-safe migrations, `VARCHAR(32600)` instead of LOB/TEXT, Mailpit
locally, generated API only in the client, update `openapi` and regenerate instead of bypassing it, skip
full builds for trivial edits. Add: controllers implement generated interfaces; every data-loading UI
element uses `DataState`; every mutation records history; every rule change comes with a unit test;
seed data updated with every new entity; new text in all five languages; every feature gets a Playwright e2e test tagged with its feature ID and screenshot tests for its states; screenshot baselines are only updated deliberately.

---

## 19. Deployment and operations

- Single Docker image (`eclipse-temurin:21-jre`), `SPRING_PROFILES_ACTIVE=prod`, version file, port 8080.
- `deploy/release.sh` as in reference: Maven build → buildx multi-arch → push `:<version>` and `:latest` → git tag.
- Environment: Postgres with daily backups (point-in-time recovery when possible), reverse proxy with TLS,
  `server.forward-headers-strategy=framework`, mail domain with MX/SPF/DKIM/DMARC for regional mailboxes.
- Domain is configuration (`app.domain`, `app.mail.domain`) until it is decided.
- Secrets via env: DB, SMTP/IMAP, Google OAuth, VAPID keys, Telegram bot token (encrypted at rest with
  `app.crypto.key`, as reference `CryptoService`), wallet signing keys, upload bucket.
- Staging environment with seed data for testers and the Zürich pilot team.
- Monitoring: Prometheus + Grafana (requests, errors, job durations, mail queue, push/Telegram failures),
  uptime check, client error reports.

---

## 20. Build milestones and launch

All milestones are internal. **Launch happens once, after milestone 7, with every feature in §13 and §14.**
Milestones are ordered so that each one is testable on staging by the Zürich team.

**Definition of done for every milestone:** all its features have passing Playwright e2e tests (tagged with
their feature IDs), screenshot tests for all states on mobile and desktop, no axe violations, translations in
all five languages, and at least three design rework rounds (§18.5) completed.

| # | Milestone | Content |
|---|---|---|
| 1 | Foundations | Repo skeleton, build bundling Angular, SPA forwarding, Docker, CI; split OpenAPI + contract rules; Optimus UI theme, shell, `DataState`/error handling, i18n (5 languages); Liquibase, seed framework, dev user switcher; `history` module; auth (Google, magic link, passkeys, sessions). |
| 2 | Regions & onboarding | Region tree, options, pins, admin; working groups & functions; explainer page; journey; learning pages; all quizzes + editor; legal agreements; EinAbs with all reference features; verification queue; passports & wallets; newcomer funnel. |
| 3 | Stores & pickups | Stores with all fields, chains, KAMs, categories; teams, applications, invitations, standby; pickups, slots, confirmation, eligibility, region pickup rule; store notes/wall, store log, statistics, weights. |
| 4 | Automations & notifications | Pickup policies, auto-accept, open-slot alerts, sign-out alerts, extra slots, metrics; bells, web push, Telegram, mail outbox, newsletter, pickup reminders. |
| 5 | Communication | Messaging with WebSocket, team/jumper chats; regional mailboxes; forum with moderation; events; polls; surveys. |
| 6 | Community & governance | Map, baskets, FSPs, profiles, bananas, buddies, ratings, sleep mode, calendar feed; reports, mediation, achievements; CMS, blog, statistics, search, resources, donations, petition, support, team page; OAuth provider; orga admin (feature toggles, blocklists, configuration). |
| 7 | Launch readiness | Feature-parity checklist signed off row by row (§13, §14) and proven by the e2e coverage check; final design rework round across the whole app; security review, load test, accessibility audit, privacy review; translations reviewed by native speakers; Zürich region data (regions CSV, stores CSV, EinAb CSV import); production deploy, monitoring, backups, runbook; usability test with Zürich newcomers. |
| — | **Launch (Zürich)** | Users re-register; old einab-tabelle switched off. |

---

## 21. Decisions and open questions

| # | Topic | Decision |
|---|---|---|
| 1 | Relationship to foodsharing.network | Separate platform, multi-region; first region Zürich. |
| 2 | Data migration | Users re-register. Regions bring stores/EinAbs via CSV import. |
| 3 | Domain and hosting | **Open.** Everything domain-related is configuration. |
| 4 | Regional email | Yes, at launch (§13.6). Needs a mail provider with IMAP or inbound webhooks once the domain is chosen. |
| 5 | Passport | Yes; layout checked and approved by our admins. |
| 6 | Languages | de, en, gws, fr, it at launch. |
| 7 | Notification channels | In-app push and Telegram (plus bell and email). |
| 8 | Scope at launch | All features, no exceptions. |

Still open:
- Domain name (blocks: mail DNS, OAuth redirect URIs, wallet pass identifiers, Telegram bot name).
- Hosting location (Swiss hosting for nDSG?) and mail provider for regional mailboxes.
- Exact Zürich sub-region list and the first ambassadors / guides to onboard on staging.

---

## 22. Appendix: sources

- foodsharing public repo: <https://gitlab.com/foodsharing-dev/foodsharing> (`src/Modules`, `src/RestApi`,
  `src/Modules/Core/DBConstants`, `migrations/20260220222156_initial_migration.php` for all tables and
  fields, `client/src/views`, `docs/en/glossary/roles-and-permissions.md`, `docs/en/permissions/store.md`,
  `docs/en/oauth.md`, `docs/en/roadmap.md`, `CHANGELOG.md`).
- foodsharing.network (live site; client-rendered, features verified via the repo).
- Reference app `foodsharing-einab-tabelle` (local): `server/pom.xml`, `openapi.yaml` (~120 operations),
  `application*.properties`, Liquibase changelogs 001-042, services under `ch.it4user.foodsharing.service`,
  `client/src/app/app.routes.ts`, `AGENTS.md`, `IMPLEMENTATION_NOTES.md`.
- Optimus UI: <https://github.com/openng-org/optimus-ui> (v2.x, Angular ≥ 22, MIT fork of PrimeNG 21),
  docs <https://optimus.openng.org>.
