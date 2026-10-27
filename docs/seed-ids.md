# Seed ids

Fixed UUIDs of the seed data (`server/src/main/resources/db/seed/`, Liquibase context `seed`, loaded in the
`local` and `e2e` profiles). Format: `0000MMMM-TTTT-4000-8000-NNNNNNNNNNNN`:
`MMMM` = module code, `TTTT` = table code inside the module, `N` = sequence (decimal digits, zero-padded).
Seed time anchor: `SEED_NOW = 2026-11-02T09:00:00Z` (all seed timestamps are at or before it).

## Module and table codes

| Module | `MMMM` | Table | `TTTT` | Seed file |
|---|---|---|---|---|
| user | 0001 | `user_account` | 0001 | `user/user_account.csv` (`changes/001-user-seed-users.yaml`) |
| auth | 0002 | `auth_session` | 0001 | none (sessions are created by logins; `SeedUsers.TEST_SESSION_ID` uses `00000002-0001-…-001` in slice tests) |
| auth | 0002 | `auth_identity` | 0002 | `auth/auth_identity.csv` (`changes/002-auth-seed-identities.yaml`) |
| auth | 0002 | `email_blocklist` | 0003 | `auth/email_blocklist.csv` (`changes/003-auth-seed-blocklist.yaml`) |
| auth | 0002 | `login_token`, `passkey_credential`, `ip_block` | 0004, 0005, 0006 | reserved (no seed rows yet) |
| history | 0003 | `history_entry` | 0001 | `history/history_entry.csv` (`changes/004-history-seed.yaml`) |
| notification | 0004 | `mail_outbox` | 0001 | reserved |
| region | 0010 | | | M2 |
| group | 0011 | | | M2 |
| store | 0020 | | | M3 |
| pickup | 0021 | | | M3 |
| intropickup | 0030 | | | M3 |

Seed changelog numbers: M1 uses `001`–`004`; later milestones follow the same ranges as the schema changelogs.

## Users (plan §7.3)

All users have the email `<name>@local`, an EMAIL login identity (`00000002-0002-4000-8000-0000000000NN`, same `NN`
as the user) and the avatar `/images/avatars/<name>.svg` (`client/public/images/avatars/`). The dev user switcher
(`GET /api/dev/users`) lists them in this order with the description key `dev.users.<name>`.

| # | Id | Email | Name | Role | Verified | Language | Foodsaver no. | Journey stage |
|---|---|---|---|---|---|---|---|---|
| 1 | `00000001-0001-4000-8000-000000000001` | newbie@local | Lea Brunner | FOODSHARER | no | de | 100001 | just registered |
| 2 | `00000001-0001-4000-8000-000000000002` | profile@local | Noah Keller | FOODSHARER | no | en | 100002 | filling in the profile |
| 3 | `00000001-0001-4000-8000-000000000003` | quiz@local | Chiara Rossi | FOODSHARER | no | it | 100003 | taking the foodsaver quiz |
| 4 | `00000001-0001-4000-8000-000000000004` | einab1@local | Luca Meier | FOODSHARER | no | gws | 100004 | 1 of 3 introductory pickups done |
| 5 | `00000001-0001-4000-8000-000000000005` | pending@local | Amélie Dubois | FOODSHARER | no | fr | 100005 | waiting for the ID check |
| 6 | `00000001-0001-4000-8000-000000000006` | foodsaver@local | Sarah Müller | FOODSAVER | yes | de | 100006 | active foodsaver (also has a Google identity) |
| 7 | `00000001-0001-4000-8000-000000000007` | jumper@local | Jonas Weber | FOODSAVER | yes | gws | 100007 | jumper |
| 8 | `00000001-0001-4000-8000-000000000008` | sleepy@local | Marco Bernasconi | FOODSAVER | yes | it | 100008 | inactive (last login ~200 days ago) |
| 9 | `00000001-0001-4000-8000-000000000009` | bieb@local | Nina Schneider | STORE_MANAGER | yes | de | 100009 | store manager |
| 10 | `00000001-0001-4000-8000-000000000010` | guide@local | Julien Favre | FOODSAVER | yes | fr | 100010 | introductory pickup guide |
| 11 | `00000001-0001-4000-8000-000000000011` | bot@local | Reto Baumann | AMBASSADOR | yes | gws | 100011 | ambassador Zürich (also has a Google identity) |
| 12 | `00000001-0001-4000-8000-000000000012` | welcome@local | Priya Sharma | AMBASSADOR | yes | en | 100012 | welcome team |
| 13 | `00000001-0001-4000-8000-000000000013` | reports@local | Céline Rochat | AMBASSADOR | yes | fr | 100013 | report team |
| 14 | `00000001-0001-4000-8000-000000000014` | orga@local | Daniel Huber | ORGA | yes | de | 100014 | orga |

Google identities (fake OIDC provider, `/dev/oidc`): `00000002-0002-4000-8000-000000000015` foodsaver@local with
subject `fake-google-foodsaver`, `00000002-0002-4000-8000-000000000016` bot@local with subject `fake-google-bot`.
"New Google user" logins on the fake provider get the subject `fake-google-new-<sha256(email)[0..16]>`.

## Email blocklist

| Id | Pattern |
|---|---|
| `00000002-0003-4000-8000-000000000001` | mailinator.com |
| `00000002-0003-4000-8000-000000000002` | example.invalid |

## History

`00000003-0001-4000-8000-000000000001` … `…000000000047`, ordered by `occurred_at`: ACCOUNT_CREATED for every user,
ROLE_CHANGED promotions (actor orga@local), and LOGIN / LOGOUT / SETTINGS_CHANGED / FIELD_CHANGED / ACCOUNT_LINKED /
PASSKEY_REGISTERED / SESSIONS_REVOKED_ALL entries for foodsaver@local, orga@local and newbie@local.
