# Secrets and environment inventory (D19.1)

Every `${VAR}` used by `application.properties` and `application-prod.properties`, plus the variables of `server/deploy/compose.yaml`. Values live only in `server/deploy/.env` on the host (mode `600`, owner `root`, never committed; template `server/deploy/.env.example`). `ProdSecretsInventoryTest` fails when a placeholder is added without a row here and a line in `.env.example`.

Legend — **Req**: `yes` = the prod profile does not start without it; `opt` = feature stays off / default applies. **Secret**: `S` = confidential, rotate on suspicion of leakage; `-` = plain configuration.

## Database

| Variable | Req | Secret | Generate / source | Rotation | Consequence of loss |
|---|---|---|---|---|---|
| `SPRING_DATASOURCE_URL` | yes | - | `jdbc:postgresql://postgres:5432/foodsharing` (compose service name) | - | - |
| `SPRING_DATASOURCE_USERNAME` | yes | - | `foodsharing` | - | - |
| `SPRING_DATASOURCE_PASSWORD` | yes | S | `openssl rand -hex 32` | `ALTER ROLE foodsharing PASSWORD '…'` in Postgres, update `.env`, `docker compose up -d app` | none (reset it in Postgres as superuser) |
| `POSTGRES_PASSWORD` | yes | S | same value as `SPRING_DATASOURCE_PASSWORD` (initialises the `postgres` container on first start only) | only used on an empty volume; rotate with `ALTER ROLE` | none |
| `DB_POOL_SIZE` | opt | - | default `20` (Hikari maximum pool size) | - | - |

## Application

