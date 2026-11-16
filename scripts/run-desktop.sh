#!/usr/bin/env bash
# INDRA AI - launch the Windows/Linux desktop app
set -euo pipefail
cd "$(dirname "$0")/../modules/indra-desktop"
exec gradle run --no-daemon
