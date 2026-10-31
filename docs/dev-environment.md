# Development environment

Applies to Linux, macOS and Windows (Git Bash). People and agents set up every shell the same way:

```bash
source scripts/env.sh
```

`scripts/env.sh`:
- finds a JDK 21+: `JAVA_HOME` if set, else `~/.jdks`, SDKMAN, `/Library/Java/JavaVirtualMachines`,
  `/usr/lib/jvm`, the usual Windows folders, then `java` on PATH
- puts the Node that Maven installs into `client/node` first on PATH
- adds the Docker Desktop CLI on Windows when it's missing from PATH
- reports what is missing; it installs nothing

## What you need
| Tool | Version | Why |
|---|---|---|
| JDK | 21+ (e.g. Temurin) | server build and tests |
| Node | 24.15+ | client; `./mvnw -DskipTests package` in `server/` installs it into `client/node`, so a system Node isn't required |
| Docker | Docker Desktop or Engine | the Postgres integration tests (Testcontainers) and the screenshot container |
| Git | any recent | – |

## Common commands (repo root, after `source scripts/env.sh`)
| What | Command |
|---|---|
| Build the jar (also installs `client/node` and runs `npm ci`) | `cd server && ./mvnw -q -DskipTests package` |
| Server unit tests | `cd server && ./mvnw -q test` |
| Everything incl. ITs | `cd server && ./mvnw verify` |
| Client API after a contract change | `cd client && npm run openapi:generate` |
| Client checks | in `client/`: `npm run lint`, `npm run i18n:check`, `npx ng test --watch=false` |
| e2e, parallel | `npm run e2e:sharded -- --project=chromium` (also `mobile-chrome`, `firefox`, `webkit`, `mobile-safari`) |
| Feature coverage | `node e2e/scripts/feature-coverage.mjs --results e2e/test-results/results.json --strict` |
| Screenshots, compare | `npm run e2e:screenshots -- --shards=4` |
| Local app | `cd server && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local` → http://localhost:8080, test-person switcher on `/login`, H2 data in `server/data/` |
| Mail for local login links | `server/start_smtp_server.sh` (Mailpit, UI http://localhost:8025) |

## Good to know (all systems)
- `client/src/app/api` is generated and never committed. Regenerate it when it's missing.
- The Maven build reinstalls `client/node_modules` (`npm ci`). Client lint and tests fail while it runs.
- `--update-snapshots` takes an optional mode. Write `--update-snapshots=changed`; a bare flag followed by a file
  path would swallow the path (the scripts normalise this).
- Lighthouse (`npx playwright test --project=lighthouse` in `e2e/`) uses Playwright's Chromium. Where the full
  browser can't be started, point `CHROME_PATH` at Playwright's headless shell.

## Windows only
- Use Git Bash, not PowerShell, for the commands above.
- A running app locks `server/target/*.jar`. Stop it before `./mvnw clean package`.
- Playwright's test worker crashes now and then (exit code 3221226505 / 0xC0000409) on a random test, which
  passes on rerun. It's infrastructure, not app code. The sharded runner turns video off, which lowers memory
  use.
- Shell scripts are checked in with LF line endings (`e2e/.gitattributes`), so they also run in the Linux
  screenshot container.
- There is no Python on the original machine; `scripts/ledger.py` needs it. Edit `docs/ledger.md` by hand
  otherwise (8 columns per feature row).
