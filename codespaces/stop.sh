#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd -- "$project_dir"
docker compose down
printf '%s\n' 'Lab containers stopped; the database volume is retained.'
printf '%s\n' 'Also stop the Codespace from the Codespaces menu to stop active compute usage.'
