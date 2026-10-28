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
