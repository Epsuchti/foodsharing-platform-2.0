# Monitoring and alerting (D19.3)

Prometheus scrapes `/actuator/prometheus` on the management port (`app:9090`, compose network only). Files: `server/deploy/monitoring/{prometheus.yml,alerts.yml,alertmanager.yml,grafana-dashboard.json,grafana-provisioning/}`, started with

```sh
cd server/deploy
docker compose -f compose.yaml -f compose.monitoring.yaml up -d
ssh -L 3000:127.0.0.1:3000 -L 9091:127.0.0.1:9091 <host>   # Grafana / Prometheus UIs
```

`MetricsContractTest` fails when an alert or dashboard panel uses a metric that the application does not export (every `fs_*`, `http_server_requests_*`, `jvm_*`, `hikaricp_*` name is checked against a real scrape).

## Metrics audit

Standard metrics (Spring Boot / Micrometer): `http_server_requests_seconds_*` (with histogram buckets for p95/p99, tags `uri`, `status`, `method`), `jvm_*`, `process_*`, `hikaricp_connections_*`, `tomcat_*`, `logback_events_total`, `executor_*`.

Operations metrics of the platform (`common/metrics/OperationsMetrics`):

| Metric | Type | Tags | Source | Used by |
|---|---|---|---|---|
| `fs_outbox_pending` | gauge | – | `mail_outbox` rows in `PENDING` (DB, cached 15 s) | dashboard |
| `fs_outbox_failed` | gauge | – | `mail_outbox` rows in `FAILED` (max attempts reached) | alert `MailOutboxFailures` |
| `fs_outbox_oldest_pending_age_seconds` | gauge | – | seconds since the oldest due `PENDING` mail became due (rate-limited mails scheduled later do not count) | alert `MailOutboxStuck` |
| `fs_notification_delivery_failures_total` | counter | `channel` = `mail`, `push`, `telegram` | failed SMTP send attempt, failed Web Push delivery, failed Telegram call | alert `NotificationDeliveryFailures` |
| `fs_circuit_breaker_state` | gauge | `integration` (SMTP, WEB_PUSH, TELEGRAM, GEOCODING, …) | `IntegrationRegistry` breaker: 0 closed, 1 half-open, 2 open | alerts `CircuitBreakerOpen`, `SmtpBreakerOpen` |
| `fs_job_duration_seconds` | timer | `name`, `outcome` = `OK`/`FAILED` | every scheduled / manual job run (`JobRunner`) | dashboard |
| `fs_job_last_success_timestamp_seconds` | gauge | `name` | epoch seconds of the last successful run (0 = none since start) | alert `JobNotSucceeding` |
| `fs_job_consecutive_failures` | gauge | `name` | failures in a row (reset on success) | alert `JobFailing` |
| `fs_async_jobs_running` | gauge | – | background jobs (imports, exports) in `RUNNING` (`ProgressTracker`) | alert `AsyncJobsPiling` |
| `fs_websocket_connected_users` | gauge | – | users with an open STOMP session | dashboard |
| `fs_rate_limit_rejections_total` | counter | `limiter` | every rejection of a `SlidingWindowRateLimiter` (login-ip, login-email, basket-requests, client-errors, group-mails, support, messages) | alert `RateLimitSpike` |
| `fs_login_failures_total` | counter | `reason` = `invalid-token`, `expired-token`, `passkey` | failed magic-link / passkey logins | alert `LoginFailureSpike` |

Metrics contain no personal data (no user ids, emails or IPs as tags).

## Alerts

| Alert | Severity | Condition | First step |
|---|---|---|---|
| `AppDown` | page | target down 2 min | runbook §6.1, §5 |
| `HighServerErrorRate` | page | > 2 % 5xx over 5 min | runbook §9.2 (log by traceId) |
| `SlowRequests` | ticket | p95 > 500 ms for 15 min | dashboard latency, `docs/load-test.md` |
| `DatabasePoolExhausted` | page | > 5 waiting connections for 5 min | runbook §6.5 |
| `HeapAlmostFull` | ticket | heap > 90 % for 10 min | restart in a quiet hour, raise memory limit |
| `MailOutboxStuck` | page | oldest pending mail > 30 min | runbook §6.3 |
| `MailOutboxFailures` | ticket | new FAILED mails in 1 h | runbook §6.3 |
| `CircuitBreakerOpen` / `SmtpBreakerOpen` | ticket / page | breaker open 15 / 10 min | runbook §6.4 |
| `JobFailing` | ticket | two failures in a row | runbook §6.2 |
| `JobNotSucceeding` | ticket | no success for 2 days | runbook §6.2 |
| `NotificationDeliveryFailures` | ticket | > 50 failures / 30 min per channel | check provider status, keys (`docs/secrets.md`) |
| `LoginFailureSpike` | ticket | > 100 failed logins / 15 min | runbook §9.4 |
| `RateLimitSpike` | ticket | > 200 rejections / 15 min per limiter | runbook §9.4 |
| `AsyncJobsPiling` | ticket | > 10 running background jobs for 15 min | runbook §6.2 |

Routing: `alertmanager.yml` mails the IT team; `page` alerts repeat every 30 min, `ticket` alerts daily. Not covered by Prometheus (host level): disk space, certificate expiry and backup freshness — the host's cron mails the output of `backup.sh` / `verify-backup.sh` (runbook §7) and `df -h` checks run there.
