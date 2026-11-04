# Build without warnings

## Goal
`cd server && ./mvnw -DskipTests install` (which also runs `npm ci`, `npm run build`, lint and `i18n:check`) prints no real warnings. The one exception is the quill advisory, which has no fixed release. Tests are not weakened, and nothing visible in the UI changes.

## Decisions
Owner answers, 2026-11-04. Record them as the next free decision number (164 if still free; check `docs/decisions.md` when merging) and add a ledger row.
- D1 **openapi-generator 7.15.0 → 7.26.0** in `server/pom.xml` and `client/openapitools.json` (decision 119's single pin stays). Add `useSpringBoot4` and `useJspecify` (instead of `useSpringBoot3`). Do this as its own commit. **Stop rule:** if the regenerated code needs more than small mechanical hand-written fixes (rough guide: more than ~20 files, or a change in behaviour or wire format), revert this commit. Then report the ExamplesUtils warning and the deprecated-`Nullable` notes as accepted.
- D2 **npm audit:** fix the findings by updating packages (targeted bumps and `overrides`). Keep the audit output of `npm ci` (no `--no-audit`). quill 2.0.3 (GHSA-v3m3-f69x-jf25, low, XSS in HTML export) has no patched release. It stays in the summary and is mitigated because the server sanitises mail HTML (A35). Record this in the decision.
- D3 **`lodash.isequal` → `fast-deep-equal@3.1.3`** through an npm override alias. It has the same `(a, b)` signature, and quill-delta only compares plain JSON ops and attributes.
- D4 **No new guards:** unused i18n keys stay a warning, and the compiler flags don't change.
- D5 **Rename the agenda path parameter `{date}` → `{day}`.** The URL format doesn't change. Only if `scripts/oasdiff.sh` reports no ERR; otherwise keep `date` and the ModelDate warning.
- D6 (planner) The i18n warnings come mostly from checker gaps, not from dead keys. Only 4 keys are deleted (see Changes). No lost UI was found.

## Scope
In: the i18n checker and dead keys, OpenAPI contract cleanups, the generator bump, npm dependencies/overrides/`angular.json`, the leaflet ESM import, test-code deprecations, the Maven resources-plugin parameter.
Out: `-Xlint:all`-only warnings (`serial`, `this-escape`), naming `createUpload_request` (an info message only), e2e/root npm audits, new guards (D4), the `Download 7.15.0` line (it's info, not a warning).

## Changes
Branch `chore/build-warnings` off `main`. Start shells with `source scripts/env.sh`.

**1. i18n checker** (`client/scripts/check-i18n.mjs`). I prototyped and verified this on a copy: only the 4 dead keys are left, with no new errors.
- `stripComments`: only strip a block comment whose `/*` is not glued to a word character, quote, `/` or `*`, e.g. `.replace(/(^|[^\w'"`/*])\/\*[\s\S]*?\*\//g, '$1')`. `accept="image/*"` in `content-editor-page`, `support-page`, `resource-form-dialog` and `upload-dropzone` currently swallows the template text up to the next `*/`. That is the cause of the `content.editor.*`, `content.support.*` and `resources.form.create` warnings. Explain this in a comment.
- `dynamicPrefixPatterns`: add `/['"`]([a-zA-Z]\w*\.[\w.]*\.)['"`]\s*[,)]/g`, which catches key prefixes of 2 or more segments passed as an argument (`opts(values, 'stores.convince.')`, `this.options('usersAdmin.list.verifiedFilter.')`). Single-segment literals like `'stores.'` must not match.
- Server-provided keys: scan `../server/src/main/java/**/*.java` for string literals that **exactly** equal an existing catalog key and count them as used (`onboarding.action.*`, `onboarding.waiting.*` from `JourneyCalculator`, `dev.users.other` from `DevMapper`). Don't scan Java prefixes (`"automation.rule." +` is a server message bundle and would hide client keys). Add `'dev.users.'` to `DYNAMIC_PREFIXES` with a comment (`DevUserCatalog` builds `"dev.users." + name`). Update the header comment.
- Delete from all 5 languages: `common.loading` (`core/i18n`), `introPickups.admin.active` and `introPickups.admin.inactive` (replaced by `status.*` in c01ad552), `pickups.mine.subtitle` (its page was merged in decision 162).

**2. Contract** (contract first, then regenerate)
- `server/src/main/resources/openapi/schemas/automation.yaml`: remove the array alias `AdvertMessages` and write it out inline (`type: array, maxItems: 10, items: {type: string, maxLength: 1000}`) in `storeMessages`, `pushMessages` and `telegramMessages` of `OpenSlotAdvert`. This removes "Failed to get the schema name: null" (verified on 7.15). Search for any other `#/AdvertMessages` refs first.
- `paths/pickups.yaml` + `openapi.yaml` (key and `$ref` `~1agenda~1{day}`): parameter `date` → `day` (D5). Follow-ups: `PickupsController.getAgenda(UUID userId, LocalDate day)`, `client/src/app/features/pickups/pages/agenda-page.component.ts` (`AgendaQuery` and the `params` object; the route parameter `:date` stays), `server/src/test/java/network/foodsharing/store/StoreIdentityLeakIT.java:277` (`"date"` → `"day"`). Run `scripts/oasdiff.sh` (needs Docker).

**3. Generator bump** (own commit, D1)
- Set `openapi-generator.version` in the pom and `client/openapitools.json` to 7.26.0. In `configOptions`, replace `useSpringBoot3` with `useSpringBoot4` and add `useJspecify` true. Regenerate both sides and compile. Fix hand-written code only where the generated signatures changed. Check that `client/openapi-generator.json` options still apply (`ngVersion` 22).
- Expected result: the ExamplesUtils warning (7.26 only warns when non-JSON responses have examples) and the ~21k `org.springframework.lang.Nullable` deprecations are gone. Confirm with a lint compile (`-Dmaven.compiler.showDeprecation=true -Dmaven.compiler.showWarnings=true test-compile`, one-off, not committed).

**4. Java tests and Maven**
- `server/src/test/java/network/foodsharing/config/StructuredLoggingTest.java` (7×) and `automation/AdvertFlowIT.java` (2×): `JsonNode.asText()` → `asString()` (Jackson 3).
- `event/EventFlowIT.java` (lines 72, 75, 79, 119, 149): `(List<String>) JsonPath.read(...)` → `JsonPath.<List<String>>read(...)` or a typed local. Assertions stay the same.
- `server/pom.xml` ~line 457 (`copy-angular-resources`): `<overwrite>true</overwrite>` → `<changeDetection>` with the value equivalent to "always overwrite" (check the valid value against maven-resources-plugin 3.5.0).

**5. npm** (`client/package.json`, `package-lock.json`, `angular.json`)
- Remove `@angular/animations`: nothing imports it, and it is an optional peer of platform-browser.
- Bump `@angular/build` and `@angular/cli` to `^22.2.5` (vite 8.3.5 fixes the vite highs).
- `overrides`: `"minimatch": "^11.0.1"`, `"glob": "^14.0.2"`, `"basic-ftp": "^6.2.1"` (all dev-only: eslint chain and openapi-generator-cli → proxy-agent), and `"lodash.isequal": "npm:fast-deep-equal@3.1.3"` (D3). If one override breaks `npm run lint` or `openapi:generate`, drop only that one and report it. Then `npm install`, `npm ci`, `npm audit` (target: only the quill low finding is left).
- Leaflet: in `src/app/shared/map-view/map-view.component.ts`, import `leaflet/dist/leaflet-src.esm.js` (named exports, so the `default ?? module` dance goes away). Add a small `src/types/leaflet-esm.d.ts` (`declare module 'leaflet/dist/leaflet-src.esm.js' { export * from 'leaflet'; }`). Keep `globalThis.L ??= L`, because `protomaps-leaflet` extends the global. Make sure the vitest spec still loads.
- `angular.json` build options: `"allowedCommonJsDependencies": ["rbush", "quill-delta"]`. JSON allows no comments, so explain both in the decision: rbush 3 is pinned by protomaps-leaflet 5.1.0, quill-delta 5.1.0 is the latest version, and neither has an ESM build.

## UI
No visible change: the deleted keys aren't rendered, and the map and editor behave the same. No mockup and no screenshot baseline update. If a screenshot diff shows up anyway, it is a regression and gets reported.

## Tests
- No new tests (D4; the checker has no test suite, so running it is the verification).
- Existing ones that change on purpose: `StoreIdentityLeakIT` (parameter name), `StructuredLoggingTest`, `AdvertFlowIT`, `EventFlowIT` (API calls only, same assertions), the `map-view` spec if it mocks `import('leaflet')`.
- Run: `./mvnw verify` (unit + IT, ArchUnit), `npm test`, `npm run lint`, `npm run i18n:check`, and the Playwright specs for the map view, the mail compose editor (Quill) and the agenda page (grep their feature tags).

## Done when
- [ ] `./mvnw -DskipTests install` output (save it and grep `-i 'warn\|deprecat\|not ESM'`) has no warnings. The only allowed remains are the npm summary "1 low severity vulnerability" (quill, D2) and `[ERROR]` lines that are just stderr info.
- [ ] `npm run i18n:check` prints no `warning:` lines. The 4 dead keys are gone in all 5 languages.
- [ ] `npm audit` lists only quill. `npm ls @angular/animations lodash.isequal` shows no deprecated packages.
- [ ] Generator 7.26.0 in both pins (or reverted under the stop rule, with the reason in the report). `scripts/oasdiff.sh` passes.
- [ ] `./mvnw verify` and the e2e specs above are green, with no test skipped or weakened.
- [ ] Decision and ledger row written. `docs/dev-environment.md` mentions the generator version if it names it.

## Risks
- The 7.26 bump is the biggest change (all generated API code; jspecify nullness, `useSpringBoot4` defaults, possible TS client changes). The stop rule limits it.
- Major-version overrides inside tooling (minimatch 11, glob 14, basic-ftp 6) could break eslint or the generator CLI. The fallback is to drop that override.
- `fast-deep-equal` differs from lodash for Map/Set/Date/typed arrays. Delta ops are plain JSON, but check the mail editor by hand (bold, list, link, undo).
- The leaflet ESM build is a different entry point: a second Leaflet instance would break `protomaps-leaflet`. Check the basemap and markers in the e2e map spec.
- oasdiff could treat the path parameter rename as breaking. If so, the fallback per D5 is to keep `date`.
- The new block-comment regex is a heuristic. A real comment glued to a word character (`foo/* x */`) would now be counted as code; that's harmless (at worst a key counts as used).
