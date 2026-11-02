# API rate limit with escalating blocks (resume item 5, D17.2 extension)

## Goal
Every `/api/**` request gets a ceiling: signed-in requests are limited per account, requests without a session per IP. Going over the limit blocks that account or IP for an escalating time (1, 5, then 30 min). During a block every answer is 429 with `Retry-After`. ORGA can see and lift active blocks on the existing IP blocklist page. Every block is recorded in the history. The client IP comes from `X-Forwarded-For`, but only when our reverse proxy sent it.

## Decisions (owner, 2026-11-01; record as the next free decision number in `docs/decisions.md`)
1. One new filter runs right after `SessionCookieAuthenticationFilter`. A request with a valid session counts only toward its account, from any IP. A request without a session counts toward its client IP. An IP block only affects requests without a session from that IP.
2. Defaults: 300 per minute per account and 300 per minute per IP without a session. Blocks of 1, 5 and 30 min; further offences stay at 30 min. The level resets after 24 h without a block, or when an admin lifts the block. Not counted: the secret webhooks (`PublicApiRateLimitFilter.WEBHOOKS`) and `/api/dev/**`.
3. Blocks and levels are kept only in memory (one app instance, decision 81). A restart clears them; the history is the permanent record.
4. Configuration comes from `app.security.api-rate-limit.*` properties. A change needs a restart. A limit of 0 turns that part off; the test and e2e profiles set 0.
5. History: one entry per block (never per rejected request), with the raw IP or the user id, the level and the end time, kept for 90 days. Lifting a block is recorded too.
6. `server.forward-headers-strategy=native`, so Tomcat's `RemoteIpValve` reads `X-Forwarded-For` only from `server.tomcat.remoteip.internal-proxies`, from the right. The Tomcat default list (127/8, 172.16/12, 10/8, 192.168/16, ::1, fc/fd) is written out explicitly in `application.properties` and can be overridden in `.env`. `X-Forwarded-Proto` keeps working, so HSTS is unaffected.
7. `PublicApiRateLimitFilter` (decision 86) stays as it is: it runs before security as the flood guard and has no escalation.
8. The e2e tag is `@D17.2`.

## Scope
- In: the new filter and block registry, escalation, 429 + `Retry-After`, admin list and lift (contract, server, client section), history, metrics and alert, the forward-headers switch, dev-only endpoints to create and reset blocks for e2e, docs.
- Out: making manual `ip_block` entries affect the API (today they only block login and registration), WebSocket `/ws`, `/oauth/**`, runtime admin settings for the values, persisting blocks, changes to `PublicApiRateLimitFilter`.

## Changes
**Contract** (`openapi/paths/admin-settings.yaml`, `schemas/admin-settings.yaml`, `paths/dev.yaml`)
- `GET /api/admin/rate-limit-blocks` (`listRateLimitBlocks`, ORGA). Returns `RateLimitBlockEntry[]`:
  - `id` (uuid), `scope` (enum `IP|ACCOUNT`)
  - `ip?` or `user?` (`UserSummary`)
  - `level` (1-based), `blockedAt`, `expiresAt`
- `DELETE /api/admin/rate-limit-blocks/{blockId}` (`liftRateLimitBlock`, ORGA, `x-history: AdminAction.RATE_LIMIT_BLOCK_LIFTED`). Answers 204 or 404 with the new error code `RATE_LIMIT_BLOCK_NOT_FOUND` in `schemas/common.yaml`.
- `x-dev-only` `POST /api/dev/rate-limit-blocks` (`{scope, ip?, userId?}`) creates a block, so e2e can show one.
- Regenerate with the project's usual generation command (see `docs/dev-environment.md`).

**Server**
- `config/AppProperties.Security`: add a nested `ApiRateLimit` with these fields:
  - `requestsPerMinutePerUser` = 300
  - `requestsPerMinutePerIp` = 300
  - `blockSteps` = `1m,5m,30m` (`List<Duration>`)
  - `quietReset` = `24h`
  - Slice tests mock `AppProperties`: fall back to the defaults the way `PublicApiRateLimitFilter` does.
