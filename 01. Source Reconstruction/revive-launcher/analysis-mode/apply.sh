#!/usr/bin/env bash
# Apply (or, with -R, reverse) the analysis-mode patch against the launcher source.
# Works from any directory; operates on the launcher root (this script's parent's parent).
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
launcher="$(cd "$here/.." && pwd)"
patch="$here/analysis-mode.patch"

reverse=""
if [ "${1:-}" = "-R" ] || [ "${1:-}" = "--reverse" ]; then
  reverse="-R"
fi

cd "$launcher"
if ! git apply --check $reverse "$patch" 2>/dev/null; then
  if [ -n "$reverse" ]; then
    echo "Nothing to reverse: the source already looks clean." >&2
  else
    echo "Patch does not apply cleanly. Is the source already patched, or modified?" >&2
  fi
  exit 1
fi

git apply $reverse "$patch"
if [ -n "$reverse" ]; then
  echo "Reversed: launcher restored to the clean reconstruction."
else
  echo "Applied: launcher is now the analysis build. Reverse with: bash analysis-mode/apply.sh -R"
fi