| Variable | Req | Secret | Generate / source | Rotation | Consequence of loss |
|---|---|---|---|---|---|
| `APP_DOMAIN` | yes | - | public host name, e.g. `foodsharing.example.org` (passkey RP id) | changing it invalidates all registered passkeys | - |
| `APP_BASE_URL` | yes | - | `https://<APP_DOMAIN>` | - | - |
| `APP_IP_SALT` | yes | S | `openssl rand -hex 32` | changes every stored IP hash: rate-limit windows and IP blocks no longer match (blocks must be re-entered) | same as rotation |
| `APP_SIGNING_SECRET` | yes | S | `openssl rand -hex 32` | Google logins in progress (signed return cookie, minutes) fail once; users log in again | same as rotation |
| `NEWSLETTER_SECRET` | yes | S | `openssl rand -hex 32` | every unsubscribe link in already sent newsletters stops working (they never expire otherwise) | same as rotation |
| `OAUTH_SIGNING_KEY` | yes | S | `openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048` (PKCS#8 PEM; in `.env` as one line with `\n`) | access tokens of OAuth clients signed with the old key are rejected; clients refresh / re-authorise | same as rotation |
| `GOOGLE_CLIENT_ID` | yes | - | Google Cloud console → OAuth client (web), redirect `https://<APP_DOMAIN>/login/oauth2/code/google` | - | Google login fails until a new client is configured |
| `GOOGLE_CLIENT_SECRET` | yes | S | same console | create a second secret, deploy, delete the old one | Google login fails |
| `MAIL_FROM` | opt | - | default `noreply@foodsharing.network` | - | - |
| `UPLOADS_DIR` | opt | - | default `/data/uploads` (the `uploads` volume) | - | - |
| `PHOTON_URL` | opt | - | default `https://photon.komoot.io` | - | - |
| `MANAGEMENT_PORT` | opt | - | default `9090` (health + Prometheus, internal only) | - | - |
| `APP_VERSION` | opt | - | set by the image (`server/deploy/version.txt`); added to every JSON log line | - | - |
| `APP_VERSION_FILE` | opt | - | set by the image (`/app/version.txt`) | - | - |

## Mail (SMTP, ProtonMail)

| Variable | Req | Secret | Generate / source | Rotation | Consequence of loss |
|---|---|---|---|---|---|
| `SMTP_HOST` | opt | - | default `smtp.protonmail.ch` | - | - |
| `SMTP_PORT` | opt | - | default `587` (STARTTLS) | - | - |
| `SMTP_USERNAME` | yes | - | Proton → Settings → IMAP/SMTP → SMTP tokens (address) | - | - |
| `SMTP_PASSWORD` | yes | S | Proton SMTP token | create a new token, deploy, revoke the old one | mails stay in the outbox (retries with backoff) until a new token is deployed |

## Push and Telegram

| Variable | Req | Secret | Generate / source | Rotation | Consequence of loss |
|---|---|---|---|---|---|
| `PUSH_VAPID_PUBLIC` | yes | - | `npx web-push generate-vapid-keys` | rotate only together with the private key: every browser subscription becomes invalid, users re-enable push | all push subscriptions lost |
| `PUSH_VAPID_PRIVATE` | yes | S | same command | as above | as above |
| `PUSH_VAPID_SUBJECT` | opt | - | default `mailto:it@foodsharing.network` | - | - |
| `TELEGRAM_BOT_TOKEN` | opt | S | BotFather `/newbot` (`/revoke` to rotate) | linked chats keep working with the same bot | Telegram notifications fail (counted in `fs_notification_delivery_failures_total`) |
| `TELEGRAM_BOT_NAME` | opt | - | default `foodsharing_bot` | - | - |
| `TELEGRAM_WEBHOOK_SECRET` | opt | S | `openssl rand -hex 32`; set again with `setWebhook` | webhook calls answer 404 until Telegram uses the new secret | same |

## Mailbox (inbound mail)

| Variable | Req | Secret | Generate / source | Rotation | Consequence of loss |
|---|---|---|---|---|---|
| `MAILBOX_INBOUND` | opt | - | `log` (default, inbound off), `imap` or `webhook` | - | - |
| `MAILBOX_DOMAIN` | opt | - | mailbox domain, e.g. `lists.example.org` | - | - |
| `MAILBOX_IMAP_HOST` | opt | - | IMAP host of the catch-all mailbox | - | - |
| `MAILBOX_IMAP_USER` | opt | - | IMAP user | - | - |
| `MAILBOX_IMAP_PASSWORD` | opt | S | provider | change at provider, deploy | inbound mail stays on the IMAP server until fixed |
| `MAILBOX_WEBHOOK_SECRET` | opt | S | `openssl rand -hex 32` | update provider webhook config together | inbound webhook rejects mails |

## Wallet passes (off by default)

| Variable | Req | Secret | Generate / source | Rotation | Consequence of loss |
|---|---|---|---|---|---|
| `APPLE_WALLET_PASS_TYPE_ID` | opt | - | Apple developer account | - | - |
| `APPLE_WALLET_TEAM_ID` | opt | - | Apple developer account | - | - |
| `APPLE_WALLET_CERTIFICATE_P12` | opt | S | pass type certificate (base64 p12) | yearly (Apple expiry) | new passes cannot be signed |
| `APPLE_WALLET_CERTIFICATE_PASSWORD` | opt | S | chosen at export | with the certificate | as above |
| `APPLE_WALLET_WWDR_CERTIFICATE` | opt | - | Apple WWDR intermediate (PEM) | on Apple renewal | as above |
| `GOOGLE_WALLET_ISSUER_ID` | opt | - | Google Pay & Wallet console | - | - |
| `GOOGLE_WALLET_CLASS_ID` | opt | - | Google Pay & Wallet console | - | - |
| `GOOGLE_WALLET_SERVICE_ACCOUNT_JSON` | opt | S | service-account key JSON | new key, deploy, delete old key | new passes cannot be issued |

## Compose / deployment

| Variable | Req | Secret | Generate / source | Rotation | Consequence of loss |
|---|---|---|---|---|---|
| `IMAGE` | opt | - | default `ghcr.io/epsuchti/foodsharing-platform` | - | - |
| `VERSION` | yes | - | image tag to run (`server/deploy/version.txt` of the release) | change + `docker compose up -d app` = upgrade / rollback | - |
| `HTTP_BIND` | opt | - | default `127.0.0.1:8080` (reverse proxy in front) | - | - |
| `GRAFANA_ADMIN_PASSWORD` | opt | S | `openssl rand -hex 16` (only with `compose.monitoring.yaml`) | change in Grafana and `.env` | reset with `grafana cli admin reset-admin-password` |

The Alertmanager SMTP token lives in `server/deploy/monitoring/smtp_password` (file, `chmod 600`, git-ignored).

## Handling rules
- Generate secrets on the host, never paste them into chats or tickets; `.env` is in the backup set of the host (encrypted), not in the database dump.
- After a suspected leak rotate the affected row and record it in the incident log (runbook §9).
- Secrets never appear in logs: the JSON log contains no request bodies and no headers.
