# Security review (D17.2)

Review of the platform against the **OWASP Top 10 2021** and **OWASP ASVS 4.0.3 level 2**, 2026-10-29, by the
backend worker (plan-m7 B8). Every row names the evidence: the code that implements the control and the test that
keeps it in place. "Gap" rows are accepted risks with a reason; "Fixed" rows were found in this review.

## Findings fixed in this review

| # | Finding | Severity | Fix | Test |
|---|---|---|---|---|
| F1 | `/api/public/**` documented 429 but had no limit: anonymous floods and scraping of public regions, statistics, markers, legal texts reached the database unthrottled | Medium (A04, ASVS 11.1.4) | `PublicApiRateLimitFilter`: per-IP sliding window (300/min, `app.security.public-api-requests-per-minute-per-ip`) before Spring Security, 429 + `Retry-After`, metric `fs_rate_limit_rejections_total{limiter="public-api"}`; secret webhooks (mail inbound, Telegram) exempt; contract documents 429 on every public operation | `PublicApiRateLimitFilterTest`, `RateLimitInventoryTest` |
| F2 | CSP had no `form-action` (does not fall back to `default-src`): an injected form could post credentials-free data to any origin | Low (A05, ASVS 14.4.3) | `form-action 'self'` | `ContentSecurityPolicyTest.policyIsStrict`, `e2e/tests/a11y/csp.spec.ts @D17.2` |
| F3 | No test that the WebSocket handshake refuses foreign origins (cross-site WebSocket hijacking with the session cookie) | Low (A01) | behaviour was correct (`setAllowedOriginPatterns(baseUrl)`); test added | `WebSocketHandshakeIT.foreignOriginIsRejectedEvenWithASessionCookie` |
| F4 | Concurrent logins of one user failed with 409 (optimistic lock on `user_account.version`); found by the load test | Low (availability) | bulk update of `last_login_at` | `LoadPathsPostgresIT.concurrentLoginsOfOneUserAllSucceed` |
| F5 | Dependency scanning only by hand | Medium (A06) | `npm audit --omit=dev --audit-level=high` (root, client, e2e) on every CI run, OWASP dependency-check (`-Psecurity`, CVSS >= 7 fails) weekly in `security.yml`; the existing Dependabot config (Maven, npm x3, Actions, Docker) stays | CI jobs `npm-audit`, `security.yml` |

## OWASP Top 10 2021

| ID | Risk | Status | Controls | Evidence |
|---|---|---|---|---|
| A01 | Broken access control | OK | Every operation has `x-permission` and is checked in the service layer (`PermissionService`, `StorePermissionService`, `ForumPermissionService`, region/fsp rules); controllers never touch repositories (ArchUnit); default deny for `/api/**` except the `PUBLIC_API` list; CSRF double-submit cookie (`CookieCsrfTokenRepository`) on every unsafe request; WebSocket subscriptions guarded per destination; IDOR spot checks on conversations, bells, store pickups, foreign pickup lists, mailboxes, uploads | `OpenApiContractTest` (x-permission on every operation), `ArchitectureTest`, `PermissionServiceTest`, `ForumPermissionMatrixTest`, `FspPermissionMatrixTest`, `IdorSpotCheckIT`, `WebSocketHandshakeIT`, CSRF in every `*ApiIT` (`with(csrf())`) |
| A02 | Cryptographic failures | OK | No passwords (Google OIDC, magic link, passkeys); session, magic-link, OAuth and calendar tokens are 256-bit random and stored as SHA-256 (`TokenHasher`); IPs stored as salted hashes (`IpHasher`, `APP_IP_SALT`); HSTS on HTTPS; `Secure` cookies in prod; OAuth ID tokens RS256 with a configured key (`app.oauth.allow-generated-key=false` in prod) | `OAuthRulesTest.tokensAndSecretsAreRandomAndHashed`, `SecurityHeadersTest.hstsOnlyOnHttps`, `SecurityHeadersTest.prodUsesSecureCookiesAndForwardedHeaders`, `ProdSecretsInventoryTest` |
| A03 | Injection | OK | JPA/JPQL with named parameters only (two native queries, both parameterised); no string-built SQL; HTML from users is sanitised with jsoup safelists (`MailHtmlSanitizer`, `NewsletterMarkdown`); Angular escapes by default, no `bypassSecurityTrust*` for user content; strict CSP as second line | `MailHtmlSanitizerTest`, `NewsletterServiceTest`, `ContentSecurityPolicyTest`, `csp.spec.ts` |
| A04 | Insecure design | OK | Rate limits on every abuse-prone path (inventory below); business rules in unit-tested rule classes (every rule change has a test, AGENTS.md); history of every mutation (ArchUnit `MUTATING_SERVICES_RECORD_HISTORY`) | `RateLimitInventoryTest`, `ArchitectureTest` |
| A05 | Security misconfiguration | OK | CSP without inline scripts (hashes computed at startup), `object-src 'none'`, `base-uri 'self'`, `form-action 'self'`, `frame-ancestors 'self'`; `X-Content-Type-Options`, `Referrer-Policy strict-origin-when-cross-origin`, `Permissions-Policy`; actuator only on management port 9090 in prod; dev paths 404 outside local/e2e; container non-root, read-only root fs, `cap_drop: ALL` | `ContentSecurityPolicyTest`, `SecurityHeadersTest`, `ProdProfileTest.managementPortOnlyInProd`, `DevPathsNotFoundFilterTest`, `server/deploy/smoke.sh` |
| A06 | Vulnerable and outdated components | OK | npm audit gate in CI, OWASP dependency-check weekly, Dependabot weekly; suppressions need a reason and an expiry (`server/dependency-check-suppressions.xml`, empty today) | `.github/workflows/ci.yml` `npm-audit` (0 findings on 2026-10-29 for root, client, e2e), `.github/workflows/security.yml`, `.github/dependabot.yml` |
| A07 | Identification and authentication failures | OK | Login rate limits per IP and per e-mail (`LoginRateLimiter`), IP blocks, magic links single-use with short expiry, passkeys (WebAuthn, origin + RP id checked), sessions revocable (list, revoke, revoke all, on e-mail change and account deletion), session cookie HttpOnly + SameSite=Lax (+ Secure in prod), session fixation impossible (new random token per login) | `MagicLinkFlowIT`, `WebAuthnEndpointsIT`, `GoogleLoginIT`, `LoginRateLimiterTest`, `AuthSessionServiceTest` |
| A08 | Software and data integrity failures | OK | Releases are built from a clean tagged tree (`release.sh` refuses dirty trees), image version from `version.txt`; uploads type-checked by magic bytes, not by name or client MIME type; no deserialisation of untrusted Java objects (JSON only) | `UploadServiceTest.magicBytesPerType`, `UploadServiceTest.rejectsTooLargeAndDisallowedTypes`, runbook §2 |
| A09 | Security logging and monitoring failures | OK | History entries for every mutation and every login/logout/session revoke; JSON logs with trace ids; Prometheus metrics for login failures and rate-limit rejections with alerts (`alerts.yml`) | `StructuredLoggingTest`, `MetricsContractTest`, `docs/monitoring.md` |
| A10 | Server-side request forgery | OK | The only server-side fetch of user-supplied URLs is the iCal import: https only, port 443, host allow-list (with subdomains), no user info, no redirects, 2 MB cap; geocoding and push go to fixed, configured hosts | `IcalServicesTest`, `IcalImportService` javadoc |

