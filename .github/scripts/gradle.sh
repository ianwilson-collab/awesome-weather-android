#!/usr/bin/env bash
# Runs Gradle. If it fails, the key errors are posted as notes on the run page (they show without opening the log).
set -uo pipefail
log=$(mktemp)
./gradlew --no-daemon "$@" 2>&1 | tee "$log"
status=${PIPESTATUS[0]}
[ "$status" -eq 0 ] && exit 0

note() { # title, text → one multi-line annotation
  local text
  text=$(printf '%s' "$2" | head -c 60000 | sed -e 's/%/%25/g' | sed -e ':a;N;$!ba;s/\r//g;s/\n/%0A/g')
  [ -n "$text" ] && echo "::error title=$1::$text"
}

note "Compile errors" "$(grep -E '^e: |error: |AAPT: error|Unresolved reference' "$log" | sed 's#file:///home/runner/work/[^/]*/[^/]*/##' | head -60)"
note "Failing tests" "$(for f in app/build/test-results/*/*.xml app/build/outputs/androidTest-results/connected/**/*.xml; do
  [ -f "$f" ] && grep -B1 -A3 '<failure' "$f" | grep -E 'testcase|<failure' | sed -E 's/.*name="([^"]+)".*/\1/; s/.*message="([^"]{0,400}).*/   \1/'
done 2>/dev/null | head -60)"
note "What went wrong" "$(sed -n '/What went wrong/,/^\* Try/p' "$log" | head -40)"
exit "$status"
