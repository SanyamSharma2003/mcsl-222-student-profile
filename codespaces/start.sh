#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd -- "$project_dir"

if [[ "${CODESPACES:-}" != "true" || -z "${CODESPACE_NAME:-}" ]]; then
  printf '%s\n' 'Run this script in the GitHub Codespaces terminal.' >&2
  exit 1
fi
forward_domain="${GITHUB_CODESPACES_PORT_FORWARDING_DOMAIN:-}"
if [[ ! "$CODESPACE_NAME" =~ ^[a-zA-Z0-9-]+$ || ! "$forward_domain" =~ ^[a-zA-Z0-9.-]+$ ]]; then
  printf '%s\n' 'GitHub has not supplied a valid Codespace name and forwarded-port domain.' >&2
  exit 1
fi

profile_url="https://${CODESPACE_NAME}-8443.${forward_domain}/student-profile/profile"
login_url="https://${CODESPACE_NAME}-8443.${forward_domain}/student-profile/login"
inbox_url="https://${CODESPACE_NAME}-8025.${forward_domain}/"

# Regenerate this exact-instance URL before building so confirmation emails work.
sed "s#https://localhost:8443/student-profile/profile#${profile_url}#g" \
  config/student-profile.xml.template > config/student-profile.xml

if ! command -v docker >/dev/null 2>&1; then
  printf '%s\n' 'Docker is not available in this Codespace. Ensure .devcontainer/devcontainer.json is at the repository root, then use Codespaces: Rebuild Container.' >&2
  exit 1
fi
docker compose version
docker_ready=false
for attempt in {1..30}; do
  if docker info >/dev/null 2>&1; then docker_ready=true; break; fi
  sleep 2
done
if [[ "$docker_ready" != true ]]; then
  printf '%s\n' 'The Codespace container engine is not ready. Check the Codespaces creation log and try this script again.' >&2
  exit 1
fi

docker compose up --build -d
app_ready=false
for attempt in {1..90}; do
  # Only the generated self-signed localhost backend certificate is ignored here.
  # The browser uses GitHub's forwarded HTTPS URL and its normal trusted certificate.
  if curl --insecure --fail --silent --show-error --max-time 5 \
      https://localhost:8443/student-profile/login >/dev/null 2>&1; then
    app_ready=true
    break
  fi
  sleep 2
done
if [[ "$app_ready" != true ]]; then
  docker compose ps
  docker compose logs --tail=80 app
  printf '%s\n' 'The login page is not ready. Review the logs above; do not change local organisation security settings.' >&2
  exit 1
fi

printf '\n%s\n' 'The lab is ready. Open these URLs in separate browser tabs:'
printf 'Application: %s\n' "$login_url"
printf 'Test inbox:  %s\n' "$inbox_url"
printf '%s\n' 'Login: demo1 / LabDemo!2026'
printf '%s\n' 'Lookup: 6000000001 or 9000000001'
printf '%s\n' 'In PORTS, keep both ports Private. Port 8443 must use HTTPS; port 8025 uses HTTP.'
