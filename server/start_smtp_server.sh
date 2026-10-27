#!/usr/bin/env sh
# Local mail catcher for the `local` profile (spring.mail.host=localhost, port 2525).
# Install: brew install mailpit. Web UI and API: http://localhost:8025
exec mailpit --smtp 0.0.0.0:2525 --listen 0.0.0.0:8025 "$@"
