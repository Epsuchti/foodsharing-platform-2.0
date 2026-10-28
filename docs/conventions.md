# Project conventions (from plan-m1 Part A; binding for all milestones)

### A1. Backend package layout
```
network.foodsharing
├── FoodsharingApplication
├── config/        SecurityConfig, DevSecurityConfig(@Profile local|e2e), SpaForwardingController, StaticResourceConfig,
│                  ClockConfig, JpaAuditingConfig, MessageSourceConfig, AppProperties(@ConfigurationProperties "app"),
│                  VersionInfoContributor, AppVersionHealthIndicator, TraceIdFilter
├── security/      SessionCookieAuthenticationFilter, SessionPrincipal, CurrentActorService, PermissionService
├── common/        error/{ApiException, ApiExceptionHandler}, persistence/BaseEntity, paging/PageMapper, web/PublicController
└── <module>/      web/  <Tag>Controller implements <Tag>Api  (exactly one generated interface, no own mapping annotations)
                   service/  @Service, @Transactional here only
                   domain/   entities, enums, <Module>Action implements HistoryAction
                   repository/ Spring Data
                   mapper/   entity <-> generated model (MapStruct not used; hand-written @Component mappers as reference ApiModelMapper)
```
Modules in M1: `user`, `auth`, `history`, `notification` (only `notification.mail.*` outbox in M1), `dev` (local/e2e only). Spring Modulith `ApplicationModules.of(FoodsharingApplication.class).verify()` in `ModulesTest`; `config`, `security`, `common` are declared shared (`@ApplicationModule(type = OPEN)` via `package-info.java`). Use `spring-modulith-bom` 2.0.x (Boot 4 line). If the artifact does not resolve, replace with ArchUnit `slices().matching("network.foodsharing.(*)..")` + explicit allowed-dependency map and note it in `docs/decisions.md`.

Cross-module access: only through the other module's `service` package (ArchUnit rule: classes in `..<m>..` may not depend on `..<other>.repository..`, `..<other>.domain..` except enums/ids declared `@NamedInterface` in Modulith terms — simplify: domain entity classes of other modules may be referenced read-only for JPA relations; repositories never).

### A2. Error codes (single source, no duplicate enum)
- `ApiErrorCode` lives **only** in `server/src/main/resources/openapi/schemas/common.yaml` as a string enum. The Java enum is the generated `network.foodsharing.api.model.ApiErrorCode`; `ApiException(HttpStatus, ApiErrorCode, List<FieldError> fieldErrors = [], Map<String,String> args = {})` in `network.foodsharing.common.error`. No hand-written copy (differs from the reference, decision to record).
- Enum values are grouped in comment-delimited blocks per module inside the YAML list (`# --- auth ---`, `# --- history ---`) so parallel tasks only touch their block.
- `ApiError { code, message (English, for logs), traceId, fieldErrors?: [{field, code, message}], retryable: boolean, retryAfterSeconds? }`. `ApiExceptionHandler` (`@RestControllerAdvice`, no `assignableTypes` filter) maps: ApiException, MethodArgumentNotValid/ConstraintViolation → `VALIDATION_FAILED` + fieldErrors, AccessDenied → 403 `ACCESS_DENIED`, AuthenticationException → 401 `AUTHENTICATION_REQUIRED`, ObjectOptimisticLockingFailure → 409 `CONCURRENT_MODIFICATION`, NoResourceFound/EntityNotFound → 404 `RESOURCE_NOT_FOUND`, HttpMessageNotReadable → 400 `MALFORMED_REQUEST`, any other → 500 `UNEXPECTED_ERROR` (logged with traceId). `retryable` = status ≥ 500 or 429 or 503.
- `TraceIdFilter` (servlet filter, first in chain): 12-hex id in MDC `traceId` + response header `X-Trace-Id`.
- Frontend i18n key `errors.<CODE>` must exist for every enum value in all 5 languages (checked by `check-i18n.mjs`, which parses `common.yaml`).

### A3. History recording API (`network.foodsharing.history`)
```java
public interface HistoryAction { String code(); SubjectType subjectType(); RetentionPolicy retention(); }   // each module declares its own enum: AuthAction, UserAction, StoreLogAction…
public enum SubjectType { USER, REGION, GROUP, STORE, PICKUP, INTRO_PICKUP, CONVERSATION, THREAD, EVENT, POLL, SURVEY, BASKET, FSP, REPORT, SYSTEM }  // owned by history module, extended per milestone
public enum ActorType { USER, SYSTEM, AUTOMATION }

@Service public class HistoryService {
  public HistoryEntry record(HistoryEvent event);                         // same transaction as the change
  public HistoryEvent.Builder event(HistoryAction action, UUID subjectId); // actor = CurrentActorService (or SYSTEM when none)
}
HistoryEvent.builder: .secondarySubject(UUID) .field(String) .oldValue(String) .newValue(String) .reason(String) .metadata(Map<String,Object> -> JSON ≤ 32600) .region(UUID) .actorSystem() .actorAutomation(String name)
// typical call: history.record(history.event(AuthAction.LOGIN, user.getId()).metadata(Map.of("method","GOOGLE")).build());
```
- `HistoryContext` (request-scoped holder, `HistoryContext.reason()` / `withReason(reason, Runnable)`): controllers put the optional `reason` request field there; `@Tracked` listener and services read it.
- `@Tracked(redact = false)` on entity fields. `TrackedFieldListener` is a Hibernate `PostUpdateEventListener` registered through an `Integrator` (`META-INF/services/org.hibernate.integrator.spi.Integrator`), compares `getOldState()`/`getState()` for dirty `@Tracked` properties and calls `HistoryService.record` with action `<Module>Action.FIELD_CHANGED` (every module with tracked entities declares one), `field`, old/new (`***` when redacted). Entities opt in by implementing `HistorySubject { SubjectType historySubjectType(); UUID historySubjectId(); UUID historyRegionId(); }`.
- `history_entry` columns exactly as plan §10.1 plus `trace_id VARCHAR(32)`; indexes `(subject_type, subject_id, occurred_at desc)`, `(actor_id, occurred_at desc)`, `(region_id, occurred_at desc)`, `(action)`.
- Read side: `HistoryQueryService.list(HistoryFilter)` + `HistoryAccessPolicy.canRead(actor, subjectType, subjectId)` (M1: USER subject readable by the user themself and ORGA; `SYSTEM` by ORGA; every later module registers its rule in the same class via a `switch` — or, to avoid a hotspot, modules implement `HistoryAccessRule` beans and the policy picks by `SubjectType`; **use the bean approach**).
- Retention: `RetentionPolicy { FOREVER, YEARS_2, DAYS_90, ACCOUNT_LIFETIME }` on each action; `HistoryRetentionJob` nightly (M1 stub + unit test; ShedLock arrives in M4 with the other jobs).

### A4. ArchUnit rules (`server/src/test/java/network/foodsharing/ArchitectureTest.java`, run in `mvn test`)
1. `classes().that().areAnnotatedWith(RestController.class).should(implementExactlyOneInterfaceFrom("network.foodsharing.api"))` and `.should().notBeAnnotatedWith(RequestMapping.class)` and methods not annotated with `@GetMapping/@PostMapping/...` (custom condition over `JavaMethod.getAnnotations()`).
2. Controllers (`..web..`) may not access `..repository..`; `..web..` may not depend on `jakarta.persistence..`.
3. Generated models (`network.foodsharing.api.model..`) may only be used from `..web..`, `..mapper..`, `common.error..`, `config..` (exception list: `ApiErrorCode`, `Language`, `Role` enums are allowed everywhere because they are the shared string enums).
4. **Mutating services record history**: methods in `..service..` annotated `@Transactional` with `readOnly=false` must (a) call a method of `HistoryService` directly or (b) call another `..service..` method that does (one level, resolved via `getMethodCallsFromSelf()`), or (c) be annotated `@NoHistory(reason = "...")`, unless they live in `history` or `dev` packages. Fails with the list of offending methods.
5. `Instant.now()`, `LocalDate.now()`, `LocalDateTime.now()`, `OffsetDateTime.now()`, `ZonedDateTime.now()`, `new Date()` are banned outside `config.ClockConfig` (everything injects `java.time.Clock`).
6. Classes in `network.foodsharing.dev..` must be annotated `@Profile` containing only `local`/`e2e` (custom condition).
7. Entities extend `BaseEntity`; no `@Lob`, no `columnDefinition = "TEXT"`; `@Enumerated(EnumType.STRING)` on every enum field.
8. `@Scheduled` methods only in classes named `*Job`.

