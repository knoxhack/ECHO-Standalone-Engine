#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
ENGINE="$ROOT/dist/echo-standalone-engine-2.0.0-beta.5.jar"
if [[ ! -f "$ENGINE" ]]; then
  echo "Missing engine JAR: $ENGINE. Run scripts/build.sh first." >&2
  exit 1
fi
exec java -Dfile.encoding=UTF-8 -jar "$ENGINE" --pack-root "$ROOT/dist" --manifest pack.json --save-root "$ROOT/saves" "$@"
