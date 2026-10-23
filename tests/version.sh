#!/usr/bin/env bash
# Checks VERSION.md against Date Versioning (https://datevers.ing): the UTC release
# date as YY.MM.DD, plus -1, -2... for later releases the same day.
# Run with: bash tests/version.sh
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PASS=0
FAIL=0
check() { # check "description" command...
    local name="$1"; shift
    if "$@"; then PASS=$((PASS + 1)); echo "ok   - $name"; else FAIL=$((FAIL + 1)); echo "FAIL - $name"; fi
}

# The spec's own pattern (2000-2099 form); calendar validity is checked separately.
DATEVER='^[0-9]{2}\.(0[1-9]|1[0-2])\.(0[1-9]|[12][0-9]|3[01])(-[1-9][0-9]*)?$'
VERSION="$(tr -d '[:space:]' < "$ROOT/VERSION.md")"

is_datever()   { [[ "$VERSION" =~ $DATEVER ]]; }
is_real_date() { # an impossible date (26.02.30) fails or comes back different
    local d="${VERSION%%-*}" yy mm dd; IFS=. read -r yy mm dd <<< "$d"
    [ "$(date -u -d "20$yy-$mm-$dd" +%y.%m.%d 2>/dev/null)" = "$d" ]
}
not_future()   { [[ ! "${VERSION%%-*}" > "$(date -u +%y.%m.%d)" ]]; }
changelog_leads() {
    [ "$(grep -m1 -oE '^## v[^[:space:]]+' "$ROOT/CHANGELOG.md" | sed 's/^## v//')" = "$VERSION" ]
}

check "VERSION.md ($VERSION) is a Date Version (YY.MM.DD or YY.MM.DD-N)" is_datever
check "VERSION.md ($VERSION) is a real calendar date" is_real_date
check "VERSION.md ($VERSION) isn't dated in the future (UTC)" not_future
check "the newest '## v...' heading in CHANGELOG.md is VERSION.md" changelog_leads

echo "$PASS passed, $FAIL failed"
[ "$FAIL" -eq 0 ]
