# Decisions

Decisions taken during the build where plan.md was ambiguous. Closest to foodsharing.network behaviour unless noted.

| # | Date | Topic | Decision | Why |
|---|---|---|---|---|
| 1 | 2026-10-27 | §14 feature IDs | §14 rows get IDs `EA1..` (§14.1 rows in order), `AU1..` (§14.2 rows), `SV1` (§14.3), `AD1` (§14.4) so they can be tagged in Playwright like §13 IDs. | plan.md gives §14 no IDs, but the coverage script needs them. |
| 2 | 2026-10-27 | Fake OIDC provider | The `local` and `e2e` profiles include an embedded fake OIDC provider (`/dev/oidc/**`, bean only in those profiles) that Spring's `oauth2-client` uses as the "google" registration. No Docker container is needed, so `local` runs fully offline. | Plan §18.3 suggests mock-oauth2-server; the embedded one gives the same OIDC flow without a container and works offline. |
| 3 | 2026-10-27 | Human-only steps | Usability tests with real Zürich users (§11.5, §18.5 round 3, §20 M7), native-speaker translation review, production deploy, real load test on prod hardware are replaced during the build by AI persona walkthroughs (screenshots reviewed against the checklist per persona), automated load test (k6-style script against the local jar) and machine translations. They are listed in `docs/launch-report.md` as needing a human before launch. | They require people or infrastructure that do not exist yet (domain and hosting are open, plan §21). |