### A5. OpenAPI split and conflict-free editing
```
server/src/main/resources/openapi/
├── openapi.yaml        info, servers [/], tags, components.securitySchemes (cookieAuth), x-framework-endpoints list,
│                       paths: one `$ref` line per path, grouped in blocks `# --- <tag> ---`
├── paths/<tag>.yaml    path items keyed by the path (`/api/auth/session: get: …`), tag == file name
└── schemas/common.yaml, <tag>.yaml
```
- `$ref` style: from `paths/auth.yaml` → `'../schemas/auth.yaml#/SessionInfo'`; from `openapi.yaml` → `'./paths/auth.yaml#/~1api~1auth~1session'`.
- One tag per module (`auth`, `users`, `history`, `dev`, `public`); public endpoints of later modules use `public-<module>` tags so one tag never spans two controllers.
- Generic pages are concrete schemas (`HistoryEntryPage { items, page: PageMeta }`); `PageMeta { page, size, totalItems, totalPages }`.
- `x-permission: <ROLE or PERMISSION or "self">` on every operation; `x-history: <ActionEnum.VALUE>` on every mutating operation (documentation; a test cross-checks that the named action enum constant exists).
- Framework endpoints not described in the spec (listed under `x-framework-endpoints`, allowed as raw HttpClient in `core/`): `/oauth2/authorization/google`, `/login/oauth2/code/google`, `/webauthn/register/options`, `/webauthn/register`, `/webauthn/authenticate/options`, `/login/webauthn`, `/dev/oidc/**`, `/actuator/**`.
- `scripts/check-openapi.mjs` (repo root): validates with `@openapitools/openapi-generator-cli validate`, checks every path in `paths/*.yaml` is referenced from `openapi.yaml`, tag==file, every operation has `operationId`, `x-permission`, 4xx responses referencing `ApiError`, every mutating op (post/put/patch/delete) has `x-history`. CI also runs `oasdiff breaking` (docker `tufin/oasdiff`) against `main` (checkout of main into a temp worktree so relative refs resolve).
- Parallel editing rule: a task only edits its own `paths/<tag>.yaml`, `schemas/<tag>.yaml`, its block in `openapi.yaml` and its block in `ApiErrorCode`. Since T0 writes the **complete M1 contract up front**, M1 feature tasks should not need to touch `openapi.yaml` at all.

### A6. Liquibase layout
```
db/changelog/db.changelog-master.yaml:
  - includeAll: { path: changes/, relativeToChangelogFile: true }          # alphabetical = ordered by number prefix
  - includeAll: { path: ../seed/changes/, relativeToChangelogFile: true, context: seed }
db/changelog/changes/NNN-<module>-<short-name>.yaml       (NNN three digits; M1 reserves 001–019, M2 020–059, M3 060–099, M4 100–129, M5 130–159, M6 160–199)
db/seed/changes/NNN-<module>-<short-name>.yaml            (same numbering; always `context: seed`, `runOnChange: true`, use `loadUpdateData` with primaryKey: id)
db/seed/<module>/<table>.csv                              (header = column names; dates ISO-8601 UTC; booleans true/false; UUIDs fixed)
```
- Nobody edits the master after T0. Each task adds files with its reserved numbers (listed per task below). Changeset ids `NNN-<n>`, author `platform`.
- Every table: `id UUID PK, version BIGINT NOT NULL DEFAULT 0, created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at …, created_by UUID NULL, updated_by UUID NULL`. Long text `VARCHAR(32600)`. Enums `VARCHAR(40)`. Geo `DOUBLE`. Index names `ix_<table>_<cols>`, FKs `fk_<table>_<col>`, uniques `uq_<table>_<cols>`.
- Seed UUID convention: `0000MMMM-TTTT-4000-8000-NNNNNNNNNNNN` — `MMMM` module code (0001 user, 0002 auth, 0003 history, 0010 region, 0011 group, 0020 store, 0021 pickup, 0030 intropickup, …; table in `docs/seed-ids.md`), `TTTT` table code inside the module, `N` sequence. Seed users: `00000001-0001-4000-8000-000000000001` = `newbie@local`, …`…014` = `orga@local`.
- Seed time anchor: `SEED_NOW = 2026-11-02T09:00:00Z`; all seed timestamps are absolute relative to it; the `e2e` profile fixes `app.clock.fixed=2026-11-02T09:00:00Z`; the browser clock is fixed to the same instant for screenshots. (M3 adds a date-shift step for pickups when they appear; M1 has only history/session timestamps.)
- `BaseEntity`: `@Id UUID` (random, `@GeneratedValue(UUID)`), `@Version long`, `@CreatedDate/@LastModifiedDate Instant` and `@CreatedBy/@LastModifiedBy UUID` via Spring Data auditing with `AuditorAware` = `CurrentActorService` and `DateTimeProvider` = `Clock` bean.

### A7. Frontend conventions
```
client/src/app/
├── api/              generated, gitignored
├── core/             auth/{session.service, auth-facade.service, auth.guard, dev-mode.guard, return-url, webauthn.client}
│                     http/{error.interceptor, loading.interceptor, retry.interceptor}
│                     data/{data-state.ts, mutation.ts, api-error.ts}   i18n/{i18n.service, t.pipe, languages.ts}
│                     theme/theme.service   online/online-status.service   errors/global-error-handler   app-info.service
├── shared/           data-state/{data-state.component, empty-state.component, error-state.component, skeleton-presets}
│                     history-timeline/   user-chip/   page-header/   offline-banner/
├── layout/           shell.component, header.component, mobile-tab-bar.component, footer.component, nav.config.ts
└── features/<module>/<module>.routes.ts, pages/*, components/*, i18n/{de,en,gws,fr,it}.json
```
- Selector prefix `fs-`. Standalone components, `ChangeDetectionStrategy.OnPush`, signals, zoneless (`provideZonelessChangeDetection()`), control flow `@if/@for`.
- Routes: `app.routes.ts` only has one `loadChildren` line per module (T0 pre-registers `auth`, `account`, `history`, `dashboard`, `dev`, `public`); feature route files export `export const AUTH_ROUTES: Routes`. Guards: `authGuard` (+ `data: { roles?: Role[], permissions?: Permission[] }`), `landingGuard`, `devModeGuard`.
- Navigation: `layout/nav.config.ts` is a declarative list `{ id, labelKey, icon, route, visible: (session) => boolean, mobileTab?: boolean }` owned by T0 (extended once per milestone in one commit, not per task).
- **i18n**: catalogs per module (`features/<m>/i18n/<lang>.json`, `core/i18n/<lang>.json`, `shared/i18n/<lang>.json`, `layout/i18n/<lang>.json`), flat keys namespaced `<module>.<page>.<key>`, shared ones `common.*`, `errors.<CODE>`, `nav.*`. `client/scripts/build-i18n.mjs` merges them into `public/i18n/<lang>.json` (gitignored, run by `npm start`, `npm run build`, `npm test`). `client/scripts/check-i18n.mjs` (extends reference): scans `i18n.t('…')`, `| t`, `t.<key>` usages, fails on missing keys in any of 5 languages, duplicate keys across modules, unused keys (warning), keys used outside their module (error unless `common./errors./nav.`), and missing `errors.<CODE>` for every `ApiErrorCode` in `schemas/common.yaml`. Usage API: `i18n.t(key, params?)` with `{name}` interpolation and `{count, plural, =0 {…} one {…} other {…}}` ICU-lite via `@formatjs/intl-messageformat`-free hand parser (keep simple: `Intl.PluralRules`). Pipe `{{ 'key' | t:params }}` for templates.
- Dates: `fs-date` pipe (`core/dates/date-time.pipe.ts`) takes `timeZone` from the region in context (default `Europe/Zurich` until regions exist); locale map `de→de-CH, gws→de-CH, en→en-CH, fr→fr-CH, it→it-CH`.
- Theme: `ThemeService` signal `'light'|'dark'|'system'`, persisted in `localStorage['fs.theme']` and (when logged in) `PATCH /api/users/me/settings`; `provideOptimus({ theme: { preset: FoodsharingPreset, options: { darkModeSelector: '.fs-dark', cssLayer: { name: 'optimus', order: 'base, optimus, app' } } } })`; the service toggles `.fs-dark` on `<html>` and follows `prefers-color-scheme` for `system`. Preset: Aura + the reference green palette (`primary.500 = #25834a` etc.).
- **DataState API** (`core/data/data-state.ts`):
```ts
export type DataStatus = 'loading' | 'refreshing' | 'success' | 'empty' | 'error' | 'forbidden' | 'notFound' | 'offline';
export interface ApiErrorView { status: number; code: ApiErrorCode | 'NETWORK' | 'UNKNOWN'; message: string; traceId?: string; retryable: boolean; retryAfterSeconds?: number; fieldErrors: FieldError[] }
export interface DataState<T> { status: Signal<DataStatus>; data: Signal<T | undefined>; error: Signal<ApiErrorView | undefined>; reload(): void; isStale: Signal<boolean> }
export function loadResource<T, P = undefined>(options: {
  params?: () => P;                                   // signal-driven; undefined params → idle
  loader: (params: P, signal: AbortSignal) => Observable<T> | Promise<T>;
  isEmpty?: (value: T) => boolean;                    // default: [] or {items: []} → 'empty'
  keepDataOnError?: boolean;                          // default true → 'refreshing' then 'error' keeps old data
  autoRetryWhenOnline?: boolean;                      // default true (via OnlineStatusService)
}): DataState<T>
export function runMutation<T>(call: () => Observable<T>, opts?: { form?: FormGroup; successToastKey?: string; onSuccess?: (v: T) => void; optimistic?: { apply(): void; rollback(): void } }): MutationState  // { busy: Signal<boolean>, error: Signal<ApiErrorView|undefined>, run(): void }
```
Implemented with Angular `resource()`/`rxResource` + `httpResource` is not used (generated client returns Observables). `<fs-data-state [state]="state" [skeleton]="'list' | 'card' | 'table' | 'form' | 'detail' | TemplateRef" [emptyTitle] [emptyActionLabel] (emptyAction) [compact]>` projects content; slots `<ng-template fsEmpty>`, `<ng-template fsError>`, `<ng-template fsSkeleton>`. `fs-error-state` shows translated `errors.<code>`, Retry (when retryable or always for GET), "Details" expander with traceId + copy button, forbidden variant with `askWhoKey`, notFound variant with back link. `fs-data-state` inside `p-table` body and `p-select` overlays via `[compact]`.
- ESLint (`client/eslint.config.js`, `angular-eslint` 22): `no-restricted-imports` for `@angular/common/http` outside `src/app/core/**` and `src/app/api/**` (D5.3); `@angular-eslint/template/*` recommended; custom check `client/scripts/check-data-state.mjs`: any `features/**` file that calls a generated `*Service` method outside `loadResource(`/`runMutation(`/`*.resource.ts` files fails.
- Component selectors/ids used by tests: `data-testid="<module>.<page>.<element>"` on every interactive element and on each DataState root (`data-state="loading|error|…"` attribute set by `fs-data-state`).

### A8. Test naming
- Backend: `XxxTest` (unit, Surefire), `XxxControllerTest` (`@WebMvcTest(XxxController.class)` + `@Import(ApiExceptionHandler, security test config)` asserting every documented error response), `XxxIT` (Failsafe, `@SpringBootTest` + Testcontainers `postgres:17`), `ArchitectureTest`, `ModulesTest`, `LocalProfileSmokeIT` (H2 + seed, asserts dev beans present), `ProdLikeProfileTest` (profile `test`, asserts no `DevController`/`FakeOidcController` beans). Test fixtures in `src/test/java/network/foodsharing/testsupport/` (`WithSeedUser` annotation, `SecurityTestConfig`, `FixedClockConfig`).
- Frontend: Vitest via `@angular/build:unit-test`; `*.spec.ts` next to the file; every data-loading component has `loading/empty/error-retry` specs using `provideDataStateTesting()` helper.
- E2E: `e2e/tests/<module>/<id-lowercase>-<slug>.spec.ts`, `test('A2 magic link login and logout @A2', …)`; one tag per feature ID in the title; screenshot tests `e2e/screenshots/<area>.screen.ts` tagged `@screenshot`; coverage-only tags for deliverables `@D4.3` are allowed.

### A9. Auth/session mechanics (fixed now so later modules can rely on them)
- Cookie `fs_session` (HttpOnly, SameSite=Lax, Secure in prod, path `/`), value = 256-bit random, stored SHA-256 in `auth_session.token_hash`; sliding expiry `app.auth.session-validity-days=365`, `last_seen_at` updated at most once per 5 min. `SessionCookieAuthenticationFilter` sets `SessionPrincipal { userId, sessionId, role, email }`. `GET /api/auth/session` is the only session bootstrap; the client never stores session data in localStorage (differs from reference).
- CSRF: `CookieCsrfTokenRepository.withHttpOnlyFalse()` (`XSRF-TOKEN`) + `CsrfTokenRequestAttributeHandler`; Angular `withXsrfConfiguration({ cookieName: 'XSRF-TOKEN', headerName: 'X-XSRF-TOKEN' })`. Framework endpoints (`/oauth2/**`, `/login/**`, `/webauthn/**`) keep Spring's defaults; `/api/dev/**` is CSRF-exempt in local/e2e.
- Spring `sessionCreationPolicy(IF_REQUIRED)` only because WebAuthn and the OAuth2 authorization-request repository need a servlet session for the handshake; `JSESSIONID` is never used for API auth.
- Google: `spring.security.oauth2.client.registration.google` with **explicit** `provider.google.{authorization-uri,token-uri,jwk-set-uri,user-info-uri,issuer-uri}` (never `issuer-uri`-only discovery: in local/e2e the issuer is the app itself at `/dev/oidc`, and discovery at startup would deadlock before Tomcat listens). Prod values point to Google; `GOOGLE_CLIENT_ID`/`GOOGLE_CLIENT_SECRET` env (D8.5).
- Return URL: `ReturnUrlValidator` (backend) and `safeReturnUrl()` (frontend) accept only in-app absolute paths (`/…`, no `//`, no `\`, not login routes, ≤ 500 chars).

---


# Milestone 2 additions (A10–A17, from plan-m2 Part A; binding for M2+)

### A10. Scoped permission model (plan §9, three layers in one `PermissionService`)
- `security.PermissionService` is the only entry point and depends on no feature module. Layers 2/3 come from the SPI
  `security.ScopedPermissionResolver` (implemented by `region.service.RegionPermissionResolver`; without a bean, e.g.
  in `@WebMvcTest`, `PermissionService` uses `NoopScopedPermissionResolver` = nobody holds scoped roles). The
  resolver works on plain user ids; the **ORGA override is applied by `PermissionService`** before it asks the resolver.
- `security.WorkingGroupFunction` (+ `isRestricted()` = REPORT, ARBITRATION, FSMANAGEMENT, BOARD) and
  `security.Permission` live in `security` so the SPI can name them without a `security → region` cycle.
- `PermissionService` methods: `isMemberOf`, `isAmbassador` (admin of region/group; ORGA is *not* automatically one),
  `isAmbassadorOrOrga`, `isAmbassadorOfAny`, `hasFunction(p, fn, regionId)`, `mayAdministrateRegion`,
  `mayAdministrateUser`, `has(p, Permission, regionId)`, `hasAnywhere`, `effectivePermissions` and the `requireXxx`
  variants (401 without principal, 403 `ACCESS_DENIED`). Services check permissions there, never ad hoc.
- Rules (`RegionPermissionResolver`):
  - member = active `region_membership`; admin = `region_admin` (ambassador of a region / admin of a group).
  - `hasFunctionGroupPermission(fn, region)`: the region has a `region_function` row for `fn` → its group's admins;
    otherwise the region's ambassadors (upstream `hasFunctionGroupPermissionForRegion`; "a function group supersedes
    the ambassadors").
  - `hasRegionPermission(p, region)`: a `region_permission` row, or membership (not only admin) of a group that holds a
    function of that region whose grants (`app.permissions.function-grants`) contain `p`. Defaults:
    FSMANAGEMENT → CAN_MANAGE_USERS, WELCOME → CAN_GIVE_INTRO_PICKUPS, STORES_COORDINATION → CAN_USE_AUTOMATIONS +
    CAN_SEE_USER_PICKUP_COUNT_GROUPING + CAN_SEE_ALL_AUTOMATION_DECISIONS, VOTING → CAN_MANAGE_SURVEYS. Grants are per
    region (no inheritance to sub-regions).
  - `mayAdministrateRegion(actor, region)`: actor is ambassador of the region **or one of its ancestors**, or admin of
    the FSMANAGEMENT group of such a region (working-group admin roles do not count). `mayAdministrateUser(actor, user)`:
    the same for any region the user is an active member of.
- Who grants `region_permission`: ambassadors of the region and ORGA; `CAN_MANAGE_USERS` holders only
  `CAN_GIVE_INTRO_PICKUPS` (T12).
- Joining: only "accessible" types `PART_OF_TOWN, CITY, REGION, DISTRICT` (`UnitType.isAccessible()`); **any signed-in
  user** may join (deviation from upstream `mayJoinRegion`, journey step 2 happens as FOODSHARER); join mode
  APPLICATION → 409 `REGION_REQUIRES_APPLICATION`. The first joined region becomes the home region. Leaving the home
  region is refused (409 `CANNOT_LEAVE_HOME_REGION`) unless another accessible membership exists, which then becomes
  the home region (earliest joined).
- `SessionInfo.permissions` strings: `"<PERMISSION>@<regionId>"`, ORGA `"<PERMISSION>@*"`, sorted.
- The full matrix is `region/PermissionServiceMatrixTest` — extend it with every rule change.

### A11. SessionInfo enrichment
- SPI `security.SessionInfoEnricher { void enrich(SessionPrincipal, SessionInfoBuilder) }`; `SessionInfoBuilder` is a
  hand-written holder of plain values (`homeRegionId`, `Unit(id, name, slug, type, parentId, member, admin,
  homeRegion, function)` records with enum *names*, pending consent kind names, extra permission strings,
  `onboarding(complete, currentStep)`) because `security` may not use generated models (ArchUnit rule 3).
- `auth.mapper.AuthMapper.sessionInfo(...)` applies every enricher bean in `@Order`; a failing enricher is logged and
  skipped (the session bootstrap never fails). `SessionUser` gained `homeRegionId, foodsaverNumber, quizRole, phone`;
  `SessionInfo` gained `units`, `pendingConsents`, `onboarding` (all optional fields in the contract).
- Enrichers: region (T12: units + nothing else; permissions already come from `PermissionService`), legal (T14),
  onboarding (T22).

### A12. Notifications before M4
- `notification.service.NotificationService.notify(NotificationRequest)`: one bell per **active** recipient
  (`notification.domain.Bell`, table `bell`, changelog 019) and, when built with `.mail(template)`, a mail through the
  M1 outbox in the recipient's language (variables = params + `link` (absolute) + `displayName`). Records
  `NotificationAction.SENT` (code `NOTIFICATION_SENT`, subject SYSTEM); every bell records
  `NotificationAction.BELL_CREATED` (`NOTIFICATION_BELL_CREATED`, secondary subject = recipient).
- Build requests with `NotificationRequest.to(recipientIds, NotificationType.X).param(..).link("/path")
  .identifier(BellService.identifier(type, ids...)).mail("<module>/<name>").build()`. Identifiers are
  `<TYPE>-<id>[-<id>]` (upstream `BellType::createIdentifier`); `NotificationService.removeBells(identifier)`
  soft-deletes them (e.g. `REGION_NEW_MEMBER-<userId>` when the user is verified, T16).
- `NotificationType` (notification module) holds all M2 types plus `GENERIC` (title from param `titleKey`); tasks do
  not add constants — use GENERIC. Bell title key = `bell.<TYPE>`.
- Mail templates: `templates/mail/<module>/<name>_<lang>.html|txt`, subject key `mail.<module>/<name>.subject`.
- Cross-module notifications run in `@TransactionalEventListener(AFTER_COMMIT)` **+
  `@Transactional(propagation = REQUIRES_NEW)`** listeners (without REQUIRES_NEW the writes of an AFTER_COMMIT listener
  are never committed). Example: `region.service.RegionNotificationListener` (welcome bell on join).
- Tests read notifications through `GET /api/dev/notifications?userId&type` (one BELL entry per bell plus a MAIL entry
  when it was mailed).

### A13. Seed date shifting (local profile only)
- SPI `common.seed.SeedDateShiftContributor { void shift(Duration delta) }`. `dev.service.SeedDateShiftRunner`
  (`@Profile("local")`, `ApplicationRunner`) shifts by the whole days between the last anchor (table
  `seed_shift_state`, initially SEED_NOW's date 2026-11-02) and today in Europe/Zurich, inside one transaction, then
  stores the new anchor. e2e has a fixed clock → nothing shifts. Contributors update only seed rows (fixed seed ids)
  with plain SQL. After `POST /api/dev/seed/reset` the next shift happens at the next start.

### A14. User-generated content in five languages
- Translatable rows use a sibling table `<table>_i18n(owner_id, language VARCHAR(5), …text columns)` with
  `uq_<table>_i18n_owner_language` (see `region_onboarding_step_i18n`); reads fall back requested → de → any. No JSON
  text blobs. Seed translations: per-table CSV `db/seed/<module>/<table>_i18n.csv` (gws/fr/it machine translated).

### A15. Time zones and "today"
- Every region has `time_zone` (default `Europe/Zurich`); services use `RegionService.zoneOf(regionId)`, never a
  constant.

### A16. Public endpoints and tags
- Public endpoints live under `/api/public/<module>/…` with tag `public-<module>` and their own
  `Public<Module>Controller` (`security: []` in the contract). Token-protected downloads answer 404 on unknown tokens.

### A17. Jobs and long-running work
- `*Job` classes have `@Scheduled` (scheduling is globally off with `app.scheduling.enabled=false`) and implement
  `common.jobs.RunnableJob { jobName(); runNow(); }` so tests can run them: `POST /api/dev/jobs/{name}/run`
  (e.g. `region-maintenance`). Single instance until ShedLock (M4).
- Long-running requests use `common.jobs.ProgressTracker` (`submit(ownerId, total, work)` on a virtual thread, or
  `runNow`; result = bytes + content type + file name, kept `app.jobs.result-ttl-minutes`) and answer `JobRef{jobId}`;
  clients poll `GET /api/jobs/{jobId}` and download `GET /api/jobs/{jobId}/result` (tag `jobs`,
  `common.web.JobsController`, owner only, 404 `JOB_NOT_FOUND` for everyone else). Work in a job has no security
  context or transaction: capture the actor id before submitting.

### A18. Backend patterns established in M2 (T10/T11)
- **Contract**: all M2 operations exist; unimplemented ones throw `ApiException.notImplemented("<operationId>")`
  (501 `NOT_IMPLEMENTED`) in their controller stubs. Implementing a task = replace the stub bodies of its controller.
  Tag files: one per controller, also for public variants (`public-content`, `public-legal`, `public-regions`,
  `public-intro-pickups`, `public-onboarding`).
- **Domain enums vs generated enums**: services and entities use the module's own enums (`region.domain.UnitType`,
  `user.domain.Gender`, `quiz.domain.QuizKind`, …); mappers convert by name (`X.fromValue(e.name())` /
  `Enum.valueOf(model.name())`). The generated enum of the same name is referenced only in `web`/`mapper`.
- **User data across modules**: the user module never depends on the region module. Region data the user module needs
  comes from the SPI `user.service.RegionDirectory` (implemented by `region.service.RegionUserDirectory`); the home
  region is a plain `home_region_id` column. User cards are `UserMapper.toSummary(user, withPhone)`; whether contact
  data may be shown is `UserService.mayCurrentUserSeeContact(user)`.
- **Profile changes** are recorded explicitly per field (`PROFILE_UPDATED`, `PROFILE_UPDATED_BY_ADMIN` with the
  required reason, `PHONE_CHANGED`, `HOME_REGION_CHANGED`); phone numbers and date of birth are redacted (`***`).
  Phone numbers are validated with `user.service.PhoneNumberValidator` and stored in E.164 (CH default).
- **Working groups are regions** (`type = WORKING_GROUP`, parent = their region); GROUP history uses `GroupAction`
  (`Region.historySubjectType()` switches by type). Closure table `region_closure` is plain SQL
  (`RegionClosureRepository`, no entity); create regions through `RegionTreeService.attach`, move with `move`.
- **Seeds**: changes to an existing table's seed go into a new numbered seed changeset with its own CSV (e.g.
  `021-user-seed-profile.yaml` updates `user_account` rows via `loadUpdateData` with the new columns only), never into
  an existing seed changeset. `scripts/gen-region-closure.mjs` regenerates `region_closure.csv` after editing
  `region.csv` (parents before children).
- **Slice tests**: `@JpaSliceTest` + `@Import(services…, RegionClosureRepository.class)`; `region/RegionFixtures`
  builds trees, users, memberships, admins, functions and grants.

## Milestone 3 additions (A19–A22, docs/plan-m3.md Part A)

**A19. Store permission model** (`store.service.StorePermissionService`, the only place store rights are decided; port of upstream `StorePermissions` + `docs/en/permissions/store.md`).
- Derived `UserTeamStatus {NO_MEMBER, APPLIED, INVITED, WAITING_LIST (jumper), MEMBER, COORDINATOR (manager)}` from `store_membership(status, manager)`.
- `mayCoordinateRegionStores(actor, store)`: ORGA; else if the store's region has a `region_function` STORES_COORDINATION → admins of that group **only** ("store coordination supersedes ambassadors"); else `PermissionService.isAmbassador(actor, region)` (ancestors count, A10).
- `mayAccessStore`: ORGA, WAITING_LIST/MEMBER/COORDINATOR, KAM of the store's chain (role ≥ STORE_MANAGER), or coordinate. `mayReadWall`/`maySeePickups`: ORGA, MEMBER/COORDINATOR, or coordinate; `maySeePickups` additionally requires `verified` (KAM may see pickups). Jumpers, applicants, invited and unverified users see the store information page but not wall/pickups.
- `mayEditStore` (= edit team, add/remove pickups, confirm slots, accept requests, see store log, see member distance): ORGA, or role ≥ STORE_MANAGER and (COORDINATOR or coordinate). `maySeePickupHistory`: mayEditStore or KAM. `maySeePhoneNumbers`: verified and mayReadWall. `mayCreateStore(region)`: ORGA or (role ≥ STORE_MANAGER and member of region). `mayDeleteStore`: coordinate and store never had sign-ups (409 `STORE_HAS_PICKUPS`). `mayRemovePickupUser(store, user)`: self, mayEditStore, or `mayAdministrateUser`.
- Wall delete: ORGA everything; own post always (author); posts older than 30 days: COORDINATOR, or ambassador/coordination admin **of the store's region** (not when they are merely team members of a store in another region). Everything else 403.
- Manager rules: max 3 managers (`StoreManagerAmount`), min 1 — both ignored by coordinators (`mayIgnoreStoreManagerCountRestrictions` = mayCoordinateRegionStores); appointee must be MEMBER/WAITING_LIST and role ≥ STORE_MANAGER; a manager cannot leave (409 `MANAGER_CANNOT_LEAVE`).
- `StorePermissionMatrixTest` encodes the six upstream tables (Wall, Team list, Pickup list, Options, Store information modal, Information) as rows `actor × status × column`; it is the contract and is extended with every rule change. Chat columns (`mayChatTeam/mayChatJumper`) are computed now and used by M5.

**A20. Pickups model.** A pickup is identified by `(storeId, pickupAt)` (UTC instant; displayed in `RegionService.zoneOf(store.regionId)`). `regular_pickup(weekday, local_time, slot_count, description)` generates occurrences over the horizon `now .. now + store.prefetch_days`; `pickup` rows (one-time) override an occurrence or add one (`slot_count = 0` cancels it); `pickup_signup(store_id, user_id, pickup_at, confirmed, reminded_at)` holds sign-ups (unique per user+store+time) and may lie beyond the horizon (shown anyway, upstream `getPickupSlots`). Signing up converts the occurrence into a `pickup` row (upstream #860: schedule edits never delete taken slots). Max 50 slots per pickup, description ≤ 100 chars. `prefetch_days` 1..42 (upstream `prefetchtime` seconds, default 14 days).

**A21. Store log = history.** Every store action is a `history_entry` with `subject_type = STORE`, action from `store.domain.StoreLogAction` (the 27 upstream codes with the same names + platform additions), `secondary_subject_id` = affected user, `metadata.pickupAt` for slot actions, `reason` for messages. The store log view is `HistoryQueryService` filtered by subject/date range/actions; `StoreHistoryAccessRule` = `maySeeStoreLog` (= mayEditStore). Pickup history (who picked up when) is read from `pickup_signup`, not from history. Required messages (decline, kick, standby, demote, slot removed) are delivered as bell + mail (`NotificationService`) until M5 messaging and stored in `reason`.

**A22. Cross-module SPIs owned by the store/pickup modules.** Region never depends on store. B1 adds default-noop SPIs in `region.service`: `RegionStoreStats { int establishedStoreCount(UUID regionId) }` (public region `storeCount`), `MemberPickupStats { Optional<Instant> lastPickupAt(UUID userId); int pickupCount(UUID userId) }` (`RegionMember.lastPickupAt`). Store implements them plus the existing `RegionStatisticsContributor` (activeCooperations, pickupsLastMonth, savedFoodWeightLastMonth), `GroupRequirementContributor.pickups`, `verification.service.UnverifyGuard` (409 `HAS_FUTURE_PICKUPS`), `SeedDateShiftContributor`, and `onboarding`'s explainer counters (stores, pickups, kg) once T22's `ExplainerCounterContributor` exists.

## Milestone 4 (automations and notifications)

**A23. Notification channels.** `NotificationService.notify` stays the single entry point. After the bell is stored it calls `notification.service.ChannelDispatcher`, which fans out to `NotificationChannel` beans (`MailChannel` = existing outbox, `WebPushChannel`, `TelegramChannel`) filtered by the user's settings per `NotificationCategory` and by channel availability (push subscription present, Telegram linked). `NotificationType.category()` → `NotificationCategory {STORE_TEAM, PICKUPS, PICKUP_REMINDER, AUTOMATION, INTRO_PICKUPS, REGION, ONBOARDING, FORUM, MESSAGES, SYSTEM}`; `SYSTEM` cannot be switched off and bells are always on. Absent `user_notification_setting` row = defaults (mail on except FORUM/MESSAGES; push/Telegram on when available). Mail is sent only when the request names a template **and** the category's mail flag is on; `pickup_reminders_enabled` (PU9) stays the master switch for `PICKUP_REMINDER`. Channel failures are logged, reported to the integration registry (A26) and never fail the caller. Push: `app.push.mode=webpush|log`, VAPID keys from `PUSH_VAPID_*`; `local`/`e2e`/`test` use `log` with a committed dev key pair. Captured pushes/Telegram messages appear in `GET /api/dev/notifications?channel=PUSH|TELEGRAM` (`DELETE` forgets them). Push/Telegram titles are the bell titles in the recipient language from `server/src/main/resources/i18n/bells/messages_<lang>.properties`, generated from the client catalog by `node scripts/sync-bell-messages.mjs` after every `bell.*` change (`BellMessagesSyncTest` fails otherwise). Domain events for consumers: `pickup.domain.PickupEvents.{PickupSignedUpEvent, PickupSignedOutEvent, PickupConfirmedEvent}`, `store.domain.StoreApplicationReceivedEvent`.

**A24. Automation module.** `automation` depends on `store`, `pickup`, `region`, `user`, `notification`, `history`; `pickup`/`store` never depend on `automation` (`ModulesTest`). Triggers are domain events published by `pickup`/`store` (`PickupSignedUpEvent`, `PickupSignedOutEvent`, `PickupConfirmedEvent`, `StoreApplicationReceivedEvent`), consumed by `@TransactionalEventListener(AFTER_COMMIT)` + `REQUIRES_NEW` listeners (decision 33). The automation acts only through public service methods with an explicit automation actor (`PickupSignupService.confirmByAutomation/removeByAutomation`, `StoreTeamService.acceptByAutomation/declineByAutomation`); history actor `AUTOMATION` with name `pickup-policy`/`request-policy`. Every decision is an audit row (`*_audit`) with decision, reasons (rule codes + rendered text), user message and dry-run flag; `DEFERRED` writes no audit. History: `automation.domain.AutomationAction` (subject `AUTOMATION` = policy/setting id, metadata `storeId`). "Calendar day" rules use the store's region time zone.

**A25. Jobs.** ShedLock (`shedlock-spring` + `shedlock-provider-jdbc-template` 7.x — the Spring 7 line, table `shedlock`, `config.ShedLockConfig` with `@EnableSchedulerLock(defaultLockAtMostFor = app.jobs.default-lock-at-most-for)`, active in every profile). Every `*Job` implements `RunnableJob`; its `@Scheduled` method is `run()`, carries `@SchedulerLock(name = <jobName()>, lockAtLeastFor = "PT5S")` and only calls `JobRunner.run(this, SCHEDULE, null)` (ArchUnit rule 9). `common.jobs.JobRunner` wraps every run (scheduled, dev or ORGA "run now"): `job_run(job_name, started_at, finished_at, outcome RUNNING|OK|FAILED, message, run_trigger SCHEDULE|MANUAL, actor_id)` written in its own transactions, a log line, and the last run per job. Jobs returning `recordEveryRun() = false` (`mail-outbox`, every 10 s) only write failures and manual runs. `runNow()` takes no lock and is re-entrant. Jobs: `hygiene-expiry`, `history-retention`, `mail-outbox`, `onboarding-nudges`, `pickup-reminder`, `region-maintenance`, `region-statistics` (+ M4 jobs).

**A26. Integrations and circuit breakers.** `common.integration.IntegrationRegistry` holds one `CircuitBreaker` per `Integration {SMTP, WEB_PUSH, TELEGRAM, GEOCODING, HOLIDAY_CALENDAR, WALLET_APPLE, WALLET_GOOGLE, IMAP}` (port of the reference `FoodsharingAvailability`): `reportFailure(where, ex)` opens only when the failure looks like an outage (timeouts, unknown host, refused connection, 5xx) **and** the registered probe fails; while open `shouldSkip()` is true until `nextProbeAt` (`app.integrations.probe-interval`), then one probe decides; `markSucceeded()` closes. Outages are remembered for 3 days (`outageOverlap`, `extendDeadline`). Clients call through `registry.call(integration, where, supplier)` (fails fast with 503 `INTEGRATION_UNAVAILABLE` while open) and register a probe with `registerProbe`. Opening publishes `IntegrationDownEvent` (ORGA bell `INTEGRATION_DOWN`). The mode (`LIVE`/`LOG`) per integration follows its property (`app.mail.transport`, `app.push.mode`, `app.telegram.mode`, `app.geocoding.provider`, `app.automation.holiday-provider`). The mail outbox job skips its run while SMTP is open (mails stay PENDING without using attempts).

**A27. Mail polish.** `mail_outbox` gains `user_id`, `category` (NotificationCategory or `NEWSLETTER`/`AUTH`), `priority` (AUTH first), `rate_limited_until`. `MailRateLimiter`: per recipient `app.mail.rate-limit.per-user-per-hour` (20) and per (recipient, template) `per-template-per-user-per-day` (5); beyond the limit mails are deferred, never dropped, except duplicates of the same template + identifier within `duplicate-window-minutes` (10), which are skipped (`NotificationAction.MAIL_SUPPRESSED`). Notification mails use `templates/mail/layout_<lang>.html` and fragments `templates/mail/notification/<TYPE>_<lang>.{html,txt}`; rendering falls back requested → de → en. Newsletters carry `List-Unsubscribe` + one-click unsubscribe.

**A28. Telegram linking.** `user_account.telegram_chat_id`, `telegram_link_token(token, user_id, expires_at = 15 min)`. "Connect Telegram" → `POST /api/telegram/link-token` → deep link `https://t.me/<bot>?start=<token>`. Updates (`telegram-poll` job in live mode, or `POST /api/public/telegram/webhook/{secret}`; `local`/`e2e`: `POST /api/dev/telegram/incoming`) with `/start <token>` link the chat and answer with a confirmation; `/stop` unlinks. Region/store alert chats: `telegram_chat(owner_type REGION|STORE, owner_id, chat_id, title, enabled)` chosen from `GET /api/telegram/chats`; "send test message" exists for both. Implementation (B3): `notification.telegram.service` — `TelegramClient` (`LiveTelegramClient` via the TELEGRAM breaker, probe `getMe`; `LogTelegramClient` captures with a fake message id), `TelegramLinkService`, `TelegramUpdateProcessor` (idempotent by `update_id`, offset row `telegram_offset.id = 1`), `TelegramPollJob` (`telegram-poll`, live mode without webhook only), `TelegramChannel`, `TelegramChatService`. The webhook is in the contract as tag `public-telegram` (`receiveTelegramWebhook`, CSRF-exempt, 404 unless `app.telegram.webhook-secret` matches). "Seen" group chats for `GET /api/telegram/chats` are kept in memory (30 days, 500 chats; configured chats keep their stored id/title). Store permission comes from the store module through the SPI `notification.service.StoreAlertAccess` (notification never depends on store). Dev: `POST /api/dev/integrations/{integration}/fail` opens a breaker (`IntegrationRegistry.simulateOutage`).

**A29. Cleanup and ORGA alerts (B3).** `common.jobs.CleanupJob` (`cleanup`, `app.jobs.cleanup-cron`) runs every `common.jobs.CleanupTask` bean (owned by the module whose data it deletes: `NotificationCleanup`, `LoginTokenCleanup`), then purges `job_run` > 90 days and expired `ProgressTracker` results; a failing task fails the run after the others ran. `notification.service.OrgaAlerts` turns `IntegrationDownEvent` into the ORGA bell + mail `INTEGRATION_DOWN` (params `integration`, `message`) and `JobRunner.JobFailedEvent` (second consecutive failure of a job, once per streak) into `AUTOMATION_RUN_FAILED` (params `job`, `message`). Notification mail fragments: `templates/mail/notification/<TYPE>_<lang>.{html,txt}`, subjects `mail.notification/<TYPE>.subject` in `i18n/mail`; callers use `.mail("notification/<TYPE>")`. `MailDraft` carries `userId`, `category` (`AUTH` for magic links: priority 10, never limited) and `identifier` (duplicate suppression).

**A30. Pickup and request policies (B4).** `automation.service.PickupPolicyEvaluator` is the port of the reference `evaluate()` (order: excluded date → holiday → ≤ 2 calendar days before: confirm → gap → experience → cleaning → linked stores; calendar days in the store's region zone) and reads everything through `PickupPolicyEvaluator.Data` (`PickupPolicyData` in production, an in-memory fake in tests). Texts are `i18n/automation/messages_<lang>.properties` `automation.rule.*` in the affected user's language; linked-store messages keep `{{placeholders}}`. `PickupPolicyRunner` decides each sign-up in its own `REQUIRES_NEW` `TransactionTemplate` (after-commit `PickupSignedUpEvent` for unconfirmed sign-ups, the `automation-evaluate` job, "run now"); it acts only through `PickupSignupService.confirmByAutomation/removeByAutomation` (actor `pickup-policy`) and writes one `pickup_policy_audit` + `AutomationAction.PICKUP_DECISION` per decision (not for DEFERRED; on the job path an unchanged SKIPPED/WOULD_* is not audited again; exceptions → FAILED audit). Bidirectional link rules are stored as a mirror row on each linked store's policy (`mirror_of`, window swapped, created disabled + dry run when the store had no policy); the owner's PUT replaces its own rules and their mirrors, mirrors in a PUT are ignored. `RequestPolicyRunner` (event `StoreApplicationReceivedEvent` + `request-automation` job, ≤ `max-request-approvals-per-run` actions) uses the haversine distance home ↔ store. Holidays: `HolidayCalendarService` (`static` = table `holiday_calendar`; `nager` = `{nager-url}/api/v3/PublicHolidays/{year}/{country}` through the HOLIDAY_CALENDAR breaker, probe calls the API directly); canton = FEDERAL_STATE ancestor slug `kanton-<x>` mapped to the ISO code. AUTOMATION history is readable with `mayUseAutomations` on the policy's store (`AutomationHistoryAccessRule`).

**A31. Adverts, sign-out alerts, extra slot, metrics, newsletters (B5).** `OpenSlotAdvertRunner` is the port of the reference `runAdvertisementAutomations` (one `REQUIRES_NEW` transaction per advert; late-cancellation settings come from the store's lowest advert number, enabled or not; "filled after advert" is set on a SENT audit when the pickup is full and cleared when it is freed again). Channels: `STORE_CHAT` = bell `PICKUP_OPEN_SLOT` to members + jumpers with identifier `PICKUP_OPEN_SLOT-<store>-<pickupAt>` (previous bell removed; team chat in M5), `PUSH` = `NotificationService.pushOnly` (no bell, PUSH settings apply), `TELEGRAM` = `TelegramChatService.sendForStore` (store chat with that id, else region chat); earlier Telegram adverts of the pickup and of passed pickups are deleted. Placeholders: `AdvertTemplates`. `SignoutAlertService` listens to `PickupSignedOutEvent` (only `byUserId == userId`) and `PickupSignedUpEvent` (rejoined); BELL = bell (+ the user's push/mail settings; the final NOT_REPLACED message also mails), PUSH alone = push only. Extra slot: `store.extra_slot_allowed` + `pickup_extra_slot` live in store/pickup (`PickupSignupService.addExtraSlot`, earliest `created_at` sign-up), the automation records `EXTRA_SLOT_ADDED`; `Pickup.extraSlot`/`mayAddExtraSlot` come from `PickupScheduleService.listPickups`. `MetricArchiveService` (`audit-retention` job) adds counts of audits older than `audit-retention-days` (cancellations and extra slots: `signout-retention-days`) to `metric_daily_count` and deletes them in the same transaction; `series` = archive + live rows. Newsletters live in `notification.newsletter` and read regions through the `NewsletterRegions` SPI (implemented by `region`); recipients are frozen on `queue`, `newsletter-send` turns `min(batch-size, mails-per-minute)` of them per run into outbox mails (category NEWSLETTER, `unsubscribeUrl` = `/newsletter/unsubscribe/<HMAC token>`, `NewsletterTokenService`, secret `app.newsletter.secret`). Follow-ups: `mail_outbox.headers` (112, `Name: value` lines → MIME headers; newsletters get `List-Unsubscribe`), newsletter body = CommonMark → jsoup `basic` safelist (+h1–h4, hr) as `bodyHtml`, markdown source in the text part; `NEWSLETTER_SECRET` is required in prod; `INTRO_PICKUP.DONE` is counted live from `IntroPickupMetrics` (never archived); `store-maintenance` (`StoreMaintenanceJob`, bells only: unconfirmed within 48 h, empty tomorrow, managers inactive 90 days → other managers once per 30 days).

## Milestone 5 (communication, docs/plan-m5.md Part A)

**A32. Realtime (STOMP).** One endpoint `/ws` (plain WebSocket, no SockJS), `config.WebSocketConfig` (`@EnableWebSocketMessageBroker`, simple broker, `setAllowedOriginPatterns(app.base-url)`, `/app` prefix, `/user` destination prefix). Handshake auth: `security.SessionHandshakeHandler` reads the `fs_session` cookie through the existing `SessionAuthenticator` and sets `SessionPrincipal` as the WebSocket `Principal`; no cookie/invalid → handshake 401 (no anonymous sockets). Inbound CSRF channel interceptor disabled (the handshake is cookie + Origin checked; decision). Clients only subscribe to **`/user/queue/events`** (server-side fan-out, so no topic authorization is needed; any other SUBSCRIBE is rejected by `common.realtime.SubscriptionGuard`). Envelope `RealtimeEvent{type, at, payload}` with `type ∈ {CONVERSATION_MESSAGE, CONVERSATION_READ, CONVERSATION_UPDATED, BELL, MAILBOX_MAIL, FORUM_POST, POLL_STATE, PING}`; `common.realtime.RealtimePublisher.toUsers(Set<UUID>, RealtimeEvent)` is the only sender (modules never touch `SimpMessagingTemplate`); it runs AFTER_COMMIT and never fails the caller. Heartbeats 10 s both ways. Client (`core/realtime/realtime.service.ts`, `@stomp/stompjs`): connects after session bootstrap, reconnect with exponential delay 1 s → 30 s, `connected` signal; every consumer refetches on (re)connect (messages `?afterSeq=`, bells unread count, mailbox unread count) — **missed events are never assumed delivered**. `local`/`e2e` expose `GET /api/dev/realtime/sessions` (connected user ids) for tests.

**A33. Messaging module.** `messaging` depends on `user`, `region`, `store`, `notification`, `history`; nobody depends on `messaging`. Conversations are `conversation(kind DIRECT|GROUP|STORE_TEAM|STORE_JUMPER|REGION, locked, name, last_seq, last_message_at, last_message_preview)`; messages carry a per-conversation `seq` assigned under the conversation row lock (ordering + `afterSeq` refetch). `DIRECT` = exactly two members, found via `lookup` (upstream `getOrCreateConversation`: an existing conversation with exactly these members and no name is reused). Store and region chats are locked (no rename, no member edits) and synced from events: `store.domain.StoreTeamChangedEvent(storeId, userId, oldStatus, newStatus, manager)` (added by B2 to `StoreTeamService` and published after every status change) and `region.service.MembershipService.RegionMemberJoined` / new `RegionMemberLeft` / `RegionMembersService.MemberRemoved`; team chat = MEMBER + managers, jumper chat = managers + JUMPER (decision 52); region chat = active members of the region/group when `region.conversation_enabled` (default true for WORKING_GROUP, false otherwise, toggled by `mayAdministrateRegion`). Hourly `conversation-sync` job reconciles memberships (missed events). Other modules write to chats only through the SPI `store.service.TeamChatPort { postToTeamChat(storeId, TEAM|JUMPER, text); sendDirect(fromUserId, toUserId, text) }` (default noop in `store`, `@Primary` implementation in `messaging`, pattern A22): used by `StoreTeamService` required messages (decline/kick/standby/demote: chat message **in addition** to the existing bell + mail), by `OpenSlotAdvertRunner` for `STORE_CHAT` (replaces the grouped `PICKUP_OPEN_SLOT` bell, decision 61 superseded) and by automation decline messages. System messages have `sender_id NULL` (rendered as "foodsharing"). Notifications: no bell per message; `NEW_MESSAGE` goes **push per message** (`pushOnly`, collapse tag = conversation id, recipients not currently connected via WS or not viewing — the client reports the open conversation with `markRead`) and **mail at most once per conversation per `app.messaging.mail-digest-minutes` (30)** while messages stay unread (`conversation_member.last_mailed_at`). Mentions in messages and forum posts use the token `@[<userId>]` (client renders the name); forum mentions bell `FORUM_MENTION` unless the mentioned user disabled the FORUM category.

**A34. Uploads (minimal `upload` module, C10 partial).** `upload(id, base, owner_user_id, file_name VARCHAR(255), mime_type VARCHAR(120), size BIGINT, sha256 VARCHAR(64), storage_key VARCHAR(200), usage_count INT DEFAULT 0, expires_at NULL)`; bytes on disk under `app.uploads.dir` (`./data/uploads` local, temp dir in tests) as `<yy>/<mm>/<id>`. `POST /api/uploads` (multipart, ≤ `app.uploads.max-size-mb` 20, allow-list mime types incl. images, pdf, office, text, zip, ics), owner-only until used; `UploadService.claim(uploadId, UploadUsage{module, ownerId})` increments usage and makes the file readable through the owning module's `UploadAccessRule` bean (mailbox: mailbox members; messaging/forum: M6 when attachments arrive). Unclaimed uploads expire after 24 h (`UploadCleanup` `CleanupTask`). Image resizing, avatars and usage tracking UI remain M6.

**A35. Mailbox.** `mailbox(name, email_name UNIQUE, owner_type REGION|USER, owner_id, last_access_at)`; address = `<email_name>@app.mailbox.domain` (`local`: `local.test`, caught by Mailpit; prod: env `MAILBOX_DOMAIN`, empty → 503 `MAILBOX_DOMAIN_NOT_CONFIGURED` on compose). `email_name` rule: `^[a-z0-9][a-z0-9.-]{1,62}$`, not in `app.mailbox.reserved-names` (postmaster, abuse, noreply, admin, root, hostmaster, webmaster, mailer-daemon), unique across region and personal mailboxes. Access (upstream `MailboxPermissions`): `mayHaveMailbox` = role ≥ STORE_MANAGER; a user sees a region mailbox when ambassador of that region (`PermissionService.isAmbassador`, direct) or listed in `mailbox_member`, and their personal mailbox (`user_account.mailbox_id`, MB6, created by ORGA or `mayAdministrateUser`). Members are managed by `mayAdministrateRegion` / group admins. Mail storage: `mail_message(mailbox_id, folder INBOX|SENT|TRASH, previous_folder, message_id VARCHAR(255), in_reply_to, from_address, from_name, to_json, cc_json, reply_to, subject VARCHAR(998), body_text VARCHAR(32600), body_html VARCHAR(32600) sanitised, received_at, read_at, answered_at, bounce BOOLEAN, bounced_message_id, raw_size, spam_score NULL)` + `mail_attachment(message_id, upload_id, file_name, mime_type, size, content_id)`. HTML is sanitised **on the server** with jsoup `Safelist.relaxed()` minus `img` (remote images stripped; inline `cid:` images rewritten to attachment URLs), links forced `rel="noopener" target="_blank"`, `style` removed; the client additionally runs DOMPurify. Outgoing mail uses the M1 outbox: `OutgoingMail` gains `fromAddress/fromName` override and `attachments` (upload refs); the outbox worker streams attachments from the upload store. Inbound: `IncomingMailSource` SPI — `ImapMailSource` (Jakarta Mail, `app.mailbox.imap.{host,port,user,password,folder,ssl}`, through the `IMAP` breaker, probe = `NOOP`; messages deleted after a successful store, UID-idempotent via `mailbox_fetch_state`), `LogImapMailSource` (`app.mailbox.inbound=log`, local/e2e/test: fetches nothing), webhook `POST /api/public/mailbox/inbound/{secret}` (tag `public-mailbox`, raw MIME base64, 404 on wrong secret, CSRF-exempt) and dev `POST /api/dev/mailbox/incoming {rawMime}`. `MailboxFetcherJob` (`mailbox-fetcher`, 1 min) and the webhook both call `IncomingMailProcessor.process(byte[] raw)`: parse `MimeMessage`, resolve every recipient `<email_name>@domain` to mailboxes (unknown recipients: dropped + `MAILBOX_UNKNOWN_RECIPIENT` history on SYSTEM), store attachments as uploads claimed by the message, detect bounces (`multipart/report; report-type=delivery-status`, `Auto-Submitted: auto-replied`, or sender `MAILER-DAEMON`/`postmaster`): mark the referenced SENT message `bounce=true` (by `In-Reply-To`/`X-Original-Message-ID`/`Message-ID` in the DSN body) and store the DSN in INBOX with `bounce=true`; bell `MAILBOX_NEW_MAIL` (category `MAILBOX`, new) to mailbox readers (grouped per mailbox per hour, identifier `MAILBOX_NEW_MAIL-<mailboxId>`), realtime `MAILBOX_MAIL`. Compose: ≤ 100 unique recipients (upstream), ≤ 10 attachments / 20 MB total, `app.mailbox.max-mails-per-hour-per-user` 50 → 429 `MAILBOX_RATE_LIMITED`; reply sets `answered_at` on the original and `In-Reply-To`; forward prefixes `Fwd:` and copies attachments.

*A35 implementation notes (B4/B5).* Compose enqueues **one outbox mail per recipient** (to, cc, bcc; same `Message-ID`, the SENT copy lists to/cc only) via `MailOutboxService.enqueueDirect(DirectMail)` (template `direct`, no `MailRateLimiter`; `mail_outbox.from_address/from_name/reply_to/attachments_json`), the outbox streams attachments from the upload store at delivery. Readers of a region mailbox = direct admins of the region/group + `mailbox_member`; ORGA manages but does not read foreign mailboxes; members need role ≥ STORE_MANAGER (400 otherwise). Rate limit counts SENT (and trashed sent) rows by `sent_by_user_id` in the last hour. Inbound copies are idempotent per mailbox by `mail_message(mailbox_id, raw_hash)`; one inbound mail to several mailboxes stores one copy each (shared uploads, deleted with the last attachment row). Bounces: a DSN or a `MAILER-DAEMON`/`postmaster` sender flags the referenced SENT mail (`bounce`, `BOUNCE_RECORDED`) and every `Final-Recipient` user address (`user_account.email_bounced_at`, history with the user as secondary subject); `Auto-Submitted: auto-replied` only flags the inbox copy. Unknown recipients at the mailbox domain → `UNKNOWN_RECIPIENT` on the SYSTEM subject. Bell `MAILBOX_NEW_MAIL` params `mailbox`, `from`, `subject`, at most one per mailbox per hour. Realtime `MAILBOX_MAIL` payload `{mailboxId, mailId, folder, change: NEW|READ|UPDATED, unreadCount}`. Local inbound: `app.mailbox.inbound=log` fetches nothing; POST a raw MIME message to `/api/dev/mailbox/incoming {rawMime}` (Mailpit only catches outgoing mail to `*@local.test`). Dev `GET /api/dev/mails` adds `from` and `attachmentNames`.

**A36. Forum, events, polls, surveys permission rules (ports).** Forum (`forum.service.ForumPermissionService`, upstream `ForumPermissions`): `mayAccessForum(region, ambassadorForum)` = ORGA, or member of the region (ambassador forum: admin of the region); `moderators(region)` = ORGA, admins of the region's MODERATION function group if one exists, **else** the region's ambassadors (ancestors count, A10); `mayStartUnmoderatedThread` = verified and (ambassador forum, or moderator, or `!region.moderated`); `mayPostToThread` = access and (thread OPEN or moderator); `mayHidePosts` = moderator and region option `ALLOW_HIDING_IN_FORUM`; unhide = moderator; delete post = ORGA or author; delete thread = moderator and thread inactive, or ORGA; rename = moderator or creator; sticky/close = moderator; search every forum = ORGA. Events (`EventPermissions`): see = `public` or region member; create = region member; edit = ORGA, region admin, author; RSVP until `ends_at + 24 h`; wall comment = may see. Polls (`VotingPermissions`): list/see = region member; create = verified member and (group: group admin; region: admin of the region's VOTING function group); edit = author and `now < starts_at`; vote = member, `starts_at ≤ now < ends_at`, not voted; results = `now ≥ ends_at` (author sees eligible count always); start ≥ now + 1 h and start < end. Surveys (reference `SurveyService`): create = `hasAnywhere(CAN_MANAGE_SURVEYS)`; edit/results/delete = owner or co-author; fill = any logged-in user, once; `STORE` questions are answered only through the link `?storeId=` (409 `STORE_ANSWER_LINK_ONLY`).

**A37. Moderation log.** Every moderation action is a `history_entry` with `subject_type = THREAD`, action from `forum.domain.ForumAction` (`THREAD_ACTIVATED, THREAD_CLOSED, THREAD_OPENED, THREAD_STICKY_CHANGED, THREAD_DELETED, POST_HIDDEN, POST_UNHIDDEN, POST_DELETED`), `region_id` = forum region, `reason` for hide/delete, `metadata.postId`. `GET /api/regions/{regionId}/moderation-log` = `HistoryQueryService` filtered to those actions + region (`ForumHistoryAccessRule` = moderators of the region); rendered with `fs-history-timeline` (D10.3 "Moderation log" row).

**A37a. Forum implementation notes (B6).** Rules are pure functions in `forum.service.ForumRules` (facts resolved by
`ForumPermissionService`). A MODERATION group **without admins** counts as absent (ambassadors moderate). Moderation
bells go to the MODERATION group admins, else the region's ambassadors, else the nearest ancestor's. Threads a user may
not see (inactive and neither author nor moderator) answer 404 `THREAD_NOT_FOUND`; posting to a closed / inactive
thread answers 409 `THREAD_CLOSED` / `THREAD_NOT_ACTIVE`; hiding without the region option answers 403
`HIDING_NOT_ALLOWED`. Hidden post bodies are returned to moderators and the author only (`body: null` otherwise).
Deleting the last post deletes the thread. `PATCH active=false` is rejected (400). Mentions in post bodies are the token
`@[Display Name](user:<uuid>)` (client renders it as a profile chip); mentioned users who may read the forum get
`FORUM_MENTION`. Bells (identifiers): `FORUM_THREAD_TO_ACTIVATE-<thread>` (+ mail `notification/FORUM_THREAD_TO_ACTIVATE`
per FORUM mail settings), `FORUM_NEW_THREAD-<thread>`, `FORUM_POST-<thread>` (replaced on every post, so one per
thread), `FORUM_MENTION-<post>`, `POST_HIDDEN-<post>`; params `title`, `author` (`reason` for POST_HIDDEN); links
`/forum/threads/<id>[#post-<postId>]`. Thread mail followers get `notification/FORUM_POST` (params `title`, `author`,
`body` ≤ 2000, `link`) directly through the outbox — following by mail is an explicit opt-in, independent of the FORUM
category mail switch. Realtime `FORUM_POST` to thread followers (+ the poster): `{threadId, regionId, postId?, change}`
with change `CREATED | EDITED | DELETED | HIDDEN | UNHIDDEN | REACTION | THREAD`. Moderation log entries come from
`HistoryQueryService.listForRegion` (access checked by `ModerationLogService`: moderators and ORGA);
`ForumHistoryAccessRule` lets everyone who may see a thread read its history.

**A37b. Event implementation notes (B7).** Rules are pure functions in `event.service.EventRules` (facts from
`EventPermissionService`; ORGA sees every event). Events a user may not see answer 403 `ACCESS_DENIED`, deleted ones
404 `EVENT_NOT_FOUND`. Region listings show non-members public events only; default = not yet ended, `includePast`
adds ended ones, `from`/`to` bound the start. The author is ACCEPTED on creation; `inviteAllMembers` invites the
region's active direct members. Invitees of private events must be region members (400 `VALIDATION_FAILED`). RSVP
creates the invitation when missing (public events); status `INVITED` is rejected (400). OFFLINE needs a location name,
ONLINE an `https://` link (any given link must be https); location lat/lon are geocoded from the postcode (CH) when
absent. PATCH: absent = unchanged, `onlineLink: ""` clears, `location` replaces. Bells (bell + push, no mail template):
`EVENT_INVITATION-<event>`, `EVENT_UPDATED-<event>` (to ACCEPTED/MAYBE except the editor; replaced per change),
`EVENT_POST-<event>` (author + ACCEPTED/MAYBE except the poster; replaced per post); params `name`, `startsAt` (+
`author` for EVENT_POST); link `/events/<id>`; deleting an event removes all three. ICS: `/api/events/{id}/ics` and
`/api/events/mine/ics` (ACCEPTED/MAYBE, last 90 days and future). No personal calendar feed exists yet (A11 in M6),
so events are not merged into one. `EventHistoryAccessRule`: region members and event editors.

**A37c. Poll implementation notes (B7).** Rules are pure functions in `poll.service.PollRules` (facts from
`PollPermissionService`). Status: CANCELLED (cancelled_at) > UPCOMING (now < start) > RUNNING (now < end) > ENDED.
Non-members get 403 `ACCESS_DENIED` (ORGA may read). Create: 403 unless `mayCreate`; 400 `POLL_DATES_INVALID` (start <
now + 1 h or start ≥ end), `POLL_OPTIONS_INVALID` (2–50, non-blank, unique ignoring case), `VALIDATION_FAILED` for a
scope not offered (working groups: FOODSAVERS / VERIFIED_FOODSAVERS only, upstream scope counts). Scope counts: poll
creators and ORGA. Eligibility (`PollEligibilityService`) is evaluated live at vote time; `eligible_count` is frozen at
creation and shown to the author/ORGA always, to others once ENDED. `voteCount` is always visible; option `values`
only once ENDED (cancelled polls never). Update: non-author 403, started/cancelled 409 `POLL_NOT_EDITABLE`; options
replaced as a whole. Cancel: author or ORGA (else 403), ended/cancelled 409 `POLL_NOT_CANCELLABLE`; sets
`ends_at = cancelled_at = now`. Vote order of checks: 409 `POLL_NOT_OPEN`, 409 `ALREADY_VOTED`, 403 `NOT_ELIGIBLE`,
400 `INVALID_VOTE` (keys = option indices as strings; SELECT_ONE exactly one option with 1, SELECT_MULTIPLE any subset
with 1, THUMBS every option 1/0/−1, SCORE every option −3…3); aggregates are incremented under a `PESSIMISTIC_WRITE`
lock on the poll row. `notifyVoters=false` marks start/end as announced at creation, so no poll bells are ever sent.
Bells: `POLL_NEW-<poll>` (creation, eligible), `POLL_STARTED-<poll>` and `POLL_ENDING_SOON-<poll>` (job
`poll-notifications`, every 15 min; ending soon = ends within 24 h and ran ≥ 30 h; both skip those who voted),
`POLL_CANCELLED-<poll>` (to the eligible except the canceller, only when voters had been notified); params `name`,
`endsAt`; link `/polls/<id>`; voting removes the voter's open poll bells, cancelling everyone's. Realtime `POLL_STATE`
`{pollId, voteCount}` to the author after each vote. `PollHistoryAccessRule`: region members; `VOTE_CAST` has no
metadata.

**A37d. Survey implementation notes (B8).** Port of the reference `SurveyService`. Managing (list, get, edit, results,
co-author candidates) needs `CAN_MANAGE_SURVEYS` somewhere (403 `ACCESS_DENIED`) and ownership or co-authorship; other
surveys answer 404 `SURVEY_NOT_FOUND`. Delete = owner only, hard delete cascading to questions, co-authors, responses.
Co-authors: active users holding `CAN_MANAGE_SURVEYS` (unknown 404 `USER_NOT_FOUND`, without the permission 400
`VALIDATION_FAILED`); changed only by the owner (`coauthorIds` absent = unchanged); newly added ones get
`SURVEY_SHARED` (identifier `SURVEY_SHARED-<survey>`, params `title`, `owner`, link `/surveys/<id>`). Definition: title
1–200, 1–100 questions, at most one STORE question (always required), choice questions ≥ 2 distinct options. Update
keeps question ids given; answers of removed questions or questions whose type changed are deleted. Fill: any
logged-in user, once (409 `SURVEY_ALREADY_SUBMITTED`); a survey with a STORE question needs the link's `storeId`
(409 `STORE_ANSWER_LINK_ONLY` without it or when the STORE answer names another store; 404 `STORE_NOT_FOUND`), the
answer is set from the link and `survey_response.store_id` is stored. Answers: required missing → 400
`SURVEY_QUESTION_REQUIRED`; TEXT ≤ 4000, BOOLEAN `true|false`, SINGLE exactly one option, MULTIPLE distinct subset,
unknown/duplicate question → 400 `SURVEY_ANSWER_INVALID`. `SurveyHistoryAccessRule`: owner and co-authors.

---

**M5 contract notes (B1).** Generated API model names avoid clashes with `java.lang`/entities: `ChatMessage`
(conversation message), `ForumThread`/`ForumThreadSummary`/`ForumPost`, `Upload`, `Mailbox`, `Mail`, `Event`, `Poll`,
`Survey`. No `uniqueItems` in schemas (the generator then emits Jackson 2 `@JsonDeserialize`; dedupe in services).
History i18n keys are `history.action.<SubjectType>.<CODE>` (`CONVERSATION`, `UPLOAD`, `MAILBOX`, `THREAD`, `EVENT`,
`POLL`, `SURVEY`). Upload storage keys are `<yy>/<mm>/<random uuid>` (ids are generated on persist); an upload
remembers one usage (`usage_module`, `usage_owner_id`) that selects the `UploadAccessRule`. Too large multipart requests
answer 413 `UPLOAD_TOO_LARGE`. `RealtimePublisher` also offers `connectedUsers()` (push suppression in A33, dev sessions
endpoint in A32). `IcsWriter` lives in `common.ics`.

## Milestone 6 (community and governance, docs/plan-m6.md Part A)

The full text of A38–A45 is in `docs/plan-m6.md` Part A; the summary below is binding for implementation.

**A38. New modules and dependencies** (`ModulesTest.m6ModulesHaveOnlyTheirAllowedDependencies`): `calendar → user, region, event, pickup, intropickup`; `basket → user, region, store, notification, history, upload`; `fsp → user, region, notification, history, upload`; `map → region, store, fsp, basket, event, user, pickup`; `activity → forum, store, mailbox, event, fsp, user, region` (leaf: nobody depends on it); `report → user, region, store, forum, notification, history`; `achievement → user, region, store, quiz, notification, history`; `statistics → user, region, store, pickup, fsp, basket`; `search → user, region, store, forum, fsp, event, poll, mailbox`; `resource → user, region, notification, history, upload`; `support → user, notification, upload`; `oauthserver → user, region, auth, history`. Every module may also use `history` and `admin` (feature toggles, configuration). `calendar`, `map`, `activity` and `search` are the only readers of the communication modules (forum, event, poll, mailbox); nothing new depends on `messaging` (direct chat messages go through `store.service.TeamChatPort.sendDirect`). `admin` gains feature toggles, configuration, blocklist/IP admin and uploads admin. `donation` is not a module (content pages + configuration keys). B1 ships every new controller as a 501 stub (`ApiException.notImplemented`).

**A39. Sleep mode.** `user_account.sleep_status NONE|TEMPORARY|FULL, sleep_from, sleep_until, sleep_message`; `UserService.isSleeping(user, today)` = FULL, or TEMPORARY with `from ≤ today ≤ until`. `UserSummary.sleepMode` (existing field name, kept for compatibility) is never null from B2 on; `UserSummary.sleepMessage` only for viewers who may see contact data. `region-maintenance` resets expired TEMPORARY to NONE. History `UserAction.SLEEP_MODE_CHANGED`.

**A40. User lifecycle SPIs** (`user.service`, no beans by default): `AccountDeletionContributor.onAccountDeleted(userId)` (runs in the deletion transaction) and `DataExportContributor{section(), export(userId)}`. Every B task that creates user-related tables adds both. Deletion anonymises the `user_account` row (email `deleted-<uuid>@deleted.invalid`, "Gelöschtes Mitglied", personal fields cleared, FOODSHARER, inactive, `deleted_at/by`, `delete_reason`), writes `user_archive(snapshot_json)` and keeps authored content attributed to the anonymised row; a deleted account's login/session → 401 `ACCOUNT_DELETED`.

**A40a. Contributors per module.** Authored content stays and points at the anonymised user row; purely personal rows go. messaging: memberships of group/store/region chats removed, DIRECT memberships kept (partner keeps the chat); forum: follows + reactions removed; poll: votes removed (counts live on option values); survey: co-authorships removed, responses kept (results); quiz: sessions removed; onboarding: progress, steps, nudges removed; verification: passports, requests, wallet passes removed; intropickup: open bookings released (slot AVAILABLE), comments about the user, guide profile, templates, subscriptions removed; automation: exemptions removed, policy/advert audits keep the row with user_id NULL, sign-out cancellations kept; basket: baskets DELETED, requests removed. AccountDeletionIT asserts the module list. Region maintenance sends user/inactivity-warning once per inactivity period (app.region.inactive-days, 0 = off, user_account.inactivity_warned_at, INACTIVITY_WARNED); UploadCleanup also removes variant files older than an hour whose upload row is gone.

**A41. Images.** `upload += width, height, variants, public`. Images get `sm` 96 / `md` 320 / `lg` 1280 px variants (`app.uploads.image-sizes`), stored as `<storage_key>_<size>`, served by `GET /api/uploads/{id}?size=`. Uploads claimed by a public usage (`USER_PHOTO, FSP_PHOTO, BASKET_PHOTO, BLOG_PICTURE, CONTENT_IMAGE, RESOURCE_IMAGE`) are `public` and readable anonymously (`GET /api/uploads/*` is permitted in `SecurityConfig`; the service decides). Cropping happens in the client before the upload.

**A42. Walls.** Per-owner wall tables (`user_wall_post`, `fsp_wall_post`, `report_wall_post`, existing `event_wall_post`/`store_wall_post`); the API model is `common.yaml#/WallEntry{id, author, body, createdAt, mayDelete}` (+ `WallEntryPage`, `WallEntryCreate{body 1..4000}`; named `WallEntry` because `stores.yaml#/WallPost` already exists). Delete = ORGA, author or the owner's editors. Client: shared `fs-wall`.

**A43. Feature toggles and configuration.** `admin.domain.FeatureToggle` (with `defaultEnabled()`) + table `feature_toggle`; `FeatureToggleService.requireEnabled` → **404 `FEATURE_DISABLED`** (used by all M6 contracts); client reads `GET /api/public/feature-toggles`. `admin.domain.ConfigurationKey` (key + default) + table `configuration`; ORGA-only under `/api/admin/**`; changes record `AdminAction.FEATURE_TOGGLE_CHANGED/CONFIGURATION_CHANGED` with old/new value.

**A44. OAuth2/OIDC provider** (hand-written on `jose4j`): `/oauth/authorize` (cookie session, redirects to the Angular `/oauth/consent?request=`), `/oauth/token` (form, CSRF-exempt, client_secret_basic/post, `authorization_code` + `refresh_token`, PKCE S256 required for public clients, codes `app.oauth.code-minutes`), `/oauth/userinfo` (Bearer), `/.well-known/openid-configuration`, `/.well-known/jwks.json` — all in `SecurityConfig.FRAMEWORK_ENDPOINTS` and excluded from SPA forwarding (`/oauth/consent` stays Angular). RS256 access tokens (`app.oauth.access-token-minutes`), hashed opaque refresh tokens (`refresh-token-days`), signing key `app.oauth.signing-key-pem` (env `OAUTH_SIGNING_KEY`). Scopes `openid, profile, email, regions`; region-restricted clients → `OAUTH_REGION_REQUIRED`. Remembered consents in `oauth_consent`, revocable under `/account/connections`. Never log or record token/secret values.

**A45. Search index.** `search_index(kind, entity_id, title, subtitle, text (lower-cased, unaccented), region_id, visibility PUBLIC|REGION_MEMBERS|FORUM|ADMINS, updated_at; uq(kind, entity_id))`, rebuilt nightly (`search-reindex`) and incrementally from domain events via `SearchIndexer.upsert/remove`; ILIKE per term (+ `pg_trgm` GIN index on PostgreSQL only); visibility filtered per actor at query time.

**M6 history codes.** `SupportAction`/`StatisticsAction` use subject SYSTEM with code prefixes `SUPPORT_`/`STATISTICS_` (like `AdminAction` → `ADMIN_`), so codes stay unique per subject type. `UserAction.NOTE_ADDED` complements `NOTE_DELETED` for the admin notes wall.

**A46. Profile module (B2).** `profile` (`network.foodsharing.profile`) owns the tables `rating` (bananas), `buddy` and `user_wall_post` (seed ids stay in user module 0001, codes 0003–0005) plus `ProfilesController`; it depends on `user, region, store, pickup, notification, history` and nobody depends on it (`ModulesTest`). Reason: bells need `notification`, profile facts need `store`/`pickup`, and `user` may depend on none of them. Profile rights are decided only in `ProfilePermissionService` → pure `ProfileRules` (matrix in `ProfilePermissionMatrixTest`). Store facts for profiles come from `store.service.ProfileStoreInfo`. User-owned SPIs (pattern A22): `user.service.CommitmentSource` (pickup implements; basket B5, event B7), `ProfileAchievements` (B9), `AccountDeletionContributor`/`DataExportContributor` (A40; `ProfileAccountData` implements them). Profile wall: everyone signed in reads, only the owner writes (upstream status wall), delete = ORGA, author, owner or `mayAdministrateUser` of the owner. Counters `stat_banana_count`/`stat_buddy_count` are kept in sync through `UserService.setCommunityCounts`; the other `stat_*` columns are filled by the seed until the statistics job (B12) maintains them (pickup count = max(column, live confirmed pickups)).

**A47. "Today" in Europe/Zurich.** The injected `Clock` is UTC and `withZone` is unsupported; compute the local day as `LocalDate.ofInstant(clock.instant(), ZoneId.of("Europe/Zurich"))` (sleep mode, commitments weeks).

**A48. Account pieces (B3, part 1).** `user.service.UserAccountService`: profile photo = own image upload claimed as `USER_PHOTO` (readable by everybody via `UserPhotoAccessRule`), `avatar_url = /api/uploads/<id>?size=md`, the previous photo upload is deleted, `PHOTO_CHANGED`; admin notes = `user_note` kind `ADMIN_NOTE`, list/add need `mayAdministrateUser`, delete = author or ORGA (`NOTE_ADDED/NOTE_DELETED`); bounce: clear = self or `mayAdministrateUser` (`BOUNCE_CLEARED`), list without region = ORGA (all), with region = region administrators (users with that home region); `MailOutboxService.enqueue` suppresses non-AUTH mails to a user whose current address bounced (`MAIL_SUPPRESSED`, reason `BOUNCED`); settings `infoMailMessage` (`INFO_MAIL_CHANGED`) and `mentionNotifications` (`SETTINGS_CHANGED`; `ForumNotifier` skips `FORUM_MENTION` for users who switched it off).



**A49. Account pieces (B3 part 2, B4).** *Email change*: `auth.service.EmailChangeService` implements the user-owned SPI `user.service.EmailChangePort` (auth owns identities, sessions, blocklist, outbox access); table `email_change` (`161`, hashes only, 24 h). A request replaces the pending one; address rule `local@domain` (dots/dashes, `@local` allowed), `EMAIL_BLOCKED` via `EmailBlocklistService`, `EMAIL_TAKEN` against `user_account` and EMAIL `auth_identity`; mails `user/email-change-confirm` (new address) and `user/email-change-notice` (old address, abort link) are category AUTH; confirm = logged-in owner, sets email + `email_verified_at`, clears the bounce flag, rewrites the EMAIL identity and revokes every other session (`SessionRevokeReason.EMAIL_CHANGED`); history REQUESTED/COMPLETED/ABORTED with `a***@domain` values. *Images*: column is `upload.is_public` (`public` is reserved); variants are rendered in `UploadService.store` (non-decodable images simply get none), served with `?size=sm|md|lg` as `image/jpeg`; public uploads are anonymous-readable and sent with `Cache-Control: public`; anonymous reads of other uploads → 401. *Info mail*: `newsletter.kind NEWSLETTER|INFO_MAIL` (domain enum `NewsletterKind`), INFO_MAIL global and ORGA only, recipients = active, not deleted, `info_mail_message`. *Calendar* (A11): token 32 hex (seed 64 hex), feed `GET /api/public/calendar/{token}.ics?events=every|invitations|maybe|accepted|none` (default `maybe`), `pickups`, `introPickups`, `history` (14 days back), `reminders=15,60` (≤ 5, 0..10080 min → `VALARM`); sources `PickupUserService.calendarPickups`, `IntroPickupBookingService.bookingsOf`, `EventIcsService.calendarEvents`. *Business card* (A14): `profile.service.BusinessCardService` implements `user.service.BusinessCardPort`; labels `businessCard.role.<ROLE>.<f|m|n>` in `i18n/user`; logo drawn as text (no SVG rasteriser on the server); personal mailbox address via `BusinessCardPort.PersonalMailboxAddress` (mailbox module). *Export* (A21): `DataExportService` runs synchronously as a `ProgressTracker` job (zip: `profile`, `settings`, `history` (own activity, ≤ 5000) + one file per `DataExportContributor.section()` + `uploads/`), one per user per 10 min (429). *Deletion* (A13/A40): `AccountDeletionService` (self, or ORGA via admin), contributors use `common.persistence.UserRows` bulk deletes; sections/contributors exist in auth, notification, region, store, pickup (future sign-ups via `deleteAllFuture`), mailbox, event, calendar, profile (`community`); admin notes and the photo upload are removed by the user module; deleted accounts are inactive with revoked sessions, so every later request is 401 (no separate `ACCOUNT_DELETED` code).