- `common/web/RateLimitBlockRegistry` (new, `@Component`, in memory, uses the injected `Clock`):
  - It keys blocks and levels as `ip:<addr>` / `user:<uuid>` and reuses two `SlidingWindowRateLimiter` instances (`api-ip`, `api-user`).
  - `check(key)` returns the remaining block time, or `Optional.empty()` when the key is not blocked.
  - `register(key)` counts the request. If the key is over the limit, it creates a block at the next level (the level starts again at 1 when the last block ended more than `quietReset` ago) and returns it.
  - `list()`, `lift(id)` (also resets the level), `create(...)` (for the dev endpoint), `clear()`.
  - Expired entries are cleaned up the same way `SlidingWindowRateLimiter` caps its size.
- `common/web/ApiRateLimitFilter` (new):
  - Not a `@Component`; it is added in `SecurityConfig` with `addFilterAfter(…, SessionCookieAuthenticationFilter.class)`.
  - It skips non-`/api/` paths, the webhooks, `/api/dev/**`, and everything when both limits are 0.
  - It takes the key from the authenticated `SessionPrincipal` or from `HttpExchange.clientIp`.
  - If the key is blocked, it answers 429 `TOO_MANY_REQUESTS` with `Retry-After` set to the remaining seconds (rounded up), through `ApiErrorResponseWriter`.
  - If `register` creates a new block, it answers 429 and calls `RateLimitBlockService.recordBlocked`.
- History service (`common/` cannot depend on `admin/`, so the filter calls it through a small interface or an event; check `ModulesTest`):
  - `RateLimitBlockService` in `admin/service`, mutating, writes the history. `recordBlocked` uses `HistoryService.recordInNewTransaction` with actor SYSTEM.
  - `AdminAction`: add `RATE_LIMIT_BLOCKED` and `RATE_LIMIT_BLOCK_LIFTED`. Make `retention()` return `DAYS_90` for these two (override per constant) and keep `YEARS_2` for the rest.
  - Metadata: `scope`, `ip` or `userId`, `level`, `until`.
- `admin/service/BlocklistAdminService`: add `rateLimitBlocks()` and `liftRateLimitBlock(id)`, ORGA only, with history.
- `admin/web/AdminSettingsController`: implements the generated methods; add mapper methods in `AdminSettingsMapper`; user summaries come from the existing user lookup.
- `dev/service/DevService`: the seed reset also calls `registry.clear()`. Add the dev create endpoint in the existing dev controller.
- `common/metrics/OperationsMetrics`:
  - Add `api-ip` and `api-user` to `LIMITERS`.
  - Add a counter `fs_rate_limit_blocks_total{scope,level}` and register it up front.
- Properties:
  - `application.properties`: `server.forward-headers-strategy=native`, the explicit `server.tomcat.remoteip.internal-proxies`, and the `app.security.api-rate-limit.*` defaults.
  - `application-test.properties` and `application-e2e.properties`: both limits 0.
- `common/web/HttpExchange.clientIp`: fix the javadoc (native strategy).

**Client** (`features/admin-settings/pages/ip-blocks-page.component.ts`)
- Add a second card, "Automatic blocks (rate limit)", with its own `loadResource` and `<fs-data-state>` (skeleton, empty, error + retry, forbidden, offline).
- Table columns: type, IP or user (linked to the profile), level, until, a "Lift" button (`runMutation`, confirm).
- testids: `adminSettings.ipBlocks.autoState`, `.autoTable`, `.autoRow`, `.autoLift`.
- i18n keys in de, en, gws, fr, it. The history labels for the two new actions go in `features/history/i18n/*`.

