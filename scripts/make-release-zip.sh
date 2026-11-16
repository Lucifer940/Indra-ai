#!/usr/bin/env bash
# INDRA AI - create the source release archive in dist/
set -euo pipefail
cd "$(dirname "$0")/.."

VERSION=$(cat VERSION)
OUT="dist/indra-ai-v${VERSION}-source.zip"
mkdir -p dist
rm -f "$OUT"

command -v zip >/dev/null 2>&1 || { echo "The 'zip' command is required." >&2; exit 1; }

zip -r "$OUT" . \
  -x ".git/*" ".git" "*/.git/*" ".gradle/*" "*/.gradle/*" ".idea/*" "*/.idea/*" \
  -x "build/*" "*/build/*" "node_modules/*" "*/node_modules/*" \
  -x "dist/*" "*.zip" "*.apk" "*.aab" "*.jar" "*.jks" "*.keystore" "*.env" \
  -x "data/*" "*/data/*" "logs/*" "*/logs/*" "*.log" "local.properties" "secrets.properties"

echo "Created $OUT"
unzip -l "$OUT" | tail -n 3