## ASVS 4.0.3 level 2 (by chapter)

| Chapter | Status | Evidence |
|---|---|---|
| V1 Architecture | OK | Modular monolith with enforced layering (`ModulesTest`, `ArchitectureTest`); contract-first API (`OpenApiContractTest`, `scripts/check-openapi.mjs`); threat surface listed in this document |
| V2 Authentication | OK (no passwords) | 2.2.1 anti-automation: `LoginRateLimiter` (per IP, per e-mail), `RateLimitInventoryTest`; 2.5 recovery = magic link; 2.7/2.8 out-of-band and one-time tokens: 256-bit, hashed, single-use, expiring (`MagicLinkFlowIT`); 2.9 passkeys (`WebAuthnEndpointsIT`) |
| V3 Session management | OK | 3.2 new token per login, 256-bit; 3.3 logout and revoke-all invalidate server-side (`AuthSessionServiceTest`); 3.4 cookie HttpOnly, SameSite=Lax, Secure in prod (`SecurityHeadersTest`); 3.5 OAuth tokens hashed (`OAuthRulesTest`) |
| V4 Access control | OK | 4.1 deny by default (`SecurityConfig.PUBLIC_API` allow-list); 4.2 IDOR (`IdorSpotCheckIT`), CSRF (`CookieCsrfTokenRepository`); 4.3 admin functions ORGA-only (`OAuthFlowIT.clientAdministrationIsOrgaOnly`, permission matrices) |
| V5 Validation, sanitisation, encoding | OK | 5.1 Bean Validation from the contract (generated models), 400 `VALIDATION_FAILED`; 5.2 HTML sanitising (`MailHtmlSanitizerTest`), SSRF (`IcalServicesTest`); 5.3 output encoding by Angular + CSP |
| V6 Stored cryptography | OK | 6.2 SHA-256 / HMAC-SHA256 / RS256 from the JDK, no own crypto; 6.3 `SecureRandom` tokens (`TokenHasher`); 6.4 secrets only from `.env` (`docs/secrets.md`, `ProdSecretsInventoryTest`) |
| V7 Error handling and logging | OK | 7.1 no secrets or tokens in logs (tokens never logged, IPs hashed); 7.4 uniform `ApiError` without stack traces (`ApiExceptionHandler`), trace id in every error |
| V8 Data protection | OK | 8.1 no caching of authenticated API responses (Spring Security cache headers); 8.3 data export and account deletion (privacy review D17.3) |
| V9 Communication | OK | TLS terminated at the reverse proxy, HSTS with includeSubDomains; app bound to 127.0.0.1 (`compose.yaml`) |
| V10 Malicious code | OK | Dependency gates (A06); no dynamic code loading; CSP without `unsafe-eval` |
| V11 Business logic | OK | 11.1.4 anti-automation: rate-limit inventory below; business rules unit-tested per rule (pickup eligibility, policies) |
| V12 Files and resources | OK | 12.1 size limits (`app.uploads.*`), 12.2 magic-byte check (`UploadServiceTest.magicBytesPerType`), 12.4 files stored outside the web root under random keys, served with `X-Content-Type-Options: nosniff`; 12.6 SSRF allow-list (A10) |
| V13 API | OK | 13.1 same CSRF/auth rules for every endpoint, 13.2 JSON content type enforced by the generated interfaces, 13.4 no GraphQL |
| V14 Configuration | OK | 14.2 dependency gates; 14.3 no debug endpoints in prod (`DevPathsNotFoundFilterTest`, `ProdProfileTest.prodNeverEnablesDevFeaturesOrSeedData`); 14.4 security headers and CSP (`SecurityHeadersTest`, `ContentSecurityPolicyTest`, `csp.spec.ts`) |

