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

