#!/usr/bin/env bash
# INDRA AI - start the backend (Linux / macOS / Git Bash on Windows)
set -euo pipefail
cd "$(dirname "$0")/.."

JAR="modules/indra-backend/build/libs/indra-backend-all.jar"
if [ ! -f "$JAR" ]; then
  echo "Backend jar not found - building it now (needs JDK 17+ and Gradle 8.x)..."
  (cd modules/indra-backend && gradle fatJar --no-daemon)
fi

if [ -z "${INDRA_JWT_SECRET:-}" ]; then
  echo "Note: INDRA_JWT_SECRET is not set. Development mode will use a temporary secret."
  echo "      Set a long random value before exposing this server to a network."
fi

exec java -jar "$JAR" serve