## Rate limits (inventory)

Enforced by `RateLimitInventoryTest`: every operation documenting 429 is in this list or under `/api/public/**`,
every anonymous operation is limited or listed with a reason, every limiter is a pre-registered metric.

| Limiter (metric tag) | Scope | Default | Operations |
|---|---|---|---|
| `public-api` | per IP, 1 min | 300 | every `/api/public/**` operation except the secret webhooks (`/api/public/mailbox/inbound/{secret}`, `/api/public/telegram/webhook/{secret}`) |
| `login-ip`, `login-email` | per IP / per e-mail | `app.auth.max-login-requests-*` | `requestMagicLink`, `getSession` (login attempts) |
| `client-errors` | per IP, 1 min | `app.client-errors.max-per-minute-per-ip` | `reportClientError` |
| `support` | per user / IP, 1 h | `app.support.max-per-hour` | `createSupportTicket`, `createPublicSupportTicket` |
| `messages` | per user, 1 min | `app.messaging.max-messages-per-minute` | `sendMessage` |
| `basket-requests` | per user, 1 h | `app.baskets.max-requests-per-hour` | `requestBasket` |
| `group-mails` | per user, 1 h | `app.region.group-mails-per-hour` | `sendGroupMail` |
| mailbox (database count) | per mailbox, 1 h | `app.mailbox.*` | `sendMail` |
| data export (cooldown) | per user | `app.users.export-cooldown-*` | `exportMyData` |
| mail outbox (deferral, not rejection) | per recipient | 20/h, 5 per template/day | every outgoing mail (`MailRateLimiter`) |

Anonymous operations outside `/api/public/**` without an own limit: `logout` (ends only the caller's session) and
`verifyMagicLink` (256-bit single-use token; failures counted in `fs_login_failures_total`).

## Specific checks (plan-m7 B8)

| Check | Result | Evidence |
|---|---|---|
| CSP strictness | No `unsafe-inline`/`unsafe-eval` scripts, no wildcards or bare schemes, all fallback-less directives set; `style-src 'unsafe-inline'` accepted (Angular component styles; no script execution possible through it) | `ContentSecurityPolicyTest.policyIsStrict`; `e2e/tests/a11y/csp.spec.ts @D17.2` fails on any `securitypolicyviolation` or CSP console error on 33 routes (visitor, foodsaver, bieb, orga) |
| Upload sniffing | Content type from magic bytes, allow-list per kind, size cap | `UploadServiceTest` |
| Sanitising | jsoup safelists for mail HTML and newsletter Markdown | `MailHtmlSanitizerTest` |
| SSRF allow-lists | iCal import: https/443, host allow-list, no redirects, 2 MB | `IcalServicesTest` |
| WebSocket origin | Only the app's base URL; foreign origin refused even with a valid session | `WebSocketHandshakeIT` |
| OAuth provider | PKCE S256 mandatory for public clients (no `plain`), redirect URI exact match at authorize and token, https or localhost only | `OAuthFlowIT.publicClientNeedsPkceAndConsent`, `OAuthRulesTest.pkceS256MatchesRfc7636Example`, `OAuthRulesTest.redirectUrisMustBeHttpsOrLocalhost`, `TokenService` (`redirect_uri mismatch`) |
| Actuator | Management port 9090 in prod, not published; public port answers 404 for `/actuator/**` | `ProdProfileTest.managementPortOnlyInProd`, `smoke.sh` |
| IDOR spot checks | 403/404 and unchanged data for foreign conversations, bells, store pickups, pickup lists, mailboxes, uploads | `IdorSpotCheckIT` |

## Accepted gaps

| Gap | Reason | Revisit |
|---|---|---|
| `style-src 'unsafe-inline'` | Angular injects component styles at runtime; nonce-based styles need Angular's `ngCspNonce` plus per-response HTML rewriting | When the SSR/HTML rendering changes |
| In-memory rate limiters | One app instance (decision 81); limits reset on restart | Before running more than one instance (move to Postgres or Redis) |
| OWASP dependency-check not on PRs | NVD download takes minutes; npm audit and Dependabot cover PRs | — |
