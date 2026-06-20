#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
ENGINE="$(find "$ROOT" -maxdepth 1 -name 'echo-standalone-engine-*.jar' | sort | head -1)"
if [[ -z "$ENGINE" ]]; then echo "ECHO engine JAR was not found in $ROOT" >&2; exit 1; fi
exec java -Dfile.encoding=UTF-8 -jar "$ENGINE" --pack-root "$ROOT" --manifest pack.json --save-root "$ROOT/saves" "$@"