**Ops and docs**
- `server/deploy/monitoring/alerts.yml`: `RateLimitBlocks`, a ticket when `increase(fs_rate_limit_blocks_total[1h]) > 20`. The `RateLimitSpike` alert already covers the new limiter tags.
- `docs/security-review.md`: add `api-ip` and `api-user` to the rate-limit inventory, and update the forward-headers line.
- `docs/conventions.md` A58: the new limits and the native strategy.
- Runbook §9.4: lifting automatic blocks and the proxy requirements.
- `plan.md` §17: the forward-headers line.
- `docs/ledger.md`: the D17.2 row.
- `todo.md` item 5: mark done.

## UI
- Yes: a new section on the existing admin IP blocks page, built from the existing table and data-state patterns. No designer mockup needed.
- The 429 message for users already exists (`errors.TOO_MANY_REQUESTS` with seconds).
- Screenshot specs: the automatic section with one block, and the empty state. Baselines come in the batch run (decision 69).

## Tests
- New `RateLimitBlockRegistryTest` (fixed `Clock`):
  - over the limit gives level 1 (1 min), then 5, 30, 30
  - after 24 h quiet the level is back at 1
  - lift ends the block and resets the level
  - blocked keys are rejected without being counted
- New `ApiRateLimitFilterTest`:
  - a session counts per user, so two users on one IP do not affect each other
  - without a session, requests count per IP
  - an IP block does not affect a signed-in request from that IP
  - 429 carries `Retry-After` = the remaining seconds
  - webhooks and `/api/dev` are skipped; limit 0 turns the filter off
  - the history is written once per block
- New `ForwardedHeadersIT` (`@SpringBootTest(webEnvironment = RANDOM_PORT)`, real Tomcat):
  - from 127.0.0.1, `X-Forwarded-For: 1.2.3.4, 5.6.7.8` gives client IP 5.6.7.8
  - `X-Forwarded-Proto: https` still triggers HSTS
  - an address that is not a trusted proxy keeps its own remote address (test the valve config through properties, or a unit test on the configured valve)
- `BlocklistAdminService` / `AdminSettingsIT`: list and lift are ORGA-only, the history entries are written, an unknown id gives 404.
- On purpose:
  - `RateLimitInventoryTest`: assert `api-ip` and `api-user` are in `LIMITERS`; the javadoc names the global filter.
  - `MetricsContractTest`: the new counter.
  - `SecurityHeadersTest`: only if it depends on the framework forwarded filter.
  - history action i18n and retention checks: the new actions.
- e2e `e2e/tests/admin/c12-blocklist.spec.ts` (or a new `d17-2-rate-limit-blocks.spec.ts`), tagged `@D17.2`: create a block through the dev endpoint, ORGA sees it, lifts it, the row is gone; screenshot specs for the section states.
- `npm run i18n:check`, ArchUnit (`MUTATING_SERVICES_RECORD_HISTORY`, controller rules, `ModulesTest`).

## Done when
- [ ] Contract changed and regenerated; the client uses only generated services
- [ ] Filter, registry, escalation and 429 + `Retry-After` work; test/e2e at 0
- [ ] Native forward headers with the explicit trusted proxies; the IT is green
- [ ] Admin section lists and lifts blocks; history entries kept 90 days
- [ ] Metrics, alert and docs (security review, A58, runbook, decision, ledger, resume) updated
- [ ] All new and changed unit, IT and e2e tests green; `i18n:check` passes

## Risks
- **The production proxy must send `X-Forwarded-For`** and must reach the app from a trusted range. If it sends nothing, every anonymous user shares the proxy's IP and its 300-per-minute budget. Check the real proxy configuration before release.
- **Native strategy ignores `X-Forwarded-Host` / `X-Forwarded-Prefix`.** Check that nothing builds URLs from the request; `app.base-url` is used instead.
- **300 per minute per account could be too low** for heavy pages or polling. Watch `fs_rate_limit_rejections_total{limiter="api-user"}` after launch; the limit is configurable.
- **A blocked admin cannot lift their own block** until it expires (at most 30 min).
- **Memory:** the count of blocked keys is capped; a burst of fresh IPs is bounded the same way as in `SlidingWindowRateLimiter`.
- **Ordering:** the filter must run inside the security chain only once (it is not a `@Component`, so Boot does not register it again).
